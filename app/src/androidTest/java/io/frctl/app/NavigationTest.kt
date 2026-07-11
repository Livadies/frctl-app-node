package io.frctl.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun searchAndSettingsScreensRender() {
        rule.onNodeWithText(rule.activity.getString(R.string.nav_search)).performClick()
        rule.onNodeWithTag("search_field").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.nav_settings)).performClick()
        rule.onNodeWithTag("token_field").assertIsDisplayed()
        rule.onNodeWithTag("github_sign_in").assertIsDisplayed()
        rule.onNodeWithTag("save_token").assertIsDisplayed()
    }
}
