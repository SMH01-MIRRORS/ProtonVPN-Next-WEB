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
 * along with this program.  I not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.data.network

import android.annotation.SuppressLint
import ru.protonmod.next.utils.ProtonLogger
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.SSLEngine
import java.security.KeyStore

/**
 * A trust manager that delegates to the system trust manager but allows
 * connections to proceed even if the trust anchor is missing.
 * Security is maintained by the [okhttp3.CertificatePinner] which runs after the handshake.
 */
@SuppressLint("CustomX509TrustManager")
class MirrorTrustManager : X509ExtendedTrustManager() {

    private val systemTrustManager: X509ExtendedTrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            systemTrustManager.checkServerTrusted(chain, authType)
        } catch (e: Exception) {
            ProtonLogger.w("MirrorTrustManager", "System trust failure (${e.message}). Trusting chain to allow pinning check.")
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        systemTrustManager.checkClientTrusted(chain, authType, socket)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        systemTrustManager.checkClientTrusted(chain, authType, engine)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        try {
            systemTrustManager.checkServerTrusted(chain, authType, socket)
        } catch (e: Exception) {
            ProtonLogger.w("MirrorTrustManager", "System trust failure (${e.message}). Trusting chain to allow pinning check.")
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        try {
            systemTrustManager.checkServerTrusted(chain, authType, engine)
        } catch (e: Exception) {
            ProtonLogger.w("MirrorTrustManager", "System trust failure (${e.message}). Trusting chain to allow pinning check.")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return systemTrustManager.acceptedIssuers
    }
}
