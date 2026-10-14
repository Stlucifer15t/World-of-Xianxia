package com.immortalworld.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.immortalworld.ImmortalWorldApplication
import com.immortalworld.core.*
import com.immortalworld.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

enum class Screen { HOME, CREATE, JOURNEY, STATUS, SAVES, SETTINGS }
data class GameUiState(
    val screen: Screen = Screen.HOME, val slots: List<SlotEntity> = emptyList(),
    val active: LoadedGame? = null, val dirty: Boolean = false, val busy: Boolean = false,
    val preferences: AppPreferences = AppPreferences(), val preferencesReady: Boolean = false,
    val error: String? = null, val notice: String? = null,
    val backupSlot: String? = null, val backups: List<BackupSummary> = emptyList(),
    val draft: CreationOptions = CreationOptions(), val seed: Long = System.nanoTime(),
)
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ImmortalWorldApplication
    private val repository = app.saves
    private val mutable = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = mutable.asStateFlow()
    init {
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
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message?.take(500) ?: "The operation failed. Your other save slots were not changed.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun navigate(screen: Screen) { if (!mutable.value.busy) mutable.update { it.copy(screen = screen, notice = null, error = null) } }
    fun dismissMessage() { mutable.update { it.copy(error = null, notice = null) } }
    fun draft(options: CreationOptions) { mutable.update { it.copy(draft = options) } }
    fun randomize() {
        val seed = System.nanoTime()
        mutable.update { it.copy(seed = seed, draft = CharacterGenerator.randomOptions(seed)) }
    }
    fun preset(background: String) { mutable.update { it.copy(draft = it.draft.copy(background = background)) } }
    private fun ensureClean() { check(!mutable.value.dirty) { "You have unsaved changes. Save or discard them in Journey before replacing the active campaign." } }
    private suspend fun activate(loaded: LoadedGame) {
        mutable.update { it.copy(active = loaded, dirty = false, screen = Screen.JOURNEY) }
        app.preferences.setLastSlot(loaded.save.id)
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
}
