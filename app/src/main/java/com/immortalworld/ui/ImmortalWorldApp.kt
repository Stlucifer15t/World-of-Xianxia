@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immortalworld.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immortalworld.core.*
import com.immortalworld.data.*
import java.text.DateFormat
import java.util.Date

private val Jade = Color(0xFF91D3BF)
private val Gold = Color(0xFFE3C88F)
private val Ink = Color(0xFF101D20)
private val Palette = darkColorScheme(
    primary = Jade, onPrimary = Color(0xFF0B372D), secondary = Gold,
    background = Ink, surface = Color(0xFF17282B), surfaceVariant = Color(0xFF23373A),
    onBackground = Color(0xFFE5EBE6), onSurface = Color(0xFFE5EBE6),
    onSurfaceVariant = Color(0xFFACBFB9), outline = Color(0xFF536961),
)

@Composable
fun ImmortalWorldApp(model: GameViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val screenState = rememberSaveableStateHolder()
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importSave) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportId
        if (uri != null && id != null) model.exportSave(uri, id)
        exportId = null
    }
    MaterialTheme(colorScheme = Palette) {
        BackHandler(enabled = state.screen != Screen.HOME) {
            model.navigate(if (state.screen == Screen.STATUS) Screen.JOURNEY else Screen.HOME)
        }
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("IMMORTAL WORLD", fontSize = 15.sp, letterSpacing = 2.sp, color = Gold) },
                    navigationIcon = { if (state.screen != Screen.HOME) TextButton(onClick = {
                        model.navigate(if (state.screen == Screen.STATUS) Screen.JOURNEY else Screen.HOME)
                    }, enabled = !state.busy) { Text("Back") } },
                    actions = { if (state.screen != Screen.SETTINGS) TextButton(onClick = { model.navigate(Screen.SETTINGS) }, enabled = !state.busy) { Text("Settings") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Ink),
                )
            },
            bottomBar = {
                if (state.active != null && state.screen in listOf(Screen.JOURNEY, Screen.STATUS, Screen.SAVES)) {
                    NavigationBar(containerColor = Ink) {
                        listOf(Triple(Screen.JOURNEY, "Journey", "◇"), Triple(Screen.STATUS, "Character", "△"), Triple(Screen.SAVES, "Saves", "▤")).forEach { (screen, label, symbol) ->
                            NavigationBarItem(selected = state.screen == screen, onClick = { model.navigate(screen) },
                                enabled = !state.busy, icon = { Text(symbol, fontSize = 24.sp) }, label = { Text(label) })
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                Column(Modifier.fillMaxSize()) {
                    if (state.dirty) {
                        Surface(color = Color(0xFF4F3B1D), modifier = Modifier.fillMaxWidth()) {
                            Text("Unsaved changes · Save in Journey before closing the app.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (state.active?.save?.rules?.godMode == true) {
                        Surface(color = Color(0xFF583158), modifier = Modifier.fillMaxWidth()) {
                            Text("GOD MODE · This save has sandbox rules enabled", Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    state.notice?.let { message ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = model::dismissMessage) { Text("Dismiss") }
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        val key = if (state.screen == Screen.JOURNEY || state.screen == Screen.STATUS)
                            "${state.screen.name}:${state.active?.save?.id}" else state.screen.name
                        screenState.SaveableStateProvider(key) {
                            when (state.screen) {
                                Screen.HOME -> Home(state, model)
                                Screen.CREATE -> Creator(state, model)
                                Screen.JOURNEY -> Journey(state, model)
                                Screen.STATUS -> CharacterStatus(state, model)
                                Screen.SAVES -> SaveManager(state, model,
                                    onImport = { importPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                                    onExport = { slot ->
                                        exportId = slot.id
                                        val safeName = slot.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(60)
                                        exportPicker.launch("$safeName.xianxia.json")
                                    })
                                Screen.SETTINGS -> Settings(state, model)
                            }
                        }
                    }
                }
                if (state.busy) {
                    Box(Modifier.matchParentSize().testTag("operation_in_progress").background(Ink.copy(alpha = .78f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(); Text("Safeguarding your journey…")
                        }
                    }
                }
            }
        }
        state.error?.let { error ->
            AlertDialog(onDismissRequest = model::dismissMessage, title = { Text("Could not complete action") },
                text = { Text(error) }, confirmButton = { TextButton(onClick = model::dismissMessage) { Text("OK") } })
        }
        if (state.backupSlot != null) {
            AlertDialog(onDismissRequest = model::closeBackups, title = { Text("Restore a revision") },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Restoring replaces this slot and first backs up its current state. Other campaigns are unaffected.")
                        if (state.backups.isEmpty()) Text("No backups yet. A backup is created before each overwrite.")
                        state.backups.forEach { backup ->
                            OutlinedButton(onClick = { model.restore(backup) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Column { Text("Revision ${backup.revision} · ${backup.reason}"); Text(date(backup.createdAt), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = model::closeBackups) { Text("Close") } })
        }
    }
}

@Composable
private fun Home(state: GameUiState, model: GameViewModel) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Text("THE JADE EXPANSE", color = Jade, letterSpacing = 3.sp, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(20.dp))
            Text("A thousand paths.\nOne immortal life.", fontFamily = FontFamily.Serif, fontSize = 38.sp, lineHeight = 44.sp, color = Gold)
            Spacer(Modifier.height(16.dp))
            Text("Give your cultivator a name, a past, and a world to call their own.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Panel {
                Text("YOUR NEXT CHAPTER", color = Jade, style = MaterialTheme.typography.labelMedium)
                val latest = state.slots.find { it.id == state.preferences.lastSlot } ?: state.slots.firstOrNull()
                Text(state.active?.save?.name ?: latest?.name ?: "The path is unwritten", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
                if (latest != null) Text("${latest.playerName} · ${latest.realmName} ${latest.substage}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = model::continueGame, enabled = state.active != null || state.slots.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Continue journey") }
                OutlinedButton(onClick = { model.navigate(Screen.CREATE) }, modifier = Modifier.fillMaxWidth()) { Text("Begin a new life") }
                TextButton(onClick = { model.navigate(Screen.SAVES) }, modifier = Modifier.fillMaxWidth()) { Text("Manage campaigns · ${state.slots.size}") }
            }
        }
        item {
            Text("OFFLINE-FIRST  /  YOUR WORLD, YOUR SAVE", color = Gold, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Phase 1 foundation: character creation, character records, journal, and portable saves. Cultivation actions, combat, exploration, and AI storytelling are not implemented yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Creator(state: GameUiState, model: GameViewModel) {
    val o = state.draft
    var saveName by rememberSaveable { mutableStateOf("My path to immortality") }
    var ageText by rememberSaveable(state.seed) { mutableStateOf(o.age.toString()) }
    val preview = remember(o, state.seed) { runCatching { CharacterGenerator.generate(o, state.seed, "preview").character }.getOrNull() }
    LazyColumn(modifier = Modifier.testTag("creator"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("01 / A NEW LIFE", "Who will you become?", "Choose your history. Your attributes and rare traits are generated by the engine, not by an AI.") }
        item { Button(onClick = model::randomize, modifier = Modifier.fillMaxWidth()) { Text("Randomize character & potential") } }
        item {
            Text("Background presets", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CreationCatalog.backgrounds) { background -> FilterChip(selected = o.background == background, onClick = { model.preset(background) }, label = { Text(background) }) }
            }
        }
        item { Field("Campaign name", saveName, 80) { saveName = it } }
        item { Field("Character name", o.name, 60) { model.draft(o.copy(name = it)) } }
        item { Field("Gender (customizable)", o.gender, 80) { model.draft(o.copy(gender = it)) } }
        item {
            OutlinedTextField(value = ageText, onValueChange = { value ->
                if (value.length <= 3 && value.all(Char::isDigit)) {
                    ageText = value; model.draft(o.copy(age = value.toIntOrNull() ?: 0))
                }
            }, label = { Text("Starting age · 16–100") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = o.age !in 16..100, modifier = Modifier.fillMaxWidth())
        }
        item { Choice("Race preset", o.race, CreationCatalog.races) { model.draft(o.copy(race = it)) } }
        item { Field("Race or custom species", o.race, 80) { model.draft(o.copy(race = it)) } }
        item { Field("Appearance", o.appearance, 500, singleLine = false) { model.draft(o.copy(appearance = it)) } }
        item { Field("Birthplace", o.birthplace, 120) { model.draft(o.copy(birthplace = it)) } }
        item { Field("Background", o.background, 500) { model.draft(o.copy(background = it)) } }
        item { Choice("Personality preset", o.personality, CreationCatalog.personalities) { model.draft(o.copy(personality = it)) } }
        item { Field("Personality", o.personality, 500) { model.draft(o.copy(personality = it)) } }
        item { Choice("Spiritual root", o.root ?: "Generated", listOf("Generated") + CreationCatalog.roots) { model.draft(o.copy(root = it.takeUnless { it == "Generated" })) } }
        item { Choice("Starting location", o.location, CreationCatalog.locations) { model.draft(o.copy(location = it)) } }
        item { Field("Starting circumstances", o.circumstances, 500, singleLine = false) { model.draft(o.copy(circumstances = it)) } }
        item { Choice("Difficulty", o.difficulty, CreationCatalog.difficulties) { model.draft(o.copy(difficulty = it)) } }
        item {
            Panel {
                Text("YOUR INNATE FOUNDATION", color = Gold, style = MaterialTheme.typography.labelLarge)
                if (preview != null) {
                    Text("${preview.potential} potential · ${preview.spiritualRoot} root", style = MaterialTheme.typography.titleMedium)
                    Text("${preview.constitution}\n${preview.bloodline}")
                    Text("Talent ${preview.attributes.talent} · Comprehension ${preview.attributes.comprehension} · Vitality ${preview.attributes.vitality}")
                    Text("Qi Refining · Stage 1 · ${preview.cultivation.maximumLifespan} years lifespan", color = Jade)
                } else Text("Complete the fields above to preview your character.")
                Text("Normal starts share the same realm, but not the same potential. Difficulty is saved for later gameplay systems. Advanced sandbox creation belongs to Phase 5.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Button(onClick = { model.create(saveName) }, enabled = preview != null && saveName.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Begin journey & create save") }
            Text("Creates a separate campaign immediately. Existing saves are never replaced.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Journey(state: GameUiState, model: GameViewModel) {
    val s = state.active?.save ?: return
    var note by rememberSaveable(s.id) { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.testTag("journey"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Heading("DAY ${s.world.day} / ${s.world.name.uppercase()}", s.world.currentLocation, "${s.player.name} · ${s.realms.first { it.id == s.player.cultivation.realmId }.name} · Stage ${s.player.cultivation.substage}") }
        item {
            Panel {
                Text("THE BEGINNING", color = Gold, style = MaterialTheme.typography.labelLarge)
                Text(s.world.startingCircumstances, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleLarge)
                Text("This is your personal chronicle, not an AI chat. Journal entries do not change your stats or advance world time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::save) { Text(if (state.dirty) "Save changes" else "Save snapshot") }
                    OutlinedButton(onClick = { model.navigate(Screen.STATUS) }) { Text("Inspect character") }
                    if (state.dirty) TextButton(onClick = { discard = true }) { Text("Discard changes") }
                }
            }
        }
        item {
            Text("Write in your chronicle", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(note, { if (it.length <= 2000) note = it }, label = { Text("Plans, promises, and reflections") }, minLines = 3, maxLines = 7, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${note.length}/2000", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { model.addJournal(note); note = "" }, enabled = note.isNotBlank() && state.preferencesReady) { Text("Add entry") }
            }
        }
        item { Text("YOUR CHRONICLE", color = Gold, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) }
        items(s.world.journal.asReversed(), key = { it.id }) { entry ->
            Panel { Text("DAY ${entry.day}", color = Jade, style = MaterialTheme.typography.labelSmall); Text(entry.text, lineHeight = 24.sp) }
        }
        item { Text("The most recent 200 entries are kept. Export a snapshot to archive older chronicles.", style = MaterialTheme.typography.bodySmall) }
    }
    if (discard) ConfirmDialog("Discard unsaved changes?", "Reload the last stored revision of this campaign. This cannot be undone.", "Discard", { discard = false }) { discard = false; model.discard() }
}

@Composable
private fun CharacterStatus(state: GameUiState, model: GameViewModel) {
    val s = state.active?.save ?: return
    val p = s.player
    var rename by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Heading("CHARACTER RECORD", p.name, "${p.race} · ${p.gender} · Age ${p.age}")
            TextButton(onClick = { rename = true }) { Text("Change name") }
        }
        item { Panel {
            Text(p.appearance, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleLarge)
            Detail("Birthplace", p.birthplace); Detail("Background", p.background); Detail("Personality", p.personality)
        } }
        item { Panel {
            Text("CULTIVATION", color = Gold, style = MaterialTheme.typography.labelLarge)
            Text("${s.realms.first { it.id == p.cultivation.realmId }.name} · Stage ${p.cultivation.substage}", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = { (p.cultivation.progress / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
            Detail("Progress", "${p.cultivation.progress}%"); Detail("Method", p.cultivation.method)
            Detail("Lifespan", "${p.cultivation.maximumLifespan} years"); Detail("Health", "${p.health} / ${p.maxHealth}")
            Detail("Spiritual energy", "${p.energy} / ${p.maxEnergy}")
            Text("Cultivation and breakthrough actions arrive in Phase 2.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { Panel {
            Text("INNATE FOUNDATION", color = Gold, style = MaterialTheme.typography.labelLarge)
            Detail("Spiritual root", p.spiritualRoot); Detail("Constitution", p.constitution); Detail("Bloodline", p.bloodline)
            Detail("Potential", p.potential); Detail("Talents", p.talents.joinToString().ifEmpty { "None" })
            // Hidden traits persist, but are intentionally not exposed in normal character inspection.
        } }
        item { Text("ATTRIBUTES", color = Gold, style = MaterialTheme.typography.labelLarge) }
        item { Panel { p.attributes.entries().forEach { (label, value) -> Detail(label, value.toString()) } } }
        item { Text("TECHNIQUES", color = Gold, style = MaterialTheme.typography.labelLarge) }
        items(p.techniques, key = { it.id }) { technique -> Panel {
            Text(technique.name, style = MaterialTheme.typography.titleMedium); Text(technique.description)
            Detail("Rank / mastery", "${technique.rank} / ${technique.mastery}%")
            Detail("Energy cost / power", "${technique.energyCost} / ${technique.power}")
            Text("Recorded for Phase 2 combat; not usable yet.", style = MaterialTheme.typography.bodySmall)
        } }
        item { Text("INVENTORY · ${p.spiritStones} SPIRIT STONES", color = Gold, style = MaterialTheme.typography.labelLarge) }
        items(p.inventory, key = { it.id }) { item -> Panel {
            Text("${item.name} ×${item.quantity}", style = MaterialTheme.typography.titleMedium); Text(item.description)
            Detail("Rank", item.rank); Detail("Equipment slot", item.equippedSlot ?: "Unequipped"); Detail("Power", item.power.toString())
        } }
    }
    if (rename) TextEditDialog("Change character name", p.name, 60, { rename = false }) { rename = false; model.renameCharacter(it) }
}

@Composable
private fun SaveManager(state: GameUiState, model: GameViewModel, onImport: () -> Unit, onExport: (SlotEntity) -> Unit) {
    var rename by remember { mutableStateOf<SlotEntity?>(null) }
    var delete by remember { mutableStateOf<SlotEntity?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("THE ARCHIVE", "Every life, preserved.", "Independent local campaigns. Export a portable .xianxia.json file to keep a copy outside this app.") }
        item { OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import a save into a new slot") } }
        item { Text("Exports contain the stored revision. Save pending changes in Journey first. Deleting the app also deletes local saves and backups.", style = MaterialTheme.typography.bodySmall, color = Gold) }
        if (state.slots.isEmpty()) item { Panel {
            Text("No campaigns yet.", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { model.navigate(Screen.CREATE) }) { Text("Create your first character") }
        } }
        items(state.slots, key = { it.id }) { slot ->
            Panel {
                if (state.active?.save?.id == slot.id) Text("ACTIVE CAMPAIGN", style = MaterialTheme.typography.labelSmall, color = Jade)
                Text(slot.name, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                Text("${slot.playerName} · ${slot.race}")
                Text("${slot.realmName} ${slot.substage} · Day ${slot.day}", color = Gold)
                Text("Revision ${slot.revision} · ${date(slot.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.load(slot.id) }) { Text("Load") }
                    OutlinedButton(onClick = { onExport(slot) }) { Text("Export") }
                    OutlinedButton(onClick = { model.duplicate(slot) }) { Text("Duplicate") }
                    TextButton(onClick = { rename = slot }) { Text("Rename") }
                    TextButton(onClick = { model.showBackups(slot) }) { Text("Backups") }
                    TextButton(onClick = { delete = slot }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    rename?.let { slot -> TextEditDialog("Rename campaign", slot.name, 80, { rename = null }) { rename = null; model.renameSlot(slot, it) } }
    delete?.let { slot -> ConfirmDialog("Delete ${slot.name}?", "This permanently deletes the campaign and all five local backup revisions. Export first if you want to keep it.", "Delete", { delete = null }) { delete = null; model.delete(slot) } }
}

@Composable
private fun Settings(state: GameUiState, model: GameViewModel) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Heading("PREFERENCES", "A world that stays yours.", "Local gameplay data never depends on an AI provider or a conversation history.") }
        item { Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Automatic saving", style = MaterialTheme.typography.titleMedium); Text("Persist each journal entry and name change.", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = state.preferences.autosave, onCheckedChange = model::setAutosave, enabled = state.preferencesReady)
            }
            Text("New campaigns are always saved immediately. With autosave off, changes remain only in memory until you save. Enabling it saves any pending changes.")
        } }
        item { Panel {
            Text("SAVE PROTECTION", color = Gold, style = MaterialTheme.typography.labelLarge)
            Text("Every overwrite is transactional and retains a previous revision. The latest five backups per campaign are kept on this device.")
            Text("Imports are validated before database writes and always create a separate slot. Unsupported versions and invalid data are rejected.")
            TextButton(onClick = { model.navigate(Screen.SAVES) }) { Text("Open save manager") }
        } }
        item { Panel {
            Text("BUILD SCOPE · PHASE 1", color = Jade, style = MaterialTheme.typography.labelLarge)
            Text("Character generation and inspection, journal editing, multiple campaigns, manual/autosaving, backups, and JSON portability.")
            Text("AI providers, combat, world simulation, and the God Mode editor are planned modules, not active features in this build. Difficulty and cultivation rules are stored but not simulated yet.")
            Text("No network permission is requested. No API keys or personal information are transmitted.", color = Gold)
        } }
    }
}

@Composable
private fun Heading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(eyebrow, color = Jade, letterSpacing = 2.sp, style = MaterialTheme.typography.labelSmall)
        Text(title, fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp, color = Gold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1.2f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
private fun Field(label: String, value: String, max: Int, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= max) onChange(it) }, label = { Text(label) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2, modifier = Modifier.fillMaxWidth())
}
@Composable
private fun Choice(label: String, current: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelSmall); Text(current) }; Text("⌄")
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) { items(options) { option -> TextButton(onClick = { onChange(option); open = false }, modifier = Modifier.fillMaxWidth()) { Text(option) } } }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
}
@Composable
private fun TextEditDialog(title: String, initial: String, max: Int, dismiss: () -> Unit, save: (String) -> Unit) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Field("Name", text, max) { text = it } },
        confirmButton = { TextButton(onClick = { save(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
@Composable
private fun ConfirmDialog(title: String, body: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = confirm) { Text(action) } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
private fun date(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
