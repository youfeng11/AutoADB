package com.youfeng.autoadb.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Parcelable

internal enum class SystemEvent {
    REFRESH,
    PORT_DETACHED,
    ENUMERATION_LOST,
    USER_SWITCHED,
    SCREEN_OFF,
    LOCALE_CHANGED,
}

/** Converts Android broadcasts to events before a later USB query can overwrite their meaning. */
internal class SystemEvents(
    private val context: Context,
    private val main: Handler,
    private val report: (String, Throwable?) -> Unit,
) {
    fun start(onEvent: (SystemEvent) -> Unit) {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_STATE)
            addAction(ACTION_USB_PORT_CHANGED)
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(ACTION_USER_SWITCHED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onEvent(decode(intent))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, null, main, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter, null, main)
        }
    }

    internal fun decode(intent: Intent): SystemEvent = when (intent.action) {
        ACTION_USB_PORT_CHANGED ->
            if (portWasDetached(intent)) SystemEvent.PORT_DETACHED else SystemEvent.REFRESH

        ACTION_USB_STATE ->
            if (!intent.getBooleanExtra("connected", false) ||
                !intent.getBooleanExtra("configured", false)
            ) {
                SystemEvent.ENUMERATION_LOST
            } else {
                SystemEvent.REFRESH
            }

        ACTION_USER_SWITCHED -> SystemEvent.USER_SWITCHED
        Intent.ACTION_SCREEN_OFF -> SystemEvent.SCREEN_OFF
        Intent.ACTION_LOCALE_CHANGED -> SystemEvent.LOCALE_CHANGED
        else -> SystemEvent.REFRESH
    }

    @Suppress("DEPRECATION")
    private fun portWasDetached(intent: Intent): Boolean = try {
        val status = intent.getParcelableExtra<Parcelable>("portStatus")
        status == null || !(status.javaClass.getMethod("isConnected").invoke(status) as Boolean)
    } catch (error: Throwable) {
        report("无法解析 USB 端口事件，撤销当前会话", error)
        true
    }

    companion object {
        private const val ACTION_USER_SWITCHED = "android.intent.action.USER_SWITCHED"
        private const val ACTION_USB_PORT_CHANGED = "android.hardware.usb.action.USB_PORT_CHANGED"
        private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
    }
}
