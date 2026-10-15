package com.immortalworld.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immortalworld.ImmortalWorldApplication
import com.immortalworld.MainActivity
import com.immortalworld.ai.AiConfig
import com.immortalworld.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FrontierFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: GameViewModel
    private lateinit var app: ImmortalWorldApplication
    private var originalProfile = ""
    private var testProfile = ""
    private val slots = mutableSetOf<String>()
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun idle() { compose.waitUntil(15_000) { !model.state.value.busy }; assertNull(model.state.value.error) }
    private fun active() = model.state.value.active!!.save.also { slots.add(it.id) }
    private fun map() { ui { model.navigate(Screen.MAP) }; compose.onNodeWithTag("library_map").assertExists() }
    private fun click(tag: String) {
        compose.onNodeWithTag("library_map").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick(); idle()
    }
    @Before fun setup() {
        ui { model = ViewModelProvider(compose.activity)[GameViewModel::class.java]; app = compose.activity.application as ImmortalWorldApplication }
        compose.waitUntil(15_000) { model.state.value.aiReady && model.state.value.preferencesReady }
        originalProfile = model.state.value.activeProfileId
        ui { model.addAiProfile("Frontier UI ${UUID.randomUUID().toString().take(8)}", AiConfig(autoConnect = false)) }; idle()
        testProfile = model.state.value.activeProfileId
        ui { model.dismissMessage() }
    }
    @After fun cleanup() {
        if (::model.isInitialized) model.state.value.active?.save?.id?.let { slots.add(it) }
        runBlocking {
            if (originalProfile.isNotBlank()) app.aiPreferences.select(originalProfile)
            if (testProfile.isNotBlank()) app.aiPreferences.remove(testProfile)
            slots.forEach { id -> runCatching { val saved = app.saves.load(id); app.saves.delete(id, saved.revision) } }
        }
    }
    @Test fun newCampaignSurveysTravelsAndReloadsTheSameFrontierOnNarrowLargeTextLayout() {
        ui {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { ImmortalWorldApp(model) }
                }
            }
            model.create("Frontier exploration")
        }; idle()
        val initial = active()
        assertNotNull(initial.adventure!!.regionSeed); assertEquals(10, initial.adventure!!.locations.size)
        map()
        compose.onNodeWithTag("add_frontier").assertDoesNotExist()
        compose.onNodeWithTag("travel_frontier_1").assertDoesNotExist()
        val hidden = initial.adventure!!.locations.first { it.id == "frontier_1" }.name
        compose.onAllNodesWithText(hidden, substring = true).assertCountEquals(0)
        click("survey_paths")
        val surveyed = active()
        assertEquals(initial.player, surveyed.player); assertEquals(initial.randomState, surveyed.randomState)
        assertEquals(initial.adventure!!.hour + 1, surveyed.adventure!!.hour)
        assertFalse(surveyed.adventure!!.locations.first { it.id == "frontier_2" }.discovered)
        val road = WorldGeography.route(surveyed.adventure!!, "village", "frontier_1")!!
        click("travel_frontier_1")
        val travelled = active()
        assertEquals("frontier_1", travelled.adventure!!.locationId)
        assertEquals(surveyed.adventure!!.hour + road.hours, travelled.adventure!!.hour)
        assertEquals(1, travelled.adventure!!.visits["frontier_1"])
        click("survey_paths")
        val discovered = active()
        assertTrue(discovered.adventure!!.locations.first { it.id == "frontier_2" }.discovered)
        assertEquals(initial.adventure!!.routes, discovered.adventure!!.routes)
        assertEquals(initial.adventure!!.regionSeed, discovered.adventure!!.regionSeed)
        ui { model.load(discovered.id) }; idle(); map()
        assertEquals(discovered, active())
        assertEquals(discovered, runBlocking { app.saves.load(discovered.id).save })
        click("travel_village")
        assertEquals("village", active().adventure!!.locationId)
        assertEquals(initial.player, active().player)
    }
    @Test fun existingCampaignExpansionIsConfirmedOnceAndKeepsOriginalData() {
        val g = CharacterGenerator.generate(CreationOptions(), 31, "legacy-player")
        val legacy = OpeningScene.attach(GameSave(id = UUID.randomUUID().toString(), name = "Established campaign", createdAt = 1, updatedAt = 1,
            player = g.character, randomState = g.randomState, world = WorldState("world", currentLocation = "Azure Cloud Village", startingCircumstances = "Continue my existing story")))
        slots.add(legacy.id); runBlocking { app.saves.create(legacy) }
        ui { model.load(legacy.id) }; idle(); map()
        assertNull(active().adventure!!.regionSeed)
        click("add_frontier")
        compose.onNodeWithText("Keep existing world").performClick()
        assertEquals(legacy, active())
        click("add_frontier")
        compose.onNodeWithTag("confirm_frontier").performClick(); idle()
        val expanded = active()
        assertNotNull(expanded.adventure!!.regionSeed)
        assertEquals(legacy.player, expanded.player); assertEquals(legacy.world, expanded.world)
        assertEquals(legacy.randomState, expanded.randomState)
        assertEquals(legacy.adventure!!.npcs, expanded.adventure!!.npcs)
        assertEquals(legacy.adventure!!.quests, expanded.adventure!!.quests)
        assertEquals(legacy.adventure!!.history.first(), expanded.adventure!!.history.first())
        assertEquals(legacy.adventure!!.locations, expanded.adventure!!.locations.take(legacy.adventure!!.locations.size))
        compose.onNodeWithTag("add_frontier").assertDoesNotExist()
        assertTrue(runBlocking { app.saves.backups(legacy.id).isNotEmpty() })
        ui { model.load(legacy.id) }; idle(); map()
        assertEquals(expanded, active())
        compose.onNodeWithTag("add_frontier").assertDoesNotExist()
    }
}
