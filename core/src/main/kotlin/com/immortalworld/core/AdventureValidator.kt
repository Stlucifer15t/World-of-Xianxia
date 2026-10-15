package com.immortalworld.core

object AdventureValidator {
    fun validate(s: GameSave) {
        val a = s.adventure ?: return
        fun text(value: String, max: Int = 2000) { require(value.isNotBlank() && value.length <= max && '\u0000' !in value) { "Invalid adventure text." } }
        fun ids(values: List<String>, max: Int) { require(values.size <= max && values.distinct().size == values.size) { "Duplicate or excessive adventure IDs." }; values.forEach { text(it, 100) } }
        ids(a.locations.map { it.id }, 128); require(a.locations.isNotEmpty())
        a.locations.forEach { text(it.name, 500); text(it.description); require(it.danger in 0..10); require(it.forage in setOf("herbs", "ore", "herbs_and_ore")) }
        require(a.locations.any { it.id == a.locationId && it.name == s.world.currentLocation }) { "Adventure location does not match the world record." }
        ids(a.routes.map { it.id }, 256)
        val edges = mutableSetOf<Set<String>>()
        a.routes.forEach { road ->
            require(road.from != road.to && a.locations.any { it.id == road.from } && a.locations.any { it.id == road.to }) { "Road endpoints must be distinct existing locations." }
            require(edges.add(setOf(road.from, road.to))) { "Duplicate road between locations." }
            require(road.hours in 1..24) { "Road travel time must be 1–24 hours." }
            text(road.description, 1000)
        }
        require(a.hour in 0..23 && a.simulationHour in 0..100_000_000_000L && a.sequence in 0..1_000_000_000L)
        require(a.simulationMode in listOf("PAUSED", "NORMAL", "ACCELERATED") && a.injury in 0..80)
        ids(a.npcs.map { it.character.id }, 100)
        a.npcs.forEach { n ->
            require(n.character.id != s.player.id && a.locations.any { it.id == n.locationId })
            SaveValidator.validate(s.copy(player = n.character, adventure = null))
            text(n.role, 80); text(n.goal); require(n.relationship in -100..100 && n.memories.size <= 20 && n.knowledge.size <= 20)
            n.memories.forEach { text(it, 1000) }; n.knowledge.forEach { text(it, 1000) }
        }
        ids(a.factions.map { it.id }, 100)
        a.factions.forEach { text(it.name, 100); require(it.resources in 0..1_000_000 && it.reputation in -100..100) }
        ids(a.quests.map { it.id }, 100)
        a.quests.forEach { q ->
            text(q.name, 200); text(q.objective, 1000); require(q.reward in 0..1_000_000)
            require(q.status in listOf("AVAILABLE", "ACTIVE", "READY", "COMPLETE") && a.npcs.any { it.character.id == q.giverId })
        }
        ids(a.events.map { it.id }, 200); a.events.forEach { text(it.text); require(it.day in 1..s.world.day && it.hour in 0..23) }
        ids(a.history.map { it.id }, 100)
        a.history.forEach {
            require(it.kind.isEmpty() || ActionKind.entries.any { kind -> kind.name == it.kind })
            require(it.locationId.isEmpty() || a.locations.any { loc -> loc.id == it.locationId })
            require(it.story.length <= 5000 && it.dialogue.length <= 1500 && it.sceneContext.length <= 3500)
            text(it.input); require(it.facts.size in 1..30 && it.calculations.size <= 30 && it.narration.length <= 6000)
            it.facts.forEach { value -> text(value) }; it.calculations.forEach { value -> text(value) }
            require(it.day in 1..s.world.day && it.hour in 0..23)
        }
        a.combat?.let {
            SaveValidator.validate(s.copy(player = it.enemy, adventure = null))
            require(it.enemy.id != s.player.id && it.enemy.health > 0 && it.turn in 1..1_000_000 && it.position in listOf("close", "cover", "high_ground"))
            require(it.npcId == null || a.npcs.any { n -> n.character.id == it.npcId })
        }
        a.idlePlan?.let {
            require(it.activity in Activities.kinds && it.checkpointMillis in 0..9_000_000_000_000_000L)
            require(it.method in Activities.methods || it.method == s.player.cultivation.method)
            require(s.rules.passiveCultivation) { "Offline plan must be explicitly enabled." }
        }
        require(a.visits.size <= 128)
        a.visits.forEach { (id, count) -> require(a.locations.any { it.id == id } && count in 1..1_000_000_000) }
        ids(a.defeated, 100); require(a.searchCounts.size <= 128)
        a.searchCounts.forEach { (id, count) -> require(a.locations.any { it.id == id } && count in 0..3) }
    }
}
