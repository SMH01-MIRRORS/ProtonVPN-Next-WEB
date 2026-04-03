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

package ru.protonmod.next.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import retrofit2.HttpException
import retrofit2.Response
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.network.*
import ru.protonmod.next.ui.screens.CaptchaRequiredException
import ru.protonmod.next.utils.DeviceInfoProvider
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.utils.crypto.CryptoWrapper
import ru.protonmod.next.utils.crypto.SrpProofs
import ru.protonmod.next.utils.crypto.VpnKeyPair

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    @Mock
    private lateinit var authApi: ProtonAuthApi

    @Mock
    private lateinit var vpnRepository: VpnRepository

    @Mock
    private lateinit var sessionDao: SessionDao

    @Mock
    private lateinit var deviceInfoProvider: DeviceInfoProvider

    @Mock
    private lateinit var cryptoWrapper: CryptoWrapper

    @Mock
    private lateinit var amneziaVpnManager: ru.protonmod.next.vpn.AmneziaVpnManager

    private val testDispatcher = StandardTestDispatcher()
    
    private val testDispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = testDispatcher
        override fun io(): CoroutineDispatcher = testDispatcher
        override fun default(): CoroutineDispatcher = testDispatcher
    }

    private lateinit var repository: AuthRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = AuthRepository(
            authApi, vpnRepository, sessionDao, deviceInfoProvider, 
            cryptoWrapper, testDispatcherProvider, { amneziaVpnManager }
        )
        
        whenever(deviceInfoProvider.getAppVersion()).thenReturn("5.16.31.0")
        whenever(deviceInfoProvider.getAppLanguage()).thenReturn("en")
        whenever(deviceInfoProvider.getTimezone()).thenReturn("UTC")
        whenever(deviceInfoProvider.getDeviceHash()).thenReturn(12345L)
        whenever(deviceInfoProvider.getRegionCode()).thenReturn("US")
        whenever(deviceInfoProvider.getTimezoneOffset()).thenReturn(0)
        whenever(deviceInfoProvider.isJailbreak()).thenReturn(false)
        whenever(deviceInfoProvider.getPreferredContentSize()).thenReturn("1.0")
        whenever(deviceInfoProvider.getStorageCapacity()).thenReturn(128.0)
        whenever(deviceInfoProvider.isDarkModeOn()).thenReturn(false)
        whenever(deviceInfoProvider.getInstalledKeyboards()).thenReturn(listOf("com.google.android.inputmethod.latin"))
    }

    @Test
    fun `loginAnonymous success flow`() = runTest(testDispatcher) {
        // Arrange
        val loginResponse = LoginResponse(
            code = 1000,
            accessToken = "final_token",
            refreshToken = "final_refresh",
            sessionId = "final_session_id",
            userId = "user_id",
            scopes = listOf("vpn")
        )

        whenever(authApi.createAnonymousSession(any(), anyOrNull(), anyOrNull())).thenReturn(loginResponse)
        whenever(authApi.performLoginLess(any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(loginResponse)
        
        whenever(cryptoWrapper.generateVpnKeyPair()).thenReturn(
            VpnKeyPair("pubkey", "privkey")
        )

        whenever(vpnRepository.registerWireGuardKey(any(), any(), any())).thenReturn(
            Result.success(CreateCertificateResponse(code = 1000, certificate = "cert"))
        )

        // Act
        val result = repository.loginAnonymous()

        // Assert
        assertTrue("Expected success but got ${result.exceptionOrNull()}", result.isSuccess)
        val response = result.getOrNull()!!
        assertEquals("final_token", response.accessToken)
        assertEquals("user_id", response.userId)
    }

    @Test
    fun `login SRP success flow`() = runTest(testDispatcher) {
        // Arrange
        val username = "testuser"
        val password = "password"
        
        val anonSession = LoginResponse(
            code = 1000,
            accessToken = "anon_token",
            sessionId = "anon_session_id"
        )
        val authInfo = AuthInfoResponse(
            code = 1000,
            salt = "salt",
            modulus = "modulus",
            serverEphemeral = "serverEphemeral",
            srpSession = "srpSession"
        )
        val loginResponse = LoginResponse(
            code = 1000,
            accessToken = "final_token",
            refreshToken = "final_refresh",
            sessionId = "final_session_id",
            userId = "user_id",
            scopes = listOf("vpn")
        )

        whenever(authApi.createAnonymousSession(any(), anyOrNull(), anyOrNull())).thenReturn(anonSession)
        whenever(authApi.getAuthInfo(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(authInfo)
        
        whenever(cryptoWrapper.generateSrpProofs(any(), any(), any(), any(), any())).thenReturn(
            SrpProofs("eph", "proof")
        )

        whenever(authApi.performLogin(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(loginResponse)
        
        whenever(cryptoWrapper.generateVpnKeyPair()).thenReturn(
            VpnKeyPair("pubkey", "privkey")
        )

        whenever(vpnRepository.registerWireGuardKey(any(), any(), any())).thenReturn(
            Result.success(CreateCertificateResponse(code = 1000, certificate = "cert"))
        )
        
        whenever(vpnRepository.getVpnInfo(any(), any())).thenReturn(
            Result.success(VpnInfoResponse(code = 1000, vpnInfo = VpnInfo(maxTier = 2)))
        )

        // Act
        val result = repository.login(username, password)

        // Assert
        assertTrue("Expected success but got ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals("final_token", result.getOrNull()?.accessToken)
    }

    @Test
    fun `login returns CaptchaRequiredException on 9001 error`() = runTest(testDispatcher) {
        // Arrange
        val errorJson = """
            {
                "Code": 9001,
                "Error": "Human verification required",
                "Details": {
                    "WebUrl": "https://captcha.url",
                    "HumanVerificationToken": "captcha_token"
                }
            }
        """.trimIndent()
        val response = Response.error<LoginResponse>(422, errorJson.toResponseBody("application/json".toResponseBody().contentType()))
        val exception = HttpException(response)

        whenever(authApi.createAnonymousSession(any(), anyOrNull(), anyOrNull())).thenThrow(exception)

        // Act
        val result = repository.login("user", "pass")

        // Assert
        assertTrue(result.isFailure)
        val caught = result.exceptionOrNull()
        assertTrue("Expected CaptchaRequiredException but was $caught", caught is CaptchaRequiredException)
        assertEquals("https://captcha.url", (caught as CaptchaRequiredException).webUrl)
    }

    @Test
    fun `login returns CaptchaRequiredException on 12087 error`() = runTest(testDispatcher) {
        // Arrange
        val errorJson = """
            {
                "Code": 12087,
                "Error": "Captcha validation failed",
                "Details": {
                    "WebUrl": "https://fresh.captcha.url",
                    "HumanVerificationToken": "fresh_token"
                }
            }
        """.trimIndent()
        val response = Response.error<LoginResponse>(422, errorJson.toResponseBody("application/json".toResponseBody().contentType()))
        val exception = HttpException(response)

        whenever(authApi.createAnonymousSession(any(), anyOrNull(), anyOrNull())).thenThrow(exception)

        // Act
        val result = repository.login("user", "pass")

        // Assert
        assertTrue(result.isFailure)
        val caught = result.exceptionOrNull()
        assertTrue("Expected CaptchaRequiredException but was $caught", caught is CaptchaRequiredException)
        assertEquals("https://fresh.captcha.url", (caught as CaptchaRequiredException).webUrl)
    }

    @Test
    fun `refreshSession success flow`() = runTest(testDispatcher) {
        // Arrange
        val sessionId = "session_id"
        val refreshToken = "refresh_token"
        val refreshResponse = LoginResponse(
            code = 1000,
            accessToken = "new_access_token",
            refreshToken = "new_refresh_token",
            sessionId = sessionId
        )
        val currentSession = SessionEntity(
            sessionId = sessionId,
            accessToken = "old_access_token",
            refreshToken = refreshToken,
            userId = "user_id"
        )

        whenever(authApi.refreshSession(any())).thenReturn(refreshResponse)
        whenever(sessionDao.getSession()).thenReturn(currentSession)

        // Act
        val result = repository.refreshSession(sessionId, refreshToken)

        // Assert
        assertTrue(result.isSuccess)
        verify(sessionDao).saveSession(argThat {
            this.accessToken == "new_access_token" && this.refreshToken == "new_refresh_token"
        })
    }

    @Test
    fun `refreshSession debounces calls within 1 minute`() = runTest(testDispatcher) {
        // Arrange
        val sessionId = "session_id"
        val refreshToken = "refresh_token"
        val refreshResponse = LoginResponse(
            code = 1000,
            accessToken = "new_access_token",
            sessionId = sessionId
        )
        whenever(authApi.refreshSession(any())).thenReturn(refreshResponse)

        // Act
        repository.refreshSession(sessionId, refreshToken) // First call
        val secondResult = repository.refreshSession(sessionId, refreshToken) // Second call (debounced)

        // Assert
        assertTrue(secondResult.isFailure)
        assertEquals("Debounced", secondResult.exceptionOrNull()?.message)
        verify(authApi, times(1)).refreshSession(any())
    }

    @Test
    fun `refreshSession triggers logout on HTTP 401`() = runTest(testDispatcher) {
        // Arrange
        val sessionId = "session_id"
        val refreshToken = "refresh_token"
        val response = Response.error<LoginResponse>(401, "Unauthorized".toResponseBody())
        val exception = HttpException(response)

        whenever(authApi.refreshSession(any())).thenThrow(exception)

        // Act
        val result = repository.refreshSession(sessionId, refreshToken)

        // Assert
        assertTrue(result.isFailure)
        verify(sessionDao).clearSession()
    }
}
