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
import dagger.hilt.android.EntryPointAccessors
import ru.protonmod.next.di.AppEntryPoint
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
        return AmneziaVpnManager.VpnState.entries.getOrNull(getStateNative())
            ?: AmneziaVpnManager.VpnState.DISCONNECTED
    }

    fun canConnect(): Boolean = canConnectNative()
    fun canDisconnect(): Boolean = canDisconnectNative()

    fun isTamperDetected(): Boolean = isTamperDetectedNative()
    fun getProtectedString(locale: String, key: String): String = getProtectedStringNative(locale, key)

    fun setLogcatEnabled(enabled: Boolean) {
        ru.protonmod.next.utils.ProtonLogger.isLogcatEnabled = enabled
        setLogcatEnabledNative(enabled)
    }

    // ImGUI Overlay support
    fun onSurfaceCreated(surface: android.view.Surface) = onSurfaceCreatedNative(surface)
    fun onSurfaceDestroyed() = onSurfaceDestroyedNative()
    fun onOverlayTouch(activity: android.app.Activity, x: Float, y: Float, action: Int) =
        onOverlayTouchNative(activity, x, y, action)

    private external fun setStateNative(state: Int)
    private external fun getStateNative(): Int
    private external fun canConnectNative(): Boolean
    private external fun canDisconnectNative(): Boolean

    private external fun isTamperDetectedNative(): Boolean
    private external fun getProtectedStringNative(locale: String, key: String): String
    external fun onActivityResumedNative(activity: android.content.Context)

    // ImGUI JNI
    private external fun onSurfaceCreatedNative(surface: android.view.Surface)
    private external fun onSurfaceDestroyedNative()
    private external fun onOverlayTouchNative(activity: android.app.Activity, x: Float, y: Float, action: Int)

    private external fun setLogcatEnabledNative(enabled: Boolean)

    companion object {
        private var isWarningShown = false
        private var overlayDialog: android.app.Dialog? = null

        @JvmStatic
        fun registerLifecycleCallbacks(application: android.app.Application) {
            application.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: android.app.Activity) {
                    val nextVpnManager = EntryPointAccessors.fromApplication(activity.applicationContext, AppEntryPoint::class.java).nextVpnManager()
                    nextVpnManager.onActivityResumedNative(activity)
                }
                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
                override fun onActivityStarted(activity: android.app.Activity) {}
                override fun onActivityPaused(activity: android.app.Activity) {}
                override fun onActivityStopped(activity: android.app.Activity) {}
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
                override fun onActivityDestroyed(activity: android.app.Activity) {}
            })
        }

        @SuppressLint("ClickableViewAccessibility")
        @JvmStatic
        fun createNativeOverlay(activity: android.app.Activity) {
            if (isWarningShown) return
            isWarningShown = true

            activity.runOnUiThread {
                val dialog = android.app.Dialog(activity, android.R.style.Theme_NoTitleBar_Fullscreen)
                overlayDialog = dialog
                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)
                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

                val surfaceView = android.view.SurfaceView(activity)
                surfaceView.holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                
                val nextVpnManager = EntryPointAccessors.fromApplication(activity.applicationContext, AppEntryPoint::class.java).nextVpnManager()

                surfaceView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        nextVpnManager.onSurfaceCreated(holder.surface)
                    }
                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        nextVpnManager.onSurfaceDestroyed()
                    }
                })

                surfaceView.setOnTouchListener { _, event ->
                    nextVpnManager.onOverlayTouch(activity, event.x, event.y, event.action)
                    true
                }

                dialog.setContentView(surfaceView)
                dialog.show()
            }
        }

        @JvmStatic
        fun logSecurityEvent(event: String) {
            ru.protonmod.next.utils.ProtonLogger.e("NextVpnManager", "Security event: $event")
        }

        @JvmStatic
        fun dismissNativeOverlay() {
            overlayDialog?.dismiss()
            overlayDialog = null
        }

        @JvmStatic
        fun openUrl(context: android.content.Context, url: String) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                ru.protonmod.next.utils.ProtonLogger.e("NextVpnManager", "Failed to open URL: $url", e)
            }
        }
    }
}
