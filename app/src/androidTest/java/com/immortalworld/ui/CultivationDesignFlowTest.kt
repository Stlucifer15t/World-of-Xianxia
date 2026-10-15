package com.immortalworld.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immortalworld.ImmortalWorldApplication
import com.immortalworld.MainActivity
import com.immortalworld.core.CharacterGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CultivationDesignFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shot(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        val small = Bitmap.createScaledBitmap(image, 420, (image.height * 420f / image.width).toInt(), true)
        File(directory, "$name.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 72, it) }
    }
    @Test fun narrowCultivationRecordsAndSettingsNavigationPreserveEngineData() {
        lateinit var model: GameViewModel
        compose.runOnUiThread {
            model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
            compose.activity.setContent {
                val current = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(current.density, 1.3f)) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { ImmortalWorldApp(model) }
                }
            }
        }
        compose.waitUntil(15_000) { model.state.value.aiReady && model.state.value.preferencesReady }
        compose.runOnUiThread { model.navigate(Screen.CREATE) }
        shot("creator")
        val seed = model.state.value.seed
        fun expand(title: String) {
            compose.onNodeWithTag("creator").performScrollToNode(hasText(title))
            compose.onNodeWithText(title).performScrollTo().performClick()
        }
        expand("Spiritual Root and Constitution")
        compose.onNodeWithText("Spiritual root").performScrollTo().performClick()
        compose.onNodeWithText("Water", substring = false).performClick()
        assertEquals("Water", model.state.value.draft.root)
        expand("Race and Bloodline")
        expand("Innate Talents")
        expand("Background and Starting Scenario")
        expand("Starting Cultivation and Abilities")
        assertEquals(seed, model.state.value.seed)
        val expected = CharacterGenerator.generate(model.state.value.draft, seed, "preview").character
        compose.onNodeWithTag("creator").performScrollToNode(hasText("Begin journey & create save"))
        compose.onNodeWithText("Begin journey & create save").performClick()
        compose.waitUntil(15_000) { !model.state.value.busy && model.state.value.active != null }
        val created = model.state.value.active!!
        try {
            assertEquals(expected.spiritualRoot, created.save.player.spiritualRoot)
            assertEquals(expected.constitution, created.save.player.constitution)
            assertEquals(expected.attributes, created.save.player.attributes)
            compose.runOnUiThread { model.navigate(Screen.CULTIVATION) }
            compose.onNodeWithTag("cultivation").performScrollToNode(hasText("Qi reserves"))
            compose.onNodeWithText("Qi reserves").assertExists()
            shot("cultivation")
            compose.runOnUiThread { model.navigate(Screen.TECHNIQUES) }; shot("techniques")
            compose.runOnUiThread { model.navigate(Screen.INVENTORY) }; shot("inventory")
            compose.runOnUiThread { model.navigate(Screen.SETTINGS) }
            shot("settings-hub")
            compose.onNodeWithTag("settings_saves").performScrollTo().performClick()
            compose.onNodeWithTag("settings").performScrollToNode(hasText("Automatic journal saving"))
            compose.onNodeWithText("Automatic journal saving").assertExists()
            compose.onNodeWithTag("settings_home").performScrollTo().performClick()
            compose.onNodeWithTag("settings_ai").performScrollTo().performClick()
            compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("choose_provider"))
            compose.onNodeWithTag("choose_provider").performScrollTo().performClick()
            compose.onNodeWithText("Google Gemini").performClick()
            shot("provider-setup")
            compose.onNodeWithTag("settings_home").performScrollTo().performClick()
            compose.onNodeWithTag("settings_about").performScrollTo().performClick()
            compose.onNodeWithTag("settings").performScrollToNode(hasText("Your choices. The world's rules."))
            compose.onNodeWithText("Your choices. The world's rules.").assertExists()
            assertEquals(created, model.state.value.active)
        } finally {
            val app = compose.activity.application as ImmortalWorldApplication
            runBlocking { app.saves.delete(created.save.id, created.revision) }
        }
    }
}
