package com.EarthWatch.myapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
        compose.onNodeWithText("Satellites").performClick()
        compose.onNodeWithText("Earth Obs.").assertIsDisplayed()
        compose.onNodeWithText("Weather").assertIsDisplayed()
        compose.onNodeWithText("Comm.").assertIsDisplayed()
        compose.onNodeWithText("CelesTrak objects", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Passes").performClick()
        compose.onNodeWithText("Alert me").assertIsDisplayed()
        compose.onNodeWithText("elevation above 10°", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Imagery").performClick()
        compose.onNodeWithText("NASA GIBS · MODIS Corrected Reflectance · Daily composite").assertIsDisplayed()
        compose.onNodeWithText("Saved").performClick()
        compose.onNodeWithContentDescription("Add location").assertIsDisplayed()
    }

    @Test
    fun settingsExplainsPrivacyAndThirdPartyServices() {
        compose.onNodeWithText("Get Started").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Privacy & Licenses").performClick()
        compose.onNodeWithText("No account, analytics, advertising, or device location is used.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("NASA GIBS", substring = true).assertIsDisplayed()
    }
}
