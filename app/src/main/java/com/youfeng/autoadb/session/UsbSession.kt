package com.youfeng.autoadb.session

/** Single-threaded policy. Time values use a monotonic clock. */
class UsbSession(private val adb: AdbSwitch) {
    enum class Phase { WAITING, PENDING, AUTHORIZED, REJECTED }
    enum class Failure { ENABLE, DISABLE }

    var phase = Phase.WAITING
        private set
    var error: Failure? = null
        private set
    private var initialized = false
    private var serial = 0L
    private var sessionId: Long? = null
    private var portId: String? = null
    private var stableSince: Long? = null
    private var interactive = false
    private var snapshot = UsbSnapshot(null)
    private var needsDisable = false

    val promptSessionId: Long?
        get() = sessionId.takeIf { initialized && phase == Phase.PENDING && interactive }
    val nextCheckAt: Long?
        get() = stableSince?.plus(STABLE_MILLIS)?.takeIf { phase == Phase.WAITING }

    fun initialize(): Boolean {
        initialized = adb.setEnabled(false)
        return initialized
    }

    fun update(next: UsbSnapshot, now: Long, canInteract: Boolean) {
        if (!initialized) return
        interactive = canInteract
        snapshot = next
        if (next.attached != true || next.portId == null) {
            endSession()
            return
        }
        if (sessionId != null && next.portId != portId) endSession()
        if (phase != Phase.AUTHORIZED && needsDisable) {
            disable()
            if (needsDisable) return
        }
        if (sessionId == null) {
            sessionId = ++serial
            portId = next.portId
        }
        if (!next.deviceRole && phase == Phase.AUTHORIZED) {
            phase = Phase.REJECTED
            needsDisable = true
            disable()
        }
        // Gadget re-enumeration alone does not end a physical session.
        if (phase == Phase.AUTHORIZED || phase == Phase.REJECTED) return
        if (!next.dataHost) {
            stableSince = null
            phase = Phase.WAITING
            return
        }
        if (stableSince == null) stableSince = now
        if (now - checkNotNull(stableSince) >= STABLE_MILLIS) phase = Phase.PENDING
    }

    fun decide(id: Long, allow: Boolean) {
        if (promptSessionId != id || !snapshot.dataHost || needsDisable) return
        phase = Phase.REJECTED
        if (!allow) return
        // A failed readback may follow a successful write; cleanup is still required.
        needsDisable = true
        if (adb.setEnabled(true)) {
            phase = Phase.AUTHORIZED
        } else {
            error = Failure.ENABLE
            disable()
        }
    }

    fun clearError() {
        error = null
    }

    /** Preserve a transient loss reported by a broadcast, even if the next query is configured. */
    fun enumerationLost() {
        snapshot = snapshot.copy(connected = false, configured = false)
        stableSince = null
        if (phase == Phase.PENDING) phase = Phase.WAITING
    }

    fun revoke() {
        endSession(); interactive = false
    }

    private fun endSession() {
        if (sessionId != null) needsDisable = true
        sessionId = null
        portId = null
        stableSince = null
        phase = Phase.WAITING
        error = null
        if (needsDisable) disable()
    }

    private fun disable() {
        needsDisable = !adb.setEnabled(false)
        if (needsDisable) error = Failure.DISABLE
        else if (error == Failure.DISABLE) error = null
    }

    companion object {
        const val STABLE_MILLIS = 500L
    }
}
