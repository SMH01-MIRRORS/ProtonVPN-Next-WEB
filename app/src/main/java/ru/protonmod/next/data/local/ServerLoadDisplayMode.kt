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

package ru.protonmod.next.data.local

/**
 * Display modes for server load in the UI.
 */
enum class ServerLoadDisplayMode {
    /** Show both the progress bar and the percentage text. */
    ALL,
    /** Show only the progress bar. */
    LINE,
    /** Show only the percentage text. */
    PERCENT,
    /** Hide server load information entirely. */
    HIDDEN
}
