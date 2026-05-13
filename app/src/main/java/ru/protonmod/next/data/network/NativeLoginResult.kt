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

import kotlinx.serialization.Serializable

@Serializable
data class NativeLoginResult(
    val success: Boolean = false,
    val code: Int = 0,
    val accessToken: String = "",
    val refreshToken: String = "",
    val sessionId: String = "",
    val userId: String = "",
    val scopes: Array<String> = emptyArray(),
    val error: String = "",
    val captchaRequired: Boolean = false,
    val captchaUrl: String = "",
    val captchaToken: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NativeLoginResult

        if (success != other.success) return false
        if (code != other.code) return false
        if (accessToken != other.accessToken) return false
        if (refreshToken != other.refreshToken) return false
        if (sessionId != other.sessionId) return false
        if (userId != other.userId) return false
        if (!scopes.contentEquals(other.scopes)) return false
        if (error != other.error) return false
        if (captchaRequired != other.captchaRequired) return false
        if (captchaUrl != other.captchaUrl) return false
        if (captchaToken != other.captchaToken) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + code
        result = 31 * result + accessToken.hashCode()
        result = 31 * result + refreshToken.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + scopes.contentHashCode()
        result = 31 * result + error.hashCode()
        result = 31 * result + captchaRequired.hashCode()
        result = 31 * result + captchaUrl.hashCode()
        result = 31 * result + captchaToken.hashCode()
        return result
    }
}
