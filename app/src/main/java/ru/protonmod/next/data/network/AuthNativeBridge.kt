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

import javax.inject.Inject
import javax.inject.Singleton

interface AuthNativeBridge {
    fun login(username: String, passwordRaw: String, captchaToken: String?): NativeLoginResult
}

@Singleton
class AuthNativeBridgeImpl @Inject constructor() : AuthNativeBridge {
    init {
        System.loadLibrary("next")
    }

    override fun login(username: String, passwordRaw: String, captchaToken: String?): NativeLoginResult {
        return loginNative(username, passwordRaw, captchaToken)
    }

    private external fun loginNative(username: String, passwordRaw: String, captchaToken: String?): NativeLoginResult
}
