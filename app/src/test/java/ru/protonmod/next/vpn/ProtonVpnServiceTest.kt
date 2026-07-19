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

package ru.protonmod.next.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtonVpnServiceTest {
    @Test
    fun `notification is shown only for active states when enabled`() {
        assertTrue(ProtonVpnService.shouldShowNotification(ProtonVpnService.STATE_CONNECTING, true))
        assertTrue(ProtonVpnService.shouldShowNotification(VpnTunnelState.UP.name, true))
        assertFalse(ProtonVpnService.shouldShowNotification(VpnTunnelState.DOWN.name, true))
    }

    @Test
    fun `disabled notification setting hides every VPN state`() {
        assertFalse(ProtonVpnService.shouldShowNotification(ProtonVpnService.STATE_CONNECTING, false))
        assertFalse(ProtonVpnService.shouldShowNotification(VpnTunnelState.UP.name, false))
        assertFalse(ProtonVpnService.shouldShowNotification(VpnTunnelState.DOWN.name, false))
    }
}
