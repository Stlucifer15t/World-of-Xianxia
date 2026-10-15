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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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

private val Palette = darkColorScheme(
    primary = Jade, onPrimary = Color(0xFF0B372D), secondary = Gold,
    primaryContainer = Color(0xFF234C3E), onPrimaryContainer = Jade,
    secondaryContainer = Color(0xFF423923), onSecondaryContainer = Gold,
    outlineVariant = Color(0xFF30433B),
    background = Ink, surface = Color(0xFF17282B), surfaceVariant = Color(0xFF23373A),
    onBackground = Color(0xFFE5EBE6), onSurface = Color(0xFFE5EBE6),
    onSurfaceVariant = Color(0xFFACBFB9), outline = Color(0xFF536961),
)

@Composable
fun ImmortalWorldApp(model: GameViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val screenState = rememberSaveableStateHolder()
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    fun goBack() {
        if (state.screen == Screen.SETTINGS && settingsPage != null) settingsPage = null
        else model.navigate(if (state.active != null && state.screen !in listOf(Screen.CREATE, Screen.JOURNEY)) Screen.JOURNEY else Screen.HOME)
    }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importSave) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportId
        if (uri != null && id != null) model.exportSave(uri, id)
        exportId = null
    }
    MaterialTheme(colorScheme = Palette, typography = Typography().let { t ->
        t.copy(bodyLarge = t.bodyLarge.copy(fontFamily = RecordFont), bodyMedium = t.bodyMedium.copy(fontFamily = RecordFont),
            bodySmall = t.bodySmall.copy(fontFamily = RecordFont), titleLarge = t.titleLarge.copy(fontFamily = ScrollFont),
            titleMedium = t.titleMedium.copy(fontFamily = RecordFont), titleSmall = t.titleSmall.copy(fontFamily = RecordFont),
            labelLarge = t.labelLarge.copy(fontFamily = RecordFont), labelMedium = t.labelMedium.copy(fontFamily = RecordFont),
            labelSmall = t.labelSmall.copy(fontFamily = RecordFont), headlineSmall = t.headlineSmall.copy(fontFamily = ScrollFont))
    }) {
        BackHandler(enabled = state.screen != Screen.HOME) {
            goBack()
        }
        Scaffold(
            topBar = {
                if (state.screen != Screen.JOURNEY) CenterAlignedTopAppBar(
                    title = { Text(when (state.screen) {
                        Screen.HOME -> "Immortal World"
                        Screen.CREATE -> "New life"
                        Screen.CULTIVATION -> "Inner world"
                        Screen.STATUS -> "Cultivator"
                        Screen.SETTINGS -> if (settingsPage == "ai") "Provider & model" else "Settings"
                        else -> state.screen.name.lowercase().replaceFirstChar { it.uppercase() }
                    }, fontFamily = ScrollFont, fontSize = 17.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Gold) },
                    navigationIcon = { if (state.screen != Screen.HOME) TextButton(onClick = {
                        goBack()
                    }, enabled = !state.busy) { Text("Back") } },
                    actions = { if (state.screen != Screen.SETTINGS) TextButton(onClick = { model.navigate(Screen.SETTINGS) }, enabled = !state.busy) { Text("Settings") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Ink),
                )
            },
            bottomBar = {
                if (state.active != null && state.screen !in listOf(Screen.HOME, Screen.CREATE, Screen.SETTINGS, Screen.JOURNEY) && WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                    NavigationBar(containerColor = Ink) {
                        listOf(Triple(Screen.JOURNEY, "Journey", "◇"), Triple(Screen.STATUS, "Character", "△"), Triple(Screen.SAVES, "Saves", "▤")).forEach { (screen, label, symbol) ->
                            NavigationBarItem(modifier = Modifier.testTag("nav_${screen.name.lowercase()}"), selected = state.screen == screen, onClick = { model.navigate(screen) },
                                enabled = !state.busy || state.aiWorking, icon = { Text(symbol, fontSize = 24.sp) }, label = { Text(label) })
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                Column(Modifier.fillMaxSize()) {
                    if (state.dirty) {
                        Surface(color = Color(0xFF4F3B1D), modifier = Modifier.fillMaxWidth()) {
                            Text("Unsaved changes · Save in Journal before closing, or submit an adventure action to commit all pending changes.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
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
                    if (state.aiWorking || state.aiNotice != null) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(if (state.knowledgeWorking) "Looking up optional cultivation references. You can cancel; saved mechanics are safe." else if (state.aiWorking) "AI request in progress. You can inspect records or cancel." else state.aiNotice.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                if (state.aiWorking) TextButton(onClick = model::cancelAi) { Text("Cancel AI request") }
                                else if (state.aiConfig.enabled && state.active?.save?.adventure?.history?.isNotEmpty() == true)
                                    TextButton(onClick = model::retryNarration, enabled = !state.busy) { Text("Retry narration only") }
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        val key = if (state.active != null && state.screen !in listOf(Screen.HOME, Screen.CREATE, Screen.SETTINGS, Screen.SAVES))
                            "${state.screen.name}:${state.active?.save?.id}" else state.screen.name
                        screenState.SaveableStateProvider(key) {
                            when (state.screen) {
                                Screen.HOME -> Home(state, model)
                                Screen.CREATE -> Creator(state, model)
                                Screen.JOURNEY -> AdventureScreen(state, model)
                                Screen.JOURNAL -> Journal(state, model)
                                Screen.CULTIVATION -> CultivationScreen(state, model)
                                Screen.INVENTORY, Screen.TECHNIQUES, Screen.QUESTS, Screen.MAP, Screen.NPCS, Screen.EVENTS -> AdventureLibrary(state, model)
                                Screen.STATUS -> CharacterStatus(state, model)
                                Screen.SAVES -> SaveManager(state, model,
                                    onImport = { importPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                                    onExport = { slot ->
                                        exportId = slot.id
                                        val safeName = slot.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(60)
                                        exportPicker.launch("$safeName.xianxia.json")
                                    })
                                Screen.SETTINGS -> Settings(state, model, settingsPage) { settingsPage = it }
                            }
                        }
                    }
                }
                if (state.busy && !state.aiWorking) {
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
            Text("A thousand paths.\nOne immortal life.", fontFamily = ScrollFont, fontSize = 38.sp, lineHeight = 44.sp, color = Gold)
            Spacer(Modifier.height(16.dp))
            Text("Give your cultivator a name, a past, and a world to call their own.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Panel {
                Text("YOUR NEXT CHAPTER", color = Jade, style = MaterialTheme.typography.labelMedium)
                val latest = state.slots.find { it.id == state.preferences.lastSlot } ?: state.slots.firstOrNull()
                Text(state.active?.save?.name ?: latest?.name ?: "The path is unwritten", style = MaterialTheme.typography.headlineSmall, fontFamily = ScrollFont)
                if (latest != null) Text("${latest.playerName} · ${latest.realmName} ${latest.substage}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = model::continueGame, enabled = state.active != null || state.slots.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Continue journey") }
                OutlinedButton(onClick = { model.navigate(Screen.CREATE) }, modifier = Modifier.fillMaxWidth()) { Text("Begin a new life") }
                TextButton(onClick = { model.navigate(Screen.SAVES) }, modifier = Modifier.fillMaxWidth()) { Text("Manage campaigns · ${state.slots.size}") }
            }
        }
        item {
            Text("OFFLINE-FIRST  /  YOUR WORLD, YOUR SAVE", color = Gold, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Active adventure: type your intentions, explore, speak, trade, and fight. Optional cultivation advances in-game hours instantly. The engine owns outcomes; AI narration is optional.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        item { Heading("THE FIRST ASCENT", "Who will you become?", "A mortal beginning. A spiritual root. A path beyond the heavens.") }
        item { Panel {
            Text("YOUR CULTIVATION FOUNDATION", color = Gold, style = MaterialTheme.typography.labelMedium)
            if (preview != null) {
                Text("${preview.spiritualRoot} root · ${preview.potential}", fontFamily = ScrollFont, style = MaterialTheme.typography.titleLarge)
                Text(preview.constitution, color = Jade)
                Text("${preview.bloodline} · Qi Refining, Stage 1", style = MaterialTheme.typography.bodySmall)
            } else Text("Complete your identity to reveal the foundation.")
            Text("Generated once from this character's seed. Opening a section never rerolls your traits.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = model::randomize, modifier = Modifier.fillMaxWidth()) { Text("Randomize character & potential") }
        } }
        item { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("Campaign name", saveName, 80) { saveName = it }
            Field("Character name", o.name, 60) { model.draft(o.copy(name = it)) }
        } }
        item { RecordSection("Identity and Appearance", "${o.race} · Age ${o.age} · ${o.gender}", "◇") {
            Field("Gender (customizable)", o.gender, 80) { model.draft(o.copy(gender = it)) }
            OutlinedTextField(value = ageText, onValueChange = { value ->
                if (value.length <= 3 && value.all(Char::isDigit)) { ageText = value; model.draft(o.copy(age = value.toIntOrNull() ?: 0)) }
            }, label = { Text("Starting age · 16–100") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = o.age !in 16..100, modifier = Modifier.fillMaxWidth())
            Field("Appearance", o.appearance, 500, singleLine = false) { model.draft(o.copy(appearance = it)) }
            Choice("Personality preset", o.personality, CreationCatalog.personalities) { model.draft(o.copy(personality = it)) }
            Field("Personality", o.personality, 500) { model.draft(o.copy(personality = it)) }
        } }
        item { RecordSection("Spiritual Root and Constitution", "${preview?.spiritualRoot ?: "Generated"} · ${preview?.constitution ?: "Awaiting identity"}", "✧") {
            Choice("Spiritual root", o.root ?: "Generated", listOf("Generated") + CreationCatalog.roots) { model.draft(o.copy(root = it.takeUnless { it == "Generated" })) }
            Detail("Heavenly constitution", preview?.constitution ?: "Complete identity first")
            Detail("Potential", preview?.potential ?: "Generated")
            Text("Choose an element or let fate decide. Constitution and potential come from the existing generator, not a cosmetic bonus.", style = MaterialTheme.typography.bodySmall)
        } }
        item { RecordSection("Race and Bloodline", "${o.race} · ${preview?.bloodline ?: "Generated bloodline"}", "◈") {
            Choice("Race preset", o.race, CreationCatalog.races) { model.draft(o.copy(race = it)) }
            Field("Race or custom species", o.race, 80) { model.draft(o.copy(race = it)) }
            Detail("Bloodline", preview?.bloodline ?: "Complete identity first")
            Text("Bloodline is generated with this character. Custom species names do not grant extra powers.", style = MaterialTheme.typography.bodySmall)
        } }
        item { RecordSection("Innate Talents", preview?.potential ?: "Unrevealed potential", "☆") {
            preview?.let { p ->
                Detail("Cultivation aptitude", p.attributes.talent.toString())
                Detail("Comprehension", p.attributes.comprehension.toString())
                Detail("Vitality", p.attributes.vitality.toString())
                Detail("Innate talents", p.talents.joinToString().ifBlank { "None revealed" })
            }
            Text("These are your generated attributes. Hidden traits remain undiscovered.", style = MaterialTheme.typography.bodySmall)
        } }
        item { RecordSection("Background and Starting Scenario", "${o.background} · ${o.location}", "⌂") {
            CreationCatalog.backgrounds.forEachIndexed { index, background ->
                SectionCard(background, listOf("Herbs & enduring vitality", "Books & comprehension", "Lineage & spiritual power", "Mountains & a hunter's agility")[index], listOf("❧", "▤", "◈", "△")[index], onClick = { model.preset(background) })
            }
            Field("Birthplace", o.birthplace, 120) { model.draft(o.copy(birthplace = it)) }
            Field("Background", o.background, 500) { model.draft(o.copy(background = it)) }
            Choice("Starting location", o.location, CreationCatalog.locations) { model.draft(o.copy(location = it)) }
            Field("Starting circumstances", o.circumstances, 500, singleLine = false) { model.draft(o.copy(circumstances = it)) }
            Choice("Difficulty", o.difficulty, CreationCatalog.difficulties) { model.draft(o.copy(difficulty = it)) }
        } }
        item { RecordSection("Starting Cultivation and Abilities", "Qi Refining · Stage 1", "☯") {
            Text("Every normal journey begins at Qi Refining, Stage 1.", color = Gold)
            preview?.let { p ->
                Detail("Cultivation method", p.cultivation.method)
                Detail("Lifespan", "${p.cultivation.maximumLifespan} years")
                p.techniques.forEach { Text("${it.name} · ${it.rank}", fontFamily = ScrollFont) }
            }
            Text("Your method and arts are real starting abilities. Higher-realm creation is not available in normal play.", style = MaterialTheme.typography.bodySmall)
        } }
        item {
            Button(onClick = { model.create(saveName) }, enabled = preview != null && saveName.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Begin journey & create save") }
            Text("A new life, saved separately. Your other campaigns stay untouched.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Journal(state: GameUiState, model: GameViewModel) {
    val focus = LocalFocusManager.current
    val s = state.active?.save ?: return
    var note by rememberSaveable(s.id) { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.testTag("journal"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Heading("DAY ${s.world.day} / ${s.world.name.uppercase()}", s.world.currentLocation, "${s.player.name} · ${s.realms.first { it.id == s.player.cultivation.realmId }.name} · Stage ${s.player.cultivation.substage}") }
        item {
            Panel {
                Text("THE BEGINNING", color = Gold, style = MaterialTheme.typography.labelLarge)
                Text(s.world.startingCircumstances, fontFamily = ScrollFont, style = MaterialTheme.typography.titleLarge)
                Text("This is your personal chronicle, not an AI chat. Journal entries do not change your stats or advance world time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::save, enabled = !state.busy) { Text(if (state.dirty) "Save changes" else "Save snapshot") }
                    OutlinedButton(onClick = { model.navigate(Screen.STATUS) }) { Text("Inspect character") }
                    if (state.dirty) TextButton(onClick = { discard = true }, enabled = !state.busy) { Text("Discard changes") }
                }
            }
        }
        item {
            Text("Write in your chronicle", style = MaterialTheme.typography.titleLarge, fontFamily = ScrollFont)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(note, { if (it.length <= 2000) note = it }, label = { Text("Plans, promises, and reflections") }, minLines = 3, maxLines = 7, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${note.length}/2000", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { model.addJournal(note); note = ""; focus.clearFocus() }, enabled = note.isNotBlank() && state.preferencesReady && !state.busy) { Text("Add entry") }
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
            TextButton(onClick = { rename = true }, enabled = !state.busy) { Text("Change name") }
        }
        item { Panel {
            Text(p.appearance, fontFamily = ScrollFont, style = MaterialTheme.typography.titleLarge)
            Detail("Birthplace", p.birthplace); Detail("Background", p.background); Detail("Personality", p.personality)
        } }
        item { RealmTablet(s) }
        item { Panel {
            Detail("Health", "${p.health} / ${p.maxHealth}")
            Detail("Lifespan", "${p.cultivation.maximumLifespan} years")
            OutlinedButton(onClick = { model.navigate(Screen.CULTIVATION) }) { Text("Enter the inner world") }
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
            Text("Use this technique during an encounter from the Techniques screen or adventure input.", style = MaterialTheme.typography.bodySmall)
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
        item { OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Import a save into a new slot") } }
        item { Text("Exports contain the stored revision. Save pending changes in Journey first. Deleting the app also deletes local saves and backups.", style = MaterialTheme.typography.bodySmall, color = Gold) }
        if (state.slots.isEmpty()) item { Panel {
            Text("No campaigns yet.", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { model.navigate(Screen.CREATE) }) { Text("Create your first character") }
        } }
        items(state.slots, key = { it.id }) { slot ->
            Panel {
                if (state.active?.save?.id == slot.id) Text("ACTIVE CAMPAIGN", style = MaterialTheme.typography.labelSmall, color = Jade)
                Text(slot.name, style = MaterialTheme.typography.titleLarge, fontFamily = ScrollFont)
                Text("${slot.playerName} · ${slot.race}")
                Text("${slot.realmName} ${slot.substage} · Day ${slot.day}", color = Gold)
                Text("Revision ${slot.revision} · ${date(slot.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.load(slot.id) }, enabled = !state.busy) { Text("Load") }
                    OutlinedButton(onClick = { onExport(slot) }, enabled = !state.busy) { Text("Export") }
                    OutlinedButton(onClick = { model.duplicate(slot) }, enabled = !state.busy) { Text("Duplicate") }
                    TextButton(onClick = { rename = slot }, enabled = !state.busy) { Text("Rename") }
                    TextButton(onClick = { model.showBackups(slot) }, enabled = !state.busy) { Text("Backups") }
                    TextButton(onClick = { delete = slot }, enabled = !state.busy) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    rename?.let { slot -> TextEditDialog("Rename campaign", slot.name, 80, { rename = null }) { rename = null; model.renameSlot(slot, it) } }
    delete?.let { slot -> ConfirmDialog("Delete ${slot.name}?", "This permanently deletes the campaign and all five local backup revisions. Export first if you want to keep it.", "Delete", { delete = null }) { delete = null; model.delete(slot) } }
}

@Composable
private fun Settings(state: GameUiState, model: GameViewModel, page: String?, open: (String?) -> Unit) {
    LazyColumn(Modifier.testTag("settings"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (page == null) {
            item { Heading("THE JADE COMPANION", "Settings", "Make this journey your own.") }
            item { SectionCard("Provider & model", "AI service, API key and saved providers", "◇", "settings_ai") { open("ai") } }
            item { SectionCard("Saving & backups", "Autosave, campaign files and protection", "▤", "settings_saves") { open("saves") } }
            item { SectionCard("About this world", "Offline play, privacy and current scope", "☯", "settings_about") { open("about") } }
        } else {
            item { TextButton(onClick = { open(null) }, modifier = Modifier.testTag("settings_home")) { Text("‹ All settings") } }
            when (page) {
                "ai" -> {
                    item { AiSettingsPanel(state, model) }
                }
                "saves" -> {
                    item { Heading("PRESERVE YOUR PATH", "Saving & backups", "Your campaigns belong to you.") }
                    item { Panel {
                        SettingSwitch("Automatic journal saving", state.preferences.autosave, state.preferencesReady && !state.busy, model::setAutosave)
                        Text("Saves journal and name edits. Adventure actions always save immediately.")
                        Text("With autosave off, save journal changes before closing the app. Turning it back on saves pending edits.", style = MaterialTheme.typography.bodySmall)
                    } }
                    item { Panel {
                        Text("Five recent backups", style = MaterialTheme.typography.titleLarge)
                        Text("Each overwrite keeps the previous revision. Imports create a separate campaign.")
                        Text("Export before uninstalling. Removing the app deletes its local saves and provider keys.", color = Gold)
                        Button(onClick = { model.navigate(Screen.SAVES) }, modifier = Modifier.fillMaxWidth()) { Text("Open save manager") }
                    } }
                }
                "about" -> {
                    item { Heading("IMMORTAL WORLD", "A world that stays yours.", "A native, offline-first cultivation adventure.") }
                    item { Panel {
                        Text("Your choices. The world's rules.", style = MaterialTheme.typography.titleLarge)
                        Text("AI can tell the story, but cannot grant stats, choose your dialogue or change the game's rules. Your private journal never goes to the storyteller.")
                        Text("Cultivation, combat, travel, trade and saves work without AI. Web references are optional.")
                    } }
                    item { Panel {
                        Text("A growing world", style = MaterialTheme.typography.titleLarge)
                        Text("The current world is small and persistent. Large procedural worlds, advanced sect management and a full God Mode editor are still in development.")
                        Text("Typography: Noto Sans and Noto Serif, under the SIL Open Font License 1.1. License included in the app assets.", style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
        }
    }
}

@Composable
internal fun Heading(eyebrow: String, title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth()) {
      FormationSeal(Modifier.align(Alignment.TopEnd).size(116.dp))
      Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(eyebrow, color = Jade, letterSpacing = 2.sp, style = MaterialTheme.typography.labelSmall)
        Text(title, fontFamily = ScrollFont, fontSize = 30.sp, lineHeight = 39.sp, color = Gold, modifier = Modifier.padding(end = 24.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
      }
    }
}
@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
internal fun Detail(label: String, value: String) {
    if (value.length > 12) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
internal fun Field(label: String, value: String, max: Int, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= max) onChange(it) }, label = { Text(label) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2, modifier = Modifier.fillMaxWidth())
}
@Composable
internal fun Choice(label: String, current: String, options: List<String>, onChange: (String) -> Unit) {
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
