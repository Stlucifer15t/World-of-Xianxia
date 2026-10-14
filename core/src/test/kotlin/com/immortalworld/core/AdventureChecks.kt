package com.immortalworld.core

object AdventureChecks {
    private fun sample() = AdventureFactory.ensure(CoreChecks.sample())
    private val engine = AdventureEngine { 1000L }
    private fun act(s: GameSave, kind: ActionKind, target: String = "", detail: String = "", hours: Int = 1) = engine.apply(s, GameAction(kind, target, detail, hours)).state
    private fun rejects(block: () -> Unit) { check(runCatching(block).isFailure) }
    val cases: Map<String, () -> Unit> = linkedMapOf(
        "world initialization is stable and preserves the player" to {
            val old = CoreChecks.sample(); val s = AdventureFactory.ensure(old)
            check(s.player == old.player && s.world == old.world && s.randomState == old.randomState)
            check(AdventureFactory.ensure(s) == s && s.adventure!!.npcs.map { it.character.attributes }.distinct().size == 3)
            SaveValidator.validate(s)
        },
        "custom hierarchy remains compatible with generated NPCs" to {
            val old = CoreChecks.sample(); val realm = Realm("custom", "First breath", 2, 150)
            val custom = old.copy(realms = listOf(realm), player = old.player.copy(cultivation = old.player.cultivation.copy(realmId = realm.id), techniques = old.player.techniques.map { it.copy(requiredRealmId = realm.id) }))
            SaveValidator.validate(AdventureFactory.ensure(custom))
        },
        "natural language local operations and dialogue are parsed" to {
            check(IntentParser.parse(sample(), "I travel to Jade River Crossing") == GameAction(ActionKind.TRAVEL, "crossing"))
            val dialogue = IntentParser.parse(sample(), "Ask Mei whether I should attack Elder Wen")!!
            check(dialogue.kind == ActionKind.TALK && dialogue.target == "mei")
            check(IntentParser.parse(sample(), "cultivate for 3 hours")!!.hours == 3)
            check(IntentParser.parse(sample(), "I magically become an immortal emperor") == null)
        },
        "named inspection returns real item and technique properties" to {
            val s = sample(); val item = s.player.inventory.first()
            val command = IntentParser.parse(s, "Inspect ${item.name}")!!
            val result = engine.apply(s, command)
            check(result.facts.single().contains("power ${item.power}") && result.state.player == s.player)
            check(IntentParser.parse(s, "Ask Elder Wen what he knows about Mei")!!.target == "wen")
        },
        "breakthrough preview uses the actual chance and respects difficulty" to {
            val s = sample()
            check(AdventureEngine.breakthroughChance(s) in 5..95)
            check(AdventureEngine.breakthroughChance(s.copy(rules = s.rules.copy(breakthroughDifficulty = 2.0))) < AdventureEngine.breakthroughChance(s))
            check(AdventureEngine.breakthroughChance(s.copy(rules = s.rules.copy(godMode = true))) == 100)
        },
        "travel validates roads and discovers locations only through rules" to {
            val s = sample(); rejects { act(s, ActionKind.TRAVEL, "ruins") }; rejects { act(s, ActionKind.TRAVEL, "missing") }
            val moved = act(s, ActionKind.TRAVEL, "crossing"); check(moved.world.currentLocation == "Jade River Crossing" && moved.adventure!!.hour == 10)
            check(act(moved, ActionKind.INVESTIGATE).adventure!!.locations.first { it.id == "ruins" }.discovered)
        },
        "NPC dialogue persists actual player words without inventing promises" to {
            val s = act(sample(), ActionKind.TALK, "mei", "Where are Moonfall Ruins?")
            val n = s.adventure!!.npcs.first { it.character.id == "mei" }
            check(n.memories.single().contains("Where are Moonfall Ruins?")); check(n.relationship == 0)
            check(s.adventure!!.locations.first { it.id == "ruins" }.discovered)
            rejects { act(s, ActionKind.TALK, "ren", "hello") }
        },
        "trade transfers inventory and currency" to {
            val s = sample(); val result = act(s, ActionKind.BUY, "mei", "herb")
            check(result.player.spiritStones == s.player.spiritStones - 4)
            check(result.player.inventory.filter { it.kind == "herb" }.sumOf { it.quantity } == 4)
            check(result.adventure!!.npcs.first { it.character.id == "mei" }.character.spiritStones == 124)
            val sold = act(result, ActionKind.SELL, "mei", "herb"); check(sold.player.spiritStones == s.player.spiritStones - 2)
        },
        "invalid trades and resource costs are atomic" to {
            val s = sample().let { it.copy(player = it.player.copy(spiritStones = 0)) }
            rejects { act(s, ActionKind.BUY, "mei", "pill") }; check(s.player.inventory.size == 2)
            rejects { act(s, ActionKind.ACTIVITY, "Alchemy", hours = 24) }
        },
        "quest rewards cannot be claimed twice" to {
            val s = act(act(sample(), ActionKind.ACCEPT_QUEST, "dewleaf"), ActionKind.TURN_IN, "dewleaf")
            check(s.player.spiritStones == 32 && s.adventure!!.quests.first().status == "COMPLETE")
            check(s.player.inventory.first { it.kind == "herb" }.quantity == 1)
            rejects { act(s, ActionKind.TURN_IN, "dewleaf") }
        },
        "faction membership requires explicit action and qualifications" to {
            rejects { act(sample(), ActionKind.JOIN, "azure") }
            var s = act(act(sample(), ActionKind.ACCEPT_QUEST, "dewleaf"), ActionKind.TURN_IN, "dewleaf")
            check(!s.adventure!!.factions.first().member)
            s = s.copy(player = s.player.copy(cultivation = s.player.cultivation.copy(substage = 2)))
            check(act(s, ActionKind.JOIN, "azure").adventure!!.factions.first().member)
        },
        "cultivation estimate equals actual gain and consumes resources" to {
            val s = sample(); val command = GameAction(ActionKind.ACTIVITY, "Cultivation", hours = 2, useHerb = true)
            val estimate = Activities.estimate(s, command); val result = engine.apply(s, command).state
            check(result.player.cultivation.progress == estimate.progress && result.adventure!!.hour == 10)
            check(result.player.inventory.first { it.kind == "herb" }.quantity == 1)
            check(result.player.cultivation.substage == 1)
        },
        "cultivation caps at stage boundary without automatic breakthrough" to {
            val s = sample().let { it.copy(player = it.player.copy(cultivation = it.player.cultivation.copy(progress = 99.0))) }
            val result = act(s, ActionKind.ACTIVITY, "Cultivation", hours = 24)
            check(result.player.cultivation.progress == 100.0 && result.player.cultivation.substage == 1)
            rejects { act(result, ActionKind.ACTIVITY, "Cultivation") }
        },
        "activity durations are bounded and never real-time waits" to {
            rejects { act(sample(), ActionKind.ACTIVITY, "Cultivation", hours = 0) }; rejects { act(sample(), ActionKind.ACTIVITY, "Cultivation", hours = 25) }
            val s = act(sample(), ActionKind.ACTIVITY, "Body training", hours = 24)
            check(s.world.day == 2L && s.player.attributes.strength == sample().player.attributes.strength + 24)
        },
        "alchemy creates a usable mechanical healing item" to {
            val s = act(sample(), ActionKind.ACTIVITY, "Alchemy")
            val pill = s.player.inventory.first { it.kind == "pill" }
            val injured = s.copy(player = s.player.copy(health = 1))
            check(act(injured, ActionKind.USE_ITEM, pill.id).player.health == 41)
        },
        "crafting and equipment have real state effects" to {
            var s = act(act(sample(), ActionKind.TRAVEL, "crossing"), ActionKind.TRAVEL, "mountain")
            s = act(act(s, ActionKind.SEARCH), ActionKind.ACTIVITY, "Crafting")
            val armor = s.player.inventory.first { it.kind == "armor" }; s = act(s, ActionKind.EQUIP, armor.id)
            check(s.player.inventory.first { it.id == armor.id }.equippedSlot == "armor")
        },
        "combat is deterministic with persisted RNG and more than realm" to {
            val s = act(sample(), ActionKind.CHALLENGE, "wen")
            val first = engine.apply(s, GameAction(ActionKind.ATTACK)); val second = engine.apply(s, GameAction(ActionKind.ATTACK))
            check(first == second && first.calculations.isNotEmpty() && first.state.randomState != s.randomState)
            val strong = s.copy(player = s.player.copy(attributes = s.player.attributes.copy(strength = 1_000_000, agility = 1_000_000)))
            val empowered = engine.apply(strong, GameAction(ActionKind.ATTACK))
            check(empowered.state.adventure!!.combat == null || empowered.state.adventure!!.combat!!.enemy.health < first.state.adventure!!.combat!!.enemy.health)
        },
        "techniques consume energy and cannot be fabricated" to {
            val s = act(sample(), ActionKind.CHALLENGE, "mei"); val t = s.player.techniques.first()
            val result = act(s, ActionKind.TECHNIQUE, t.id)
            check(result.player.energy == s.player.energy - t.energyCost)
            rejects { act(s, ActionKind.TECHNIQUE, "made-up") }
            rejects { act(s.copy(player = s.player.copy(energy = 0)), ActionKind.TECHNIQUE, t.id) }
        },
        "combat blocks travel and idle actions" to {
            val s = act(sample(), ActionKind.CHALLENGE, "mei")
            rejects { act(s, ActionKind.TRAVEL, "crossing") }; rejects { act(s, ActionKind.ACTIVITY, "Meditation") }
        },
        "defend dodge movement retreat and negotiation resolve through combat" to {
            val s = act(sample(), ActionKind.CHALLENGE, "mei")
            listOf(ActionKind.DEFEND, ActionKind.DODGE, ActionKind.RETREAT).forEach { SaveValidator.validate(act(s, it)) }
            check(act(s, ActionKind.MOVE, "cover").adventure!!.combat!!.position == "cover")
            check(act(s, ActionKind.NEGOTIATE).adventure!!.combat == null)
        },
        "background modes affect schedules not player choices" to {
            val s = sample()
            val paused = act(act(s, ActionKind.SIMULATION_MODE, "PAUSED"), ActionKind.ACTIVITY, "Meditation", hours = 24)
            check(paused.world.day == 2L && paused.adventure!!.simulationHour == 0L && paused.adventure!!.factions.first().resources == 100)
            val fast = act(act(s, ActionKind.SIMULATION_MODE, "ACCELERATED"), ActionKind.ACTIVITY, "Meditation", hours = 24)
            check(fast.adventure!!.simulationHour == 72L && fast.adventure!!.factions.first().resources == 115)
            check(fast.adventure!!.quests.all { it.status == "AVAILABLE" } && fast.world.currentLocation == s.world.currentLocation)
        },
        "offline claims are opt-in bounded and cannot replay an interval" to {
            var now = 1000L; val e = AdventureEngine { now }; val s = sample()
            rejects { e.apply(s, GameAction(ActionKind.CLAIM_OFFLINE)) }
            val armed = e.apply(s, GameAction(ActionKind.ENABLE_OFFLINE, "Body training")).state
            now += 48 * 3_600_000L
            val claimed = e.apply(armed, GameAction(ActionKind.CLAIM_OFFLINE)).state
            check(claimed.player.attributes.strength == s.player.attributes.strength + 24)
            rejects { e.apply(claimed, GameAction(ActionKind.CLAIM_OFFLINE)) }
            check(e.apply(claimed, GameAction(ActionKind.DISABLE_OFFLINE)).state.adventure!!.idlePlan == null)
        },
        "receipt IDs make mechanics idempotent" to {
            val s = sample(); val command = GameAction(ActionKind.SEARCH)
            val first = engine.execute(s, command, "Search the village", "request-1")
            val replay = engine.execute(first.state, command, "Search the village", "request-1")
            check(first.state == replay.state && replay.state.adventure!!.history.size == 1)
        },
        "rejected input is recorded without mechanical mutation" to {
            val s = sample(); val result = engine.execute(s, GameAction(ActionKind.TRAVEL, "missing"), "Go somewhere nonexistent", "bad")
            check(result.state.player == s.player && result.state.world == s.world && result.state.randomState == s.randomState)
            check(!result.state.adventure!!.history.single().accepted)
        },
        "creative claims cannot grant cultivation or control player dialogue" to {
            val s = sample(); val result = engine.execute(s, GameAction(ActionKind.ATTEMPT, detail = "I become an emperor and everyone obeys"), "I become an emperor", "attempt")
            check(result.state.player.cultivation == s.player.cultivation && result.state.player.inventory == s.player.inventory)
            check(result.state.adventure!!.npcs.map { it.relationship } == s.adventure!!.npcs.map { it.relationship })
        },
        "history and event memory remain bounded" to {
            var s = sample(); repeat(110) { s = engine.execute(s, GameAction(ActionKind.OBSERVE), "look", "request-$it").state }
            check(s.adventure!!.history.size == 100 && s.adventure!!.events.size == 200)
        },
        "breakthrough requires progress and preserves explicit sandbox state" to {
            rejects { act(sample(), ActionKind.BREAKTHROUGH) }
            val s = sample().let { it.copy(rules = it.rules.copy(godMode = true)) }
            val result = act(s, ActionKind.BREAKTHROUGH)
            check(result.player.cultivation.substage == 2 && result.rules.godMode)
            check(!sample().rules.godMode)
        },
        "AI context excludes hidden traits and private journal" to {
            val s = sample().let { it.copy(player = it.player.copy(hiddenTraits = listOf("SECRET TRAIT")), world = it.world.copy(journal = listOf(JournalEntry("private", 1, "SECRET NOTE")))) }
            val context = ContextBuilder.build(s, "look").toString()
            check("SECRET TRAIT" !in context && "SECRET NOTE" !in context)
        },
    )
    @JvmStatic fun main(args: Array<String>) { cases.forEach { (name, test) -> test(); println("PASS $name") }; println("${cases.size} adventure checks passed") }
}
