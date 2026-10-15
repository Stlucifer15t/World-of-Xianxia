@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.immortalworld.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.immortalworld.ai.AiConfig
import com.immortalworld.core.*
import kotlinx.coroutines.delay

@Composable
private fun StoryProse(text: String, dialogue: Boolean = false) {
    StoryText.paragraphs(text).forEach { paragraph ->
        Text(paragraph, fontFamily = ScrollFont, fontSize = if (dialogue) 18.sp else 19.sp,
            lineHeight = if (dialogue) 28.sp else 30.sp,
            color = if (dialogue) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
    }
}

@Composable
private fun StoryReceipt(record: ActionRecord, liveDraft: String = "", waiting: Boolean = false) {
    val opening = OpeningScene.isOpening(record)
    Column(Modifier.fillMaxWidth().testTag("story_${record.id}")) {
        Text(if (opening) "YOUR STORY · OPENING" else "YOU · Day ${record.day}, ${record.hour.toString().padStart(2, '0')}:00", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
        if (!opening) Text(record.input, fontSize = 17.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        if (!record.accepted) Text("ACTION NOT RESOLVED", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        if (record.accepted && record.kind in listOf("BREAKTHROUGH", "JOIN", "TURN_IN", "CHALLENGE"))
            Text("A TURN IN THE JOURNEY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 12.dp))
        if (waiting && liveDraft.isBlank()) {
            Text(if (opening) "Writing your opening from your character and starting scenario…" else "Writing a new version…", color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
            if (record.narration.isNotBlank()) StoryProse(record.narration)
        } else if (liveDraft.isNotBlank()) {
            Text("STREAMING DRAFT · UNVERIFIED · NOT SAVED", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
            StoryProse(liveDraft)
            Text("Offline result remains authoritative until the complete narration passes validation.", style = MaterialTheme.typography.bodySmall)
        } else {
            if (opening && record.narration.isBlank()) Text("LOCAL OPENING · NO AI RESPONSE SAVED", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
            StoryProse(record.narration.ifBlank { record.story.ifBlank { record.facts.joinToString(" ") } })
        }
        if (record.dialogue.isNotBlank()) StoryProse(record.dialogue, dialogue = true)
        var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide action details" else "Action details · facts & calculations") }
        if (expanded) {
            Text(if (record.narration.isBlank()) "AUTHORITATIVE RESULTS" else "AUTHORITATIVE RESULTS · AI prose cannot change these", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            record.facts.forEach { Text(it, modifier = Modifier.padding(vertical = 5.dp), fontSize = 15.sp, lineHeight = 22.sp) }
            record.calculations.forEach { Text(it, modifier = Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun NarrationControls(state: GameUiState, model: GameViewModel, record: ActionRecord?) {
    if (!state.aiReady) return
    if (!state.aiConfig.enabled) {
        TextButton(onClick = { model.navigate(Screen.SETTINGS) }, enabled = !state.busy) { Text("Set up AI storytelling") }
    } else {
        val opening = record == null || OpeningScene.isOpening(record)
        OutlinedButton(onClick = model::retryNarration, enabled = !state.busy,
            modifier = Modifier.testTag(if (opening) "regenerate_opening" else "regenerate_story")) {
            Text(if (opening) {
                if (record?.narration.isNullOrBlank()) "Generate AI opening" else "Regenerate opening"
            } else "Regenerate response")
        }
        Text("Prose only · keeps your character and outcomes · may use paid tokens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AdventureScreen(state: GameUiState, model: GameViewModel) {
    val save = state.active?.save ?: return
    val a = save.adventure ?: return
    val p = save.player
    val focus = LocalFocusManager.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val submit = { focus.clearFocus(); model.submitAction() }
    var sheet by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableIntStateOf(3) }
    val scroll = rememberLazyListState()
    LaunchedEffect(a.history.lastOrNull()?.id) { scroll.scrollToItem(0) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("adventure")) {
        val compact = maxHeight < 360.dp * fontScale
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(save.world.currentLocation, fontSize = 18.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.secondary)
                        if (!compact) Text("${SceneNarrator.timeOfDay(a.hour)} · ${SceneNarrator.weather(save)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { focus.clearFocus(); sheet = "menu" }, modifier = Modifier.testTag("journey_menu")) { Text("Menu") }
                }
                Text("Day ${save.world.day} · ${a.hour.toString().padStart(2, '0')}:00 · HP ${p.health}/${p.maxHealth} · Qi ${p.energy}/${p.maxEnergy}", style = MaterialTheme.typography.bodySmall)
                Text("${save.realms.first { it.id == p.cultivation.realmId }.name} ${p.cultivation.substage} · ${p.cultivation.progress.toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                a.combat?.let { Text("${it.enemy.name} · ${it.enemy.health}/${it.enemy.maxHealth} HP · Turn ${it.turn} · ${it.position}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
            HorizontalDivider(color = Gold.copy(alpha = .25f))
            LazyColumn(Modifier.weight(1f).testTag("adventure_history"), state = scroll,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                state.pendingAction?.let { pending -> item { Column {
                    Text("REVIEW AI INTERPRETATION", color = MaterialTheme.colorScheme.secondary)
                    Text("${pending.action.kind}: ${pending.action.target}\n${pending.action.detail}\nDuration (activities): ${pending.action.hours}h")
                    Text("No mechanics have run. Confirm only if this matches your intention.", style = MaterialTheme.typography.bodySmall)
                    if (IntentParser.needsReview(pending.input)) Text("Conditions are not automatically fulfilled. Confirm only if you want the displayed operation resolved now, without assuming NPC permission.", color = MaterialTheme.colorScheme.secondary)
                    FlowRow { Button(onClick = model::confirmProposal, enabled = !state.busy) { Text("Confirm action") }; TextButton(onClick = model::dismissProposal) { Text("Reject") } }
                } } }
                if (a.history.isEmpty()) item {
                    Column {
                        Text("IMMORTAL WORLD · THE JOURNEY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 18.dp))
                        SceneNarrator.scene(save).forEach { StoryProse(it) }
                        Text("Your words direct the journey. Ask Mei about cultivation resources, inspect the recruitment notice, or describe your own approach.", fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (OpeningScene.canAttach(save)) NarrationControls(state, model, null)
                    }
                }
                items(a.history.takeLast(shown).asReversed(), key = { it.id }) { record ->
                    Column {
                        StoryReceipt(record, if (state.streamingRecordId == record.id) state.streamingText else "", state.aiWorking && state.streamingRecordId == record.id)
                        if (record.id == a.history.lastOrNull()?.id && record.accepted) NarrationControls(state, model, record)
                    }
                }
                if (a.history.size > shown) item { TextButton(onClick = { shown += 10 }) { Text("Read earlier entries (${a.history.size - shown})") } }
                if (a.locationId == "ruins" && "wolf" !in a.defeated && a.combat == null) item {
                    Text("The inner courtyard is guarded. Searching it starts combat; investigating the outer wards does not.", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, lineHeight = 24.sp)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    OutlinedTextField(state.actionDraft, model::actionDraft, label = { Text("What do you do or say?") },
                        placeholder = { Text("Ask, explore, bargain, cultivate…") }, minLines = if (compact) 1 else if (keyboardVisible) 2 else 3, maxLines = if (compact) 2 else 5,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (!state.busy && state.actionDraft.isNotBlank()) submit() }),
                        modifier = Modifier.fillMaxWidth().testTag("action_input"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = submit, enabled = !state.busy && state.actionDraft.isNotBlank(), modifier = Modifier.weight(1f).testTag("submit_action")) { Text("Submit action") }
                        if (state.aiConfig.enabled) TextButton(onClick = model::attemptOffline, enabled = !state.busy && state.actionDraft.isNotBlank()) { Text("Offline") }
                        TextButton(onClick = { focus.clearFocus(); model.navigate(Screen.JOURNAL) }, enabled = !state.busy || state.aiWorking) { Text("Notes") }
                    }
                }
            }
        }
    }
    if (sheet.isNotEmpty()) ModalBottomSheet(onDismissRequest = { sheet = "" }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().testTag("journey_sheet"), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when (sheet) { "scene" -> "Here, now"; "recent" -> "Recent developments"; else -> "Journey menu" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
                TextButton(onClick = { sheet = "" }, modifier = Modifier.testTag("close_journey_sheet")) { Text("Close") }
            } }
            if (sheet == "scene") item { Column { SceneNarrator.scene(save).forEach { StoryProse(it) } } }
            if (sheet == "recent") {
                item { Text("${save.world.currentLocation} · Day ${save.world.day}, ${a.hour}:00\nClosing this panel returns to your place in the story. The campaign event log and NPC records keep the existing retention rules.", style = MaterialTheme.typography.bodyMedium) }
                if (a.events.isEmpty()) item { Text("No recorded developments yet.") }
                items(a.events.takeLast(20).asReversed(), key = { it.id }) { event -> Column {
                    Text("Day ${event.day} · ${event.hour}:00", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Text(event.text, fontSize = 17.sp, lineHeight = 26.sp)
                } }
                item { TextButton(onClick = { sheet = ""; model.navigate(Screen.EVENTS) }) { Text("Full event log") } }
            }
            if (sheet == "menu") {
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { sheet = "scene" }) { Text("Current surroundings") }
                    OutlinedButton(onClick = { sheet = "recent" }) { Text("Recent events") }
                } }
                item { Text("OPTIONAL ACTIONS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shortcuts = if (a.combat == null) listOf("Observe" to GameAction(ActionKind.OBSERVE), "Investigate" to GameAction(ActionKind.INVESTIGATE), "Search" to GameAction(ActionKind.SEARCH), "Rest (2h)" to GameAction(ActionKind.REST))
                        else listOf("Attack" to GameAction(ActionKind.ATTACK), "Defend" to GameAction(ActionKind.DEFEND), "Dodge" to GameAction(ActionKind.DODGE), "Retreat" to GameAction(ActionKind.RETREAT))
                    shortcuts.forEach { (label, action) -> OutlinedButton(onClick = { sheet = ""; model.command(action, label) }, enabled = !state.busy) { Text(label) } }
                    TextButton(onClick = { sheet = ""; model.save() }, enabled = !state.busy) { Text("Save snapshot") }
                } }
                item { Text("CAMPAIGN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Screen.STATUS to "Character", Screen.CULTIVATION to "Cultivation", Screen.INVENTORY to "Inventory", Screen.TECHNIQUES to "Techniques", Screen.QUESTS to "Quests", Screen.MAP to "Map", Screen.NPCS to "NPCs", Screen.EVENTS to "Events", Screen.JOURNAL to "Journal", Screen.SAVES to "Saves", Screen.SETTINGS to "Settings / AI", Screen.HOME to "Home").forEach { (screen, name) ->
                        TextButton(onClick = { sheet = ""; model.navigate(screen) }, enabled = !state.busy || state.aiWorking) { Text(name) }
                    }
                } }
                item { Text("Try: “examine Elder Wen with spiritual sense”, “travel to Jade River Crossing”, “negotiate medicinal herbs”, or “sit beneath a tree and cultivate”. Creative attempts use validated checks, not automatic success. Spiritual sense costs 3 energy and 1 hour and can be noticed.\n\n${p.spiritStones} spirit stones · ${a.simulationMode.lowercase()} background simulation", fontSize = 16.sp, lineHeight = 24.sp) }
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
    LazyColumn(Modifier.testTag("cultivation"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("THE INNER WORLD", "Refine qi. Temper the soul.", "Practice at your own pace. Simulate hours instantly—no real-time wait.") }
        item { RealmTablet(save) }
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
            Text("Breakthrough preview: ${AdventureEngine.breakthroughChance(save)}% chance; ${if (save.rules.godMode) 0 else 10 * (save.realms.indexOfFirst { it.id == save.player.cultivation.realmId } + 1)} stones. Failure leaves 75% progress, adds injury, and costs health. Success is never automatic.", style = MaterialTheme.typography.bodySmall)
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
    LazyColumn(Modifier.testTag("library_${state.screen.name.lowercase()}"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading(when (state.screen) { Screen.TECHNIQUES -> "PAVILION OF ARTS"; Screen.INVENTORY -> "THE TREASURE POUCH"; Screen.QUESTS -> "SECT & WORLD AFFAIRS"; else -> "JADE RECORDS" }, state.screen.name.lowercase().replaceFirstChar { it.uppercase() }, "Day ${s.world.day} · ${a.hour}:00 · ${s.world.currentLocation}") }
        if (state.screen == Screen.INVENTORY) {
            item { Text("${s.player.spiritStones} spirit stones. Buy/sell through nearby NPCs or the text input.") }
            items(s.player.inventory, key = { it.id }) { item -> Panel {
                Text(when (item.kind) { "pill" -> "◉  SPIRIT PILLS"; "herb", "material" -> "❧  CULTIVATION MATERIALS"; "weapon" -> "◇  WEAPONS"; "armor" -> "◈  ARMOR"; "artifact" -> "✧  SPIRIT TREASURES"; else -> item.kind.uppercase() }, color = Gold, style = MaterialTheme.typography.labelSmall)
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
            Text("▤  CULTIVATION MANUAL · ${technique.rank}", color = Gold, style = MaterialTheme.typography.labelSmall)
            Text(technique.name, fontFamily = ScrollFont, style = MaterialTheme.typography.titleLarge); Text(technique.description)
            LinearProgressIndicator(progress = { technique.mastery.toFloat() / 100f }, modifier = Modifier.fillMaxWidth())
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
                    if (location.id in AdventureEngine.neighbors(a.locationId)) SceneNarrator.route(a.locationId, location.id)?.let { Text(it, fontSize = 17.sp, lineHeight = 26.sp) }
                    Button(onClick = { model.command(GameAction(ActionKind.TRAVEL, location.id), "Travel to ${location.name}") }, enabled = ready && a.combat == null && location.id != a.locationId && (s.rules.godMode || location.id in AdventureEngine.neighbors(a.locationId))) { Text(if (location.id == a.locationId) "Current location" else "Travel · 2 hours") }
                }
            } }
        }
        if (state.screen == Screen.NPCS) items(a.npcs, key = { it.character.id }) { n -> Panel {
            Text(n.character.name, style = MaterialTheme.typography.titleLarge)
            Text("${n.character.personality}\nOpinion: ${n.relationship}\nLast recorded location: ${a.locations.first { it.id == n.locationId }.name}")
            if (n.locationId == a.locationId) Text(SceneNarrator.npcActivity(s, n), fontSize = 18.sp, lineHeight = 28.sp)
            var mechanics by rememberSaveable(n.character.id) { mutableStateOf(false) }
            TextButton(onClick = { mechanics = !mechanics }) { Text(if (mechanics) "Hide mechanical record" else "Mechanical record · out of character") }
            if (mechanics) {
                Text("${s.realms.first { it.id == n.character.cultivation.realmId }.name} ${n.character.cultivation.substage} · HP ${n.character.health} · Strength ${n.character.attributes.strength}")
                Text("System reference, not knowledge granted by looking or dialogue. Spiritual-sense examinations resolve separately.", style = MaterialTheme.typography.bodySmall)
            }
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
