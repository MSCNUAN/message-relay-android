package io.github.messagerelay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun appShowsPersistedHomeOrOnboarding() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("四步完成首次配置").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("消息接力").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("四步完成首次配置").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("四步完成首次配置").assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("消息接力").assertIsDisplayed()
        }
    }
}
