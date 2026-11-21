package com.youfeng.autoadb.session

fun interface AdbSwitch {
    /** Success requires reading back the requested setting. */
    fun setEnabled(enabled: Boolean): Boolean
}
