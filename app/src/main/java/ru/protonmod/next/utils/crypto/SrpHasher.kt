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

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.MessageDigest
import java.util.Locale

object SrpHasher {

    fun concat(vararg arrays: ByteArray): ByteArray {
        val totalLength = arrays.sumOf { it.size }
        val result = ByteArray(totalLength)
        var currentOffset = 0
        for (array in arrays) {
            System.arraycopy(array, 0, result, currentOffset, array.size)
            currentOffset += array.size
        }
        return result
    }

    /**
     * Replicates Go's expandHash which extends SHA-512 into 256 bytes.
     */
    fun expandHash(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        val result = ByteArray(256)
        for (i in 0..3) {
            digest.update(data)
            digest.update(i.toByte())
            val hash = digest.digest()
            System.arraycopy(hash, 0, result, i * 64, 64)
        }
        return result
    }

    private val BCRYPT_ALPHABET = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray()

    /**
     * Port of Proton's hashPasswordVersion3 (used in SRP versions 3 and 4).
     */
    fun hashPasswordVersion3(password: ByteArray, salt: ByteArray, modulus: ByteArray): ByteArray {
        val saltWithProton = concat(salt, "proton".toByteArray())
        
        // Proton uses a custom bcrypt salt encoding logic.
        // They take append(salt, "proton"), base64 encode it with a custom alphabet, 
        // and then pass the string to bcrypt.
        // Standard bcrypt only uses the first 22 characters of the salt string (16 bytes).
        // However, the 22nd character of the salt string depends on the 17th byte.
        // To replicate this using standard libraries that take 16 bytes of salt, 
        // we must encode, truncate to 22 chars, and decode back to 16 bytes.
        
        val encodedSalt = encodeBcryptBase64(saltWithProton).take(22)
        val mangledSalt = decodeBcryptBase64(encodedSalt)
        
        val hashData = BCrypt.with(BCrypt.Version.VERSION_2Y).hashRaw(10, mangledSalt, password)
        
        // We need the full formatted string: $2y$10$<22-char-salt><31-char-hash>
        val fullHashed = "\$2y\$10\$$encodedSalt${encodeBcryptBase64(hashData.rawHash)}"
        
        return expandHash(concat(fullHashed.toByteArray(), modulus))
    }

    private fun encodeBcryptBase64(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        val len = data.size
        while (i < len) {
            val b1 = data[i++].toInt() and 0xff
            sb.append(BCRYPT_ALPHABET[b1 shr 2])
            var b2 = 0
            if (i < len) {
                b2 = data[i++].toInt() and 0xff
                sb.append(BCRYPT_ALPHABET[((b1 shl 4) or (b2 shr 4)) and 0x3f])
            } else {
                sb.append(BCRYPT_ALPHABET[(b1 shl 4) and 0x3f])
                break
            }
            var b3 = 0
            if (i < len) {
                b3 = data[i++].toInt() and 0xff
                sb.append(BCRYPT_ALPHABET[((b2 shl 2) or (b3 shr 6)) and 0x3f])
                sb.append(BCRYPT_ALPHABET[b3 and 0x3f])
            } else {
                sb.append(BCRYPT_ALPHABET[(b2 shl 2) and 0x3f])
                break
            }
        }
        return sb.toString()
    }

    private fun decodeBcryptBase64(s: String): ByteArray {
        val decodingTable = IntArray(128) { -1 }
        for (i in BCRYPT_ALPHABET.indices) decodingTable[BCRYPT_ALPHABET[i].code] = i
        
        val out = ByteArray((s.length * 6) / 8)
        var byteIdx = 0
        var i = 0
        while (i < s.length) {
            val c1 = decodingTable[s[i++].code]
            val c2 = if (i < s.length) decodingTable[s[i++].code] else 0
            out[byteIdx++] = ((c1 shl 2) or (c2 shr 4)).toByte()
            if (byteIdx >= out.size) break
            
            val c3 = if (i < s.length) decodingTable[s[i++].code] else 0
            out[byteIdx++] = ((c2 shl 4) or (c3 shr 2)).toByte()
            if (byteIdx >= out.size) break
            
            val c4 = if (i < s.length) decodingTable[s[i++].code] else 0
            out[byteIdx++] = ((c3 shl 6) or c4).toByte()
            if (byteIdx >= out.size) break
        }
        return out
    }

    /**
     * Port of hashPasswordVersion1.
     */
    fun hashPasswordVersion1(password: ByteArray, userName: String, modulus: ByteArray): ByteArray {
        throw UnsupportedOperationException("hashPasswordVersion1 not fully implemented")
    }
}
