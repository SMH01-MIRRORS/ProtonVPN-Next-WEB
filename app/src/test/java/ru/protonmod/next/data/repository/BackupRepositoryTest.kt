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

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.RecentConnectionDao
import ru.protonmod.next.data.local.RecentConnectionEntity
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.data.model.BackupCategory

class BackupRepositoryTest {

    @Mock
    private lateinit var settingsManager: SettingsManager
    
    @Mock
    private lateinit var profileDao: ProfileDao
    
    @Mock
    private lateinit var recentConnectionDao: RecentConnectionDao

    private lateinit var repository: BackupRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = BackupRepository(settingsManager, profileDao, recentConnectionDao)
    }

    @Test
    fun `exportData includes requested categories`() = runTest {
        // Arrange
        val settings = mapOf("kill_switch" to "true", "vpn_port" to "51820")
        whenever(settingsManager.getAllPreferences()).thenReturn(settings)
        
        val profiles = listOf(VpnProfileEntity("id1", "Profile 1", "AmneziaWG", 51820, false, null, null, null, null, null))
        whenever(profileDao.getAllProfiles()).thenReturn(profiles)

        // Act
        val json = repository.exportData(setOf(BackupCategory.GENERAL_SETTINGS, BackupCategory.PROFILES))

        // Assert
        // Verify that JSON contains settings and profiles
        assert(json.contains("kill_switch"))
        assert(json.contains("Profile 1"))
        // VPN Port was NOT requested in categories, but it is in the map. 
        // Our getKeysForCategory for GENERAL_SETTINGS is: listOf("kill_switch", "auto_connect", "notifications", "app_theme", "server_load_display_mode")
        // So vpn_port should NOT be in filteredSettings.
        assert(!json.contains("vpn_port"))
    }

    @Test
    fun `importData restores settings and database`() = runTest {
        // Arrange
        val jsonContent = """
            {
                "version": 1,
                "timestamp": 123456789,
                "settings": {
                    "kill_switch": "true"
                },
                "profiles": [
                    {
                        "id": "id1",
                        "name": "Profile 1",
                        "protocol": "AmneziaWG",
                        "port": 51820,
                        "isObfuscationEnabled": false,
                        "obfuscationProfileId": null,
                        "autoOpenUrl": null,
                        "targetServerId": null,
                        "targetCountry": null,
                        "targetCity": null,
                        "createdAt": 123456789
                    }
                ],
                "recentConnections": null
            }
        """.trimIndent()

        // Act
        repository.importData(jsonContent, setOf(BackupCategory.GENERAL_SETTINGS, BackupCategory.PROFILES))

        // Assert
        verify(settingsManager).importPreferences(argThat { containsKey("kill_switch") && get("kill_switch") == "true" })
        verify(profileDao).deleteAllProfiles()
        verify(profileDao).insertProfiles(any())
    }
}
