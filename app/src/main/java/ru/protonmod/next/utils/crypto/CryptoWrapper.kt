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

import kotlinx.serialization.Serializable
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
        serverEphemeral: ByteArray // Changed from String to ByteArray to avoid double decoding
    ): SrpProofs {
        val hashed = SrpHasher.hashPasswordVersion3(
            password = passwordRaw,
            salt = Base64Utils.decode(salt),
            modulus = SrpClient.verifyAndExtractModulus(modulus)
        )
        
        val client = SrpClient(
            modulus = SrpClient.verifyAndExtractModulus(modulus),
            hashedPassword = hashed,
            serverEphemeral = serverEphemeral
        )
        
        return client.generateProofs()
    }

    fun generateVpnKeyPair(): VpnKeyPair {
        return Ed25519KeyPairGenerator.generate()
    }
}
