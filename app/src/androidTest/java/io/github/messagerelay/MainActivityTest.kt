package io.github.messagerelay

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun onboardingSupportsMultipleSourcesAndSingleChannel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { AppSettingsRepository(context).setOnboardingComplete(false) }
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("四步完成首次配置").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("继续").performClick()
        composeRule.onNodeWithText("选择来源应用（可多选）").assertIsDisplayed()
        composeRule.onNodeWithText("搜索应用").assertIsDisplayed()
        composeRule.onNodeWithText("手动输入包名").assertExists()
        composeRule.onAllNodes(isToggleable()).onFirst().performClick()
        composeRule.onNodeWithText("模板：", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("继续").performScrollTo().performClick()
        composeRule.onNodeWithText("推送渠道（三选一）").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bark 地址").assertIsDisplayed()
        composeRule.onNodeWithText("钉钉 Webhook").assertDoesNotExist()
    }
}
