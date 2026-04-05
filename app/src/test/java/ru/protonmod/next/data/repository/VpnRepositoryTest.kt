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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
import retrofit2.Response
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.network.*
import ru.protonmod.next.utils.coroutines.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class VpnRepositoryTest {

    @Mock
    private lateinit var vpnApi: ProtonVpnApi
    
    @Mock
    private lateinit var serverDao: ServerDao
    
    @Mock
    private lateinit var sessionDao: SessionDao
    
    @Mock
    private lateinit var serversCacheDao: ServersCacheDao

    @Mock
    private lateinit var cityTranslationDao: ru.protonmod.next.data.local.CityTranslationDao

    @Mock
    private lateinit var profileDao: ru.protonmod.next.data.local.ProfileDao

    @Mock
    private lateinit var recentConnectionDao: ru.protonmod.next.data.local.RecentConnectionDao

    @Mock
    private lateinit var cityRepository: ru.protonmod.next.data.repository.CityRepository

    private val testDispatcher = StandardTestDispatcher()
    
    private val testDispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = testDispatcher
        override fun io(): CoroutineDispatcher = testDispatcher
        override fun default(): CoroutineDispatcher = testDispatcher
    }
    
    private val testScope = CoroutineScope(testDispatcher + SupervisorJob())

    private lateinit var repository: VpnRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = VpnRepository(
            vpnApi, serverDao, sessionDao, serversCacheDao,
            cityTranslationDao, profileDao, recentConnectionDao,
            cityRepository, testDispatcherProvider, testScope
        )
    }

    @Test
    fun `getServers maps logical loads correctly and saves to DB`() = runTest(testDispatcher) {
        // Arrange
        val logicalId = "logical_1"
        val physicalId = "physical_1"
        val loadValue = 75

        val logicalServersResponse = LogicalServersResponse(
            code = 1000,
            logicalServers = listOf(
                LogicalServer(
                    id = logicalId,
                    name = "Test Server",
                    tier = 0,
                    features = 0,
                    entryCountry = "US",
                    exitCountry = "US",
                    city = "New York",
                    servers = listOf(
                        PhysicalServer(id = physicalId, domain = "test.protonvpn.com", status = 1)
                    )
                )
            )
        )

        val loadsJson = """
            {
                "Code": 1000,
                "LogicalServers": [
                    {
                        "ID": "$logicalId",
                        "Load": $loadValue
                    },
                    {
                        "ID": "$physicalId",
                        "Load": $loadValue
                    }
                ]
            }
        """.trimIndent()

        // Mock Session
        whenever(sessionDao.getSession()).thenReturn(
            SessionEntity(
                accessToken = "token",
                refreshToken = "refresh",
                sessionId = "session",
                userId = "user",
                userTier = 0
            )
        )

        // Mock Cache (empty)
        whenever(serversCacheDao.getCacheInfo()).thenReturn(null)
        whenever(serverDao.getAllServers()).thenReturn(emptyList())

        // Mock API with flexible matching
        whenever(vpnApi.getLogicalServers(
            authorization = any(),
            sessionId = any(),
            lastModified = anyOrNull(),
            locale = anyOrNull(),
            protocols = anyOrNull(),
            withState = any()
        )).thenReturn(Response.success(logicalServersResponse))

        whenever(vpnApi.getLoads(any(), any())).thenReturn(
            Response.success(loadsJson.toResponseBody())
        )

        // Act
        val result = repository.getServers("token", "session", 0, forceRefresh = true)

        // Assert
        assertTrue("Expected success but got ${result.exceptionOrNull()}", result.isSuccess)
        val servers = result.getOrNull()!!
        assertEquals(1, servers.size)
        assertEquals(loadValue, servers[0].averageLoad)
        assertEquals(loadValue, servers[0].servers[0].load)
        
        // Verify DB interactions
        verify(serverDao, atLeastOnce()).insertServers(any())
        verify(serversCacheDao, atLeastOnce()).saveCacheInfo(any())
    }
}
