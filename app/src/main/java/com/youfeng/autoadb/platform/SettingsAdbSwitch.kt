package com.youfeng.autoadb.platform

import android.content.Context
import android.os.UserManager
import android.provider.Settings
import com.youfeng.autoadb.session.AdbSwitch

internal class SettingsAdbSwitch(
    private val context: Context,
    private val report: (String, Throwable?) -> Unit,
) : AdbSwitch {
    override fun setEnabled(enabled: Boolean): Boolean = try {
        val users = context.getSystemService(UserManager::class.java)
        check(!enabled || !users.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES)) {
            "DISALLOW_DEBUGGING_FEATURES"
        }
        val value = if (enabled) 1 else 0
        val written =
            Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, value)
        val actual =
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, -1)
        check(written && actual == value) { "ADB_ENABLED requested=$value actual=$actual written=$written" }
        report("USB 调试设置已确认：$value", null)
        true
    } catch (error: Throwable) {
        report("写入 USB 调试设置失败：enabled=$enabled", error)
        false
    }
}
