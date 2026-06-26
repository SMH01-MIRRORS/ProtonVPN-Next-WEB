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

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Ed25519KeyPairGenerator {

    fun generate(): VpnKeyPair {
        val random = SecureRandom()
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(random))
        
        val keyPair = generator.generateKeyPair()
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        
        val seed = privateKey.encoded // 32 bytes seed
        val pubBytes = publicKey.encoded
        
        // Convert to X25519 for WireGuard
        val x25519Private = deriveX25519Private(seed)
        
        // PEM encode Public Key in PKIX format
        val pemPublic = toPkixPem(pubBytes, "PUBLIC KEY")
        
        return VpnKeyPair(
            publicKeyPem = pemPublic,
            privateKeyX25519 = Base64.getEncoder().encodeToString(x25519Private)
        )
    }

    private fun deriveX25519Private(seed: ByteArray): ByteArray {
        val hash = MessageDigest.getInstance("SHA-512").digest(seed)
        hash[0] = (hash[0].toInt() and 0xF8).toByte()
        hash[31] = (hash[31].toInt() and 0x7F).toByte()
        hash[31] = (hash[31].toInt() or 0x40).toByte()
        return hash.copyOfRange(0, 32)
    }

    private fun toPkixPem(publicKey: ByteArray, type: String): String {
        // ASN.1 prefix for Ed25519 Public Key (PKIX)
        val prefix = byteArrayOf(0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00)
        val pkix = prefix + publicKey
        
        val writer = StringWriter()
        PemWriter(writer).use { pemWriter ->
            pemWriter.writeObject(PemObject(type, pkix))
        }
        return writer.toString()
    }
}
