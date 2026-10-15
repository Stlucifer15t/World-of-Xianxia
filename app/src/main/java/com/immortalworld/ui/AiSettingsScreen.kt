@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immortalworld.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.immortalworld.ai.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val providers = linkedMapOf(
    "Google Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
    "OpenAI" to "https://api.openai.com/v1/chat/completions",
    "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions",
    "Groq" to "https://api.groq.com/openai/v1/chat/completions",
    "Other compatible API" to "",
)

@Composable
internal fun SettingSwitch(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
internal fun AiSettingsPanel(state: GameUiState, model: GameViewModel) {
    if (!state.aiReady) { Text("Loading saved providers…"); return }
    // Never save credentials into a Bundle. These values survive tab changes, not process death.
    var draft by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig) }
    var context by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig.contextTokens.toString()) }
    var output by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig.maxOutputTokens.toString()) }
    var temperature by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig.temperature.toString()) }
    var sources by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig.sources.joinToString("\n")) }
    var tab by rememberSaveable { mutableStateOf("Setup") }
    var showKey by remember(state.activeProfileId) { mutableStateOf(false) }
    var providerPicker by remember { mutableStateOf(false) }
    var browse by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var custom by remember(state.activeProfileId, state.aiConfig.endpoint) { mutableStateOf(state.aiConfig.endpoint !in providers.values) }
    var help by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var switchTo by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val active = state.aiProfiles.firstOrNull { it.id == state.activeProfileId }
    var profileName by remember(state.activeProfileId, active?.name) { mutableStateOf(active?.name.orEmpty()) }
    val catalog = model.catalogFor(draft.copy(apiKey = draft.apiKey.trim()))
    val selected = catalog?.models?.firstOrNull { it.id == draft.model }
    val provider = if (draft.endpoint.isBlank() && !custom) "Choose a provider" else if (custom) "Other compatible API" else providers.entries.firstOrNull { it.value == draft.endpoint }?.key ?: "Choose a provider"
    val uri = LocalUriHandler.current
    LaunchedEffect(selected, draft.manualContext, draft.model, draft.endpoint) {
        if (!draft.manualContext) context = (selected?.contextTokens ?: if (draft.model == state.aiConfig.model && draft.endpoint == state.aiConfig.endpoint) state.aiConfig.contextTokens else 32768).toString()
    }
    val config = draft.copy(apiKey = draft.apiKey.trim(), contextTokens = context.toIntOrNull() ?: 0,
        maxOutputTokens = output.toIntOrNull() ?: 0, temperature = temperature.toDoubleOrNull() ?: Double.NaN,
        sources = sources.lines().map(String::trim).filter(String::isNotEmpty).distinct())
    fun endpoint(value: String) {
        val old = draft.endpoint.toHttpUrlOrNull(); val next = value.toHttpUrlOrNull()
        val changed = old?.host != next?.host || old?.port != next?.port || old?.scheme != next?.scheme
        draft = draft.copy(endpoint = value, apiKey = if (changed) "" else draft.apiKey,
            model = if (changed) "" else draft.model, manualContext = if (value != draft.endpoint) false else draft.manualContext)
        showKey = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Setup", "Saved", "Limits", "Knowledge").forEach { name ->
                FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) }, modifier = Modifier.testTag("ai_tab_$name"))
            }
        }
        when (tab) {
            "Setup" -> {
                OutlinedCard(onClick = { providerPicker = true }, modifier = Modifier.fillMaxWidth().testTag("choose_provider")) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Provider", style = MaterialTheme.typography.labelSmall, color = Gold)
                            Text(provider, style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Change", color = Jade, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Panel {
                    if (custom) OutlinedTextField(draft.endpoint, { if (it.length <= 500) endpoint(it.trim()) },
                        label = { Text("Chat API URL") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_endpoint"))
                    OutlinedTextField(draft.apiKey, { if (it.length <= 1000) draft = draft.copy(apiKey = it) }, label = { Text("API key") },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true,
                        trailingIcon = { TextButton(onClick = { showKey = !showKey }, modifier = Modifier.testTag("toggle_api_key")) { Text(if (showKey) "Hide" else "Show") } },
                        modifier = Modifier.fillMaxWidth().testTag("ai_key"))
                    OutlinedTextField(draft.model, { if (it.length <= 200) draft = draft.copy(model = it.trim(), manualContext = if (it.trim() != draft.model) false else draft.manualContext) },
                        label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_model"))
                    Text("Enter an ID, or load models.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { model.detectModels(config) }, enabled = !state.catalogLoading && !state.busy) { Text(if (state.catalogLoading) "Loading…" else "Load models") }
                        OutlinedButton(onClick = { filter = ""; browse = true }, enabled = !catalog?.models.isNullOrEmpty()) { Text("Browse ${catalog?.models?.size ?: 0}") }
                        if (state.catalogLoading) TextButton(onClick = model::cancelModelLookup) { Text("Cancel") }
                    }
                    SettingSwitch("Use AI storytelling", draft.enabled, !state.busy) { draft = draft.copy(enabled = it) }
                }
                Text("Last check · ${state.aiConnection}", modifier = Modifier.testTag("ai_connection_status"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "Saved" -> {
                Text("Saved providers", style = MaterialTheme.typography.titleLarge)
                state.aiProfiles.forEach { profile ->
                    SectionCard(profile.name, "${profile.config.model.ifBlank { "No model selected" }} · ${if (profile.id == state.activeProfileId) "Selected" else "Tap to switch"}", if (profile.id == state.activeProfileId) "✓" else "◇", "profile_${profile.id}") {
                        if (!state.busy && profile.id != state.activeProfileId) {
                            if (config != state.aiConfig || profileName != active?.name) switchTo = profile.id else model.selectAiProfile(profile.id)
                        }
                    }
                }
                Panel {
                    OutlinedTextField(profileName, { if (it.length <= 60) profileName = it }, label = { Text("Profile name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("profile_name"))
                    Text("A new profile copies this form and its key. Up to eight can be saved.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { model.addAiProfile(profileName, config) }, enabled = !state.busy && state.aiProfiles.size < 8 && profileName.isNotBlank() && state.aiProfiles.none { it.name.equals(profileName.trim(), true) }) { Text("Save as new profile") }
                        TextButton(onClick = { model.renameAiProfile(profileName) }, enabled = !state.busy && profileName.isNotBlank()) { Text("Rename profile") }
                        TextButton(onClick = { deleting = true }, enabled = !state.busy && state.aiProfiles.size > 1) { Text("Delete selected profile") }
                    }
                }
            }
            "Limits" -> Panel {
                Text("Response settings", style = MaterialTheme.typography.titleLarge)
                SettingSwitch("Reconnect automatically", draft.autoConnect) { draft = draft.copy(autoConnect = it) }
                SettingSwitch("Show responses as they arrive", draft.streaming) { draft = draft.copy(streaming = it) }
                OutlinedTextField(context, { context = it.filter(Char::isDigit).take(8); draft = draft.copy(manualContext = true) }, label = { Text("Context window · tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_context"))
                Text(when { draft.manualContext -> "Your limit. Check that the model supports it."; selected?.contextTokens != null -> "Detected for ${selected.id}."; else -> "Not detected. Enter your model's limit, such as 1,000,000." }, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { draft = draft.copy(manualContext = false) }) { Text("Use detected context") }
                OutlinedTextField(output, { output = it.filter(Char::isDigit).take(5) }, label = { Text("Maximum output · tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_output"))
                Text("Context is what the model can read. Output is how much it may write. Story replies also have their own shorter limits.", style = MaterialTheme.typography.bodySmall)
                Field("Creativity · 0 to 2", temperature, 8) { temperature = it }
            }
            "Knowledge" -> Panel {
                Text("Cultivation references", style = MaterialTheme.typography.titleLarge)
                SettingSwitch("Look up web references", draft.knowledgeEnabled) { draft = draft.copy(knowledgeEnabled = it) }
                Text("Optional excerpts for lore and terminology. Never changes your stats or downloads whole sites.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(sources, { if (it.length <= 8000) sources = it }, label = { Text("Website links · one per line") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth().testTag("knowledge_sources"))
                TextButton(onClick = { sources = (sources.lines().filter(String::isNotBlank) + "https://martial-world.fandom.com/wiki/Cultivation").distinct().joinToString("\n") }, enabled = config.sources.size < 8) { Text("Add Fandom example") }
                Field("Search service URL · optional SearXNG", draft.searchEndpoint, 1000) { draft = draft.copy(searchEndpoint = it.trim()) }
                Field("What would you like to look up?", query, 200) { query = it }
                OutlinedButton(onClick = { model.previewKnowledge(config, query) }, enabled = !state.busy && draft.knowledgeEnabled && query.isNotBlank()) { Text("Test lookup") }
                if (state.knowledgeWorking) Text("Finding relevant excerpts…")
                state.knowledgeResult.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                state.knowledgeResult.references.forEach { reference ->
                    Text(reference.title, style = MaterialTheme.typography.titleMedium)
                    Text(reference.excerpt)
                    TextButton(onClick = { runCatching { uri.openUri(reference.url) } }) { Text("Open source") }
                }
            }
        }
        if (tab != "Saved") {
            if (tab == "Setup") {
                Button(onClick = { model.testAndSaveAi(config) }, enabled = !state.busy && config.endpoint.isNotBlank() && config.model.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Test connection & save") }
                Text("Sends one short test. Your provider may charge. Saves only after success.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { model.configureAi(config) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Save without testing") }
        }
        TextButton(onClick = { help = true }) { Text("Privacy & connection help") }
    }
    if (providerPicker) AlertDialog(onDismissRequest = { providerPicker = false }, title = { Text("Choose a provider") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            providers.forEach { (name, url) ->
                TextButton(onClick = { custom = name == "Other compatible API"; if (!custom) endpoint(url); providerPicker = false }, modifier = Modifier.fillMaxWidth()) { Text(name) }
            }
        }
    }, confirmButton = { TextButton(onClick = { providerPicker = false }) { Text("Cancel") } })
    if (browse) AlertDialog(onDismissRequest = { browse = false }, title = { Text("Choose a model") }, text = {
        Column {
            OutlinedTextField(filter, { filter = it.take(200) }, label = { Text("Search models") }, singleLine = true)
            val matches = catalog?.models.orEmpty().filter { it.id.contains(filter, true) }
            if (matches.isEmpty()) Text("No matching models. You can still type an ID.")
            LazyColumn(Modifier.heightIn(max = 320.dp)) { items(matches, key = { it.id }) { m ->
                TextButton(onClick = { draft = draft.copy(model = m.id, manualContext = false); browse = false }, modifier = Modifier.fillMaxWidth()) { Text(m.id) }
            } }
        }
    }, confirmButton = { TextButton(onClick = { browse = false }) { Text("Close") } })
    switchTo?.let { id -> AlertDialog(onDismissRequest = { switchTo = null }, title = { Text("Switch provider profile?") }, text = { Text("Discard unsaved edits and load this provider?") },
        confirmButton = { TextButton(onClick = { switchTo = null; model.selectAiProfile(id) }, modifier = Modifier.testTag("confirm_profile_switch")) { Text("Discard edits and switch") } }, dismissButton = { TextButton(onClick = { switchTo = null }) { Text("Keep editing") } }) }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete provider profile?") }, text = { Text("Remove ${active?.name} and its key? Your campaigns stay safe.") },
        confirmButton = { TextButton(onClick = { deleting = false; model.deleteAiProfile(state.activeProfileId) }, modifier = Modifier.testTag("confirm_delete_profile")) { Text("Delete profile") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Your story, your choice") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text("AI is optional. Keys are encrypted on this device and never included in campaign exports. Changing provider hosts clears the draft key.\n\nOnly relevant scenes and actions go to your provider—not private journals or full saves. Streaming text is a draft until checked.\n\nAuto-connect checks the model list without buying a reply. Model lists and limits vary by service; manual entry always works. Gemini uses Google's compatible chat API. Other services must support chat/completions.\n\nReferences use public pages. Automatic searches send generic cultivation terms; test queries send what you type. Retrieved excerpts go to your AI provider. Protected sites may not work. Up to eight sources are saved, at most two fetched per lookup. General search needs your own accessible SearXNG JSON endpoint.")
        }
    }, confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } })
}
