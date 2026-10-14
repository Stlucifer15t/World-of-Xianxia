@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immortalworld.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.immortalworld.ai.AiConfig
import com.immortalworld.core.*
import kotlinx.coroutines.delay

@Composable
internal fun AdventureScreen(state: GameUiState, model: GameViewModel) {
    val save = state.active?.save ?: return
    val a = save.adventure ?: return
    val p = save.player
    Column(Modifier.fillMaxSize().testTag("adventure")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(save.world.currentLocation, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
            Text("Day ${save.world.day} · ${a.hour.toString().padStart(2, '0')}:00 · HP ${p.health}/${p.maxHealth} · Qi ${p.energy}/${p.maxEnergy}", style = MaterialTheme.typography.bodySmall)
            Text("${save.realms.first { it.id == p.cultivation.realmId }.name} ${p.cultivation.substage} · ${p.cultivation.progress.toInt()}% · ${p.spiritStones} stones", style = MaterialTheme.typography.bodySmall)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(Screen.STATUS to "Character", Screen.CULTIVATION to "Cultivation", Screen.INVENTORY to "Inventory", Screen.TECHNIQUES to "Techniques", Screen.QUESTS to "Quests", Screen.MAP to "Map", Screen.NPCS to "NPCs", Screen.EVENTS to "Events", Screen.JOURNAL to "Journal")) { (screen, name) ->
                SuggestionChip(onClick = { model.navigate(screen) }, label = { Text(name) })
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("adventure_history"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Panel {
                Text(if (a.combat != null) "ENCOUNTER · YOUR TURN" else "CURRENT SCENE", color = MaterialTheme.colorScheme.primary)
                Text(a.locations.first { it.id == a.locationId }.description)
                Text("Nearby: ${a.npcs.filter { it.locationId == a.locationId }.joinToString { it.character.name }.ifEmpty { "no known NPCs" }}")
                a.combat?.let { Text("${it.enemy.name}: ${it.enemy.health}/${it.enemy.maxHealth} HP · Turn ${it.turn} · ${it.position}", color = MaterialTheme.colorScheme.secondary) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shortcuts = if (a.combat == null) listOf("Observe" to GameAction(ActionKind.OBSERVE), "Investigate" to GameAction(ActionKind.INVESTIGATE), "Search" to GameAction(ActionKind.SEARCH), "Rest (2h)" to GameAction(ActionKind.REST))
                    else listOf("Attack" to GameAction(ActionKind.ATTACK), "Defend" to GameAction(ActionKind.DEFEND), "Dodge" to GameAction(ActionKind.DODGE), "Retreat" to GameAction(ActionKind.RETREAT))
                    TextButton(onClick = model::save, enabled = !state.busy) { Text("Save snapshot") }
                    shortcuts.forEach { (label, action) -> OutlinedButton(onClick = { model.command(action, label) }, enabled = !state.busy) { Text(label) } }
                }
                if (a.locationId == "ruins" && "wolf" !in a.defeated && a.combat == null) Text("Searching the guarded courtyard starts combat. Investigating the outer wards does not.", style = MaterialTheme.typography.bodySmall)
            } }
            state.pendingAction?.let { pending -> item { Panel {
                Text("REVIEW AI INTERPRETATION", color = MaterialTheme.colorScheme.secondary)
                Text("${pending.action.kind}: ${pending.action.target}\n${pending.action.detail}\nDuration (activities): ${pending.action.hours}h")
                Text("No mechanics have run. Confirm only if this matches your intention.", style = MaterialTheme.typography.bodySmall)
                Row { Button(onClick = model::confirmProposal, enabled = !state.busy) { Text("Confirm action") }; TextButton(onClick = model::dismissProposal) { Text("Reject") } }
            } } }
            if (a.history.isEmpty()) item { Text("Your adventure is yours to direct. Try “Ask Mei about Moonfall Ruins”, “travel to Jade River Crossing”, or describe a creative approach. Unknown offline actions use a bounded skill check, not automatic success.") }
            items(a.history.asReversed(), key = { it.id }) { record -> Panel {
                Text("YOU · Day ${record.day}, ${record.hour}:00", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                Text(record.input)
                Text(if (record.accepted) "ENGINE RESULTS" else "NOT RESOLVED", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                record.facts.forEach { Text(it) }
                if (record.narration.isNotBlank()) { HorizontalDivider(); Text("AI narration · engine results above remain authoritative", style = MaterialTheme.typography.labelSmall); Text(record.narration) }
                var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
                if (record.calculations.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide calculations" else "Show calculations") }
                    if (expanded) record.calculations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            } }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                OutlinedTextField(state.actionDraft, model::actionDraft, label = { Text("What do you do or say?") },
                    placeholder = { Text("Describe your action, dialogue, or intention…") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth().testTag("action_input"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::submitAction, enabled = !state.busy && state.actionDraft.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Submit action") }
                    if (state.aiConfig.enabled) TextButton(onClick = model::attemptOffline, enabled = !state.busy && state.actionDraft.isNotBlank()) { Text("Attempt offline") }
                    TextButton(onClick = { model.navigate(Screen.JOURNAL) }) { Text("Notes") }
                }
            }
        }
    }
}

@Composable
internal fun CultivationScreen(state: GameUiState, model: GameViewModel) {
    val save = state.active?.save ?: return
    val a = save.adventure ?: return
    var activity by rememberSaveable { mutableStateOf("Cultivation") }
    var method by rememberSaveable(save.id) { mutableStateOf(save.player.cultivation.method) }
    var hours by rememberSaveable { mutableStateOf("1") }
    var herbs by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }
    val action = GameAction(ActionKind.ACTIVITY, activity, hours = hours.toIntOrNull() ?: 0, method = method, useHerb = herbs)
    val estimate = runCatching { Activities.estimate(save, action) }.getOrNull()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("OPTIONAL PRACTICE", "Your time. Your path.", "Advance in-game hours instantly. The adventure never requires waiting in real time.") }
        item { Choice("Activity", activity, Activities.kinds) { activity = it } }
        item { Choice("Method", method, (Activities.methods + save.player.cultivation.method).distinct()) { method = it } }
        item { OutlinedTextField(hours, { if (it.length <= 2 && it.all(Char::isDigit)) hours = it }, label = { Text("In-game hours (1–24)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
        item { Row { Checkbox(herbs, { herbs = it }, enabled = activity == "Cultivation"); Text("Use one herb/hour for 50% faster cultivation", Modifier.padding(top = 12.dp)) } }
        item { Panel {
            Text("ESTIMATE BEFORE STARTING", color = MaterialTheme.colorScheme.secondary)
            if (estimate != null) {
                Text("Progress +${"%.2f".format(estimate.progress)} · Health up to +${estimate.health} · Energy ${estimate.energy}\nStrength +${estimate.strength} · Mastery training +${estimate.mastery}\nCost: ${estimate.herbs} herbs, ${estimate.materials} ore, ${estimate.stones} stones.")
                Text(estimate.explanation, style = MaterialTheme.typography.bodySmall)
            } else Text("Enter a duration from 1 to 24 hours.")
            Text("Available: ${save.player.inventory.filter { it.kind == "herb" }.sumOf { it.quantity }} herbs, ${save.player.inventory.filter { it.kind == "material" }.sumOf { it.quantity }} ore, ${save.player.spiritStones} stones. Energy ${save.player.energy}.")
            Button(onClick = { model.command(action, "$activity for ${action.hours} hours using $method${if (herbs) " with herbs" else ""}") }, enabled = !state.busy && estimate != null && a.combat == null) { Text("Simulate now") }
            OutlinedButton(onClick = { model.command(GameAction(ActionKind.BREAKTHROUGH), "Attempt a breakthrough") }, enabled = !state.busy && a.combat == null) { Text("Attempt breakthrough · 100% required") }
        } }
        item { Panel {
            Text("REAL-TIME OFFLINE CLAIMS · OPTIONAL", color = MaterialTheme.colorScheme.secondary)
            Text("Nothing advances automatically. Arm a chosen activity, then explicitly claim whole elapsed hours later (maximum 24). Costs still apply. Cancelling a plan discards unclaimed time.")
            val plan = a.idlePlan
            if (plan == null) OutlinedButton(onClick = { model.command(action.copy(kind = ActionKind.ENABLE_OFFLINE), "Arm optional offline $activity") }, enabled = !state.busy && estimate != null && a.combat == null) { Text("Arm this offline activity") }
            else {
                val available = ((now - plan.checkpointMillis).coerceAtLeast(0) / 3_600_000).coerceAtMost(24)
                Text("Armed: ${plan.activity}, ${plan.method}. $available hours available.")
                Button(onClick = { model.command(GameAction(ActionKind.CLAIM_OFFLINE), "Claim available offline activity") }, enabled = !state.busy && available > 0 && a.combat == null) { Text("Claim $available hours") }
                TextButton(onClick = { model.command(GameAction(ActionKind.DISABLE_OFFLINE), "Disable offline activity") }, enabled = !state.busy && a.combat == null) { Text("Disable offline activity") }
            }
        } }
        item { Panel {
            Text("BACKGROUND SIMULATION")
            Text("Action time always advances. Paused freezes NPC/faction schedules; normal follows action time; accelerated runs schedules three times faster. No mode chooses player actions.")
            FlowRow { listOf("PAUSED", "NORMAL", "ACCELERATED").forEach { mode -> FilterChip(selected = a.simulationMode == mode, onClick = { model.command(GameAction(ActionKind.SIMULATION_MODE, mode), "Set background simulation to $mode") }, label = { Text(mode.lowercase()) }, enabled = !state.busy && a.combat == null) } }
        } }
        a.history.lastOrNull()?.let { last -> item { Panel { Text("LATEST ENGINE RESULT", color = MaterialTheme.colorScheme.primary); last.facts.forEach { Text(it) } } } }
    }
}

@Composable
internal fun AdventureLibrary(state: GameUiState, model: GameViewModel) {
    val s = state.active?.save ?: return
    val a = s.adventure ?: return
    val ready = !state.busy
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("CAMPAIGN RECORD", state.screen.name.lowercase().replaceFirstChar { it.uppercase() }, "Day ${s.world.day} · ${a.hour}:00 · ${s.world.currentLocation}") }
        if (state.screen == Screen.INVENTORY) {
            item { Text("${s.player.spiritStones} spirit stones. Buy/sell through nearby NPCs or the text input.") }
            items(s.player.inventory, key = { it.id }) { item -> Panel {
                Text("${item.name} ×${item.quantity}", style = MaterialTheme.typography.titleMedium); Text(item.description)
                Text("Power ${item.power} · ${item.equippedSlot ?: "unequipped"}")
                if (item.kind in listOf("weapon", "armor", "artifact")) {
                    Text("Currently equipped ${item.kind} power: ${s.player.inventory.firstOrNull { it.equippedSlot == item.kind }?.power ?: 0}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { model.command(GameAction(ActionKind.EQUIP, item.id), "Equip ${item.name}") }, enabled = ready && a.combat == null && item.equippedSlot == null) { Text("Equip") }
                }
                if (item.kind == "pill") Button(onClick = { model.command(GameAction(ActionKind.USE_ITEM, item.id), "Consume ${item.name}") }, enabled = ready) { Text("Consume pill") }
            } }
        }
        if (state.screen == Screen.TECHNIQUES) items(s.player.techniques, key = { it.id }) { technique -> Panel {
            Text(technique.name, style = MaterialTheme.typography.titleMedium); Text(technique.description)
            Text("${technique.effect} · Power ${technique.power} · Cost ${technique.energyCost} energy · Mastery ${technique.mastery}%")
            Button(onClick = { model.command(GameAction(ActionKind.TECHNIQUE, technique.id), "Use ${technique.name}") }, enabled = ready && a.combat != null) { Text("Use in encounter") }
            TextButton(onClick = { model.navigate(Screen.CULTIVATION) }) { Text("Practice techniques") }
        } }
        if (state.screen == Screen.MAP) {
            item { Text("Travel takes two hours along adjacent roads. Investigate the crossing/mountain or ask a knowledgeable NPC to discover Moonfall.") }
            items(a.locations, key = { it.id }) { location -> Panel {
                Text(if (location.discovered || location.id == a.locationId) location.name else "Unexplored site", style = MaterialTheme.typography.titleMedium)
                if (location.discovered || location.id == a.locationId) {
                    Text(location.description); Text("Danger ${location.danger} · Roads: ${AdventureEngine.neighbors(location.id).mapNotNull { id -> a.locations.firstOrNull { it.id == id }?.name }.joinToString()}")
                    Button(onClick = { model.command(GameAction(ActionKind.TRAVEL, location.id), "Travel to ${location.name}") }, enabled = ready && a.combat == null && location.id != a.locationId && (s.rules.godMode || location.id in AdventureEngine.neighbors(a.locationId))) { Text(if (location.id == a.locationId) "Current location" else "Travel · 2 hours") }
                }
            } }
        }
        if (state.screen == Screen.NPCS) items(a.npcs, key = { it.character.id }) { n -> Panel {
            Text(n.character.name, style = MaterialTheme.typography.titleLarge)
            Text("${n.character.personality}\nGoal: ${n.goal}\nOpinion: ${n.relationship}\nLocation: ${a.locations.first { it.id == n.locationId }.name}")
            Text("${s.realms.first { it.id == n.character.cultivation.realmId }.name} ${n.character.cultivation.substage} · HP ${n.character.health} · Strength ${n.character.attributes.strength}")
            val nearby = n.locationId == a.locationId && a.combat == null
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.command(GameAction(ActionKind.TALK, n.character.id, "What do you know about Moonfall Ruins?"), "Ask ${n.character.name} about Moonfall Ruins") }, enabled = ready && nearby) { Text("Ask about ruins") }
                OutlinedButton(onClick = { model.command(GameAction(ActionKind.CHALLENGE, n.character.id), "Challenge ${n.character.name} to a nonlethal duel") }, enabled = ready && nearby) { Text("Challenge duel") }
                if (n.role == "merchant") {
                    Button(onClick = { model.command(GameAction(ActionKind.BUY, n.character.id, "herb"), "Buy one herb from ${n.character.name}") }, enabled = ready && nearby) { Text("Buy herb · ${if (n.negotiated) 3 else 4} stones") }
                    Button(onClick = { model.command(GameAction(ActionKind.BUY, n.character.id, "pill"), "Buy one pill from ${n.character.name}") }, enabled = ready && nearby) { Text("Buy pill · ${if (n.negotiated) 11 else 12} stones") }
                    TextButton(onClick = { model.command(GameAction(ActionKind.SELL, n.character.id, "herb"), "Sell one herb to ${n.character.name}") }, enabled = ready && nearby) { Text("Sell herb · 2 stones") }
                    TextButton(onClick = { model.command(GameAction(ActionKind.NEGOTIATE, n.character.id), "Negotiate prices with ${n.character.name}") }, enabled = ready && nearby && !n.negotiated) { Text("Negotiate") }
                }
            }
            Text("Recorded memories", color = MaterialTheme.colorScheme.secondary)
            if (n.memories.isEmpty()) Text("No shared encounters yet.") else n.memories.takeLast(3).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Use the adventure input for your own dialogue. NPCs only know their stored knowledge and memories.", style = MaterialTheme.typography.bodySmall)
        } }
        if (state.screen == Screen.QUESTS) {
            items(a.quests, key = { it.id }) { quest -> Panel {
                Text(quest.name, style = MaterialTheme.typography.titleLarge); Text(quest.objective)
                Text("${quest.status} · Reward ${quest.reward} stones · Given by ${a.npcs.first { it.character.id == quest.giverId }.character.name}")
                val nearby = a.npcs.any { it.character.id == quest.giverId && it.locationId == a.locationId }
                if (quest.status != "COMPLETE") Button(onClick = { model.command(GameAction(if (quest.status == "AVAILABLE") ActionKind.ACCEPT_QUEST else ActionKind.TURN_IN, quest.id), "${if (quest.status == "AVAILABLE") "Accept" else "Turn in"} ${quest.name}") }, enabled = ready && nearby && a.combat == null) { Text(if (!nearby) "Visit the quest giver" else if (quest.status == "AVAILABLE") "Accept quest" else "Turn in quest") }
            } }
            items(a.factions, key = { "faction_" + it.id }) { faction -> Panel {
                Text(faction.name, style = MaterialTheme.typography.titleLarge); Text("Resources ${faction.resources} · Your reputation ${faction.reputation}\n${if (faction.member) "Member: outer disciple" else "Not a member"}")
                Text("Entry: stage 2+, reputation 5, speak to Elder Wen at the village.")
                if (!faction.member) OutlinedButton(onClick = { model.command(GameAction(ActionKind.JOIN, faction.id), "Ask to join ${faction.name}") }, enabled = ready && a.combat == null) { Text("Request membership") }
            } }
        }
        if (state.screen == Screen.EVENTS) {
            item { Text("Background mode: ${a.simulationMode}. Events arise from your actions and scheduled patrol/supply activity; no automatic quests, wars, or player decisions.") }
            if (a.events.isEmpty()) item { Text("No recorded developments yet.") }
            items(a.events.asReversed(), key = { it.id }) { event -> Panel { Text("Day ${event.day} · ${event.hour}:00", color = MaterialTheme.colorScheme.secondary); Text(event.text) } }
        }
        if (state.screen != Screen.EVENTS) a.history.lastOrNull()?.let { last -> item { Panel { Text("LATEST ENGINE RESULT", color = MaterialTheme.colorScheme.primary); last.facts.forEach { Text(it) } } } }
    }
}

@Composable
internal fun AiSettingsPanel(state: GameUiState, model: GameViewModel) {
    var enabled by remember(state.aiConfig) { mutableStateOf(state.aiConfig.enabled) }
    var endpoint by remember(state.aiConfig) { mutableStateOf(state.aiConfig.endpoint) }
    var name by remember(state.aiConfig) { mutableStateOf(state.aiConfig.model) }
    // Deliberately not rememberSaveable: API keys must not be serialized into a UI state bundle.
    var key by remember(state.aiConfig) { mutableStateOf(state.aiConfig.apiKey) }
    var temperature by remember(state.aiConfig) { mutableStateOf(state.aiConfig.temperature.toString()) }
    val config = AiConfig(enabled, endpoint.trim(), name.trim(), key.trim(), temperature.toDoubleOrNull() ?: Double.NaN)
    Panel {
        Text("AI STORYTELLER · OPTIONAL", color = MaterialTheme.colorScheme.secondary)
        Row { Switch(enabled, { enabled = it }, enabled = !state.busy); Text("Enable provider calls", Modifier.padding(12.dp)) }
        Field("Complete HTTPS chat/completions URL", endpoint, 500) { endpoint = it }
        Field("Model name", name, 100) { name = it }
        OutlinedTextField(key, { if (it.length <= 1000) key = it }, label = { Text("API key (stored encrypted on device)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Field("Temperature (0–2)", temperature, 8) { temperature = it }
        Text("Only relevant scene, NPC memories, your submitted action and engine facts are sent to this endpoint. Your full save, journal, hidden traits and other provider credentials are not sent. Network use may incur provider charges.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.configureAi(config) }, enabled = !state.busy) { Text("Save AI settings") }
            OutlinedButton(onClick = { model.testAi(config) }, enabled = !state.busy) { Text("Test connection") }
        }
        Text("One compatible provider configuration is supported in this increment. Calls are cancellable. Failed narration never rolls back or replays resolved mechanics.", style = MaterialTheme.typography.bodySmall)
    }
}
