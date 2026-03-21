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

package ru.protonmod.next.ui.screens.dashboard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.amnezia.awg.backend.Tunnel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import ru.protonmod.next.data.local.RecentConnectionDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.ui.screens.MainDispatcherRule
import ru.protonmod.next.vpn.AmneziaVpnManager

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var vpnRepository: VpnRepository

    @Mock
    private lateinit var sessionDao: SessionDao

    @Mock
    private lateinit var amneziaVpnManager: AmneziaVpnManager

    @Mock
    private lateinit var connectedServerState: ConnectedServerState

    @Mock
    private lateinit var recentConnectionDao: RecentConnectionDao

    private lateinit var viewModel: DashboardViewModel

    private val testServer = LogicalServer(
        id = "us_1", name = "US-FREE-1", tier = 0, features = 0,
        entryCountry = "US", exitCountry = "US", city = "New York",
        averageLoad = 10, servers = listOf(PhysicalServer(id = "p1", domain = "d1", status = 1, load = 10))
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.getBoolean(any(), any())).thenReturn(false)
        
        whenever(vpnRepository.getServersFlow()).thenReturn(flowOf(listOf(testServer)))
        whenever(amneziaVpnManager.tunnelState).thenReturn(MutableStateFlow(Tunnel.State.DOWN))
        whenever(amneziaVpnManager.isConnecting).thenReturn(MutableStateFlow(false))
        whenever(amneziaVpnManager.certState).thenReturn(MutableStateFlow(AmneziaVpnManager.CertificateState.Valid))
        whenever(connectedServerState.connectedServer).thenReturn(MutableStateFlow(null))
        whenever(recentConnectionDao.getRecentConnections()).thenReturn(flowOf(emptyList()))

        viewModel = DashboardViewModel(
            context,
            vpnRepository,
            sessionDao,
            amneziaVpnManager,
            connectedServerState,
            recentConnectionDao
        )
    }

    @Test
    fun `initial state becomes Success after loading servers`() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue("Expected Success but was $state", state is DashboardUiState.Success)
        
        collectJob.cancel()
    }

    @Test
    fun `dashboard updates when VPN state changes`() = runTest {
        val tunnelStateFlow = MutableStateFlow(Tunnel.State.DOWN)
        whenever(amneziaVpnManager.tunnelState).thenReturn(tunnelStateFlow)
        
        // Re-init viewModel to use the new tunnelState mock
        viewModel = DashboardViewModel(
            context, vpnRepository, sessionDao, amneziaVpnManager,
            connectedServerState, recentConnectionDao
        )

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        tunnelStateFlow.value = Tunnel.State.UP
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is DashboardUiState.Success)
        assertTrue((state as DashboardUiState.Success).isConnected)
        
        collectJob.cancel()
    }
}
