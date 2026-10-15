package com.immortalworld.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immortalworld.ImmortalWorldApplication
import com.immortalworld.MainActivity
import com.immortalworld.ai.AiConfig
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AiSettingsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: GameViewModel
    private lateinit var app: ImmortalWorldApplication
    private var originalId: String? = null
    private val prefix = "UI provider ${UUID.randomUUID().toString().take(8)}"
    private fun waitFor(condition: () -> Boolean) { compose.waitUntil(15_000, condition) }
    private fun idle() { waitFor { !model.state.value.busy } }
    private fun edit(tag: String, text: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text) }
    private fun click(text: String) { compose.onNodeWithText(text, substring = false).performScrollTo().performClick() }
    @Before fun setup() {
        compose.runOnUiThread {
            model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
            app = compose.activity.application as ImmortalWorldApplication
        }
        waitFor { model.state.value.aiReady }
        originalId = model.state.value.activeProfileId
        compose.runOnUiThread { model.addAiProfile(prefix, AiConfig(autoConnect = false)) }
        idle()
        compose.runOnUiThread { model.navigate(Screen.SETTINGS) }
        compose.onNodeWithTag("settings").assertExists()
        compose.onNodeWithTag("settings_ai").performScrollTo().performClick()
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("choose_provider"))
    }
    @After fun restore() {
        if (!::app.isInitialized || originalId == null) return
        idle()
        runBlocking {
            val current = app.aiPreferences.loadProfiles()
            if (current.profiles.any { it.id == originalId }) app.aiPreferences.select(originalId!!)
            current.profiles.filter { it.name.startsWith(prefix) }.forEach { app.aiPreferences.remove(it.id) }
        }
    }
    @Test fun manualMillionTokenSettingsAndIndependentProfilesWorkThroughTheUi() {
        val firstId = model.state.value.activeProfileId
        compose.onNodeWithTag("choose_provider").performScrollTo().performClick()
        compose.onNodeWithText("Other compatible API").performClick()
        edit("ai_endpoint", "https://example.invalid/v1/chat/completions")
        edit("ai_model", "manual-cultivator")
        edit("ai_key", "instrumentation-only-key")
        compose.onNodeWithTag("ai_tab_Limits").performScrollTo().performClick()
        edit("ai_context", "1,000,000")
        edit("ai_output", "900")
        click("Save without testing"); idle()
        assertEquals(1_000_000, model.state.value.aiConfig.contextTokens)
        assertEquals(900, model.state.value.aiConfig.maxOutputTokens)
        assertTrue(model.state.value.aiConfig.manualContext)
        assertFalse(model.state.value.aiConfig.enabled)
        assertEquals(model.state.value.aiConfig, runBlocking { app.aiPreferences.load() })

        // A fresh ViewModel takes the startup path, not the already edited form's in-memory state.
        val restoredStore = ViewModelStore()
        lateinit var restored: GameViewModel
        try {
            compose.runOnUiThread {
                val owner = object : ViewModelStoreOwner { override val viewModelStore = restoredStore }
                restored = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[GameViewModel::class.java]
            }
            waitFor { restored.state.value.aiReady }
            assertEquals(firstId, restored.state.value.activeProfileId)
            assertEquals(model.state.value.aiConfig, restored.state.value.aiConfig)
        } finally { compose.runOnUiThread { restoredStore.clear() } }

        compose.onNodeWithTag("ai_tab_Saved").performScrollTo().performClick()
        edit("profile_name", "$prefix copy")
        click("Save as new profile"); idle()
        val secondId = model.state.value.activeProfileId
        assertNotEquals(firstId, secondId)
        compose.onNodeWithTag("profile_$firstId").performScrollTo().performClick(); idle()
        compose.onNodeWithTag("ai_tab_Setup").performScrollTo().performClick()
        edit("ai_key", "unsaved-key")
        compose.onNodeWithTag("ai_tab_Saved").performScrollTo().performClick()
        compose.onNodeWithTag("profile_$secondId").performScrollTo().performClick()
        compose.onNodeWithText("Switch provider profile?").assertIsDisplayed()
        compose.onNodeWithTag("confirm_profile_switch").performClick(); idle()
        assertEquals("instrumentation-only-key", model.state.value.aiConfig.apiKey)
        assertEquals("instrumentation-only-key", model.state.value.aiProfiles.first { it.id == firstId }.config.apiKey)
        edit("profile_name", "$prefix renamed")
        click("Rename profile"); idle()
        assertEquals("$prefix renamed", model.state.value.aiProfiles.first { it.id == secondId }.name)
        click("Delete selected profile")
        compose.onNodeWithTag("confirm_delete_profile").performClick(); idle()
        assertFalse(model.state.value.aiProfiles.any { it.id == secondId })
        assertTrue(model.state.value.aiProfiles.any { it.id == firstId })
    }
    @Test fun providerSelectionClearsCredentialsAndFailedTestDoesNotSave() {
        val original = model.state.value.aiConfig
        compose.onNodeWithTag("choose_provider").performScrollTo().performClick()
        compose.onNodeWithText("Google Gemini").performClick()
        edit("ai_key", "test-only-not-a-live-key")
        edit("ai_model", "manual-model")
        compose.onNodeWithTag("toggle_api_key").performClick()
        compose.onNodeWithTag("ai_key").assertTextContains("test-only-not-a-live-key")
        compose.onNodeWithTag("choose_provider").performScrollTo().performClick()
        compose.onNodeWithText("OpenAI").performClick()
        compose.onNodeWithTag("ai_key").assertTextContains("")
        compose.onNodeWithTag("choose_provider").performScrollTo().performClick()
        compose.onNodeWithText("Other compatible API").performClick()
        edit("ai_endpoint", "http://example.invalid/v1/chat/completions")
        edit("ai_model", "invalid-endpoint-test")
        click("Test connection & save"); idle()
        assertNotNull(model.state.value.error)
        assertEquals(original, runBlocking { app.aiPreferences.load() })
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("ai_tab_Knowledge").performScrollTo().performClick()
        click("Add Fandom example")
        compose.onNodeWithTag("knowledge_sources").assertTextContains("https://martial-world.fandom.com/wiki/Cultivation")
    }

}
