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

object LanExclusionUtils {
    /**
     * Comprehensive list of AllowedIPs that excludes common LAN ranges:
     * - 10.0.0.0/8
     * - 127.0.0.0/8
     * - 169.254.0.0/16
     * - 172.16.0.0/12
     * - 192.168.0.0/16
     * - fc00::/7
     * - fe80::/10
     */
    val REFINED_ALLOWED_IPS = listOf(
        "1.0.0.0/8", "2.0.0.0/7", "4.0.0.0/6", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", 
        "16.0.0.0/4", "32.0.0.0/3", "64.0.0.0/3", "96.0.0.0/4", "112.0.0.0/5", "120.0.0.0/6", 
        "124.0.0.0/7", "126.0.0.0/8", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/8", "169.0.0.0/9", 
        "169.128.0.0/10", "169.192.0.0/11", "169.224.0.0/12", "169.240.0.0/13", "169.248.0.0/14", 
        "169.252.0.0/15", "169.255.0.0/16", "170.0.0.0/7", "172.0.0.0/12", "172.32.0.0/11", 
        "172.64.0.0/10", "172.128.0.0/9", "173.0.0.0/8", "174.0.0.0/7", "176.0.0.0/4", 
        "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16", "192.170.0.0/15", 
        "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10", "193.0.0.0/8", "194.0.0.0/7", 
        "196.0.0.0/6", "200.0.0.0/5", "208.0.0.0/4", "224.0.0.0/4", 
        "::/1", "8000::/2", "c000::/3", "e000::/4", "f000::/5", "f800::/6", "fe00::/9", "fec0::/10", "ff00::/8"
    )

    /**
     * Ranges that are excluded to produce REFINED_ALLOWED_IPS.
     */
    val EXCLUDED_RANGES = listOf(
        "10.0.0.0/8", 
        "127.0.0.0/8", 
        "169.254.0.0/16", 
        "172.16.0.0/12", 
        "192.168.0.0/16",
        "fc00::/7", 
        "fe80::/10"
    )
}
