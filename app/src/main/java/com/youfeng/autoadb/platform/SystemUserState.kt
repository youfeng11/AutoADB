package com.youfeng.autoadb.platform

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.UserManager

internal class SystemUserState(private val context: Context) {
    fun canInteract(): Boolean {
        @SuppressLint("PrivateApi")
        val properties = Class.forName("android.os.SystemProperties")
        val booted = properties.getMethod("get", String::class.java)
            .invoke(null, "sys.boot_completed") == "1"
        val currentUser = Class.forName("android.app.ActivityManager")
            .getMethod("getCurrentUser").invoke(null) as Int
        val users = context.getSystemService(UserManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return booted && currentUser == 0 && users.isUserUnlocked &&
                power.isInteractive && !keyguard.isKeyguardLocked
    }
}
