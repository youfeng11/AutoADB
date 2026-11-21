package com.youfeng.autoadb.xposed

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.youfeng.autoadb.runtime.UsbRuntime
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.util.concurrent.atomic.AtomicBoolean

class AutoAdbModule : XposedModule() {
    private val started = AtomicBoolean()
    private var runtime: UsbRuntime? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        report(
            "模块入口已加载：process=${param.processName} sdk=${Build.VERSION.SDK_INT} api=$apiVersion",
            null
        )
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        report("进入系统服务启动回调", null)
        try {
            val properties = frameworkProperties
            report("系统 Hook 能力：properties=$properties", null)
            if (properties and PROP_CAP_SYSTEM == 0L) {
                report("需要支持系统进程注入的 Xposed 框架", null)
                return
            }
            @SuppressLint("PrivateApi")
            val service = param.classLoader.loadClass("com.android.server.usb.UsbService")
            hook(service.getDeclaredMethod("systemReady")).intercept { chain ->
                val result = chain.proceed()
                report("UsbService.systemReady 已执行", null)
                if (started.compareAndSet(false, true)) {
                    val instance = chain.thisObject
                    Handler(Looper.getMainLooper()).post {
                        try {
                            runtime = UsbRuntime(
                                checkNotNull(instance),
                                moduleApplicationInfo.packageName,
                                ::report
                            )
                                .also { it.start() }
                        } catch (error: Throwable) {
                            report("USB 会话监听初始化失败", error)
                        }
                    }
                }
                result
            }
            report("已安装 UsbService.systemReady Hook", null)
        } catch (error: Throwable) {
            report("无法安装 USB 服务 Hook", error)
        }
    }

    private fun report(message: String, error: Throwable?) {
        val priority = if (error == null) Log.INFO else Log.ERROR
        Log.println(
            priority,
            "AutoADBXposed",
            message + (error?.let { "\n${Log.getStackTraceString(it)}" } ?: ""))
        if (error == null) log(priority, "AutoADBXposed", message)
        else log(priority, "AutoADBXposed", message, error)
    }
}
