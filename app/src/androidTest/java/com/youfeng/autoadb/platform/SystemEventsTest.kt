package com.youfeng.autoadb.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemEventsTest {
    private val failures = mutableListOf<Throwable?>()
    private val events = SystemEvents(
        InstrumentationRegistry.getInstrumentation().targetContext,
        Handler(Looper.getMainLooper()),
    ) { _, error -> failures += error }

    @Test fun onlyConfiguredDataConnectionsProduceAnOrdinaryRefresh() {
        fun usb(connected: Boolean, configured: Boolean) =
            Intent("android.hardware.usb.action.USB_STATE")
                .putExtra("connected", connected)
                .putExtra("configured", configured)

        assertEquals(SystemEvent.REFRESH, events.decode(usb(true, true)))
        assertEquals(SystemEvent.ENUMERATION_LOST, events.decode(usb(true, false)))
        assertEquals(SystemEvent.ENUMERATION_LOST, events.decode(usb(false, true)))
        assertEquals(SystemEvent.ENUMERATION_LOST, events.decode(usb(false, false)))
        assertEquals(
            SystemEvent.ENUMERATION_LOST,
            events.decode(Intent("android.hardware.usb.action.USB_STATE")),
        )
    }

    @Test fun lifecycleEventsRetainTheirDistinctMeaning() {
        val cases = mapOf(
            "android.intent.action.USER_SWITCHED" to SystemEvent.USER_SWITCHED,
            Intent.ACTION_SCREEN_OFF to SystemEvent.SCREEN_OFF,
            Intent.ACTION_LOCALE_CHANGED to SystemEvent.LOCALE_CHANGED,
            Intent.ACTION_SCREEN_ON to SystemEvent.REFRESH,
            Intent.ACTION_BOOT_COMPLETED to SystemEvent.REFRESH,
            Intent.ACTION_USER_UNLOCKED to SystemEvent.REFRESH,
            Intent.ACTION_USER_PRESENT to SystemEvent.REFRESH,
        )
        cases.forEach { (action, expected) ->
            assertEquals(action, expected, events.decode(Intent(action)))
        }
    }

    @Test fun missingOrUnreadablePortStateRequiresSessionRevocation() {
        val action = "android.hardware.usb.action.USB_PORT_CHANGED"
        assertEquals(SystemEvent.PORT_DETACHED, events.decode(Intent(action)))
        // A Parcelable with no isConnected method exercises the ROM parsing failure path.
        assertEquals(
            SystemEvent.PORT_DETACHED,
            events.decode(Intent(action).putExtra("portStatus", Bundle())),
        )
        assertEquals(1, failures.size)
        assertNotNull(failures.single())
    }

    @Test fun receiverRegistrationMatchesTheRunningSdk() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        var received: SystemEvent? = null
        SystemEvents(context, Handler(Looper.getMainLooper())) { _, _ -> }.start { received = it }

        assertNotNull(context.receiver)
        assertEquals(Build.VERSION.SDK_INT >= 33, context.usedFlagsOverload)
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals(Context.RECEIVER_EXPORTED, context.flags)
        } else {
            assertNull(context.flags)
        }
        context.receiver!!.onReceive(context, Intent(Intent.ACTION_LOCALE_CHANGED))
        assertEquals(SystemEvent.LOCALE_CHANGED, received)
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var receiver: BroadcastReceiver? = null
        var filter: IntentFilter? = null
        var flags: Int? = null
        var usedFlagsOverload = false

        override fun registerReceiver(
            receiver: BroadcastReceiver?, filter: IntentFilter,
            broadcastPermission: String?, scheduler: Handler?,
        ): Intent? {
            this.receiver = receiver
            this.filter = filter
            return null
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?, filter: IntentFilter,
            broadcastPermission: String?, scheduler: Handler?, flags: Int,
        ): Intent? {
            this.receiver = receiver
            this.filter = filter
            this.flags = flags
            usedFlagsOverload = true
            return null
        }
    }
}
