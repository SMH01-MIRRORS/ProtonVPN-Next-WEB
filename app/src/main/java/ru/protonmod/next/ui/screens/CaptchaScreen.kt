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

package ru.protonmod.next.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.utils.DeviceInfoProvider
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.ui.utils.isTablet
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptchaScreen(
    webUrl: String,
    sessionId: String?,
    isApiBypassEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onCaptchaSolved: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    val isTablet = isTablet()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.captcha_title),
                            color = colors.textNorm,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.desc_close),
                                tint = colors.textNorm
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.backgroundNorm
                    )
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.brandNorm,
                        trackColor = colors.shade20
                    )
                }
            }
        },
        containerColor = colors.backgroundNorm
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.backgroundNorm),
            horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Surface(
                modifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth(),
                color = colors.backgroundNorm,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.captcha_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak
                    )

                    if (isApiBypassEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.captcha_proxy_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Box(
                modifier = (if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth())
                    .weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)

                            val proxyBaseUrl = "https://shimmering-stroopwafel-51675e.netlify.app"

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            val customUserAgent = DeviceInfoProvider.getSpoofedUserAgent()
                            settings.userAgentString = customUserAgent

                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            val jsInterface = object {
                                @JavascriptInterface
                                fun dispatch(response: String) {
                                    try {
                                        ProtonLogger.d("CaptchaScreen", "JS Dispatch: $response")
                                        val json = JSONObject(response)
                                        val type = json.optString("type")

                                        if (type == "HUMAN_VERIFICATION_SUCCESS" || type == "Success") {
                                            val payload = json.optJSONObject("payload")
                                            val token = payload?.optString("token")

                                            if (!token.isNullOrEmpty()) {
                                                coroutineScope.launch {
                                                    onCaptchaSolved(token)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        ProtonLogger.e("CaptchaScreen", "JS Parse Error", e)
                                    }
                                }
                            }

                            addJavascriptInterface(jsInterface, "AndroidInterface")

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                    if (newProgress >= 90) {
                                        isLoading = false
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                private val okHttpClient = OkHttpClient.Builder().build()

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    ProtonLogger.e("CaptchaScreen", "WebView renderer process gone (crashed: ${detail?.didCrash()})")
                                    // Handle the renderer crash gracefully by dismissing the captcha screen
                                    // Returning true prevents the entire app from being killed by the system
                                    onDismiss()
                                    return true
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    if (!isApiBypassEnabled) return super.shouldInterceptRequest(view, request)

                                    val originalUrl = request.url.toString()
                                    if (request.method != "GET") return super.shouldInterceptRequest(view, request)

                                    var targetProxyUrl: String? = null
                                    if (originalUrl.startsWith("https://verify.proton.me")) {
                                        targetProxyUrl = originalUrl.replace("https://verify.proton.me", "$proxyBaseUrl/verify")
                                    } else if (originalUrl.startsWith("https://verify-api.proton.me")) {
                                        targetProxyUrl = originalUrl.replace("https://verify-api.proton.me", "$proxyBaseUrl/verify-api")
                                    }

                                    if (targetProxyUrl != null) {
                                        try {
                                            val okRequest = Request.Builder()
                                                .url(targetProxyUrl)
                                                .apply {
                                                    request.requestHeaders?.forEach { (key, value) ->
                                                        if (!key.equals("Host", ignoreCase = true)) {
                                                            addHeader(key, value)
                                                        }
                                                    }
                                                }
                                                .build()

                                            val response = okHttpClient.newCall(okRequest).execute()
                                            val contentTypeHeader = response.header("Content-Type", "application/octet-stream") ?: "application/octet-stream"
                                            val mimeType = contentTypeHeader.substringBefore(";").trim()
                                            val encoding = if (contentTypeHeader.contains("charset=")) {
                                                contentTypeHeader.substringAfter("charset=").substringBefore(";").trim()
                                            } else {
                                                "utf-8"
                                            }

                                            val responseHeaders = response.headers.toMap().toMutableMap()
                                            responseHeaders.keys.filter { it.equals("Content-Security-Policy", ignoreCase = true) }
                                                .forEach { responseHeaders.remove(it) }
                                            responseHeaders["Access-Control-Allow-Origin"] = "*"

                                            var bodyStream = response.body.byteStream()
                                            if (mimeType.contains("text/html", ignoreCase = true)) {
                                                val html = response.body.string()
                                                val jsInject = """
                                                    <script>
                                                    (function() {
                                                        var proxyBase = '$proxyBaseUrl';
                                                        function rewriteUrl(url) {
                                                            if (typeof url !== 'string') return url;
                                                            if (url.startsWith('https://verify-api.proton.me')) {
                                                                return url.replace('https://verify-api.proton.me', proxyBase + '/verify-api');
                                                            }
                                                            if (url.startsWith('https://verify.proton.me')) {
                                                                return url.replace('https://verify.proton.me', proxyBase + '/verify');
                                                            }
                                                            return url;
                                                        }
                                                        var origFetch = window.fetch;
                                                        window.fetch = function() {
                                                            if (arguments[0] instanceof Request) {
                                                                var newUrl = rewriteUrl(arguments[0].url);
                                                                if (newUrl !== arguments[0].url) {
                                                                    arguments[0] = new Request(newUrl, arguments[0]);
                                                                }
                                                            } else {
                                                                arguments[0] = rewriteUrl(arguments[0]);
                                                            }
                                                            return origFetch.apply(this, arguments);
                                                        };
                                                        var origOpen = XMLHttpRequest.prototype.open;
                                                        XMLHttpRequest.prototype.open = function() {
                                                            arguments[1] = rewriteUrl(arguments[1]);
                                                            return origOpen.apply(this, arguments);
                                                        };
                                                    })();
                                                    </script>
                                                """.trimIndent()

                                                val injectedHtml = if (html.contains("<head>", ignoreCase = true)) {
                                                    html.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>\n$jsInject")
                                                } else {
                                                    jsInject + html
                                                }
                                                bodyStream = ByteArrayInputStream(injectedHtml.toByteArray())
                                            }

                                            return WebResourceResponse(mimeType, encoding, 200, "OK", responseHeaders, bodyStream)
                                        } catch (e: Exception) {
                                            ProtonLogger.e("CaptchaScreen", "Proxy Error", e)
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            // When API bypass is enabled, proxy the captcha URL through the
                            // netlify proxy so that captcha requests are not blocked by regional
                            // restrictions. The interceptor handles subsequent resource requests,
                            // but the initial page load URL must also be rewritten here.
                            val effectiveWebUrl = if (isApiBypassEnabled) {
                                when {
                                    webUrl.startsWith("https://verify-api.proton.me") ->
                                        webUrl.replace("https://verify-api.proton.me", "$proxyBaseUrl/verify-api")
                                    webUrl.startsWith("https://verify.proton.me") ->
                                        webUrl.replace("https://verify.proton.me", "$proxyBaseUrl/verify")
                                    else -> webUrl
                                }
                            } else {
                                webUrl
                            }

                            val optimizedUrl = buildString {
                                append(effectiveWebUrl)
                                if (!effectiveWebUrl.contains("?")) append("?") else append("&")
                                append("embed=true&theme=1&vpn=true")
                            }

                            val extraHeaders = mutableMapOf(
                                "x-pm-appversion" to "android-vpn@${DeviceInfoProvider.SPOOFED_APP_VERSION}-dev+play",
                                "x-pm-apiversion" to "4",
                                "Accept" to "application/vnd.protonmail.v1+json"
                            )
                            if (sessionId != null) {
                                extraHeaders["x-pm-uid"] = sessionId
                            }
                            loadUrl(optimizedUrl, extraHeaders)
                        }
                    },
                    update = { /* No-op */ }
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoading,
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.backgroundNorm),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.brandNorm)
                    }
                }
            }
        }
    }
}
