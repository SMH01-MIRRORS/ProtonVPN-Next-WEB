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
class NextVpnManager @Inject constructor() {

    init {
        System.loadLibrary("next")
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

    fun onSurfaceCreated(surface: Surface) = onSurfaceCreatedNative(surface)
    fun onSurfaceDestroyed() = onSurfaceDestroyedNative()
    fun onOverlayTouch(activity: Activity, x: Float, y: Float, action: Int) = onOverlayTouchNative(activity, x, y, action)

    private external fun setStateNative(state: Int)
    private external fun getStateNative(): Int
    private external fun canConnectNative(): Boolean
    private external fun canDisconnectNative(): Boolean

    private external fun isTamperDetectedNative(): Boolean
    private external fun getProtectedStringNative(locale: String, key: String): String
    external fun onActivityResumedNative(activity: Context)

    private external fun onSurfaceCreatedNative(surface: Surface)
    private external fun onSurfaceDestroyedNative()
    private external fun onOverlayTouchNative(activity: Activity, x: Float, y: Float, action: Int)

    private external fun setLogcatEnabledNative(enabled: Boolean)

    companion object {
        var isWarningShown = false
        var overlayDialog: Dialog? = null

        @JvmStatic
        fun registerLifecycleCallbacks(app: Application) {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    val manager = EntryPointAccessors.fromApplication(app, AppEntryPoint::class.java).nextVpnManager()
                    manager.onActivityResumedNative(activity)
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }

        @SuppressLint("InflateParams")
        @JvmStatic
        fun createNativeOverlay(activity: Activity) {
            if (overlayDialog != null) return

            activity.runOnUiThread {
                val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCancelable(false)

                val surfaceView = SurfaceView(activity)
                dialog.setContentView(surfaceView)

                surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        val manager = EntryPointAccessors.fromApplication(activity.application, AppEntryPoint::class.java).nextVpnManager()
                        manager.onSurfaceCreated(holder.surface)
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        val manager = EntryPointAccessors.fromApplication(activity.application, AppEntryPoint::class.java).nextVpnManager()
                        manager.onSurfaceDestroyed()
                    }
                })

                surfaceView.setOnTouchListener { _, event ->
                    val manager = EntryPointAccessors.fromApplication(activity.application, AppEntryPoint::class.java).nextVpnManager()
                    manager.onOverlayTouch(activity, event.x, event.y, event.action)
                    true
                }

                dialog.show()
                overlayDialog = dialog
            }
        }

        @JvmStatic
        fun logSecurityEvent(event: String) {
            ProtonLogger.e("AntiTamper", "Security Event: $event")
        }

        /**
         * Honeypot: A constant that looks like a security key.
         * Modders often try to change such constants to "bypass" checks.
         */
        const val SECURITY_VERIFICATION_TOKEN = "7b74cef88678ecb3e6047ac6b4abf139"

        @JvmStatic
        fun dismissNativeOverlay() {
            overlayDialog?.dismiss()
            overlayDialog = null
        }

        data class NativeResponse(val code: Int, val body: String)

        @JvmStatic
        fun performNativeRequest(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String?
        ): NativeResponse {
            val client = OkHttpClient()
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            
            val requestBody = body?.toRequestBody("application/json".toMediaTypeOrNull())
            requestBuilder.method(method, requestBody)
            
            return try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    NativeResponse(response.code, response.body?.string() ?: "")
                }
            } catch (e: Exception) {
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
