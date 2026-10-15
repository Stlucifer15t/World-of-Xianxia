package com.immortalworld.core

import org.junit.Assert.*
import org.junit.Test

class WorldGeographyTest {
    private val engine = AdventureEngine { 1000L }
    private fun base() = AdventureFactory.ensure(CoreChecks.sample())
    private fun frontier() = WorldGeography.expand(base())
    @Test fun generationIsDeterministicBoundedConnectedAndDoesNotConsumePlayerRandomness() {
        val before = base(); val s = frontier(); val a = s.adventure!!
        assertEquals(s, WorldGeography.expand(before))
        assertEquals(before.player, s.player); assertEquals(before.world, s.world); assertEquals(before.randomState, s.randomState)
        assertEquals(before.adventure, a.copy(locations = before.adventure!!.locations, routes = before.adventure!!.routes, regionSeed = null))
        assertEquals(10, a.locations.size); assertEquals(9, a.routes.size); assertNotNull(a.regionSeed)
        val visited = mutableSetOf(a.locationId)
        repeat(a.locations.size) { visited.toList().forEach { visited.addAll(WorldGeography.neighbors(a, it)) } }
        assertEquals(a.locations.map { it.id }.toSet(), visited)
        assertEquals(6, a.locations.count { it.id.startsWith("frontier_") && !it.discovered })
        assertThrows(IllegalArgumentException::class.java) { WorldGeography.expand(s) }
    }
    @Test fun differentSeedsProduceDifferentButStableFrontiersAndSupportCustomStarts() {
        val variants = (1L..20L).map { WorldGeography.expand(CoreChecks.sample(it)).adventure!!.locations.takeLast(6) }
        assertTrue(variants.distinct().size > 15)
        val s = CoreChecks.sample().let { it.copy(world = it.world.copy(currentLocation = "My refuge")) }
        val g = WorldGeography.expand(s)
        assertEquals("My refuge", g.world.currentLocation)
        assertTrue(WorldGeography.neighbors(g.adventure!!, "origin").contains("village"))
    }
    @Test fun surveyRevealsOnlyAdjacentPathsAndTravelUsesSavedDurationBothWays() {
        val before = frontier()
        assertThrows(IllegalArgumentException::class.java) { engine.apply(before, GameAction(ActionKind.TRAVEL, "frontier_1")) }
        val survey = engine.apply(before, GameAction(ActionKind.INVESTIGATE)).state
        assertEquals(before.player, survey.player); assertEquals(before.randomState, survey.randomState)
        assertEquals("village", survey.adventure!!.locationId); assertEquals(9, survey.adventure!!.hour)
        assertTrue(survey.adventure!!.locations.first { it.id == "frontier_1" }.discovered)
        assertFalse(survey.adventure!!.locations.first { it.id == "frontier_2" }.discovered)
        val road = WorldGeography.route(survey.adventure!!, "village", "frontier_1")!!
        val travelled = engine.execute(survey, GameAction(ActionKind.TRAVEL, "frontier_1"), "Walk the trail", "travel").state
        assertEquals(9 + road.hours, travelled.adventure!!.hour)
        assertEquals(1, travelled.adventure!!.visits["frontier_1"])
        assertTrue(travelled.adventure!!.history.last().facts.any { it.contains("${road.hours} in-game hours on foot") })
        assertTrue(travelled.adventure!!.history.last().story.contains(road.description))
        val back = engine.apply(travelled, GameAction(ActionKind.TRAVEL, "village")).state
        assertEquals(9 + 2 * road.hours, back.adventure!!.hour)
        assertEquals(before.randomState, back.randomState)
        val otherKnown = survey.copy(adventure = survey.adventure!!.copy(locations = survey.adventure!!.locations.map { it.copy(discovered = true) }))
        assertThrows(IllegalArgumentException::class.java) { engine.apply(otherKnown, GameAction(ActionKind.TRAVEL, "frontier_6")) }
    }
    @Test fun materialTablesAreAuthoritativeFiniteAndDoNotCreateRngLoot() {
        val s = frontier(); val a = s.adventure!!
        for (type in listOf("herbs", "ore", "herbs_and_ore")) {
            var state = s.copy(adventure = a.copy(locations = a.locations.map { if (it.id == a.locationId) it.copy(forage = type) else it }))
            fun count(save: GameSave, kind: String) = save.player.inventory.filter { it.kind == kind }.sumOf { it.quantity }
            repeat(3) { state = engine.apply(state, GameAction(ActionKind.SEARCH)).state }
            assertEquals(if (type == "ore") 0 else 6, count(state, "herb") - count(s, "herb"))
            assertEquals(if (type == "herbs") 0 else 6, count(state, "material") - count(s, "material"))
            assertEquals(s.randomState, state.randomState)
            assertThrows(IllegalArgumentException::class.java) { engine.apply(state, GameAction(ActionKind.SEARCH)) }
        }
    }
    @Test fun expansionIsExplicitOneTimeAndDuplicateReceiptDoesNotApplyTwice() {
        val before = base()
        assertNull(AdventureFactory.ensure(before).adventure!!.regionSeed)
        val first = engine.execute(before, GameAction(ActionKind.EXPAND_WORLD), "Extend frontier", "expand")
        assertEquals(before.player, first.state.player); assertEquals(before.randomState, first.state.randomState)
        assertEquals(before.world, first.state.world); assertEquals(before.adventure!!.hour, first.state.adventure!!.hour)
        assertEquals(first.state, engine.execute(first.state, GameAction(ActionKind.EXPAND_WORLD), "Extend frontier", "expand").state)
        val rejected = engine.execute(first.state, GameAction(ActionKind.EXPAND_WORLD), "Extend again", "again")
        assertFalse(rejected.state.adventure!!.history.last().accepted)
        assertEquals(first.state.adventure!!.locations, rejected.state.adventure!!.locations)
        assertEquals(first.state.adventure!!.routes, rejected.state.adventure!!.routes)
    }
    @Test fun routeValidatorRejectsDanglingDuplicateLoopAndInvalidHours() {
        val s = frontier(); val a = s.adventure!!; val road = a.routes.first()
        listOf(road.copy(from = "missing"), road.copy(to = road.from), road.copy(hours = 0), road.copy(hours = 25), road.copy(description = "")).forEach {
            assertThrows(IllegalArgumentException::class.java) { SaveValidator.validate(s.copy(adventure = a.copy(routes = listOf(it)))) }
        }
        assertThrows(IllegalArgumentException::class.java) { SaveValidator.validate(s.copy(adventure = a.copy(routes = a.routes + road.copy(id = "other", from = road.to, to = road.from)))) }
        val collision = base().let { it.copy(adventure = it.adventure!!.copy(locations = it.adventure!!.locations + Location("frontier_1", "Custom place", "Keep this place"))) }
        assertThrows(IllegalArgumentException::class.java) { WorldGeography.expand(collision) }
    }
    @Test fun unknownFrontierNamesDoNotLeakIntoNarrativeOrProviderTargets() {
        val s = frontier(); val context = ContextBuilder.build(s, "Look around").toString()
        s.adventure!!.locations.filter { !it.discovered }.forEach { assertFalse(context.contains(it.name)) }
        val known = engine.apply(s, GameAction(ActionKind.INVESTIGATE)).state
        val contextAfter = ContextBuilder.build(known, "Where can I walk?")
        assertTrue(contextAfter.availableTargets.any { it.contains("adjacent foot route to frontier_1:") })
        assertFalse(contextAfter.toString().contains(known.adventure!!.locations.first { it.id == "frontier_2" }.name))
    }
    @Test fun longRoutesAdvanceDayAndBackgroundOnceAndCombatBlocksExpansion() {
        val s = base().let { it.copy(adventure = it.adventure!!.copy(hour = 23, routes = it.adventure!!.routes.map { road -> road.copy(hours = 4) })) }
        val travel = engine.execute(s, GameAction(ActionKind.TRAVEL, "crossing"), "Travel", "day-crossing")
        assertEquals(2L, travel.state.world.day); assertEquals(3, travel.state.adventure!!.hour)
        assertEquals(4L, travel.state.adventure!!.simulationHour)
        assertEquals(travel.state, engine.execute(travel.state, GameAction(ActionKind.TRAVEL, "crossing"), "Travel", "day-crossing").state)
        val fight = engine.apply(base(), GameAction(ActionKind.CHALLENGE, "mei")).state
        assertThrows(IllegalArgumentException::class.java) { engine.apply(fight, GameAction(ActionKind.EXPAND_WORLD)) }
    }
}
