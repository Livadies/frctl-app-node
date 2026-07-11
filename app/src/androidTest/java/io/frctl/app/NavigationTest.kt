package io.frctl.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun searchAndSettingsScreensRender() {
        rule.onNodeWithTag("search_field").assertIsDisplayed()
        rule.onNodeWithTag("settings_button").performClick()
        rule.onNodeWithTag("token_field").assertIsDisplayed()
        rule.onNodeWithTag("save_token").assertIsDisplayed()
    }
}
