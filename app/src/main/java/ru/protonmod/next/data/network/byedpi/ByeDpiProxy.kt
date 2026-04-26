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

package ru.protonmod.next.data.network.byedpi

/**
 * JNI wrapper for the ByeDPI (ciadpi) native engine.
 */
class ByeDpiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    /**
     * Starts the proxy with given command line arguments.
     * This is a blocking call that runs the event loop.
     */
    fun startProxy(args: Array<String>): Int {
        return jniStartProxy(args)
    }

    /**
     * Gracefully stops the proxy by shutting down the server socket.
     */
    fun stopProxy(): Int {
        return jniStopProxy()
    }

    /**
     * Forcefully closes the server socket.
     */
    fun forceClose(): Int {
        return jniForceClose()
    }

    private external fun jniStartProxy(args: Array<String>): Int
    private external fun jniStopProxy(): Int
    private external fun jniForceClose(): Int
}
