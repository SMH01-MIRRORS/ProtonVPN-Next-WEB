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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ed25519KeyPairTest {

    @Test
    fun testGenerateKeyPair() {
        val keyPair = Ed25519KeyPairGenerator.generate()
        assertNotNull(keyPair.publicKeyPem)
        assertNotNull(keyPair.privateKeyX25519)
        
        assertTrue(keyPair.publicKeyPem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(keyPair.publicKeyPem.endsWith("-----END PUBLIC KEY-----\n"))
        
        // Private key should be base64 (32 bytes -> 44 chars)
        assertEquals(44, keyPair.privateKeyX25519.length)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
