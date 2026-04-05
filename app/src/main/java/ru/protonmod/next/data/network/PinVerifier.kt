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

import android.util.Base64
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

/**
 * Utility to verify SSL certificate pins manually.
 * Useful for scenarios where standard hostname verification fails (e.g., decoy domains or direct IP requests).
 */
object PinVerifier {
    fun check(session: SSLSession, allowedPins: List<String>): Boolean {
        return try {
            val certs = session.peerCertificates
            if (certs.isEmpty()) return false
            val cert = certs[0] as? X509Certificate ?: return false
            
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(cert.publicKey.encoded)
            val pin = Base64.encodeToString(hash, Base64.NO_WRAP)
            
            allowedPins.contains(pin)
        } catch (e: Exception) {
            false
        }
    }
}
