package com.youfeng.autoadb.platform

import android.content.Context
import android.os.Handler
import com.youfeng.autoadb.session.UsbSnapshot
import java.lang.reflect.Field

private fun Any.systemField(name: String): Any? {
    var type: Class<*>? = javaClass
    var field: Field? = null
    while (type != null && field == null) {
        try {
            field = type.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            type = type.superclass
        }
    }
    return checkNotNull(field) { "Missing ${javaClass.name}.$name" }
        .apply { isAccessible = true }.get(this)
}

/** Read the framework USB gadget fields on their owning handler thread. */
internal class SystemUsbAdapter(service: Any) {
    val context = service.systemField("mContext") as Context
    private val ports = checkNotNull(service.systemField("mPortManager"))
    private val gadget = checkNotNull(service.systemField("mDeviceManager"))
        .systemField("mHandler") as Handler
    val handler: Handler get() = gadget
    private val getPorts = ports.javaClass.getMethod("getPorts")
    private val getStatus = ports.javaClass.getMethod("getPortStatus", String::class.java)

    fun read(): UsbSnapshot {
        val available = getPorts.invoke(ports) as Array<*>
        if (available.size != 1) return UsbSnapshot(null)
        val port = checkNotNull(available.single())
        val id = port.javaClass.getMethod("getId").invoke(port) as String
        val status = getStatus.invoke(ports, id) ?: return UsbSnapshot(null)
        val attached = status.javaClass.getMethod("isConnected").invoke(status) as Boolean
        val role = status.javaClass.getMethod("getCurrentDataRole").invoke(status) as Int
        return UsbSnapshot(
            attached = attached, portId = id, deviceRole = role == DATA_ROLE_DEVICE,
            connected = gadget.systemField("mConnected") as Boolean,
            configured = gadget.systemField("mConfigured") as Boolean,
        )
    }

    companion object {
        private const val DATA_ROLE_DEVICE = 2
    }
}
