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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ported logic from Proton Converter (quic.js) to generate I1 string from a domain.
 * Constructs a QUIC Initial packet with a TLS ClientHello containing the SNI.
 */
object QuicI1Generator {

    private val secureRandom = SecureRandom()
    private val quicSalt = byteArrayOf(
        0x38, 0x76, 0x2c, 0xf7.toByte(), 0xf5.toByte(), 0x59, 0x34, 0xb3.toByte(), 0x4d, 0x17,
        0x9a.toByte(), 0xe6.toByte(), 0xa4.toByte(), 0xc8.toByte(), 0x0c, 0xad.toByte(), 0xcc.toByte(), 0xbb.toByte(), 0x7f, 0x0a.toByte()
    )

    fun generateI1(domain: String): String {
        val dcid = ByteArray(1).apply { secureRandom.nextBytes(this) }
        val scid = ByteArray(0)
        val token = ByteArray(0)
        val pkn = byteArrayOf(0)
        val clientHello = quicTlsClientHelloSniOnly(domain)
        val payload = quicCryptoFrame(clientHello)
        val packet = quicInitial(dcid, scid, token, pkn, payload, 0)
        
        return "<b 0x${packet.toHexString()}>"
    }

    private fun quicTlsClientHelloSniOnly(sni: String): ByteArray {
        val randomBytes = ByteArray(32).apply { secureRandom.nextBytes(this) }
        
        // quicTlsExtSni logic
        val sniBuffer = quicStr16(sni)
        val extBuffer = ByteBuffer.allocate(sniBuffer.size + 3).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort((sniBuffer.size + 1).toShort())
            put(0) // Name type: host_name
            put(sniBuffer)
        }.array()
        val tlsExtSni = quicTlsExt(0, extBuffer)
        
        // extensions = quicStr16(quicTlsExtSni(sni))
        val extensions = quicStr16(tlsExtSni)
        
        // payload construction with 4 bytes allocated before
        val helloBody = ByteBuffer.allocate(2 + 32 + 4 + extensions.size).apply {
            put(byteArrayOf(0x03, 0x03)) // Version
            put(randomBytes)
            put(byteArrayOf(0, 0, 0, 0)) // Session ID + Cipher suites
            put(extensions)
        }.array()

        val finalHello = ByteBuffer.allocate(4 + helloBody.size).apply {
            order(ByteOrder.BIG_ENDIAN)
            put(0x01.toByte()) // Handshake Type: Client Hello
            val len = helloBody.size
            put(((len shr 16) and 0xFF).toByte())
            put(((len shr 8) and 0xFF).toByte())
            put((len and 0xFF).toByte())
            put(helloBody)
        }.array()
        
        return finalHello
    }

    private fun quicTlsExt(code: Int, content: ByteArray): ByteArray {
        return ByteBuffer.allocate(content.size + 4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(code.toShort())
            putShort(content.size.toShort())
            put(content)
        }.array()
    }

    private fun quicStr16(data: String): ByteArray {
        val bytes = data.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(bytes.size + 2).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(bytes.size.toShort())
            put(bytes)
        }.array()
    }

    private fun quicStr16(data: ByteArray): ByteArray {
        return ByteBuffer.allocate(data.size + 2).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(data.size.toShort())
            put(data)
        }.array()
    }

    private fun quicStr8(data: ByteArray): ByteArray {
        return ByteBuffer.allocate(data.size + 1).apply {
            put(data.size.toByte())
            put(data)
        }.array()
    }

    private fun quicCryptoFrame(data: ByteArray, offset: Int = 0): ByteArray {
        val offsetVarint = quicVarint(offset)
        val lenVarint = quicVarint(data.size)
        return ByteBuffer.allocate(1 + offsetVarint.size + lenVarint.size + data.size).apply {
            put(0x06.toByte()) // CRYPTO frame type
            put(offsetVarint)
            put(lenVarint)
            put(data)
        }.array()
    }

    private fun quicVarint(value: Int): ByteArray {
        return when {
            value < 0x40 -> byteArrayOf(value.toByte())
            value < 0x4000 -> ByteBuffer.allocate(2).apply { putShort((value or 0x4000).toShort()) }.array()
            value < 0x40000000 -> ByteBuffer.allocate(4).apply { putInt((value.toLong() or 0x80000000L).toInt()) }.array()
            else -> {
                val buf = ByteBuffer.allocate(8)
                buf.putLong((value.toLong() or (0xC0L shl 56)))
                buf.array()
            }
        }
    }

    private fun quicInitial(
        dcid: ByteArray,
        scid: ByteArray,
        token: ByteArray,
        pkn: ByteArray,
        payload: ByteArray,
        padto: Int
    ): ByteArray {
        val pknLength = pkn.size
        val tagLength = 16
        
        // Measure lengths logic from quic.js
        val baseHeaderLength = 8 + dcid.size + scid.size + token.size + pknLength
        
        var paddingLength = 0
        fun getOverallLength() = baseHeaderLength + quicVarintLength(pknLength + payload.size + paddingLength + tagLength) + payload.size + paddingLength + tagLength
        
        var overallLength = getOverallLength()
        if (overallLength < padto) {
            paddingLength = padto - overallLength
            while (paddingLength > 0 && getOverallLength() > padto) {
                paddingLength--
            }
            if (getOverallLength() < padto) {
                paddingLength++
            }
            overallLength = getOverallLength()
        }
        
        // Extra safety from quic.js
        if (pknLength + payload.size + paddingLength + tagLength < 20) {
            paddingLength = 20 - pknLength - payload.size - tagLength
            overallLength = getOverallLength()
        }

        val lenVarint = quicVarint(pknLength + payload.size + paddingLength + tagLength)
        
        val header = ByteBuffer.allocate(5 + 1 + dcid.size + 1 + scid.size + 1 + token.size + lenVarint.size + pknLength).apply {
            put((0xC0 or (pknLength - 1)).toByte())
            put(byteArrayOf(0, 0, 0, 1)) // Version 1
            put(quicStr8(dcid))
            put(quicStr8(scid))
            put(quicStr8(token))
            put(lenVarint)
            put(pkn)
        }.array()

        // Crypto derivation
        val initSecret = hmacSha256(quicSalt, dcid)
        val clientSecret = quicDeriveSecret(initSecret, 32, "client in")
        val quicKey = quicDeriveSecret(clientSecret, 16, "quic key")
        val quicIv = quicDeriveSecret(clientSecret, 12, "quic iv")
        val quicHp = quicDeriveSecret(clientSecret, 16, "quic hp")

        // XOR IV with PKN
        val ivWithPkn = quicIv.copyOf()
        for (i in 0 until pknLength) {
            ivWithPkn[ivWithPkn.size - 1 - i] = (ivWithPkn[ivWithPkn.size - 1 - i].toInt() xor pkn[pkn.size - 1 - i].toInt()).toByte()
        }

        // Create padded payload
        val paddedPayload = ByteArray(payload.size + paddingLength)
        System.arraycopy(payload, 0, paddedPayload, 0, payload.size)

        // Encrypt payload
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, ivWithPkn)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(quicKey, "AES"), spec)
        cipher.updateAAD(header)
        val encryptedPayload = cipher.doFinal(paddedPayload)

        // Header Protection
        val sample = encryptedPayload.copyOfRange(4 - pknLength, 20 - pknLength)
        val hpCipher = Cipher.getInstance("AES/ECB/NoPadding")
        hpCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(quicHp, "AES"))
        val mask = hpCipher.doFinal(sample)
        
        val finalHeader = header.copyOf()
        finalHeader[0] = (finalHeader[0].toInt() xor (mask[0].toInt() and 0x0F)).toByte()
        val pknOffset = header.size - pknLength
        for (i in 0 until pknLength) {
            finalHeader[pknOffset + i] = (finalHeader[pknOffset + i].toInt() xor mask[1 + i].toInt()).toByte()
        }

        return finalHeader + encryptedPayload
    }

    private fun quicVarintLength(value: Int): Int {
        return when {
            value < 0x40 -> 1
            value < 0x4000 -> 2
            value < 0x40000000 -> 4
            else -> 8
        }
    }

    private fun quicDeriveSecret(key: ByteArray, length: Int, label: String): ByteArray {
        val labelBytes = "tls13 $label".toByteArray(Charsets.UTF_8)
        // HKDF-Expand-Label structure:
        // 2 bytes: length
        // 1 byte: label length + "tls13 " prefix
        // labelBytes.size: label data
        // 1 byte: context length (0)
        // 1 byte: 0x01 (HKDF expand constant)
        val info = ByteBuffer.allocate(2 + 1 + labelBytes.size + 1 + 1).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(length.toShort())
            put(labelBytes.size.toByte())
            put(labelBytes)
            put(0.toByte()) // Context length 0
            put(0x01.toByte())
        }.array()
        
        // This is HKDF-Expand-Label simplified as per quic.js
        return hmacSha256(key, info).copyOfRange(0, length)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
