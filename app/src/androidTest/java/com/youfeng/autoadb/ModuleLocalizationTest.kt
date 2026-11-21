package com.youfeng.autoadb

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youfeng.autoadb.presentation.moduleResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModuleLocalizationTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext

    private fun systemContext(tags: String): Context {
        val framework = app.createPackageContext("android", 0)
        val configuration = Configuration(framework.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tags))
        }
        return framework.createConfigurationContext(configuration)
    }

    @Test fun moduleTextUsesTheSystemContextLanguage() {
        val cases = mapOf(
            "en-US" to "Enable USB debugging?",
            "en-GB" to "Enable USB debugging?",
            "zh-CN" to "开启 USB 调试？",
            "zh-SG" to "开启 USB 调试？",
            "zh-TW" to "開啟 USB 偵錯？",
            "zh-HK" to "開啟 USB 偵錯？",
            "zh-MO" to "開啟 USB 偵錯？",
            "zh-Hant" to "開啟 USB 偵錯？",
            "fr-FR" to "Enable USB debugging?",
            "en-US,zh-TW" to "Enable USB debugging?",
            "fr-FR,zh-TW" to "開啟 USB 偵錯？",
        )
        cases.forEach { (language, expected) ->
            val resources = systemContext(language).moduleResources(app.packageName)
            assertEquals(language, expected, resources.getString(R.string.dialog_title))
        }
    }

    @Test fun changingLanguageDoesNotMutateTheOriginalResources() {
        val original = systemContext("en-US").moduleResources(app.packageName)
        val changed = systemContext("zh-TW").moduleResources(app.packageName)
        assertEquals("開啟", changed.getString(R.string.button_allow))
        assertEquals("Enable", original.getString(R.string.button_allow))
    }

    @Test fun messagesButtonsAndModuleDescriptionAreLocalized() {
        val english = systemContext("en-US").moduleResources(app.packageName)
        val simplified = systemContext("zh-CN").moduleResources(app.packageName)
        val traditional = systemContext("zh-TW").moduleResources(app.packageName)
        assertEquals("Cancel", english.getString(R.string.button_cancel))
        assertEquals("取消", simplified.getString(R.string.button_cancel))
        assertEquals("知道了", traditional.getString(R.string.button_ok))
        assertTrue(english.getString(R.string.error_enable_failed).startsWith("Unable to enable"))
        assertTrue(simplified.getString(R.string.error_disable_failed).contains("无法关闭"))
        assertTrue(traditional.getString(R.string.error_disable_failed).contains("無法關閉"))
        assertTrue(english.getString(R.string.module_summary).startsWith("Ask before"))
        assertTrue(simplified.getString(R.string.dialog_message).contains("本次连接"))
        assertTrue(traditional.getString(R.string.dialog_message).contains("此次連線"))
    }
}
