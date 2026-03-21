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

package ru.protonmod.next.utils.crypto

import android.util.Base64
import kotlinx.serialization.Serializable
import com.proton.gopenpgp.srp.Srp
import com.proton.gopenpgp.ed25519.KeyPair
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SrpProofs(
    val clientEphemeral: String,
    val clientProof: String
)

@Serializable
data class VpnKeyPair(
    val publicKeyPem: String,
    val privateKeyX25519: String
)

@Singleton
class CryptoWrapper @Inject constructor() {
    
    fun generateSrpProofs(
        username: String,
        passwordRaw: ByteArray,
        salt: String,
        modulus: String,
        serverEphemeral: String
    ): SrpProofs {
        val auth = Srp.newAuth(4L, username, passwordRaw, salt, modulus, serverEphemeral)
        val proofs = auth.generateProofs(2048L)
        
        return SrpProofs(
            clientEphemeral = Base64.encodeToString(proofs.clientEphemeral, Base64.NO_WRAP),
            clientProof = Base64.encodeToString(proofs.clientProof, Base64.NO_WRAP)
        )
    }

    fun generateVpnKeyPair(): VpnKeyPair {
        val keyPair = KeyPair()
        return VpnKeyPair(
            publicKeyPem = keyPair.publicKeyPKIXPem(),
            privateKeyX25519 = keyPair.toX25519Base64()
        )
    }
}
