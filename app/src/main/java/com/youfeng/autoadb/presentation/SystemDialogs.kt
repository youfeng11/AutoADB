package com.youfeng.autoadb.presentation

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.WindowManager
import com.youfeng.autoadb.R
import com.youfeng.autoadb.session.UsbSession

internal class SystemDialogs(private val modulePackageName: String) {
    private var dialog: AlertDialog? = null
    private var key: String? = null

    fun render(
        sessionId: Long?, error: UsbSession.Failure?,
        decide: (Long, Boolean) -> Unit, clearError: () -> Unit,
    ) {
        val nextKey = error?.let { "error:$it" } ?: sessionId?.let { "session:$it" }
        if (key == nextKey) return
        hide()
        if (nextKey == null) return
        @SuppressLint("PrivateApi")
        val threadClass = Class.forName("android.app.ActivityThread")
        val thread = threadClass.getMethod("currentActivityThread").invoke(null)
        val uiContext = threadClass.getMethod("getSystemUiContext").invoke(thread) as Context
        val windowContext =
            uiContext.createWindowContext(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG, null)
        val strings = windowContext.moduleResources(modulePackageName)
        val themed =
            ContextThemeWrapper(windowContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val builder = AlertDialog.Builder(themed)
        if (error != null) {
            val errorText = when (error) {
                UsbSession.Failure.ENABLE -> R.string.error_enable_failed
                UsbSession.Failure.DISABLE -> R.string.error_disable_failed
            }
            builder.setTitle(strings.getString(R.string.error_title))
                .setMessage(strings.getString(errorText))
                .setPositiveButton(strings.getString(R.string.button_ok)) { _, _ -> clearError() }
        } else {
            builder.setTitle(strings.getString(R.string.dialog_title))
                .setMessage(strings.getString(R.string.dialog_message))
                .setNegativeButton(strings.getString(R.string.button_cancel)) { _, _ ->
                    decide(
                        checkNotNull(sessionId),
                        false
                    )
                }
                .setPositiveButton(strings.getString(R.string.button_allow)) { _, _ ->
                    decide(
                        checkNotNull(sessionId),
                        true
                    )
                }
        }
        val created = builder.create()
        created.setCanceledOnTouchOutside(false)
        created.setOnCancelListener {
            if (error != null) clearError() else decide(checkNotNull(sessionId), false)
        }
        created.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG)
        created.show()
        key = nextKey
        dialog = created
        created.setOnDismissListener {
            if (dialog === created) {
                dialog = null; key = null
            }
        }
    }

    fun hide() {
        val previous = dialog
        dialog = null
        key = null
        previous?.dismiss()
    }
}
