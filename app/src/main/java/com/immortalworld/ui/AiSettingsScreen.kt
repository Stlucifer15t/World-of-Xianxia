@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immortalworld.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.immortalworld.ai.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun AiSettingsPanel(state: GameUiState, model: GameViewModel) {
    if (!state.aiReady) { Text("Loading encrypted provider settings…"); return }
    var draft by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig) }
    var context by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig.contextTokens.toString()) }
    var output by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig.maxOutputTokens.toString()) }
    var temperature by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig.temperature.toString()) }
    var sourceText by remember(state.aiConfig, state.activeProfileId) { mutableStateOf(state.aiConfig.sources.joinToString("\n")) }
    var knowledgeExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val catalog = state.catalog?.takeIf { it.endpoint == draft.endpoint }
    val selected = catalog?.models?.firstOrNull { it.id == draft.model }
    val uri = LocalUriHandler.current
    LaunchedEffect(selected, draft.manualContext, draft.model, draft.endpoint) {
        if (!draft.manualContext) context = (selected?.contextTokens ?: if (draft.model == state.aiConfig.model && draft.endpoint == state.aiConfig.endpoint) state.aiConfig.contextTokens else 32768).toString()
    }
    val config = draft.copy(contextTokens = context.toIntOrNull() ?: 0, maxOutputTokens = output.toIntOrNull() ?: 0,
        temperature = temperature.toDoubleOrNull() ?: Double.NaN, sources = sourceText.lines().map(String::trim).filter(String::isNotEmpty))
    fun endpoint(value: String) {
        val old = draft.endpoint.toHttpUrlOrNull(); val next = value.toHttpUrlOrNull()
        val changedHost = old?.host != next?.host || old?.port != next?.port || old?.scheme != next?.scheme
        draft = draft.copy(endpoint = value, apiKey = if (changedHost) "" else draft.apiKey, model = if (changedHost) "" else draft.model)
    }
    Panel {
        Text("AI STORYTELLER · OPTIONAL", color = MaterialTheme.colorScheme.secondary)
        Text(state.aiConnection, modifier = Modifier.testTag("ai_connection_status"), style = MaterialTheme.typography.bodyMedium)
        Row { Switch(draft.enabled, { draft = draft.copy(enabled = it) }, enabled = !state.busy); Text("Enable provider calls", Modifier.padding(12.dp)) }
        Text("Saved settings load automatically. Auto-connect checks the model catalog on startup/network return; it does not buy a completion or send campaign data.", style = MaterialTheme.typography.bodySmall)
        Row { Switch(draft.autoConnect, { draft = draft.copy(autoConnect = it) }); Text("Auto-connect saved provider", Modifier.padding(12.dp)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("OpenAI" to "https://api.openai.com/v1/chat/completions", "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions", "Groq" to "https://api.groq.com/openai/v1/chat/completions").forEach { (name, url) ->
                TextButton(onClick = { endpoint(url) }) { Text(name) }
            }
        }
        Field("Complete HTTPS chat/completions URL", draft.endpoint, 500, onChange = { endpoint(it.trim()) })
        // The whole draft uses remember, never rememberSaveable: no API key in instance-state bundles.
        OutlinedTextField(draft.apiKey, { if (it.length <= 1000) draft = draft.copy(apiKey = it) }, label = { Text("API key · encrypted on device") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_key"))
        Text("Changing the endpoint host clears the draft key. Only OpenAI-compatible chat APIs are supported; the presets do not claim support for every provider feature.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.detectModels(config) }, enabled = !state.catalogLoading && !state.busy) { Text("Detect models / check access") }
            if (state.catalogLoading) TextButton(onClick = model::cancelModelLookup) { Text("Cancel model lookup") }
        }
        OutlinedTextField(draft.model, { if (it.length <= 200) draft = draft.copy(model = it.trim()) }, label = { Text("Model ID · type or select") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_model"))
        catalog?.let {
            Text("${it.models.size} reported models. Type part of an ID to filter; manual IDs are always allowed.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                it.models.filter { m -> draft.model.isBlank() || m.id.contains(draft.model, true) }.take(12).forEach { m ->
                    SuggestionChip(onClick = { draft = draft.copy(model = m.id) }, label = { Text(m.id) })
                }
            }
        }
        OutlinedTextField(context, { context = it.filter(Char::isDigit).take(8); draft = draft.copy(manualContext = true) },
            label = { Text("Context window · tokens") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_context"))
        Text(when { draft.manualContext -> "Manual override. Verify this limit with your provider."
            selected?.contextTokens != null -> "Detected from this provider's explicit metadata for ${selected.id}."
            else -> "Capacity not detected. The current value is a saved/default budget, not a verified model limit. Edit it manually if needed." }, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { draft = draft.copy(manualContext = false) }) { Text("Use detected context when available") }
        OutlinedTextField(output, { output = it.filter(Char::isDigit).take(5) }, label = { Text("Maximum output · tokens") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_output"))
        Text("Context supports up to 10,000,000 tokens, including 1,000,000 when your model supports it. This does not upload more history. Output is separate; task budgets remain 500 for interpretation, 650 for brief narration and 1,200 for scenes, capped by your maximum. Saved prose remains limited to 6,000 characters.", style = MaterialTheme.typography.bodySmall)
        selected?.maxOutputTokens?.let { Text("Provider-reported output ceiling: $it tokens.", style = MaterialTheme.typography.bodySmall) }
        Row { Switch(draft.streaming, { draft = draft.copy(streaming = it) }); Text("Stream narration previews", Modifier.padding(12.dp)) }
        Text("Streaming drafts are unverified and never saved as outcomes. Disable streaming if your endpoint does not support SSE; retry never repeats the action.", style = MaterialTheme.typography.bodySmall)
        Field("Temperature (0–2)", temperature, 8) { temperature = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.configureAi(config) }, enabled = !state.busy) { Text("Save AI settings") }
            OutlinedButton(onClick = { model.testAi(config) }, enabled = !state.busy) { Text("Test generation · may cost") }
        }
        Text("Only relevant observed scenes, submitted actions and engine facts go to the selected provider. Full saves, journals, hidden traits and other profiles' keys are excluded.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { knowledgeExpanded = !knowledgeExpanded }) { Text(if (knowledgeExpanded) "Hide cultivation knowledge" else "Optional cultivation web knowledge") }
        if (knowledgeExpanded) {
            Row { Switch(draft.knowledgeEnabled, { draft = draft.copy(knowledgeEnabled = it) }); Text("Enable reference lookups", Modifier.padding(12.dp)) }
            Text("Opt-in. Automatic searches send generic cultivation concepts, never your full action or journal. Explicit test queries are sent as typed. Retrieved excerpts also go to your AI provider; they cannot change rules, grant powers or become NPC knowledge.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(sourceText, { if (it.length <= 8000) sourceText = it }, label = { Text("Knowledge sources · one HTTPS URL per line") },
                placeholder = { Text("https://martial-world.fandom.com/wiki/Cultivation") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth().testTag("knowledge_sources"))
            Text("Use direct pages or MediaWiki api.php endpoints. Fandom sources try a focused wiki search, then the specified page when accessible. Up to eight sources can be stored; at most two are fetched per lookup. No crawling, login bypass, JavaScript execution or whole-site download.", style = MaterialTheme.typography.bodySmall)
            Field("Optional SearXNG JSON search endpoint", draft.searchEndpoint, 1000) { draft = draft.copy(searchEndpoint = it.trim()) }
            Text("Optional general web search requires an accessible SearXNG-compatible /search endpoint with JSON enabled. Leave blank for source/wiki lookup only. No built-in search-service subscription or key is supplied.", style = MaterialTheme.typography.bodySmall)
            Field("Focused lookup query", query, 200) { query = it }
            OutlinedButton(onClick = { model.previewKnowledge(config, query) }, enabled = !state.busy && draft.knowledgeEnabled && query.isNotBlank()) { Text("Test knowledge lookup") }
            if (state.knowledgeWorking) Text("Retrieving a small number of relevant excerpts…")
            if (!state.knowledgeWorking && state.knowledgeResult.references.isEmpty()) Text("No references retrieved yet. Protected, unsupported or irrelevant pages may yield no excerpt.", style = MaterialTheme.typography.bodySmall)
            state.knowledgeResult.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
            state.knowledgeResult.references.forEach { reference ->
                Text(reference.title, style = MaterialTheme.typography.titleMedium)
                Text(reference.excerpt, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { runCatching { uri.openUri(reference.url) } }) { Text("Open source: ${reference.url.toHttpUrlOrNull()?.host}") }
            }
            Text("Knowledge changes are saved with Save AI settings. Cache: up to 16 focused lookups for 30 minutes in memory. Unavailable sources do not stop narration or local play.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
