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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import retrofit2.Response
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.network.*

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

    private lateinit var repository: VpnRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = VpnRepository(vpnApi, serverDao, sessionDao, serversCacheDao)
    }

    @Test
    fun `getServers maps logical loads correctly and saves to DB`() = runTest {
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
                "Loads": [
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

        // Mock API
        whenever(vpnApi.getLogicalServers(
            authorization = eq("Bearer token"),
            sessionId = eq("session"),
            lastModified = any(),
            locale = any(),
            protocols = any(),
            withState = any(),
            userTier = any()
        )).thenReturn(Response.success(logicalServersResponse))

        whenever(vpnApi.getLoads(eq("Bearer token"), eq("session"), any())).thenReturn(
            Response.success(loadsJson.toResponseBody())
        )

        // Act
        val result = repository.getServers("token", "session", 0, forceRefresh = true)

        // Assert
        assertTrue(result.isSuccess)
        val servers = result.getOrNull()!!
        assertEquals(1, servers.size)
        assertEquals(loadValue, servers[0].averageLoad)
        assertEquals(loadValue, servers[0].servers[0].load)
    }
}
