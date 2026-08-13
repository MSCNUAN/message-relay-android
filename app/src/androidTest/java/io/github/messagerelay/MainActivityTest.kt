package io.github.messagerelay

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun bottomNavigationUsesSimpleThreePageStructure() {
        prepareApp()
        waitForHome()
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("记录").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onAllNodesWithText("规则").assertCountEquals(0)
    }

    @Test fun homeShowsSimpleModeCardsAndRepairProgress() {
        prepareApp(channels = "[]")
        waitForHome()
        listOf("运行状态", "需要修复配置", "推送渠道", "软件选择", "消息模板", "免打扰", "后台运行", "记录保存", "备份与恢复").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
    }

    @Test fun settingsShowsGroupedHelpGithubUpdateAndAboutAreas() {
        prepareApp()
        waitForHome()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("基础设置").assertIsDisplayed()
        composeRule.onNodeWithText("高级设置").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("帮助与关于").performScrollTo().assertIsDisplayed()
        listOf("使用教程", "更新日志", "版本更新", "GitHub 项目", "问题反馈", "关于消息接力").forEach {
            composeRule.onAllNodesWithText(it).onFirst().performScrollTo().assertExists()
        }
    }

    @Test fun versionUpdateAndAboutPagesAreReachable() {
        prepareApp()
        waitForHome()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("版本更新").performScrollTo().performClick()
        composeRule.onNodeWithText("当前版本").assertIsDisplayed()
        composeRule.onNodeWithText("v0.1.3 · 内部版本 4").assertExists()
        composeRule.onNodeWithText("自动检查更新").assertExists()
        composeRule.onNodeWithText("检查更新").assertExists()
        composeRule.onNodeWithText("查看 GitHub Releases").performScrollTo().assertExists()
        pressBack()
        composeRule.onNodeWithText("关于消息接力").performScrollTo().performClick()
        composeRule.onNodeWithText("消息接力 Message Relay").assertIsDisplayed()
        composeRule.onNodeWithText("GPL-3.0-only").assertExists()
        composeRule.onNodeWithText("OpenAI Codex 辅助开发", substring = true).assertExists()
        composeRule.onNodeWithText("GitHub 开源项目").assertExists()
    }

    @Test fun simpleModeEntryPagesAreReachable() {
        prepareApp()
        waitForHome()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("推送渠道").performScrollTo().performClick()
        composeRule.onNodeWithText("主推送渠道").performScrollTo().assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("软件选择").performScrollTo().performClick()
        composeRule.onNodeWithText("推荐应用").assertIsDisplayed()
        composeRule.onNodeWithText("其他应用").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("设置").onFirst().assertExists()
        pressBack()
        composeRule.onNodeWithText("消息模板").performScrollTo().performClick()
        composeRule.onNodeWithText("简洁模板").assertIsDisplayed()
        composeRule.onNodeWithText("标准模板").assertIsDisplayed()
        composeRule.onNodeWithText("隐私模板").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("原始通知模板").performScrollTo().assertIsDisplayed()
    }

    @Test fun recordsPageShowsStatusTabs() {
        prepareApp()
        waitForHome()
        composeRule.onNodeWithText("记录").performClick()
        listOf("全部", "成功", "失败", "已过滤").forEach {
            composeRule.onAllNodesWithText(it).onFirst().assertIsDisplayed()
        }
    }

    @Test fun recordsDetailShowsActionsForSuccessFailureAndFiltered() {
        prepareApp()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val dao = RelayDatabase.get(context).relayDao()
            dao.deleteAllRecords()
            dao.addRecord(DeliveryRecord(packageName = "com.sms", app = "短信", title = "验证码", body = "123456", status = "成功", createdAt = 1))
            dao.addRecord(DeliveryRecord(packageName = "com.sms", app = "短信", title = "发送失败", body = "网络错误", status = "发送失败", channelResults = """[{"error":"网络错误"}]""", createdAt = 2))
            dao.addRecord(DeliveryRecord(packageName = "com.sms", app = "短信", title = "广告", body = "促销", status = "已过滤", channelResults = """[{"reason":"规则未命中"}]""", createdAt = 3))
        }
        waitForHome()
        composeRule.onNodeWithText("记录").performClick()
        composeRule.onAllNodesWithText("成功").onFirst().performClick()
        composeRule.onNodeWithText("验证码").performScrollTo().performClick()
        composeRule.onNodeWithText("重新发送").assertExists()
        composeRule.onNodeWithText("复制验证码").assertExists()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("失败").performClick()
        composeRule.onNodeWithText("发送失败").performScrollTo().performClick()
        composeRule.onNodeWithText("立即重试").assertExists()
        composeRule.onNodeWithText("一键诊断").assertExists()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("已过滤").performClick()
        composeRule.onNodeWithText("广告").performScrollTo().performClick()
        composeRule.onNodeWithText("仍然发送").assertExists()
        composeRule.onNodeWithText("调整规则").assertExists()
        composeRule.onNodeWithText("删除").assertExists()
    }

    @Test fun backupRestoreKeepsCoreConfigurationAndRejectsInvalidInput() {
        prepareApp()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backup = runBlocking {
            val repository = AppSettingsRepository(context)
            val dao = RelayDatabase.get(context).relayDao()
            dao.deleteAllRules()
            dao.saveRule(
                RuleEntity(
                    "com.sms",
                    "短信",
                    "验证码",
                    enabled = true,
                    templateId = TemplateCatalog.STANDARD_ID,
                    screenOffOnly = true,
                    enabledCallEventTypes = CallEventTypes.serialize(setOf(CallEventType.MISSED_CALL))
                )
            )
            repository.setQuiet(true, "23:00", "08:00", "验证码\n来电")
            repository.setHistoryRetention("7")
            repository.setSelectedTemplatePreset("privacy")
            SecureStore(context).put("channels", ChannelSender.serialize(listOf(ChannelConfig("bark", "https://api.day.app/token", id = "bark_a", name = "iPhone"))))
            repository.setPrimaryChannelId("bark_a")
            ConfigBackup.export(context, false)
        }
        runBlocking {
            val repository = AppSettingsRepository(context)
            val dao = RelayDatabase.get(context).relayDao()
            repository.setQuiet(false, "22:00", "07:00", "")
            repository.setHistoryRetention("30")
            repository.setSelectedTemplatePreset(TemplateCatalog.STANDARD_ID)
            repository.setPrimaryChannelId("")
            dao.deleteAllRules()
            SecureStore(context).put("channels", "[]")
            ConfigBackup.import(context, backup)
            val settings = repository.current()
            assertTrue(settings.quietEnabled)
            assertEquals("7", settings.historyRetention)
            assertEquals("privacy", settings.selectedTemplatePreset)
            assertEquals("bark_a", settings.primaryChannelId)
            assertEquals("短信", dao.allRules().single().appName)
            assertTrue(dao.allRules().single().screenOffOnly)
            assertEquals(setOf(CallEventType.MISSED_CALL), CallEventTypes.parse(dao.allRules().single().enabledCallEventTypes))
            assertEquals("iPhone", ChannelSender.parse(SecureStore(context).get("channels").orEmpty()).single().name)
            dao.deleteAllRules()
            ConfigBackup.import(
                context,
                """
                {
                  "version": 2,
                  "settings": {},
                  "rules": [{
                    "packageName": "com.tencent.mm",
                    "appName": "微信",
                    "includes": "",
                    "excludes": "",
                    "enabled": true,
                    "templateId": "wechat",
                    "barkTargetIds": "bark_a",
                    "useIndependentBarkRoute": true,
                    "barkIconMode": "CUSTOM_URL",
                    "barkIconUrl": "https://example.com/icon.png"
                  }],
                  "channels": "[]"
                }
                """.trimIndent()
            )
            val migratedRule = dao.allRules().single()
            assertFalse(migratedRule.useIndependentBarkRoute)
            assertEquals("", migratedRule.barkTargetIds)
            val channelsBeforeInvalid = SecureStore(context).get("channels")
            val rulesBeforeInvalid = dao.allRules()
            runCatching { ConfigBackup.import(context, """{"version":99,"settings":{},"rules":[]}""") }
            assertEquals(channelsBeforeInvalid, SecureStore(context).get("channels"))
            assertEquals(rulesBeforeInvalid, dao.allRules())
        }
    }

    @Test fun manualAndChangelogExposeUpdatedContent() {
        prepareApp()
        waitForHome()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("使用教程").performScrollTo().performClick()
        composeRule.onNodeWithText("1. 快速开始").assertExists()
        composeRule.onNodeWithText("5. 设置仅锁屏时推送").performScrollTo().assertExists()
        composeRule.onNodeWithText("6. 设置电话通知类型").performScrollTo().assertExists()
        composeRule.onNodeWithText("12. 版本更新").performScrollTo().assertExists()
        composeRule.onNodeWithText("13. 常见问题").performScrollTo().assertExists()
        composeRule.onAllNodesWithContentDescription("展开教程章节：12. 版本更新").onFirst().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("教程章节已展开：12. 版本更新").onFirst().assertExists()
        pressBack()
        composeRule.onNodeWithText("更新日志").performScrollTo().assertExists()
        composeRule.onAllNodesWithText("新增").onFirst().performScrollTo().assertExists()
        composeRule.onAllNodesWithText("优化").onFirst().performScrollTo().assertExists()
        composeRule.onAllNodesWithText("修复").onFirst().performScrollTo().assertExists()
    }

    @Test fun appRuleShowsScreenOffOnlyAndCallTypesWithoutIndependentBarkUi() {
        prepareApp()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            RelayDatabase.get(context).relayDao().saveRule(
                RuleEntity(
                    packageName = "com.android.dialer",
                    appName = "电话",
                    templateId = "phone",
                    screenOffOnly = true,
                    enabledCallEventTypes = CallEventTypes.serialize(CallEventTypes.default)
                )
            )
        }
        waitForHome()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("高级设置区域").performScrollTo().performClick()
        composeRule.onNodeWithText("继续进入").performClick()
        composeRule.onNodeWithText("关键词过滤与应用独立规则").performClick()
        composeRule.onAllNodesWithText("仅锁屏时推送").onFirst().performScrollTo().assertExists()
        composeRule.onAllNodesWithText("电话通知类型").onFirst().performScrollTo().assertExists()
        composeRule.onAllNodesWithText("未接来电").onFirst().performScrollTo().assertExists()
        composeRule.onAllNodesWithText("来电提醒").onFirst().performScrollTo().assertExists()
        composeRule.onAllNodesWithText("来电已接通").onFirst().performScrollTo().assertExists()
        composeRule.onAllNodesWithText("使用独立 Bark 设置").assertCountEquals(0)
        composeRule.onAllNodesWithText("应用独立 Bark 设置").assertCountEquals(0)
        composeRule.onAllNodesWithText("发送到 Bark").assertCountEquals(0)
    }

    @Test fun advancedConfirmationAppearsOnlyOnce() {
        prepareApp()
        waitForHome()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("高级设置区域").performScrollTo().performClick()
        composeRule.onNodeWithText("进入高级设置？").assertIsDisplayed()
        composeRule.onNodeWithText("继续进入").performClick()
        composeRule.onNodeWithText("风险提示").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("高级设置区域").performScrollTo().performClick()
        composeRule.onAllNodesWithText("进入高级设置？").assertCountEquals(0)
        composeRule.onNodeWithText("风险提示").assertIsDisplayed()
    }

    private fun prepareApp(channels: String = ChannelSender.serialize(listOf(ChannelConfig("bark", "https://api.day.app/a", id = "bark_a", name = "Bark 1")))) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val repository = AppSettingsRepository(context)
            val dao = RelayDatabase.get(context).relayDao()
            dao.deleteAllRecords()
            dao.deleteAllRules()
            repository.setOnboardingComplete(true)
            repository.setPaused(false)
            repository.setThemeMode("system")
            repository.setPrimaryChannelId("bark_a")
            repository.setSelectedTemplatePreset("standard")
            repository.setAdvancedAcknowledged(false)
            repository.setAutoCheckUpdates(false)
            repository.setLastUpdateCheckAt(System.currentTimeMillis())
            SecureStore(context).put("channels", channels)
        }
    }

    private fun waitForHome() {
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithText("消息接力").assertExists()
            }.isSuccess
        }
    }

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }
}
