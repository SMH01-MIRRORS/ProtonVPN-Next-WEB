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

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

/**
 * A generic bridge to handle Java interface proxies in native code.
 * This prevents modders from easily patching Kotlin implementations of
 * security-critical listeners and callbacks.
 */
internal class AntiTamperBridge(private val nativeHandlerAddr: Long) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        return invokeNative(nativeHandlerAddr, proxy, method.name, args)
    }

    private external fun invokeNative(
        handlerAddr: Long,
        proxy: Any,
        methodName: String,
        args: Array<out Any>?
    ): Any?
}
