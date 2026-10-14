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
    private fun awaitNode(matcher: SemanticsMatcher) {
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
    }
    private fun awaitOperation() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("operation_in_progress").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun createInspectAndWriteJournal() {
        // Lazy content must be scrolled into view before injecting a touch. API 35
        // edge-to-edge/viewport dimensions can place this button below the fold.
        compose.onNodeWithText("Begin a new life").performScrollTo().assertIsDisplayed().performClick()
        awaitNode(hasTestTag("creator"))
        compose.onNodeWithTag("creator").performScrollToNode(hasText("Campaign name"))
        compose.onNodeWithText("Campaign name").performTextReplacement("UI test campaign")
        compose.onNodeWithTag("creator").performScrollToNode(hasText("Character name"))
        compose.onNodeWithText("Character name").performTextReplacement("Test Cultivator")
        compose.onNodeWithTag("creator").performScrollToNode(hasText("Begin journey & create save"))
        compose.onNodeWithText("Begin journey & create save").performClick()
        awaitNode(hasTestTag("adventure"))
        awaitOperation()
        compose.onNodeWithTag("action_input").performTextInput("Cultivate for 1 hour")
        compose.onNodeWithText("Submit action").performClick()
        awaitNode(hasText("Day 1 · 09:00", substring = true))
        awaitOperation()
        compose.onNodeWithText("Notes").performClick()
        awaitNode(hasTestTag("journal"))
        compose.onNodeWithTag("journal").performScrollToNode(hasText("Plans, promises, and reflections"))
        compose.onNodeWithText("Plans, promises, and reflections").performTextInput("I will return to Jade River.")
        compose.onNodeWithTag("journal").performScrollToNode(hasText("Add entry"))
        compose.onNodeWithText("Add entry").performClick()
        awaitNode(hasText("Saved locally. Your previous revision is backed up."))
        awaitOperation()
        compose.onNodeWithTag("journal").performScrollToNode(hasText("I will return to Jade River."))
        compose.onNodeWithText("I will return to Jade River.").assertIsDisplayed()
        compose.onNodeWithTag("nav_status").performClick()
        awaitNode(hasText("CHARACTER RECORD"))
        compose.onNodeWithText("CHARACTER RECORD").assertIsDisplayed()
        compose.onNodeWithText("Test Cultivator").assertIsDisplayed()
    }
}
