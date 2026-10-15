package com.immortalworld.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immortalworld.ImmortalWorldApplication
import com.immortalworld.MainActivity
import com.immortalworld.ai.AiConfig
import com.immortalworld.core.*
import com.immortalworld.data.SaveCodec
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Controlled transport for lifecycle/UI races; AiProviderTest separately checks the actual HTTP/SSE path. */
@RunWith(AndroidJUnit4::class)
class OpeningFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: ImmortalWorldApplication
    private lateinit var model: GameViewModel
    private var store = ViewModelStore()
    private var originalProfile = ""
    private var profile = ""
    private val slots = mutableSetOf<String>()
    private val requests = CopyOnWriteArrayList<NarrativeContext>()
    private val config = AiConfig(true, "https://example.invalid/v1", "opening-test", "instrumentation-only", autoConnect = false)
    private val first = "River mist brushes the worn jade clasp. The ferry gate stands beyond the quay."
    private val second = "The ferry gate creaks in the river breeze. Pale light rests on the jade clasp."
    private var handler: suspend (NarrativeContext, (String) -> Unit) -> String = { _, _ -> first }
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(15_000, condition)
    private fun idle() { waitFor { !model.state.value.busy }; assertNull(model.state.value.error) }
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun active() = model.state.value.active!!.save.also { slots.add(it.id) }
    private fun mechanics(s: GameSave) = s.copy(updatedAt = 0, adventure = s.adventure!!.copy(history = s.adventure!!.history.map { it.copy(narration = "") }))
    private fun regenerate(tag: String = "regenerate_opening") {
        compose.onNodeWithTag("adventure_history").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo().assertIsEnabled().performClick()
    }
    private fun replaceModel(createImmediately: Boolean = false) {
        ui {
            store.clear(); store = ViewModelStore()
            val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(app) { actual, context, preview ->
                    assertEquals(config.endpoint, actual.endpoint)
                    assertEquals(config.model, actual.model)
                    requests.add(context)
                    handler(context, preview)
                } as T
            }
            model = ViewModelProvider(owner, factory)[GameViewModel::class.java]
            compose.activity.setContent { ImmortalWorldApp(model) }
            if (createImmediately) model.create("Cold startup opening")
        }
    }
    @Before fun setup() {
        app = compose.activity.application as ImmortalWorldApplication
        runBlocking {
            originalProfile = app.aiPreferences.loadProfiles().activeId
            profile = app.aiPreferences.add("Opening test ${UUID.randomUUID().toString().take(8)}", config).activeId
        }
        replaceModel(); waitFor { model.state.value.aiReady }
    }
    @After fun cleanup() {
        if (::model.isInitialized) {
            model.state.value.active?.save?.id?.let { slots.add(it) }
            ui { store.clear() }
        }
        runBlocking {
            if (originalProfile.isNotEmpty()) app.aiPreferences.select(originalProfile)
            if (profile.isNotEmpty()) app.aiPreferences.remove(profile)
            slots.forEach { id -> runCatching { val saved = app.saves.load(id); app.saves.delete(id, saved.revision) } }
        }
    }
    @Test fun createButtonCallsAiAfterSavingSelectionsAndRegenerateChangesProseOnly() {
        var initial: GameSave? = null
        handler = { _, preview ->
            val saved = model.state.value.active!!
            assertEquals(saved, app.saves.load(saved.save.id)) // durable before the first provider token
            initial = saved.save
            preview("River mist brushes the worn jade clasp.")
            first
        }
        ui {
            model.draft(CreationOptions(name = "Yue", appearance = "A worn jade clasp", background = "Exiled cartographer",
                personality = "Patient and curious", location = "Jade River Crossing", circumstances = "Waiting at a sealed ferry gate", root = "Water"))
            model.navigate(Screen.CREATE)
        }
        compose.onNodeWithTag("creator").performScrollToNode(hasText("Begin journey & create save"))
        compose.onNodeWithText("Begin journey & create save").performClick(); idle()
        val saved = active()
        assertEquals(1, requests.size)
        assertEquals("opening", requests.single().pace)
        listOf("Yue", "Exiled cartographer", "Patient and curious", "A worn jade clasp", "sealed ferry gate", "Water").forEach {
            assertTrue(requests.single().authoritativeFacts.joinToString().contains(it))
        }
        assertEquals(mechanics(initial!!), mechanics(saved))
        assertEquals(first, saved.adventure!!.history.single().narration)
        handler = { _, _ -> second }
        regenerate(); idle()
        val regenerated = active()
        assertEquals(2, requests.size)
        assertEquals(mechanics(saved), mechanics(regenerated))
        assertEquals(second, regenerated.adventure!!.history.single().narration)
        assertEquals(requests.first().authoritativeFacts, requests.last().authoritativeFacts)
        assertTrue(requests.last().recentOpenings.single().startsWith(first))
        assertEquals(regenerated, runBlocking { app.saves.load(saved.id).save })
    }
    @Test fun failedFirstRequestLeavesSavedFallbackAndButtonRetriesWithoutRerolling() {
        handler = { _, _ -> error("Controlled provider failure") }
        ui { model.create("Failure recovery") }; idle()
        val before = active()
        assertTrue(before.adventure!!.history.single().narration.isBlank())
        assertTrue(before.adventure!!.history.single().story.isNotBlank())
        assertTrue(model.state.value.aiNotice.orEmpty().contains("Opening could not be generated"))
        assertEquals(before, runBlocking { app.saves.load(before.id).save })
        handler = { _, _ -> second }
        regenerate(); idle()
        assertEquals(2, requests.size)
        assertEquals(mechanics(before), mechanics(active()))
        assertEquals(second, active().adventure!!.history.single().narration)
    }
    @Test fun badAndCancelledRegenerationsKeepGoodProseAndIgnoreLateTokens() {
        ui { model.create("Preserve good opening") }; idle()
        val before = active()
        handler = { _, preview -> preview("An unsafe unverified draft."); "You decide to join the sect." }
        regenerate(); idle()
        assertEquals(before, active())
        assertTrue(model.state.value.aiNotice.orEmpty().contains("Opening could not be generated"))
        var latePreview: ((String) -> Unit)? = null
        handler = { _, preview -> latePreview = preview; preview("A partial replacement."); awaitCancellation() }
        regenerate(); waitFor { model.state.value.streamingText == "A partial replacement." }
        assertEquals(before, runBlocking { app.saves.load(before.id).save })
        // Busy guards prevent a second charge even if called twice outside Compose.
        ui { model.retryNarration() }
        assertEquals(3, requests.size)
        compose.onNodeWithText("Cancel AI request").performClick(); idle()
        ui { latePreview!!("Late cancelled tokens") }
        assertEquals("", model.state.value.streamingText)
        assertEquals(before, active())
        handler = { _, _ -> second }
        regenerate(); idle()
        assertEquals(4, requests.size)
        assertEquals(mechanics(before), mechanics(active()))
        assertEquals(second, active().adventure!!.history.single().narration)
    }
    @Test fun explicitOfflineStartMakesNoRequestAndCanGenerateAfterEnablingAi() {
        ui { model.configureAi(config.copy(enabled = false)) }; idle()
        ui { model.create("Offline opening") }; idle()
        val before = active()
        assertTrue(requests.isEmpty())
        assertTrue(before.adventure!!.history.single().narration.isBlank())
        compose.onNodeWithTag("regenerate_opening").assertDoesNotExist()
        ui { model.configureAi(config) }; idle(); ui { model.dismissMessage() }
        regenerate(); idle()
        assertEquals(1, requests.size)
        assertEquals(mechanics(before), mechanics(active()))
    }
    @Test fun coldStartupWaitsForProviderAndReloadImportAndDuplicateNeverAutoGenerate() {
        replaceModel(createImmediately = true); idle()
        val before = active()
        assertEquals(1, requests.size)
        replaceModel(); waitFor { model.state.value.aiReady }
        ui { model.load(before.id) }; idle()
        assertEquals(before, active()); assertEquals(1, requests.size)
        val imported = runBlocking { app.saves.importJson(SaveCodec.encode(before)) }.save.also { slots.add(it.id) }
        val duplicate = runBlocking { app.saves.duplicate(before.id) }.save.also { slots.add(it.id) }
        listOf(imported, duplicate).forEach { copy ->
            ui { model.load(copy.id) }; idle()
            assertTrue(OpeningScene.isOpening(active().adventure!!.history.single()))
            assertEquals(first, active().adventure!!.history.single().narration)
            assertEquals(1, requests.size)
        }
        handler = { _, _ -> second }
        regenerate(); idle()
        assertEquals(2, requests.size)
        assertEquals(before, runBlocking { app.saves.load(before.id).save })
        assertEquals(mechanics(duplicate), mechanics(active()))
    }
    @Test fun legacyUntouchedSaveOffersExplicitOpeningWithoutAutomaticProviderUse() {
        val g = CharacterGenerator.generate(CreationOptions(background = "Exiled cartographer"), 42, "legacy-player")
        val old = GameSave(id = UUID.randomUUID().toString(), name = "Legacy untouched start", createdAt = 1, updatedAt = 1,
            randomState = g.randomState, player = g.character,
            world = WorldState("legacy-world", currentLocation = "Azure Cloud Village", startingCircumstances = "A missing map"))
        slots.add(old.id)
        runBlocking { app.saves.create(old) }
        ui { model.load(old.id) }; idle()
        val before = active()
        assertTrue(requests.isEmpty()); assertTrue(before.adventure!!.history.isEmpty())
        regenerate(); idle()
        assertEquals(1, requests.size)
        assertTrue(requests.single().authoritativeFacts.joinToString().contains("Exiled cartographer"))
        val after = active()
        assertEquals(before.copy(updatedAt = 0), after.copy(updatedAt = 0, adventure = after.adventure!!.copy(history = emptyList())))
        assertEquals(first, after.adventure!!.history.single().narration)
    }
    @Test fun cancellingFirstOpeningKeepsDurableCharacterAndReloadDoesNotSpendAgain() {
        handler = { _, preview -> preview("The unfinished first opening."); awaitCancellation() }
        ui { model.create("Cancelled first opening") }
        waitFor { model.state.value.streamingText == "The unfinished first opening." }
        val before = active()
        assertTrue(before.adventure!!.history.single().narration.isBlank())
        compose.onNodeWithText("Cancel AI request").performClick(); idle()
        assertEquals(before, runBlocking { app.saves.load(before.id).save })
        replaceModel(); waitFor { model.state.value.aiReady }
        ui { model.load(before.id) }; idle()
        assertEquals(1, requests.size); assertEquals(before, active())
        handler = { _, _ -> second }
        regenerate(); idle()
        assertEquals(2, requests.size); assertEquals(mechanics(before), mechanics(active()))
    }
    @Test fun latestActionRegenerationNeverReplaysItsTimeOrMechanics() {
        ui { model.create("Action regeneration") }; idle()
        ui { model.command(GameAction(ActionKind.REST), "Rest") }; idle()
        val before = active()
        assertEquals(2, requests.size)
        assertEquals(1L, before.adventure!!.sequence)
        handler = { _, _ -> second }
        regenerate("regenerate_story"); idle()
        assertEquals(3, requests.size)
        assertEquals(mechanics(before), mechanics(active()))
        assertEquals(first, active().adventure!!.history.first().narration)
        assertEquals(second, active().adventure!!.history.last().narration)
    }
}
