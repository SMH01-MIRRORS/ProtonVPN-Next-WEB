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

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import java.io.ByteArrayInputStream

class SrpClient(
    private val modulus: ByteArray,
    private val hashedPassword: ByteArray,
    private val serverEphemeral: ByteArray
) {
    companion object {
        private val GENERATOR = BigInteger.valueOf(2)
        private const val BIT_LENGTH = 2048

        fun fromSignedModulus(
            signedModulus: String,
            hashedPassword: ByteArray,
            serverEphemeral: ByteArray
        ): SrpClient {
            val modulusBytes = verifyAndExtractModulus(signedModulus)
            return SrpClient(modulusBytes, hashedPassword, serverEphemeral)
        }

        fun verifyAndExtractModulus(signedModulus: String): ByteArray {
            // Proton's modulus is returned as a PGP Cleartext Signed Message.
            // We extract the message body (the base64 modulus) between the header and the signature.
            
            try {
                val lines = signedModulus.lines().map { it.trim() }
                
                // Find the start of the message body (after the Hash header and the first empty line)
                var bodyStart = -1
                for (i in lines.indices) {
                    if (lines[i].startsWith("-----BEGIN PGP SIGNED MESSAGE-----")) {
                        continue
                    }
                    if (lines[i].isEmpty()) {
                        bodyStart = i + 1
                        break
                    }
                }
                
                val sigStart = lines.indexOfFirst { it.startsWith("-----BEGIN PGP SIGNATURE-----") }
                
                if (bodyStart == -1 || sigStart == -1 || bodyStart >= sigStart) {
                    // Fallback: if markers not found, try to decode the whole string if it's pure base64
                    return Base64.getDecoder().decode(signedModulus.trim())
                }
                
                val b64 = lines.subList(bodyStart, sigStart).filter { it.isNotEmpty() }.joinToString("")
                return Base64Utils.decode(b64)
            } catch (e: Exception) {
                // If anything fails, try one last time to decode the raw input
                return Base64Utils.decode(signedModulus)
            }
        }

        private fun toBigInt(bytes: ByteArray): BigInteger {
            // Proton uses Little-Endian for BigInt conversion
            return BigInteger(1, bytes.reversedArray())
        }

        private fun fromBigInt(num: BigInteger, length: Int): ByteArray {
            val bytes = num.toByteArray()
            val result = ByteArray(length)
            val offset = if (bytes.size > 1 && bytes[0] == 0.toByte()) 1 else 0
            val count = bytes.size - offset
            val copyCount = count.coerceAtMost(length)
            // Copy Big-Endian bytes to the END of the result array to ensure proper zero-padding
            System.arraycopy(bytes, offset + (count - copyCount), result, length - copyCount, copyCount)
            // Reverse the entire padded array to get Little-Endian representation
            return result.reversedArray()
        }
    }

    fun generateProofs(): SrpProofs {
        val N = toBigInt(modulus)
        val x = toBigInt(hashedPassword)
        val B = toBigInt(serverEphemeral)
        val k = toBigInt(SrpHasher.expandHash(SrpHasher.concat(fromBigInt(GENERATOR, 256), fromBigInt(N, 256)))).mod(N)

        val random = SecureRandom()
        var a: BigInteger
        var A_int: BigInteger
        var A: ByteArray

        // Generate client ephemeral A
        while (true) {
            a = BigInteger(BIT_LENGTH, random).mod(N.subtract(BigInteger.ONE))
            if (a.compareTo(BigInteger.valueOf(BIT_LENGTH.toLong() * 2)) <= 0) continue
            
            A_int = GENERATOR.modPow(a, N)
            A = fromBigInt(A_int, 256)
            
            val u = toBigInt(SrpHasher.expandHash(SrpHasher.concat(A, serverEphemeral)))
            if (u != BigInteger.ZERO) break
        }

        val u = toBigInt(SrpHasher.expandHash(SrpHasher.concat(A, serverEphemeral)))

        // Shared Secret S = (B - k*g^x)^(a + u*x) % N
        val exp = a.add(u.multiply(x)).mod(N.subtract(BigInteger.ONE))
        val base = B.subtract(k.multiply(GENERATOR.modPow(x, N)).mod(N)).add(N).mod(N)
        val S_int = base.modPow(exp, N)
        val S = fromBigInt(S_int, 256)

        // Client Proof M1 = H(A, B, S)
        val M1 = SrpHasher.expandHash(SrpHasher.concat(A, serverEphemeral, S))
        
        return SrpProofs(
            clientEphemeral = Base64.getEncoder().encodeToString(A),
            clientProof = Base64.getEncoder().encodeToString(M1)
        )
    }
}
