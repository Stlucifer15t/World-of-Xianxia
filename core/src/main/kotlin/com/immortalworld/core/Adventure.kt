package com.immortalworld.core

/** Campaign-scoped records; no provider text is ever an executable state patch. */
data class Location(val id: String, val name: String, val description: String, val danger: Int = 0, val discovered: Boolean = true,
    val forage: String = if (id in listOf("mountain", "ruins")) "herbs_and_ore" else "herbs")
data class Npc(val character: Character, val locationId: String, val role: String, val goal: String,
    val knowledge: List<String>, val relationship: Int = 0, val memories: List<String> = emptyList(), val negotiated: Boolean = false)
data class Faction(val id: String, val name: String, val resources: Int = 100, val reputation: Int = 0, val member: Boolean = false)
data class Quest(val id: String, val name: String, val giverId: String, val objective: String, val status: String = "AVAILABLE", val reward: Int = 12)
data class WorldEvent(val id: String, val day: Long, val hour: Int, val text: String)
data class CombatState(val enemy: Character, val npcId: String? = null, val turn: Int = 1, val position: String = "close")
data class IdlePlan(val activity: String, val method: String, val useHerb: Boolean, val checkpointMillis: Long)
data class ActionRecord(val id: String, val input: String, val day: Long, val hour: Int,
    val facts: List<String>, val calculations: List<String>, val narration: String = "", val accepted: Boolean = true,
    val kind: String = "", val locationId: String = "", val story: String = "", val dialogue: String = "", val sceneContext: String = "")
data class AdventureState(
    val locationId: String, val hour: Int = 8, val simulationHour: Long = 0, val simulationMode: String = "NORMAL",
    val locations: List<Location>, val npcs: List<Npc>, val factions: List<Faction>, val quests: List<Quest>,
    val events: List<WorldEvent> = emptyList(), val history: List<ActionRecord> = emptyList(),
    val combat: CombatState? = null, val idlePlan: IdlePlan? = null,
    val searchCounts: Map<String, Int> = emptyMap(), val defeated: List<String> = emptyList(),
    val injury: Int = 0, val sequence: Long = 0,
    val visits: Map<String, Int> = emptyMap(),
    val routes: List<TravelRoute> = WorldGeography.legacyRoutes(locations), val regionSeed: Long? = null,
)

object AdventureFactory {
    fun ensure(save: GameSave): GameSave {
        if (save.adventure != null) return save
        val places = listOf(
            Location("village", "Azure Cloud Village", "A herbalist's stall faces a sect noticeboard. Mei tends her stock; Elder Wen receives applicants."),
            Location("crossing", "Jade River Crossing", "Caravans pause at the ford. The water and tracks offer clues to the surrounding hills."),
            Location("mountain", "Silent Pine Mountain", "A spiritual spring lies beneath ancient pines. Scout Ren patrols the mountain trail.", 1),
            Location("ruins", "Moonfall Ruins", "Broken wards shelter medicinal herbs and ore. A territorial spirit wolf guards the inner courtyard.", 3, false),
        ).map(SceneNarrator::upgradeLocation).let { known -> if (known.any { it.name == save.world.currentLocation }) known else known +
            Location("origin", save.world.currentLocation, "Your starting refuge connects to the roads of the Jade Expanse.") }
        fun npc(id: String, name: String, background: String, personality: String, role: String, goal: String, knowledge: List<String>, location: String): Npc {
            val g = CharacterGenerator.generate(CreationOptions(name = name, age = when (role) { "elder" -> 60; "merchant" -> 33; else -> 24 }, background = background, personality = personality), save.randomState xor id.hashCode().toLong(), id)
            val p = g.character.copy(cultivation = g.character.cultivation.copy(realmId = save.realms.first().id),
                techniques = g.character.techniques.map { it.copy(requiredRealmId = save.realms.first().id) }, spiritStones = 120, inventory = if (role == "merchant") listOf(
                Item("merchant_herb", "Dewleaf herb", "A medicinal ingredient.", "herb", quantity = 12, maxStack = 99),
                Item("merchant_pill", "Mending pill", "Restores 40 health when consumed.", "pill", quantity = 6, maxStack = 99, power = 40),
            ) else g.character.inventory)
            return Npc(p, location, role, goal, knowledge)
        }
        val npcs = listOf(
            npc("mei", "Mei", "Village herbalist", "Cautious and pragmatic", "merchant", "Keep the village supplied with medicine.", listOf("Dewleaf grows near water and old stone.", "Moonfall Ruins are guarded by a spirit wolf."), "village"),
            npc("wen", "Elder Wen", "Wandering scholar", "Patient and observant", "elder", "Recruit disciplined disciples, not boastful strangers.", listOf("The sect admits stage-two cultivators with proven service.", "Moonfall's stone wards merit a careful survey."), "village").let {
                it.copy(character = it.character.copy(cultivation = it.character.cultivation.copy(substage = minOf(4, save.realms.first().stages)), attributes = it.character.attributes.copy(strength = 26, constitution = 24)))
            },
            npc("ren", "Scout Ren", "Mountain hunter", "Proud and ambitious", "scout", "Patrol the ford and mountain without abandoning duty.", listOf("The spring improves cultivation.", "Moonfall Ruins lie beyond the pine trail."), "mountain"),
        )
        return save.copy(adventure = AdventureState(
            locationId = places.first { it.name == save.world.currentLocation }.id, locations = places, npcs = npcs,
            visits = mapOf(places.first { it.name == save.world.currentLocation }.id to 1),
            factions = listOf(Faction("azure", "Azure Cloud Sect")),
            quests = listOf(Quest("dewleaf", "Medicine for the village", "mei", "Deliver two Dewleaf herbs to Mei."),
                Quest("survey", "Survey Moonfall", "wen", "Investigate the ruins, then report to Elder Wen.", reward = 24)),
        ))
    }
}

enum class ActionKind { OBSERVE, SENSE, TRAVEL, INVESTIGATE, SEARCH, TALK, NEGOTIATE, BUY, SELL, JOIN, ACCEPT_QUEST, TURN_IN,
    CHALLENGE, ATTACK, TECHNIQUE, DEFEND, DODGE, MOVE, USE_ITEM, EQUIP, RETREAT, REST, ACTIVITY, BREAKTHROUGH, ATTEMPT,
    SIMULATION_MODE, ENABLE_OFFLINE, DISABLE_OFFLINE, CLAIM_OFFLINE, EXPAND_WORLD }

data class ActivityEstimate(val hours: Int, val progress: Double, val health: Int, val energy: Int,
    val mastery: Int, val strength: Int, val herbs: Int, val materials: Int, val stones: Int, val explanation: String)
object Activities {
    val kinds = listOf("Cultivation", "Meditation", "Technique practice", "Body training", "Alchemy", "Crafting")
    val methods = listOf("Still Water Breathing", "Focused Meridian Cycle")
    fun estimate(s: GameSave, a: GameAction): ActivityEstimate {
        require(a.hours in 1..24) { "Choose 1–24 in-game hours." }
        require(a.target in kinds) { "Unknown activity." }
        require(a.method in methods || a.method == s.player.cultivation.method) { "Unknown cultivation method." }
        val p = s.player; val h = a.hours
        val spring = if (s.adventure?.locationId == "mountain") 1.25 else 1.0
        val method = if (a.method == "Focused Meridian Cycle") 1.35 else 1.0
        val herb = if (a.useHerb && a.target == "Cultivation") h else 0
        val progress = if (a.target == "Cultivation") h * (1.5 + p.attributes.talent * .10 + p.attributes.comprehension * .04) *
            s.rules.cultivationSpeed * spring * method * (if (herb > 0) 1.5 else 1.0) else 0.0
        return ActivityEstimate(h, minOf(progress, 100 - p.cultivation.progress),
            if (a.target == "Meditation") h * (4 + p.attributes.constitution / 5) else 0,
            when { a.target == "Meditation" -> h * (5 + p.attributes.soulStrength / 5)
                a.target == "Cultivation" && a.method == "Focused Meridian Cycle" -> -h * 3; else -> 0 },
            if (a.target == "Technique practice") h * (1 + p.attributes.comprehension / 20) else 0,
            if (a.target == "Body training") h else 0,
            herb + if (a.target == "Alchemy") h * 2 else 0,
            if (a.target == "Crafting") h * 2 else 0,
            when (a.target) { "Alchemy" -> h * 3; "Crafting" -> h * 2; else -> 0 },
            "No real-time wait. Progress stops at 100%; breakthroughs require your explicit action. Alchemy makes one mending pill/hour; crafting makes one armor/hour.")
    }
}
