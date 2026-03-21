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

package ru.protonmod.next.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.LoginResponse
import ru.protonmod.next.data.repository.AuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var authRepository: AuthRepository

    @Mock
    private lateinit var settingsManager: SettingsManager

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(settingsManager.apiBypassEnabled).thenReturn(flowOf(false))
        viewModel = LoginViewModel(authRepository, settingsManager)
    }

    @Test
    fun `login success sets Success state`() = runTest {
        // Arrange
        val response = LoginResponse(
            code = 1000,
            accessToken = "access",
            refreshToken = "refresh",
            userId = "user_id",
            sessionId = "session_id",
            scopes = listOf("vpn")
        )
        // Using doReturn to avoid potential issues with Result value class
        doReturn(Result.success(response)).whenever(authRepository).login(any(), any(), anyOrNull())

        // Act
        viewModel.login("user", "pass")

        // Assert
        val state = viewModel.uiState.value
        assertTrue("Expected Success state but was $state", state is LoginUiState.Success)
        assertEquals("access", (state as LoginUiState.Success).accessToken)
    }

    @Test
    fun `login failure sets Error state`() = runTest {
        // Arrange
        val exception = Exception("Auth failed")
        doReturn(Result.failure<LoginResponse>(exception)).whenever(authRepository).login(any(), any(), anyOrNull())

        // Act
        viewModel.login("user", "pass")

        // Assert
        val state = viewModel.uiState.value
        assertTrue("Expected Error state but was $state", state is LoginUiState.Error)
        assertEquals("Auth failed", (state as LoginUiState.Error).message)
    }

    @Test
    fun `login with 2FA requirement sets Requires2FA state`() = runTest {
        // Arrange
        val response = LoginResponse(
            code = 1000,
            accessToken = "temp_access",
            refreshToken = "refresh",
            userId = "user_id",
            sessionId = "session_id",
            scopes = listOf("twofactor")
        )
        doReturn(Result.success(response)).whenever(authRepository).login(any(), any(), anyOrNull())

        // Act
        viewModel.login("user", "pass")

        // Assert
        val state = viewModel.uiState.value
        assertTrue("Expected Requires2FA state but was $state", state is LoginUiState.Requires2FA)
        assertEquals("session_id", (state as LoginUiState.Requires2FA).sessionId)
    }
}
