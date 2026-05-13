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

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IpSubnetCalculatorImpl @Inject constructor() : IpSubnetCalculator {

    init {
        System.loadLibrary("next")
    }

    override fun isValidIpOrCidr(input: String): Boolean {
        return isValidIpOrCidrNative(input)
    }

    override fun normalizeIp(ip: String): String {
        return if (ip.contains("/")) ip else "$ip/32"
    }

    override fun complementOfExcluded(excludedCidrs: Collection<String>): List<String> {
        return complementOfExcludedNative(excludedCidrs.toTypedArray()).toList()
    }

    private external fun isValidIpOrCidrNative(input: String): Boolean
    private external fun complementOfExcludedNative(excludedCidrs: Array<String>): Array<String>
}
