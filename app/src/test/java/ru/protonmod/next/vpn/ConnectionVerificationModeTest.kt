package ru.protonmod.next.vpn

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.protonmod.next.data.local.ConnectionVerificationMode

class ConnectionVerificationModeTest {
    @Test
    fun `aggressive mode reacts faster than balanced and relaxed modes`() {
        assertTrue(ConnectionVerificationMode.AGGRESSIVE.failureThreshold < ConnectionVerificationMode.BALANCED.failureThreshold)
        assertTrue(ConnectionVerificationMode.BALANCED.failureThreshold < ConnectionVerificationMode.RELAXED.failureThreshold)
        assertTrue(ConnectionVerificationMode.AGGRESSIVE.verificationRetryDelayMs < ConnectionVerificationMode.RELAXED.verificationRetryDelayMs)
    }

    @Test
    fun `disabled mode cannot trigger health reconnect threshold`() {
        assertTrue(ConnectionVerificationMode.DISABLED.failureThreshold > ConnectionVerificationMode.RELAXED.failureThreshold)
    }
}
