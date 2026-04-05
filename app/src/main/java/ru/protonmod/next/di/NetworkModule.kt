/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import retrofit2.Retrofit
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.*
import ru.protonmod.next.data.network.ota.UpdateApi
import ru.protonmod.next.data.repository.AuthRepository
import ru.protonmod.next.utils.DeviceInfoProvider
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.vpn.AmneziaVpnManager
import org.amnezia.awg.backend.Tunnel
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PROTON_PROXY_NETLIFY_URL = "https://shimmering-stroopwafel-51675e.netlify.app/"
    private const val PROTON_PROXY_CLOUDFLARE_URL = "https://api.protonnext.qzz.io/"
    private const val PROTON_DIRECT_URL = "https://vpn-api.proton.me/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun buildDnsOverHttps(bootstrapClient: OkHttpClient): DnsOverHttps {
        return DnsOverHttps.Builder().client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        sessionDao: SessionDao,
        authRepositoryProvider: Provider<AuthRepository>
    ): TokenAuthenticator {
        return TokenAuthenticator(sessionDao, authRepositoryProvider)
    }

    /**
     * Helper function to determine if API requests should be routed through the bypass proxy.
     * Evaluates active VPN states (both app-level and OS-level) and user preferences.
     */
    private fun shouldUseApiBypass(
        context: Context,
        vpnManagerProvider: Provider<AmneziaVpnManager>,
        settingsManagerProvider: Provider<SettingsManager>
    ): Boolean {
        // 1. If our VPN tunnel is active, bypass is not needed
        // Using provider.get() here is safe because this function is called inside interceptors/DNS
        // which run on background threads, OR it's called during OkHttp init which we've made safer.
        val vpnManager = vpnManagerProvider.get()
        if (vpnManager.tunnelState.value == Tunnel.State.UP) return false

        // 2. If a third-party VPN is active at the OS level, bypass is not needed
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                return false
            }
        } catch (e: Exception) {
            // Ignore potential permission issues and fallback to reading settings
        }

        // 3. Read user preferences synchronously.
        val settingsManager = settingsManagerProvider.get()
        return settingsManager.isApiBypassEnabledSync()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        vpnManagerProvider: Provider<AmneziaVpnManager>,
        settingsManagerProvider: Provider<SettingsManager>,
        tokenAuthenticator: TokenAuthenticator,
        dohFallbackInterceptor: DohFallbackInterceptor,
        dohFallbackStore: DohFallbackStore
    ): OkHttpClient {
        try {
            OkHttp.initialize(context)
        } catch (e: Throwable) {}

        val protonDirectHost = PROTON_DIRECT_URL.toHttpUrl().host
        val protonNetlifyHost = PROTON_PROXY_NETLIFY_URL.toHttpUrl().host
        val protonCloudflareHost = PROTON_PROXY_CLOUDFLARE_URL.toHttpUrl().host

        val certificatePinner = CertificatePinner.Builder()
            .apply {
                val allPins = NetworkConstants.DEFAULT_SPKI_PINS + NetworkConstants.ALTERNATIVE_API_SPKI_PINS
                listOf(
                    "vpn-api.proton.me",
                    "api.protonmail.ch",
                    "api.protonvpn.ch",
                    "*.proton.me",
                    "*.protonmail.ch",
                    "*.protonvpn.ch",
                    "*.qzz.io",
                    "*.netlify.app"
                ).forEach { host ->
                    allPins.forEach { pin ->
                        add(host, "sha256/$pin")
                    }
                }
            }
            .build()

        // Interceptor to dynamically swap the base URL depending on bypass rules
        val dynamicBaseUrlInterceptor = Interceptor { chain ->
            val request = chain.request()
            val originalUrl = request.url
            val userAgent = DeviceInfoProvider.getSpoofedUserAgent()
            
            // Only rewrite if it's a Proton API request (direct or through one of the proxies)
            val isProtonApi = (originalUrl.host == protonDirectHost || 
                              originalUrl.host == "api.protonmail.ch" ||
                              originalUrl.host == "api.protonvpn.ch" ||
                              originalUrl.host == "api.protonmail.com" ||
                              originalUrl.host == "mail.proton.me" ||
                              originalUrl.host == protonNetlifyHost ||
                              originalUrl.host == protonCloudflareHost)
            
            if (!isProtonApi) {
                // For non-Proton requests (like OTA mirrors), ensure we still provide a standard User-Agent.
                // Some hosting providers return 404 or 403 for requests without a User-Agent.
                val builder = request.newBuilder()
                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", userAgent)
                }
                // Add a generic Accept header if not present
                if (request.header("Accept") == null) {
                    builder.header("Accept", "application/json, text/plain, */*")
                }
                return@Interceptor chain.proceed(builder.build())
            }

            val spoofedVersion = DeviceInfoProvider.SPOOFED_APP_VERSION

            val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
            val settings = settingsManagerProvider.get()
            val strategy = settings.getApiBypassStrategySync()
            
            if (useProxy && strategy == SettingsManager.STRATEGY_PROTON_MIRRORS) {
                // For Proton Mirrors strategy, we rely on DohFallbackInterceptor and dynamicDns
                // No URL rewriting needed here, just proceed with original Host and let DNS handle it.
                val builder = request.newBuilder()
                    .addHeader("User-Agent", userAgent)
                    .addHeader("x-pm-appversion", "android-vpn@$spoofedVersion-dev+play")
                    .addHeader("x-pm-apiversion", "4")
                    .addHeader("Accept", "application/vnd.protonmail.v1+json")
                    .apply {
                        if (settings.isSpoofCountryEnabledSync()) {
                            if (!settings.isSpoofCountryNullSync()) {
                                val code = settings.getSpoofCountryCodeSync().uppercase()
                                if (code.length == 2) addHeader("x-pm-country", code)
                            }
                        }
                    }
                
                // Ensure correct Host header is set even if rewritten by other mechanisms
                builder.header("Host", originalUrl.host)
                
                return@Interceptor chain.proceed(builder.build())
            }

            val proxyBaseUrl = if (strategy == SettingsManager.STRATEGY_CLOUDFLARE) {
                PROTON_PROXY_CLOUDFLARE_URL
            } else {
                PROTON_PROXY_NETLIFY_URL
            }

            val newBaseUrl = if (useProxy) proxyBaseUrl.toHttpUrl() else PROTON_DIRECT_URL.toHttpUrl()

            val newUrl = originalUrl.newBuilder()
                .scheme(newBaseUrl.scheme)
                .host(newBaseUrl.host)
                .port(newBaseUrl.port)
                .build()

            val newRequest = request.newBuilder()
                .url(newUrl)
                .addHeader("User-Agent", userAgent)
                .addHeader("x-pm-appversion", "android-vpn@$spoofedVersion-dev+play")
                .addHeader("x-pm-apiversion", "4")
                .addHeader("Accept", "application/vnd.protonmail.v1+json")
                .apply {
                    val settings = settingsManagerProvider.get()
                    if (settings.isSpoofCountryEnabledSync()) {
                        if (settings.isSpoofCountryNullSync()) {
                            // Null spoofing means no x-pm-country header is sent.
                            // Some versions of the backend may fallback to IP-based detection.
                        } else {
                            val code = settings.getSpoofCountryCodeSync().uppercase()
                            if (code.length == 2) {
                                addHeader("x-pm-country", code)
                            }
                        }
                    }
                }
                .build()

            try {
                chain.proceed(newRequest)
            } catch (e: Exception) {
                // Log network errors for debugging lifecycle issues
                if (e is SocketTimeoutException || e is ConnectException) {
                    ProtonLogger.w("NetworkModule", "Network timeout during ${newRequest.url}: ${e.message}")
                }
                throw e
            }
        }

        // Bootstrap client for DNS over HTTPS requires longer timeouts
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val doh = buildDnsOverHttps(bootstrapClient)

        val trustManager = MirrorTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)

        val hostnameVerifier = HostnameVerifier { hostname, session ->
            val standardVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            if (standardVerifier.verify(hostname, session)) return@HostnameVerifier true

            // If standard verification fails (likely because of IP or decoy domain),
            // we check if the certificate is one we trust via pinning.
            val allPins = NetworkConstants.DEFAULT_SPKI_PINS + NetworkConstants.ALTERNATIVE_API_SPKI_PINS
            val isIp = hostname.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))
            
            if (isIp || hostname.endsWith(".qzz.io") || hostname.endsWith(".netlify.app") || 
                hostname == "vpn-api.proton.me" || hostname == "api.protonmail.ch") {
                 return@HostnameVerifier PinVerifier.check(session, allPins)
            }

            false
        }

        // Dynamic DNS configuration
        val dynamicDns = Dns { hostname ->
            val result = mutableListOf<InetAddress>()
            
            // Check DoH Fallback Store first
            val fallbackIps = dohFallbackStore.getFallbackIps(hostname)
            if (!fallbackIps.isNullOrEmpty()) {
                ProtonLogger.i("NetworkManager", "Using fallback IPs from DoH store for $hostname")
                result.addAll(fallbackIps)
            } else {
                val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
                
                if (useProxy) {
                    try {
                        result.addAll(doh.lookup(hostname))
                    } catch (e: Exception) {
                        result.addAll(Dns.SYSTEM.lookup(hostname))
                    }
                } else {
                    try {
                        // Try system DNS first
                        result.addAll(Dns.SYSTEM.lookup(hostname))
                    } catch (e: Exception) {
                        // Fallback to DoH if system DNS fails (helps bypass some blocks)
                        try {
                            result.addAll(doh.lookup(hostname))
                        } catch (ignore: Exception) {
                            throw e // Throw original exception if both fail
                        }
                    }
                }
            }

            // Log the resolve result for debugging connectivity issues in restricted regions
            ProtonLogger.i("NetworkManager", "Resolved $hostname to: ${result.joinToString(", ") { it.hostAddress ?: "unknown" }}")
            
            result
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(dohFallbackInterceptor)
            .authenticator(tokenAuthenticator)
            .dns(dynamicDns)
            .certificatePinner(certificatePinner)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
            // Reduced timeouts to detect network failures faster and prevent JNI reference leaks
            // Original: 30s connect timeout. On mobile, 15s is more responsive and safer.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // The base URL provided here is just a placeholder, the interceptor rewrites it
            .baseUrl(PROTON_DIRECT_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideProtonAuthApi(retrofit: Retrofit): ProtonAuthApi = retrofit.create(ProtonAuthApi::class.java)

    @Provides
    @Singleton
    fun provideProtonVpnApi(retrofit: Retrofit): ProtonVpnApi = retrofit.create(ProtonVpnApi::class.java)

    @Provides
    @Singleton
    fun provideUpdateApi(retrofit: Retrofit): UpdateApi = retrofit.create(UpdateApi::class.java)
}