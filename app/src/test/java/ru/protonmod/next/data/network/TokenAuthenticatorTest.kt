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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity

@OptIn(ExperimentalCoroutinesApi::class)
class TokenAuthenticatorTest {

    @Mock
    private lateinit var sessionDao: SessionDao

    @Mock
    private lateinit var authApi: ProtonAuthApi

    private lateinit var authenticator: TokenAuthenticator

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        authenticator = TokenAuthenticator(sessionDao, { authApi })
    }

    @Test
    fun `authenticate returns null when no session found`() {
        runBlocking {
            whenever(sessionDao.getSession()).thenReturn(null)
        }

        val response = mockResponse("http://test.com")
        val result = authenticator.authenticate(null, response)

        assertNull(result)
    }

    @Test
    fun `authenticate refreshes token and retries request`() {
        // Arrange
        val oldSession = SessionEntity(
            accessToken = "old_token",
            refreshToken = "refresh_token",
            sessionId = "session_id",
            userId = "user_id"
        )
        val refreshResponse = LoginResponse(
            code = 1000,
            accessToken = "new_token",
            refreshToken = "new_refresh_token"
        )

        runBlocking {
            whenever(sessionDao.getSession()).thenReturn(oldSession)
            whenever(authApi.refreshSession(any())).thenReturn(refreshResponse)
        }

        val response = mockResponse("http://test.com", "Bearer old_token")

        // Act
        val result = authenticator.authenticate(null, response)

        // Assert
        assertEquals("Bearer new_token", result?.header("Authorization"))
    }

    private fun mockResponse(url: String, authHeader: String? = null): Response {
        val requestBuilder = Request.Builder().url(url)
        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader)
        }
        val request = requestBuilder.build()
        
        return mock<Response>().apply {
            whenever(this.request).thenReturn(request)
            whenever(this.code).thenReturn(401)
        }
    }
}
