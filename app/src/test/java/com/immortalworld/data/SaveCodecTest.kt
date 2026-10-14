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
}
