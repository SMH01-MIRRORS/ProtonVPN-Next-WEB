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

package ru.protonmod.next.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.utils.PiiScrubber

/**
 * Interceptor for logging authentication-related network traffic.
 * Uses direct [Log.d] calls to avoid forwarding sensitive payloads to Sentry.
 */
class AuthLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        // Capture both standard auth and some core calls that might contain auth data
        val isAuth = url.contains("auth/v4") || url.contains("core/v4/users")

        if (isAuth && BuildConfig.ALLOW_LOGCAT) {
            val userAgent = request.header("User-Agent") ?: "None"
            Log.d("AuthLogging", "[DEBUG] Request URL: $url")
            Log.d("AuthLogging", "[DEBUG] User-Agent: $userAgent")
            request.body?.let { body ->
                try {
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    val rawBody = buffer.readUtf8()
                    Log.d("AuthLogging", "[DEBUG] Request Body: ${PiiScrubber.scrub(rawBody)}")
                } catch (e: Exception) {
                    Log.d("AuthLogging", "[DEBUG] Could not log request body: ${e.message}")
                }
            }
        }

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            if (isAuth && BuildConfig.ALLOW_LOGCAT) {
                Log.d("AuthLogging", "[DEBUG] Request Failed: ${e.message}")
            }
            throw e
        }

        if (isAuth && BuildConfig.ALLOW_LOGCAT) {
            Log.d("AuthLogging", "[DEBUG] Response Code: ${response.code}")
            response.body.let { body ->
                try {
                    val source = body.source()
                    source.request(Long.MAX_VALUE)
                    val buffer = source.buffer
                    val rawBody = buffer.clone().readUtf8()
                    Log.d("AuthLogging", "[DEBUG] Response Body: ${PiiScrubber.scrub(rawBody)}")
                } catch (e: Exception) {
                    Log.d("AuthLogging", "[DEBUG] Could not log response body: ${e.message}")
                }
            }
        }

        return response
    }
}
