package com.youfeng.autoadb.presentation

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.WindowManager
import android.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youfeng.autoadb.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemDialogContextTest {
    @Test fun systemWindowTypeAndModuleTextWorkFromTheMinimumSdk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext
            val display = app.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
            val window = app.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG, null)
            val strings = window.moduleResources(app.packageName)
            val themed = ContextThemeWrapper(window, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            val dialog = AlertDialog.Builder(themed)
                .setTitle(strings.getString(R.string.dialog_title))
                .setMessage(strings.getString(R.string.dialog_message))
                .setPositiveButton(strings.getString(R.string.button_allow), null)
                .setNegativeButton(strings.getString(R.string.button_cancel), null)
                .create()
            dialog.window!!.setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG)
            assertEquals(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG, dialog.window!!.attributes.type)
            assertNotNull(window.getSystemService(Context.WINDOW_SERVICE))
            assertNotNull(strings.getString(R.string.dialog_title))
            dialog.dismiss()
        }
    }
}
