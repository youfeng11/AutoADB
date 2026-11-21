package com.youfeng.autoadb.runtime

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.youfeng.autoadb.platform.SettingsAdbSwitch
import com.youfeng.autoadb.platform.SystemEvent
import com.youfeng.autoadb.platform.SystemEvents
import com.youfeng.autoadb.platform.SystemUsbAdapter
import com.youfeng.autoadb.platform.SystemUserState
import com.youfeng.autoadb.presentation.SystemDialogs
import com.youfeng.autoadb.session.UsbSession
import com.youfeng.autoadb.session.UsbSnapshot

/** Owns the module lifetime and serializes USB observations, decisions, and rendering. */
internal class UsbRuntime(
    service: Any,
    modulePackageName: String,
    private val report: (String, Throwable?) -> Unit,
) {
    private val usb = SystemUsbAdapter(service)
    private val main = Handler(Looper.getMainLooper())
    private val session = UsbSession(SettingsAdbSwitch(usb.context, report))
    private val dialogs = SystemDialogs(modulePackageName)
    private val userState = SystemUserState(usb.context)
    private val events = SystemEvents(usb.context, main, report)
    private val timer = Runnable { refresh() }
    private var lastStatus: String? = null

    fun start() {
        check(session.initialize()) { "无法确认启动时 USB 调试已关闭" }
        events.start(::onSystemEvent)
        refresh()
        report("系统 USB 会话监听已启动", null)
    }

    private fun onSystemEvent(event: SystemEvent) {
        when (event) {
            SystemEvent.ENUMERATION_LOST -> session.enumerationLost()
            SystemEvent.PORT_DETACHED, SystemEvent.USER_SWITCHED -> session.revoke()
            SystemEvent.SCREEN_OFF, SystemEvent.LOCALE_CHANGED, SystemEvent.REFRESH -> Unit
        }
        if (event != SystemEvent.REFRESH) dialogs.hide()
        refresh()
    }

    private fun refresh(decision: Decision? = null) {
        usb.handler.post {
            val snapshot = try {
                usb.read()
            } catch (error: Throwable) {
                report("无法读取 USB 端口状态", error)
                UsbSnapshot(null)
            }
            main.post {
                try {
                    applySnapshot(snapshot, decision)
                } catch (error: Throwable) {
                    dialogs.hide()
                    report("处理 USB 会话失败", error)
                }
            }
        }
    }

    private fun applySnapshot(snapshot: UsbSnapshot, decision: Decision?) {
        val interactive = try {
            userState.canInteract()
        } catch (error: Throwable) {
            report("无法检查当前解锁状态", error)
            false
        }
        session.update(snapshot, SystemClock.elapsedRealtime(), interactive)
        if (decision != null) session.decide(decision.sessionId, decision.allow)

        val status =
            "port=${snapshot.portId} attached=${snapshot.attached} device=${snapshot.deviceRole} " +
                    "connected=${snapshot.connected} configured=${snapshot.configured} phase=${session.phase}"
        if (status != lastStatus) {
            report(status, null)
            lastStatus = status
        }
        main.removeCallbacks(timer)
        session.nextCheckAt?.let {
            main.postDelayed(timer, (it - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
        }
        dialogs.render(
            session.promptSessionId,
            session.error.takeIf { interactive },
            { id, allow -> refresh(Decision(id, allow)) },
            { session.clearError(); refresh() },
        )
    }

    private data class Decision(val sessionId: Long, val allow: Boolean)
}
