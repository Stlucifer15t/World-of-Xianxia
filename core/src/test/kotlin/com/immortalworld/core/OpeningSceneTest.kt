package com.immortalworld.core

import org.junit.Assert.*
import org.junit.Test

class OpeningSceneTest {
    @Test fun snapshotDoesNotExecuteAnActionOrRerollTheCharacter() {
        val before = AdventureFactory.ensure(CoreChecks.sample())
        val after = OpeningScene.attach(before)
        assertEquals(before, after.copy(adventure = after.adventure!!.copy(history = emptyList())))
        assertEquals(after, OpeningScene.attach(after))
        assertTrue(OpeningScene.isOpening(after.adventure!!.history.single()))
        assertTrue(after.adventure!!.history.single().narration.isEmpty())
    }
    @Test fun contextIncludesAllVisibleSelectionsButNoPrivateTraitsOrJournal() {
        val s = CoreChecks.sample().let { it.copy(
            player = it.player.copy(name = "Yue", appearance = "A worn jade clasp", background = "Exiled cartographer",
                personality = "Patient and curious", birthplace = "Reed Province", race = "Moon Fox", gender = "Agender", age = 23,
                hiddenTraits = listOf("PRIVATE_TRAIT")),
            world = it.world.copy(currentLocation = "Reed Harbour", startingCircumstances = "A missing map and a sealed ferry gate",
                journal = listOf(JournalEntry("private", 1, "PRIVATE_JOURNAL")))) }
        val opening = OpeningScene.attach(s)
        val record = opening.adventure!!.history.single()
        val context = OpeningScene.context(record)
        val all = context.toString()
        listOf("Yue", "A worn jade clasp", "Exiled cartographer", "Patient and curious", "Reed Province", "Moon Fox", "Agender", "23",
            "Reed Harbour", "A missing map and a sealed ferry gate", s.player.spiritualRoot, s.player.constitution,
            s.player.bloodline, s.player.potential, s.player.cultivation.method, s.rules.difficulty,
            s.player.techniques.first().name, s.player.inventory.first().name).forEach { assertTrue(it, all.contains(it)) }
        assertFalse(all.contains("PRIVATE_TRAIT")); assertFalse(all.contains("PRIVATE_JOURNAL"))
        opening.adventure!!.npcs.forEach { assertFalse(all.contains(it.goal)) }
        assertEquals("opening", context.pace); assertEquals(record.facts, context.authoritativeFacts)
        assertTrue(record.facts.size <= 20)
    }
    @Test fun regenerationContextUsesSavedOriginNotLaterStatsOrLocation() {
        val s = OpeningScene.attach(CoreChecks.sample())
        val record = s.adventure!!.history.single()
        val changed = s.copy(player = s.player.copy(name = "LATER_NAME", bloodline = "LATER_BLOODLINE"), world = s.world.copy(currentLocation = "Later land"))
        assertEquals(OpeningScene.context(record), OpeningScene.context(changed.adventure!!.history.single()))
        assertTrue(OpeningScene.isOpening(record.copy(id = record.id)))
        assertTrue(OpeningScene.isOpening(s.copy(id = "imported-copy").adventure!!.history.single()))
    }
    @Test fun progressedCampaignsNeverGetAnInventedBeginning() {
        val s = AdventureFactory.ensure(CoreChecks.sample())
        listOf(s.copy(world = s.world.copy(day = 2)), s.copy(adventure = s.adventure!!.copy(sequence = 1)),
            s.copy(adventure = s.adventure!!.copy(hour = 9)), s.copy(adventure = s.adventure!!.copy(simulationHour = 1)))
            .forEach { assertFalse(OpeningScene.canAttach(it)); assertEquals(it, OpeningScene.attach(it)) }
        val action = AdventureEngine { 1000L }.execute(s, GameAction(ActionKind.OBSERVE), "look", "look-1").state
        assertFalse(OpeningScene.canAttach(action)); assertEquals(action, OpeningScene.attach(action))
    }
    @Test fun boundedHistoryRetainsOpeningWithoutChangingEngineOutcomes() {
        var s = OpeningScene.attach(CoreChecks.sample())
        val origin = s.adventure!!.history.single()
        val engine = AdventureEngine { 1000L }
        repeat(105) { s = engine.execute(s, GameAction(ActionKind.OBSERVE), "look", "look-$it").state }
        assertEquals(100, s.adventure!!.history.size)
        assertEquals(origin, s.adventure!!.history.first())
        assertEquals("look-104", s.adventure!!.history.last().id)
        assertEquals(105L, s.adventure!!.sequence)
        SaveValidator.validate(s)
    }
}
