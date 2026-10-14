package com.immortalworld.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immortalworld.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun createInspectAndWriteJournal() {
        compose.onNodeWithText("Begin a new life").performClick()
        compose.onNodeWithTag("creator").performScrollToNode(hasText("Campaign name"))
        compose.onNodeWithText("Campaign name").performTextReplacement("UI test campaign")
        compose.onNodeWithTag("creator").performScrollToNode(hasText("Character name"))
        compose.onNodeWithText("Character name").performTextReplacement("Test Cultivator")
        compose.onNodeWithTag("creator").performScrollToNode(hasText("Begin journey & create save"))
        compose.onNodeWithText("Begin journey & create save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("journey").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("journey").performScrollToNode(hasText("Plans, promises, and reflections"))
        compose.onNodeWithText("Plans, promises, and reflections").performTextInput("I will return to Jade River.")
        compose.onNodeWithTag("journey").performScrollToNode(hasText("Add entry"))
        compose.onNodeWithText("Add entry").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Saved locally. Your previous revision is backed up.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("journey").performScrollToNode(hasText("I will return to Jade River."))
        compose.onNodeWithText("I will return to Jade River.").assertIsDisplayed()
        compose.onNodeWithText("Character", useUnmergedTree = true).performClick()
        compose.onNodeWithText("CHARACTER RECORD").assertIsDisplayed()
        compose.onNodeWithText("Test Cultivator").assertIsDisplayed()
    }
}
