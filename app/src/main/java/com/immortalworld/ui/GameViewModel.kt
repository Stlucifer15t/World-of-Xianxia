package com.immortalworld.ui

import android.app.Application
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
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
    val aiReady: Boolean = false, val aiProfiles: List<AiProfile> = emptyList(), val activeProfileId: String = "",
    val aiConnection: String = "Loading saved provider…", val catalogLoading: Boolean = false, val catalog: ModelCatalog? = null,
    val knowledgeResult: KnowledgeResult = KnowledgeResult(), val knowledgeWorking: Boolean = false,
    val streamingRecordId: String? = null, val streamingText: String = "",
)
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ImmortalWorldApplication
    private val repository = app.saves
    private val mutable = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = mutable.asStateFlow()
    private val engine = AdventureEngine()
    private var activeJob: Job? = null
    private var catalogJob: Job? = null
    private var reconnectJob: Job? = null
    private var catalogEpoch = 0L
    private var catalogKey = ""
    private var lastConnectionCheck = 0L
    private val knowledge = KnowledgeService()
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private var validatedNetwork: Network? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            viewModelScope.launch {
                if (!validated) { if (validatedNetwork == network) validatedNetwork = null; return@launch }
                if (validatedNetwork == network) return@launch // Not a new connection; avoid polling on signal/bandwidth updates.
                validatedNetwork = network
                if (!mutable.value.aiReady || !mutable.value.aiConfig.enabled || !mutable.value.aiConfig.autoConnect) return@launch
                if (mutable.value.aiConnection.startsWith("Ready") || reconnectJob?.isActive == true) return@launch
                reconnectJob = viewModelScope.launch {
                    delay((30_000L - (SystemClock.elapsedRealtime() - lastConnectionCheck)).coerceAtLeast(0))
                    val state = mutable.value; val current = state.aiConfig
                    if (current.enabled && current.autoConnect && !state.catalogLoading && !state.aiConnection.startsWith("Ready")) detectModels(current)
                }
            }
        }
        override fun onLost(network: Network) {
            viewModelScope.launch { if (validatedNetwork == network) validatedNetwork = null }
        }
    }
    init {
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
        viewModelScope.launch {
            try { installProfiles(app.aiPreferences.loadProfiles()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(aiReady = true, aiConnection = "Saved provider could not be loaded. Offline play is available; existing settings were not overwritten.") } }
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
    override fun onCleared() {
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }
    private fun installProfiles(store: AiProfileStore) {
        catalogEpoch++; catalogJob?.cancel(); reconnectJob?.cancel(); knowledge.clear(); catalogKey = ""
        mutable.update { it.copy(aiReady = true, aiProfiles = store.profiles, activeProfileId = store.activeId, aiConfig = store.active.config,
            catalog = null, catalogLoading = false, knowledgeResult = KnowledgeResult(),
            aiConnection = store.warning ?: if (store.active.config.enabled) "Restored saved provider; ready to check." else "AI disabled · saved settings retained.") }
        if (store.active.config.enabled && store.active.config.autoConnect) detectModels(store.active.config)
    }
    fun detectModels(config: AiConfig) {
        val epoch = ++catalogEpoch; catalogJob?.cancel(); lastConnectionCheck = SystemClock.elapsedRealtime()
        mutable.update { it.copy(catalogLoading = true, notice = null, aiConnection = "Checking provider/model catalog…") }
        catalogJob = viewModelScope.launch {
            try {
                val catalog = ModelDiscovery().fetch(config)
                if (epoch == catalogEpoch) {
                    catalogKey = config.apiKey
                    mutable.update { it.copy(catalog = catalog, aiConnection = "Ready · ${catalog.models.size} models reported. Listing availability is not a generation test.") }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == catalogEpoch) mutable.update { it.copy(catalog = null, aiConnection = "Saved/manual settings remain usable. ${e.message?.take(220)}") }
            } finally { if (epoch == catalogEpoch) mutable.update { it.copy(catalogLoading = false) } }
        }
    }
    fun cancelModelLookup() { catalogEpoch++; catalogJob?.cancel(); mutable.update { it.copy(catalogLoading = false, aiConnection = "Model lookup cancelled. Manual settings retained.") } }
    fun catalogFor(config: AiConfig): ModelCatalog? = mutable.value.catalog?.takeIf { it.endpoint == config.endpoint && catalogKey == config.apiKey }
    private fun runtimeAiConfig(): AiConfig {
        val state = mutable.value; val c = state.aiConfig
        val model = catalogFor(c)?.models?.firstOrNull { it.id == c.model }
        return c.copy(contextTokens = if (c.manualContext) c.contextTokens else model?.contextTokens ?: c.contextTokens,
            maxOutputTokens = minOf(c.maxOutputTokens, model?.maxOutputTokens ?: c.maxOutputTokens))
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
        require(mutable.value.aiReady) { "Wait for saved provider settings to load." }
        installProfiles(app.aiPreferences.save(config, mutable.value.activeProfileId))
        mutable.update { it.copy(notice = "Provider saved.") }
    }
    fun addAiProfile(name: String, config: AiConfig) = task {
        require(mutable.value.aiReady) { "Wait for provider settings to load." }
        installProfiles(app.aiPreferences.add(name, config))
        mutable.update { it.copy(notice = "New provider saved and selected.") }
    }
    fun selectAiProfile(id: String) = task { installProfiles(app.aiPreferences.select(id)) }
    fun renameAiProfile(name: String) = task {
        val updated = app.aiPreferences.rename(mutable.value.activeProfileId, name)
        mutable.update { it.copy(aiProfiles = updated.profiles, notice = "Provider profile renamed.") }
    }
    fun deleteAiProfile(id: String) = task {
        installProfiles(app.aiPreferences.remove(id))
        mutable.update { it.copy(notice = "Provider profile removed. No campaigns were deleted.") }
    }
    fun testAndSaveAi(config: AiConfig) = task {
        require(mutable.value.aiReady) { "Wait for saved providers to load." }
        val targetId = mutable.value.activeProfileId
        mutable.update { it.copy(aiWorking = true) }
        CompatibleStoryteller(config).testConnection()
        installProfiles(app.aiPreferences.save(config, targetId))
        mutable.update { it.copy(notice = "Connection tested. Provider saved.") }
    }
    fun testAi(config: AiConfig) = task {
        mutable.update { it.copy(aiWorking = true) }
        val response = CompatibleStoryteller(config).testConnection()
        mutable.update { it.copy(notice = response, aiConnection = "Ready · selected model responded to the explicit test.") }
    }
    fun previewKnowledge(config: AiConfig, query: String) = task {
        require(config.knowledgeEnabled) { "Enable optional web knowledge before sending a query." }
        require(query.isNotBlank() && query.length <= 200) { "Enter a focused query of up to 200 characters." }
        mutable.update { it.copy(aiWorking = true, knowledgeWorking = true) }
        try { val result = knowledge.lookup(config, query); mutable.update { it.copy(knowledgeResult = result) } }
        finally { mutable.update { it.copy(knowledgeWorking = false) } }
    }
    fun submitAction() = task {
        require(mutable.value.aiReady) { "Saved provider settings are still loading. Please wait a moment; no action has run." }
        val active = mutable.value.active ?: error("No campaign is open.")
        val input = mutable.value.actionDraft.trim()
        require(input.isNotBlank()) { "Describe an action first." }
        mutable.update { it.copy(pendingAction = null, aiNotice = null) }
        val local = withContext(Dispatchers.Default) { IntentParser.parse(active.save, input) }
        if (local != null) resolve(local, input)
        else if (!mutable.value.aiConfig.enabled) {
            require(!IntentParser.needsReview(input)) { "No action taken: this request contains a condition or prohibition. Describe one immediate action, or ask the NPC your question in your own words." }
            resolve(GameAction(ActionKind.ATTEMPT, detail = input.take(1000)), input)
        }
        else {
            mutable.update { it.copy(aiWorking = true) }
            val proposal = CompatibleStoryteller(runtimeAiConfig()).proposeAction(input, ContextBuilder.build(active.save, input))
            // Unknown wording interpreted by an external provider never silently spends resources,
            // starts combat, joins factions, or chooses dialogue for the player.
            if (!IntentParser.needsReview(input) && proposal.kind in setOf(ActionKind.OBSERVE, ActionKind.INVESTIGATE, ActionKind.ATTEMPT)) resolve(proposal, input)
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
        require(!IntentParser.needsReview(input)) { "No action taken: clarify the condition or prohibition before attempting an offline action." }
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
            mutable.update { it.copy(active = saved, dirty = false, actionDraft = if (it.actionDraft.trim() == input) "" else it.actionDraft, pendingAction = null, aiNotice = null) }
        }
        if (narrate && mutable.value.aiConfig.enabled) narrateRecord(id)
    }
    private suspend fun narrateRecord(id: String) {
        val active = mutable.value.active ?: return
        val record = active.save.adventure!!.history.lastOrNull { it.id == id } ?: return
        if (!record.accepted) return
        mutable.update { it.copy(aiWorking = true) }
        try {
            val config = runtimeAiConfig()
            val query = KnowledgeQuery.from(record.input)
            val refs = if (config.knowledgeEnabled && query.isNotBlank()) {
                mutable.update { it.copy(knowledgeWorking = true) }
                try { knowledge.lookup(config, query) } finally { mutable.update { it.copy(knowledgeWorking = false) } }
            } else KnowledgeResult()
            mutable.update { it.copy(knowledgeResult = refs, streamingRecordId = id, streamingText = "") }
            val context = ContextBuilder.build(active.save, record.input, record.facts, record).copy(referenceNotes = refs.references.map { it.context() })
            val prose = CompatibleStoryteller(config).narrateStreaming(context) { draft ->
                mutable.update { if (it.streamingRecordId == id) it.copy(streamingText = draft) else it }
            }
            withContext(NonCancellable) {
                val changed = active.save.copy(adventure = active.save.adventure!!.copy(history = active.save.adventure!!.history.map { if (it.id == id) it.copy(narration = prose) else it }))
                val saved = repository.save(changed, active.revision, "Before AI narration")
                mutable.update { it.copy(active = saved, aiNotice = null) }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(aiNotice = "${e.message?.take(300)} Engine results are saved. Retry narration does not replay the action.") } }
        finally { mutable.update { it.copy(streamingRecordId = null, streamingText = "", knowledgeWorking = false) } }
    }
    fun retryNarration() = task {
        require(mutable.value.aiConfig.enabled) { "Configure and enable an AI provider first." }
        val id = mutable.value.active?.save?.adventure?.history?.lastOrNull()?.id ?: error("No action to narrate yet.")
        narrateRecord(id)
    }

}
