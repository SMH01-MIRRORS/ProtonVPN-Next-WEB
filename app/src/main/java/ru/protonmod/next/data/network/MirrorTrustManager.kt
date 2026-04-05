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

import ru.protonmod.next.utils.ProtonLogger
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore

/**
 * A trust manager that delegates to the system trust manager but allows
 * connections to proceed even if the trust anchor is missing.
 * Security is maintained by the [okhttp3.CertificatePinner] which runs after the handshake.
 */
class MirrorTrustManager : X509TrustManager {

    private val systemTrustManager: X509TrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            systemTrustManager.checkServerTrusted(chain, authType)
        } catch (e: Exception) {
            // We allow trust failures to proceed ONLY because we have okhttp3.CertificatePinner
            // configured for all Proton domains and their mirrors. The pinner will perform
            // the final cryptographic validation of the public key.
            // This bypasses "Trust anchor not found" errors which occur when mirrors use
            // certificates not trusted by the system CA store or when connecting via IP.
            ProtonLogger.w("MirrorTrustManager", "System trust failure (${e.message}). Trusting chain to allow pinning check.")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return systemTrustManager.acceptedIssuers
    }
}
