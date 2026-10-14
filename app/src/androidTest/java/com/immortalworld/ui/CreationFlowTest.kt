package com.immortalworld.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
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
        try {
            compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
        } catch (failure: Throwable) {
            throw AssertionError("Awaited $matcher. UI tree: " + compose.onRoot(useUnmergedTree = true).printToString(), failure)
        }
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
        compose.onNodeWithText("Submit action").assertIsDisplayed().performClick()
        awaitNode(hasText("Day 1 · 09:00", substring = true))
        awaitOperation()
        compose.onNodeWithTag("action_input").performTextInput("Ask Mei about Moonfall Ruins")
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
        compose.onNodeWithTag("nav_journey").performClick()
        awaitNode(hasTestTag("adventure"))
        compose.onNodeWithTag("action_input").assertTextContains("Ask Mei about Moonfall Ruins")
        // The new layout must preserve the draft through the non-navigating scene sheet.
        compose.onNodeWithTag("journey_menu").performClick()
        awaitNode(hasTestTag("journey_sheet"))
        compose.onNodeWithText("Current surroundings").performClick()
        compose.onNodeWithText("Here, now").assertIsDisplayed()
        compose.onNodeWithTag("close_journey_sheet").performClick()
        compose.onNodeWithTag("action_input").assertTextContains("Ask Mei about Moonfall Ruins")
        compose.onNodeWithTag("action_input").performTextReplacement("Ask Mei about cultivation resources")
        compose.onNodeWithTag("submit_action").assertIsDisplayed().performClick()
        awaitNode(hasText("Day 1 · 10:00", substring = true))
        awaitOperation()
        compose.onNodeWithText("Dewleaf grows near water", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("action_input").performTextReplacement("inspect the recruitment notice")
        compose.onNodeWithTag("submit_action").performClick()
        awaitOperation()
        compose.onNodeWithText("The recruitment notice names", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("journey_menu").performClick()
        compose.onNodeWithText("Recent events").performClick()
        compose.onNodeWithText("Recent developments").assertIsDisplayed()
        compose.onNodeWithTag("close_journey_sheet").performClick()
        compose.onNodeWithTag("journey_menu").performClick()
        compose.onNodeWithTag("journey_sheet").performScrollToNode(hasText("Map"))
        compose.onNodeWithText("Map", substring = false).performClick()
        awaitNode(hasText("CAMPAIGN RECORD"))
        compose.onNodeWithTag("nav_journey").performClick()
        awaitNode(hasTestTag("adventure"))
        compose.onNodeWithTag("action_input").performTextReplacement("travel to Jade River Crossing")
        compose.onNodeWithTag("submit_action").performClick()
        awaitNode(hasText("Day 1 · 12:00", substring = true))
        awaitOperation()
        compose.onNodeWithText("Jade River Crossing", substring = false).assertIsDisplayed()

    }
    @Test fun narrowLargeTextLayoutKeepsComposerAndNavigationUsable() {
        compose.runOnUiThread {
            val model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
            model.create("Narrow story viewport")
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.3f)) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { ImmortalWorldApp(model) }
                }
            }
        }
        awaitNode(hasTestTag("adventure")); awaitOperation()
        compose.onNodeWithTag("action_input").performTextInput("Sit beneath a tree and cultivate for 1 hour")
        compose.onNodeWithTag("submit_action").assertIsDisplayed().performClick()
        awaitNode(hasText("Day 1 · 09:00", substring = true)); awaitOperation()
        compose.onNodeWithTag("action_input").performTextInput("An unfinished, deliberately longer approach to the next conversation. ".repeat(4))
        compose.onNodeWithTag("submit_action").assertIsDisplayed()
        compose.onNodeWithTag("journey_menu").assertIsDisplayed().performClick()
        compose.onNodeWithTag("journey_sheet").performScrollToNode(hasText("Inventory", substring = false))
        compose.onNodeWithText("Inventory", substring = false).performClick()
        awaitNode(hasText("CAMPAIGN RECORD"))
        compose.onNodeWithTag("nav_journey").performClick()
        awaitNode(hasTestTag("adventure"))
        compose.onNodeWithTag("action_input").assertTextContains("An unfinished, deliberately longer approach", substring = true)
    }

}
