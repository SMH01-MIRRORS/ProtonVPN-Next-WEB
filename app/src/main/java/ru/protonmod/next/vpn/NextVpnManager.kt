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

package ru.protonmod.next.vpn

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window
import dagger.hilt.android.EntryPointAccessors
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.protonmod.next.di.AppEntryPoint
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextVpnManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    data class NativeResponse(val code: Int, val body: String)

    init {
        System.loadLibrary("next")
        instance = this
    }

    fun setState(state: AmneziaVpnManager.VpnState) {
        setStateNative(state.ordinal)
    }

    fun getState(): AmneziaVpnManager.VpnState {
        val stateInt = getStateNative()
        return AmneziaVpnManager.VpnState.entries[stateInt]
    }

    fun canConnect() = canConnectNative()
    fun canDisconnect() = canDisconnectNative()

    fun performLegacyIntegrityCheck(): Boolean {
        // Advanced dynamic integrity check using a combination of checksums and behavior analysis
        // This is a honeypot method; actual integrity checks are also done in native code.
        ProtonLogger.d("NextVpnManager", "Performing background integrity check...")
        val isTampered = isTamperDetected()
        if (isTampered) {
            ProtonLogger.e("NextVpnManager", "INTEGRITY CHECK FAILED!")
        }
        return !isTampered
    }

    fun isTamperDetected() = isTamperDetectedNative()
    fun getProtectedString(locale: String, key: String) = getProtectedStringNative(locale, key)

    fun setLogcatEnabled(enabled: Boolean) {
        setLogcatEnabledNative(enabled)
        ProtonLogger.isLogcatEnabled = enabled
    }

    private external fun setStateNative(state: Int)
    private external fun getStateNative(): Int
    private external fun canConnectNative(): Boolean
    private external fun canDisconnectNative(): Boolean

    private external fun isTamperDetectedNative(): Boolean
    private external fun getProtectedStringNative(locale: String, key: String): String

    private external fun setLogcatEnabledNative(enabled: Boolean)

    companion object {
        var isWarningShown = false
        private var instance: NextVpnManager? = null

        /**
         * Honeypot: A constant that looks like a security key.
         * Modders often try to change such constants to "bypass" checks.
         */
        const val SECURITY_VERIFICATION_TOKEN = "7b74cef88678ecb3e6047ac6b4abf139"

        @JvmStatic
        fun performNativeRequest(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String?
        ): NativeResponse {
            ProtonLogger.d("NextVpnManager", "Native Request: $method $url")
            val client = instance?.okHttpClient ?: OkHttpClient()
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            
            val requestBody = body?.toRequestBody("application/json".toMediaTypeOrNull())
            requestBuilder.method(method, requestBody)
            
            return try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    ProtonLogger.v("NextVpnManager", "Native Response [$url]: ${response.code}")
                    NativeResponse(response.code, responseBody)
                }
            } catch (e: Exception) {
                ProtonLogger.e("NextVpnManager", "Native Request Failed [$url]", e)
                NativeResponse(500, e.message ?: "Unknown error")
            }
        }

        @JvmStatic
        fun openUrl(context: Context, url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                ProtonLogger.e("NextVpnManager", "Failed to open URL: $url", e)
            }
        }
    }
}
