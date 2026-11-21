package com.youfeng.autoadb.session

/** null attachment means the physical port cannot currently be observed. */
data class UsbSnapshot(
    val attached: Boolean?,
    val portId: String? = null,
    val deviceRole: Boolean = false,
    val connected: Boolean = false,
    val configured: Boolean = false,
) {
    val dataHost: Boolean
        get() = attached == true && deviceRole && connected && configured
}
