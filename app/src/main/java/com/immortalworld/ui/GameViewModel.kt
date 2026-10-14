package com.immortalworld.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.immortalworld.ImmortalWorldApplication
import com.immortalworld.core.*
import com.immortalworld.data.*
import com.immortalworld.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

enum class Screen { HOME, CREATE, JOURNEY, JOURNAL, STATUS, CULTIVATION, INVENTORY, TECHNIQUES, QUESTS, MAP, NPCS, EVENTS, SAVES, SETTINGS }
data class PendingAction(val action: GameAction, val input: String, val slotId: String, val revision: Long)
data class GameUiState(
    val screen: Screen = Screen.HOME, val slots: List<SlotEntity> = emptyList(),
    val active: LoadedGame? = null, val dirty: Boolean = false, val busy: Boolean = false,
    val preferences: AppPreferences = AppPreferences(), val preferencesReady: Boolean = false,
    val error: String? = null, val notice: String? = null,
    val backupSlot: String? = null, val backups: List<BackupSummary> = emptyList(),
    val draft: CreationOptions = CreationOptions(), val seed: Long = System.nanoTime(),
    val aiConfig: AiConfig = AiConfig(), val aiWorking: Boolean = false, val actionDraft: String = "",
    val pendingAction: PendingAction? = null, val aiNotice: String? = null,
)
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ImmortalWorldApplication
    private val repository = app.saves
    private val mutable = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = mutable.asStateFlow()
    private val engine = AdventureEngine()
    private var activeJob: Job? = null
    init {
        viewModelScope.launch {
            try { val config = app.aiPreferences.load(); mutable.update { it.copy(aiConfig = config) } }
            catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
        }
        viewModelScope.launch {
            repository.slots.catch { e -> mutable.update { it.copy(error = "Unable to read save slots: ${e.message}") } }
                .collect { slots -> mutable.update { it.copy(slots = slots) } }
        }
        viewModelScope.launch {
            app.preferences.values.catch { e -> mutable.update { it.copy(error = "Unable to read preferences: ${e.message}") } }
                .collect { preferences -> mutable.update { it.copy(preferences = preferences, preferencesReady = true) } }
        }
    }
    private fun task(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null, notice = null) }
        activeJob = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { mutable.update { it.copy(aiNotice = "AI request cancelled. Any already-resolved engine results remain saved.") }; throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message?.take(500) ?: "The operation failed. Your other save slots were not changed.") } }
            finally { mutable.update { it.copy(busy = false, aiWorking = false) } }
        }
    }
    fun navigate(screen: Screen) { if (!mutable.value.busy || mutable.value.aiWorking) mutable.update { it.copy(screen = screen, notice = null, error = null) } }
    fun dismissMessage() { mutable.update { it.copy(error = null, notice = null) } }
    fun draft(options: CreationOptions) { mutable.update { it.copy(draft = options) } }
    fun randomize() {
        val seed = System.nanoTime()
        mutable.update { it.copy(seed = seed, draft = CharacterGenerator.randomOptions(seed)) }
    }
    fun preset(background: String) { mutable.update { it.copy(draft = it.draft.copy(background = background)) } }
    private fun ensureClean() { check(!mutable.value.dirty) { "You have unsaved changes. Save or discard them in Journey before replacing the active campaign." } }
    private suspend fun activate(loaded: LoadedGame) {
        val prepared = withContext(Dispatchers.Default) { AdventureFactory.ensure(loaded.save) }
        val ready = if (prepared != loaded.save) repository.save(prepared, loaded.revision, "Before adventure initialization / v1 migration") else loaded
        mutable.update { it.copy(active = ready, dirty = false, screen = Screen.JOURNEY, pendingAction = null, actionDraft = "", aiNotice = null) }
        app.preferences.setLastSlot(ready.save.id)
    }
    fun create(name: String) = task {
        ensureClean()
        val options = mutable.value.draft; val seed = mutable.value.seed
        val save = withContext(Dispatchers.Default) {
            val generated = CharacterGenerator.generate(options, seed, UUID.randomUUID().toString())
            val now = System.currentTimeMillis()
            SaveValidator.validate(GameSave(
                id = UUID.randomUUID().toString(), name = name.trim(), createdAt = now, updatedAt = now,
                randomState = generated.randomState, player = generated.character,
                world = WorldState(UUID.randomUUID().toString(), currentLocation = options.location,
                    startingCircumstances = options.circumstances,
                    journal = listOf(JournalEntry(UUID.randomUUID().toString(), 1, "${generated.character.name} begins a path at ${options.location}."))),
                rules = GameRules(difficulty = options.difficulty),
            ))
        }
        activate(repository.create(save))
    }
    fun load(id: String) = task { ensureClean(); activate(repository.load(id)) }
    fun continueGame() {
        if (mutable.value.active != null) navigate(Screen.JOURNEY)
        else (mutable.value.slots.find { it.id == mutable.value.preferences.lastSlot } ?: mutable.value.slots.firstOrNull())?.let { load(it.id) }
    }
    private suspend fun persist(reason: String) {
        val active = mutable.value.active ?: error("No campaign is open.")
        val saved = repository.save(active.save, active.revision, reason)
        mutable.update { it.copy(active = saved, dirty = false, notice = "Saved locally. Your previous revision is backed up.") }
    }
    fun save() = task { persist("Before manual save") }
    fun discard() = task {
        val id = mutable.value.active?.save?.id ?: error("No campaign is open.")
        activate(repository.load(id))
        mutable.update { it.copy(notice = "Unsaved changes discarded.") }
    }
    fun addJournal(text: String) = task {
        check(mutable.value.preferencesReady) { "Preferences are still loading. Try again." }
        val active = mutable.value.active ?: error("No campaign is open.")
        val entry = JournalEntry(UUID.randomUUID().toString(), active.save.world.day, text.trim())
        val world = active.save.world.copy(journal = (active.save.world.journal + entry).takeLast(SaveValidator.MAX_JOURNAL))
        val updated = SaveValidator.validate(active.save.copy(world = world))
        mutable.update { it.copy(active = active.copy(save = updated), dirty = true, notice = "Entry added. Save to keep it after closing the app.") }
        if (mutable.value.preferences.autosave) persist("Before journal edit")
    }
    fun renameCharacter(name: String) = task {
        check(mutable.value.preferencesReady) { "Preferences are still loading. Try again." }
        val active = mutable.value.active ?: error("No campaign is open.")
        val changed = SaveValidator.validate(active.save.copy(player = active.save.player.copy(name = name.trim())))
        mutable.update { it.copy(active = active.copy(save = changed), dirty = true) }
        if (mutable.value.preferences.autosave) persist("Before character rename")
    }
    fun setAutosave(enabled: Boolean) = task {
        app.preferences.setAutosave(enabled)
        mutable.update { it.copy(preferences = it.preferences.copy(autosave = enabled)) }
        if (enabled && mutable.value.dirty) persist("Before enabling autosave")
    }
    fun renameSlot(slot: SlotEntity, name: String) = task {
        ensureClean()
        val renamed = repository.rename(slot.id, name, slot.revision)
        mutable.update { it.copy(active = if (it.active?.save?.id == slot.id) renamed else it.active, notice = "Campaign renamed.") }
    }
    fun duplicate(slot: SlotEntity) = task { ensureClean(); repository.duplicate(slot.id); mutable.update { it.copy(notice = "Independent copy created.") } }
    fun delete(slot: SlotEntity) = task {
        ensureClean(); repository.delete(slot.id, slot.revision)
        mutable.update { it.copy(active = if (it.active?.save?.id == slot.id) null else it.active,
            backups = emptyList(), backupSlot = null, notice = "Campaign and its backups deleted.") }
        if (mutable.value.preferences.lastSlot == slot.id) app.preferences.setLastSlot(null)
    }
    fun showBackups(slot: SlotEntity) = task {
        val backups = repository.backups(slot.id)
        mutable.update { it.copy(backupSlot = slot.id, backups = backups) }
    }
    fun closeBackups() { mutable.update { it.copy(backupSlot = null, backups = emptyList()) } }
    fun restore(backup: BackupSummary) = task {
        ensureClean()
        val slot = mutable.value.slots.find { it.id == backup.slotId } ?: error("Save slot no longer exists.")
        val restored = repository.restore(slot.id, backup.id, slot.revision)
        mutable.update { it.copy(backupSlot = null, backups = emptyList()) }
        activate(restored)
        mutable.update { it.copy(notice = "Backup restored. The replaced revision was also backed up.") }
    }
    fun importSave(uri: Uri) = task {
        ensureClean()
        val text = DocumentFiles.read(app.contentResolver, uri)
        val loaded = repository.importJson(text)
        activate(loaded)
        mutable.update { it.copy(notice = "Imported into a new, independent slot.") }
    }
    fun exportSave(uri: Uri, slotId: String) = task {
        // Archive export intentionally exports the stored slot, not another campaign's working copy.
        val loaded = repository.load(slotId)
        val json = withContext(Dispatchers.Default) { SaveCodec.encode(loaded.save) }
        DocumentFiles.write(app.contentResolver, uri, json)
        mutable.update { it.copy(notice = "Exported ${loaded.save.name}. Unsaved edits, if any, are not included.") }
    }
    fun actionDraft(text: String) { if (text.length <= 2000) mutable.update { it.copy(actionDraft = text) } }
    fun cancelAi() { if (mutable.value.aiWorking) activeJob?.cancel() }
    fun dismissProposal() { mutable.update { it.copy(pendingAction = null) } }
    fun configureAi(config: AiConfig) = task {
        app.aiPreferences.save(config)
        mutable.update { it.copy(aiConfig = config, notice = "Provider settings saved on this device. API keys never enter campaign saves.") }
    }
    fun testAi(config: AiConfig) = task {
        mutable.update { it.copy(aiWorking = true) }
        val response = CompatibleStoryteller(config).testConnection()
        mutable.update { it.copy(notice = response) }
    }
    fun submitAction() = task {
        val active = mutable.value.active ?: error("No campaign is open.")
        val input = mutable.value.actionDraft.trim()
        require(input.isNotBlank()) { "Describe an action first." }
        mutable.update { it.copy(pendingAction = null, aiNotice = null) }
        val local = withContext(Dispatchers.Default) { IntentParser.parse(active.save, input) }
        if (local != null) resolve(local, input)
        else if (!mutable.value.aiConfig.enabled) resolve(GameAction(ActionKind.ATTEMPT, detail = input.take(1000)), input)
        else {
            mutable.update { it.copy(aiWorking = true) }
            val proposal = CompatibleStoryteller(mutable.value.aiConfig).proposeAction(input, ContextBuilder.build(active.save, input))
            // Unknown wording interpreted by an external provider never silently spends resources,
            // starts combat, joins factions, or chooses dialogue for the player.
            if (proposal.kind in setOf(ActionKind.OBSERVE, ActionKind.INVESTIGATE, ActionKind.ATTEMPT)) resolve(proposal, input)
            else mutable.update { it.copy(pendingAction = PendingAction(proposal, input, active.save.id, active.revision), aiNotice = "Review the AI's interpretation before any mechanics are applied.") }
        }
    }
    fun confirmProposal() = task {
        val pending = mutable.value.pendingAction ?: error("No proposal is waiting.")
        val active = mutable.value.active ?: error("No campaign is open.")
        require(active.save.id == pending.slotId && active.revision == pending.revision) { "The campaign changed. Submit your intention again before confirming." }
        mutable.update { it.copy(pendingAction = null) }
        resolve(pending.action, pending.input)
    }
    fun attemptOffline() = task {
        val input = mutable.value.actionDraft.trim(); require(input.isNotEmpty()) { "Describe your approach first." }
        resolve(GameAction(ActionKind.ATTEMPT, detail = input.take(1000)), input, narrate = false)
    }
    fun command(action: GameAction, label: String) = task { mutable.update { it.copy(pendingAction = null) }; resolve(action, label) }
    private suspend fun resolve(action: GameAction, input: String, narrate: Boolean = true) {
        mutable.update { it.copy(aiWorking = false) }
        val active = mutable.value.active ?: error("No campaign is open.")
        val id = UUID.randomUUID().toString()
        val result = withContext(Dispatchers.Default) { engine.execute(active.save, action, input, id) }
        // Mechanics and receipt commit before narration. Cancellation cannot split this pair.
        withContext(NonCancellable) {
            val saved = repository.save(result.state, active.revision, "Before player action")
            mutable.update { it.copy(active = saved, dirty = false, actionDraft = "", pendingAction = null, aiNotice = null) }
        }
        if (narrate && mutable.value.aiConfig.enabled) narrateRecord(id)
    }
    private suspend fun narrateRecord(id: String) {
        val active = mutable.value.active ?: return
        val record = active.save.adventure!!.history.lastOrNull { it.id == id } ?: return
        if (!record.accepted) return
        mutable.update { it.copy(aiWorking = true) }
        try {
            val prose = CompatibleStoryteller(mutable.value.aiConfig).narrate(ContextBuilder.build(active.save, record.input, record.facts))
            withContext(NonCancellable) {
                val changed = active.save.copy(adventure = active.save.adventure!!.copy(history = active.save.adventure!!.history.map { if (it.id == id) it.copy(narration = prose) else it }))
                val saved = repository.save(changed, active.revision, "Before AI narration")
                mutable.update { it.copy(active = saved, aiNotice = null) }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(aiNotice = "${e.message?.take(300)} Engine results are saved. Retry narration does not replay the action.") } }
    }
    fun retryNarration() = task {
        require(mutable.value.aiConfig.enabled) { "Configure and enable an AI provider first." }
        val id = mutable.value.active?.save?.adventure?.history?.lastOrNull()?.id ?: error("No action to narrate yet.")
        narrateRecord(id)
    }

}
