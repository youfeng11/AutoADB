package com.youfeng.autoadb.session

import org.junit.Assert.*
import org.junit.Test

class UsbSessionTest {
    private class FakeAdb : AdbSwitch {
        val writes = mutableListOf<Boolean>()
        var enableWorks = true
        var disableWorks = true
        override fun setEnabled(enabled: Boolean): Boolean {
            writes += enabled
            return if (enabled) enableWorks else disableWorks
        }
    }
    private val adb = FakeAdb()
    private val policy = UsbSession(adb).also { assertTrue(it.initialize()) }
    private val host = UsbSnapshot(true, "port0", true, true, true)
    private val detached = UsbSnapshot(false, "port0")

    private fun prompt(start: Long = 0): Long {
        policy.update(host, start, true)
        policy.update(host, start + 500, true)
        return checkNotNull(policy.promptSessionId)
    }

    @Test fun requiresStableDataEnumerationAndIgnoresChargers() {
        policy.update(host.copy(connected = false, configured = false), 0, true)
        policy.update(host.copy(connected = false, configured = false), 5000, true)
        assertNull(policy.promptSessionId)
        policy.update(host, 6000, true)
        policy.update(host, 6499, true)
        assertNull(policy.promptSessionId)
        policy.update(host, 6500, true)
        assertNotNull(policy.promptSessionId)
        assertEquals(listOf(false), adb.writes)
    }

    @Test fun repeatedBroadcastsDoNotResetStabilityDeadline() {
        policy.update(host, 0, true)
        policy.update(host, 200, true)
        policy.update(host, 400, true)
        assertEquals(500L, policy.nextCheckAt)
        policy.update(host, 500, true)
        assertNotNull(policy.promptSessionId)
    }

    @Test fun rejectionLastsUntilPhysicalReconnect() {
        val id = prompt()
        policy.decide(id, false)
        policy.update(host.copy(configured = false), 600, true)
        policy.update(host, 9000, true)
        assertNull(policy.promptSessionId)
        assertEquals(UsbSession.Phase.REJECTED, policy.phase)
        policy.update(detached, 9100, true)
        assertNotEquals(id, prompt(10000))
    }

    @Test fun enablingAdbReenumerationDoesNotRevokeSession() {
        policy.decide(prompt(), true)
        policy.update(host.copy(connected = false, configured = false), 600, true)
        policy.update(host, 5000, true)
        assertEquals(UsbSession.Phase.AUTHORIZED, policy.phase)
        assertEquals(listOf(false, true), adb.writes)
        policy.update(detached, 5100, true)
        assertEquals(listOf(false, true, false), adb.writes)
    }

    @Test fun staleDecisionCannotAuthorizeNewConnection() {
        val stale = prompt()
        policy.update(detached, 600, true)
        val current = prompt(1000)
        policy.decide(stale, true)
        assertEquals(current, policy.promptSessionId)
        assertFalse(adb.writes.contains(true))
        policy.decide(current, true)
        assertTrue(adb.writes.last())
    }

    @Test fun lockHidesPromptAndInvalidatesClicksUntilUnlock() {
        val id = prompt()
        policy.update(host, 600, false)
        assertNull(policy.promptSessionId)
        policy.decide(id, true)
        assertFalse(adb.writes.contains(true))
        policy.update(host, 4000, true)
        assertEquals(id, policy.promptSessionId)
    }

    @Test fun lostEnumerationMustStabilizeAgainBeforeAllowingClick() {
        val id = prompt()
        policy.update(host.copy(configured = false), 600, true)
        policy.decide(id, true)
        policy.update(host, 700, true)
        policy.decide(id, true)
        assertFalse(adb.writes.contains(true))
        policy.update(host, 1200, true)
        assertEquals(id, policy.promptSessionId)
    }

    @Test fun transientBroadcastLossRestartsTheFullStabilityWindow() {
        val id = prompt()
        policy.enumerationLost()
        policy.decide(id, true)
        policy.update(host, 600, true)
        policy.update(host, 1099, true)
        assertNull(policy.promptSessionId)
        assertFalse(adb.writes.contains(true))
        policy.update(host, 1100, true)
        assertEquals(id, policy.promptSessionId)
        policy.decide(id, true)
        policy.enumerationLost()
        assertEquals(UsbSession.Phase.AUTHORIZED, policy.phase)
        assertTrue(adb.writes.last())
    }

    @Test fun unknownPhysicalStateRevokesAuthorization() {
        val old = prompt()
        policy.decide(old, true)
        policy.update(UsbSnapshot(null), 600, true)
        policy.decide(old, true)
        assertEquals(listOf(false, true, false), adb.writes)
        assertEquals(UsbSession.Phase.WAITING, policy.phase)
    }

    @Test fun failedEnableIsNotAuthorizedAndAttemptsCleanup() {
        adb.enableWorks = false
        policy.decide(prompt(), true)
        assertEquals(UsbSession.Phase.REJECTED, policy.phase)
        assertEquals(listOf(false, true, false), adb.writes)
        assertEquals(UsbSession.Failure.ENABLE, policy.error)
        assertNull(policy.promptSessionId)
    }

    @Test fun failedDisableBlocksAuthorizationUntilCleanupSucceeds() {
        policy.decide(prompt(), true)
        adb.disableWorks = false
        policy.update(detached, 600, true)
        assertEquals(UsbSession.Failure.DISABLE, policy.error)
        policy.update(host, 1000, true)
        policy.update(host, 1500, true)
        assertNull(policy.promptSessionId)
        adb.disableWorks = true
        policy.update(host, 2000, true)
        policy.update(host, 2500, true)
        assertNotNull(policy.promptSessionId)
        assertNull(policy.error)
    }

    @Test fun initializationFailurePreventsPrompting() {
        val failed = UsbSession(AdbSwitch { false })
        assertFalse(failed.initialize())
        failed.update(host, 0, true)
        failed.update(host, 1000, true)
        assertNull(failed.promptSessionId)
    }

    @Test fun dataRoleChangeClosesAuthorizedSession() {
        policy.decide(prompt(), true)
        policy.update(host.copy(deviceRole = false), 600, true)
        assertEquals(listOf(false, true, false), adb.writes)
        policy.update(host, 1200, true)
        assertNull(policy.promptSessionId)
    }

    @Test fun explicitDetachEventPreservesRapidReconnectBoundary() {
        val old = prompt()
        policy.decide(old, true)
        // The next query already says connected; the broadcast's detach was observed first.
        policy.revoke()
        val current = prompt(600)
        policy.decide(old, true)
        assertEquals(current, policy.promptSessionId)
        assertEquals(listOf(false, true, false), adb.writes)
    }

    @Test fun userSwitchRevokesAuthorizationAndOldDecision() {
        val id = prompt()
        policy.decide(id, true)
        policy.revoke()
        policy.decide(id, true)
        assertEquals(listOf(false, true, false), adb.writes)
    }
}
