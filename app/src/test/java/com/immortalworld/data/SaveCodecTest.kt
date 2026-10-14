package com.immortalworld.data

import com.immortalworld.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SaveCodecTest {
    private fun sample(): GameSave {
        val g = CharacterGenerator.generate(CreationOptions(name = "岚月 🐉"), Long.MIN_VALUE, "player-1")
        return GameSave(id = "save-1", name = "A jade path", createdAt = 1, updatedAt = 2, randomState = g.randomState,
            player = g.character.copy(hiddenTraits = listOf("Ancient memory")),
            world = WorldState("world-1", currentLocation = "Jade River", startingCircumstances = "A beginning.",
                journal = listOf(JournalEntry("entry-1", 1, "A promise: \"return\".\nWith a new line."))))
    }
    private fun rejects(json: String) { assertThrows(IllegalArgumentException::class.java) { SaveCodec.decode(json) } }
    @Test fun completeSnapshotRoundTrips() { val s = sample(); assertEquals(s, SaveCodec.decode(SaveCodec.encode(s))) }
    @Test fun editedSavePreservesGodModeAndLargeAttributes() {
        val s = sample().let { it.copy(rules = it.rules.copy(godMode = true), player = it.player.copy(attributes = it.player.attributes.copy(strength = 1_000_000))) }
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }
    @Test fun rejectsInvalidJSON() { listOf("", "{", "not json", "null", "[]", "{\"player\": {}}").forEach(::rejects) }
    @Test fun refusesUnknownFieldsWithoutSilentDataLoss() {
        rejects(JSONObject(SaveCodec.encode(sample())).put("npcs", "unsupported v1 content").toString())
    }
    @Test fun refusesNonJsonSyntaxAcceptedByLenientAndroidParser() {
        rejects("{'format':'xianxia-save'}")
        rejects("{/* comment */}")
    }
    @Test fun refusesFutureVersions() { val json = JSONObject(SaveCodec.encode(sample())).put("version", 999); rejects(json.toString()) }
    @Test fun refusesMissingRequiredFields() { val json = JSONObject(SaveCodec.encode(sample())); json.remove("randomState"); rejects(json.toString()) }
    @Test fun refusesStringCoercionForNumbers() {
        val json = JSONObject(SaveCodec.encode(sample())); json.getJSONObject("player").put("health", "120"); rejects(json.toString())
    }
    @Test fun refusesFractionalIntegers() {
        val json = JSONObject(SaveCodec.encode(sample())); json.getJSONObject("player").put("age", 18.5); rejects(json.toString())
    }
    @Test fun refusesTruncatedIntegers() {
        val json = JSONObject(SaveCodec.encode(sample())); json.getJSONObject("player").put("age", 4294967314L); rejects(json.toString())
    }
    @Test fun refusesStringBooleans() {
        val json = JSONObject(SaveCodec.encode(sample())); json.getJSONObject("rules").put("godMode", "false"); rejects(json.toString())
    }
    @Test fun refusesInvalidStacks() {
        val json = JSONObject(SaveCodec.encode(sample())); json.getJSONObject("player").getJSONArray("inventory").getJSONObject(0).put("quantity", 100); rejects(json.toString())
    }
    @Test fun refusesTrailingPayload() { rejects(SaveCodec.encode(sample()) + " {}") }
    @Test fun refusesDeepNestingBeforeParser() { rejects("[".repeat(10000) + "]".repeat(10000)) }
    @Test fun refusesOversizedFiles() { rejects(" ".repeat(SaveValidator.MAX_BYTES + 1)) }
    @Test fun bracketsInsideStringsDoNotCountAsNesting() {
        val s = sample().copy(name = "[{{ an ordinary name }}]")
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }
    @Test fun recordsAndEquipmentRoundTrip() {
        val s = sample().let { it.copy(player = it.player.copy(cultivation = it.player.cultivation.copy(
            breakthroughs = listOf("First awakening"), tribulations = listOf("Lightning scar")) )) }
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }
    @Test fun handlesLongRandomStateExtremes() {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L).forEach { value ->
            val s = sample().copy(randomState = value); assertEquals(value, SaveCodec.decode(SaveCodec.encode(s)).randomState)
        }
    }
    @Test fun versionOneMigratesWithoutReplacingExistingPlayerOrJournal() {
        val original = sample()
        val json = JSONObject(SaveCodec.encode(original)).put("version", 1).apply { remove("adventure") }.toString()
        val migrated = SaveCodec.decode(json)
        assertEquals(3, migrated.version)
        assertEquals(original.player, migrated.player); assertEquals(original.world, migrated.world)
        assertEquals(original.randomState, migrated.randomState); assertNull(migrated.adventure)
        val initialized = AdventureFactory.ensure(migrated)
        assertEquals(original.player, initialized.player)
        assertEquals(initialized, SaveCodec.decode(SaveCodec.encode(initialized)))
    }
    @Test fun activeEncounterAndActionReceiptsRoundTripExactly() {
        val engine = AdventureEngine()
        var s = AdventureFactory.ensure(sample().copy(world = sample().world.copy(currentLocation = "Azure Cloud Village")))
        s = engine.execute(s, GameAction(ActionKind.CHALLENGE, "wen"), "Challenge Wen", "challenge").state
        s = engine.execute(s, GameAction(ActionKind.DEFEND), "Defend", "defend").state
        assertNotNull(s.adventure!!.combat)
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(s, restored)
        assertEquals(engine.apply(s, GameAction(ActionKind.ATTACK)), engine.apply(restored, GameAction(ActionKind.ATTACK)))
    }
    @Test fun invalidAdventureReferencesAreRejected() {
        val s = AdventureFactory.ensure(sample())
        val root = JSONObject(SaveCodec.encode(s))
        root.getJSONObject("adventure").put("locationId", "does-not-exist")
        rejects(root.toString())
    }
    @Test fun versionTwoRequiresItsAdventureField() {
        val json = JSONObject(SaveCodec.encode(sample())).put("version", 2).apply { remove("adventure") }
        rejects(json.toString())
    }

    private fun versionTwo(s: GameSave): JSONObject = JSONObject(SaveCodec.encode(s)).put("version", 2).apply {
        getJSONObject("adventure").apply {
            remove("visits")
            val history = getJSONArray("history")
            for (i in 0 until history.length()) listOf("kind", "locationId", "story", "dialogue", "sceneContext").forEach { history.getJSONObject(i).remove(it) }
        }
    }
    @Test fun versionTwoMigrationPreservesMechanicsAndOriginalReceipts() {
        val base = sample().let { it.copy(world = it.world.copy(currentLocation = "Azure Cloud Village")) }
        val old = AdventureEngine().execute(base, GameAction(ActionKind.TALK, "mei", "Ask Mei about Moonfall"), "Ask Mei about Moonfall", "talk").state
        val migrated = SaveCodec.decode(versionTwo(old).toString())
        assertEquals(3, migrated.version)
        assertEquals(old.player, migrated.player); assertEquals(old.world, migrated.world); assertEquals(old.rules, migrated.rules)
        assertEquals(old.randomState, migrated.randomState); assertEquals(old.adventure!!.npcs, migrated.adventure!!.npcs)
        assertEquals(old.adventure!!.quests, migrated.adventure!!.quests); assertEquals(old.adventure!!.events, migrated.adventure!!.events)
        val receipt = migrated.adventure!!.history.single()
        assertEquals(old.adventure!!.history.single().facts, receipt.facts); assertEquals("", receipt.story); assertEquals("", receipt.sceneContext)
        assertEquals(mapOf("village" to 1), migrated.adventure!!.visits)
        assertEquals(migrated, SaveCodec.decode(SaveCodec.encode(migrated)))
    }
    @Test fun versionTwoOnlyUpgradesExactStockDescriptions() {
        val old = versionTwo(AdventureFactory.ensure(sample()))
        val locations = old.getJSONObject("adventure").getJSONArray("locations")
        locations.getJSONObject(0).put("description", "A herbalist's stall faces a sect noticeboard. Mei tends her stock; Elder Wen receives applicants.")
        locations.getJSONObject(1).put("description", "Custom crossing observatory")
        val migrated = SaveCodec.decode(old.toString())
        assertTrue(migrated.adventure!!.locations[0].description.contains("Timber shopfronts"))
        assertEquals("Custom crossing observatory", migrated.adventure!!.locations[1].description)
    }
    @Test fun v3RequiresStoryFieldsWithoutCoercionOrDroppingUnknowns() {
        val s = AdventureEngine().execute(AdventureFactory.ensure(sample()), GameAction(ActionKind.OBSERVE), "Observe", "look").state
        for (key in listOf("kind", "locationId", "story", "dialogue", "sceneContext")) {
            val root = JSONObject(SaveCodec.encode(s)); root.getJSONObject("adventure").getJSONArray("history").getJSONObject(0).remove(key)
            rejects(root.toString())
        }
        val root = JSONObject(SaveCodec.encode(s)); root.getJSONObject("adventure").getJSONArray("history").getJSONObject(0).put("story", 9)
        rejects(root.toString())
        val v2 = versionTwo(s); v2.getJSONObject("adventure").put("visits", JSONObject())
        rejects(v2.toString())
    }
    @Test fun rejectsInvalidVisitsAndOversizedStoryFields() {
        val s = AdventureEngine().execute(AdventureFactory.ensure(sample()), GameAction(ActionKind.OBSERVE), "Observe", "look").state
        for ((field, size) in listOf("story" to 5001, "dialogue" to 1501, "sceneContext" to 3501, "narration" to 6001)) {
            val root = JSONObject(SaveCodec.encode(s)); root.getJSONObject("adventure").getJSONArray("history").getJSONObject(0).put(field, "x".repeat(size)); rejects(root.toString())
        }
        for ((id, count) in listOf("origin" to -1, "unknown-place" to 1, "origin" to 0)) {
            val root = JSONObject(SaveCodec.encode(s)); root.getJSONObject("adventure").put("visits", JSONObject().put(id, count)); rejects(root.toString())
        }
    }
    @Test fun longerNarrationAndObservedSnapshotsRoundTrip() {
        val s = AdventureEngine().execute(AdventureFactory.ensure(sample()), GameAction(ActionKind.OBSERVE), "Observe", "look").state
        val a = s.adventure!!
        val enriched = s.copy(adventure = a.copy(history = a.history.map { it.copy(narration = "A quiet road. ".repeat(400)) }))
        assertEquals(enriched, SaveCodec.decode(SaveCodec.encode(enriched)))
    }

}
