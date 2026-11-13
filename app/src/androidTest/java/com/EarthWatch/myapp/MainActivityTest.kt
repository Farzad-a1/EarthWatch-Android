package com.EarthWatch.myapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcomeStartsAndOpensExploreTabs() {
        compose.onNodeWithText("Earth").assertIsDisplayed()
        compose.onNodeWithText("Watch").assertIsDisplayed()
        compose.onNodeWithText("Get Started").performClick()
        compose.onNodeWithText("Explore").assertIsDisplayed()
        compose.onNodeWithText("Satellites").assertIsDisplayed()
    }
}
