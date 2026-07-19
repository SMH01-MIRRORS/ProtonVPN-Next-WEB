/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.data.local

/** Controls active tunnel probes and transport-failure sensitivity. */
enum class ConnectionVerificationMode(
    val verificationTimeoutMs: Long,
    val verificationRetryDelayMs: Long,
    val failureThreshold: Int,
    val failureWindowMs: Long,
    val reconnectCooldownMs: Long,
) {
    DISABLED(0, 0, Int.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE),
    RELAXED(12_000, 750, 4, 30_000, 45_000),
    BALANCED(8_000, 200, 2, 15_000, 15_000),
    AGGRESSIVE(5_000, 100, 1, 8_000, 5_000),
}
