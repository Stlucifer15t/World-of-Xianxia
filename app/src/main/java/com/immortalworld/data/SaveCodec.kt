package com.immortalworld.data

import com.immortalworld.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Explicit schema: no reflection, arbitrary classes, or provider-dependent content. */
object SaveCodec {
    fun encode(save: GameSave): String {
        val s = SaveValidator.validate(save)
        return obj(
            "format" to s.format, "version" to s.version, "id" to s.id, "name" to s.name,
            "createdAt" to s.createdAt, "updatedAt" to s.updatedAt, "randomState" to s.randomState,
            "adventure" to s.adventure?.let(::encodeAdventure),
            "player" to encodePlayer(s.player), "world" to obj(
                "id" to s.world.id, "name" to s.world.name, "day" to s.world.day,
                "currentLocation" to s.world.currentLocation, "startingCircumstances" to s.world.startingCircumstances,
                "journal" to array(s.world.journal.map { obj("id" to it.id, "day" to it.day, "text" to it.text) }),
            ),
            "rules" to obj(
                "difficulty" to s.rules.difficulty, "cultivationSpeed" to s.rules.cultivationSpeed,
                "breakthroughDifficulty" to s.rules.breakthroughDifficulty, "lifespanEnabled" to s.rules.lifespanEnabled,
                "skipTribulations" to s.rules.skipTribulations, "passiveCultivation" to s.rules.passiveCultivation,
                "godMode" to s.rules.godMode,
            ),
            "realms" to array(s.realms.map { obj("id" to it.id, "name" to it.name, "stages" to it.stages, "baseLifespan" to it.baseLifespan) }),
        ).toString(2).also { require(it.toByteArray(Charsets.UTF_8).size <= SaveValidator.MAX_BYTES) { "Save exceeds the 2 MiB limit." } }
    }

    fun decode(text: String): GameSave {
        require(text.toByteArray(Charsets.UTF_8).size <= SaveValidator.MAX_BYTES) { "Save exceeds the 2 MiB limit." }
        checkNesting(text)
        try {
            val tokener = JSONTokener(text)
            val root = tokener.nextValue() as? JSONObject ?: error("Save must be a JSON object.")
            root.only("format", "version", "id", "name", "createdAt", "updatedAt", "randomState", "player", "world", "rules", "realms", "adventure")
            require(tokener.nextClean() == '\u0000') { "Unexpected content after save." }
            require(root.string("format") == "xianxia-save") { "This is not an Immortal World save." }
            val version = root.int("version")
            require(version in 1..3) { "Unsupported save version $version. No data was changed." }
            // v1/v2 -> v3 preserves characters, mechanics, RNG, journals and past receipts. Initialization is a backed-up
            // repository update on activation, not a destructive Room migration.
            require(version != 1 || !root.has("adventure")) { "Version 1 cannot contain adventure data." }
            val w = root.getJSONObject("world")
            val r = root.getJSONObject("rules")
            w.only("id", "name", "day", "currentLocation", "startingCircumstances", "journal")
            r.only("difficulty", "cultivationSpeed", "breakthroughDifficulty", "lifespanEnabled", "skipTribulations", "passiveCultivation", "godMode")
            return SaveValidator.validate(GameSave(
                format = root.string("format"), version = 3, id = root.string("id"), name = root.string("name"),
                createdAt = root.long("createdAt"), updatedAt = root.long("updatedAt"), randomState = root.long("randomState"),
                adventure = if (version == 1 || root.get("adventure") == JSONObject.NULL) null else decodeAdventure(root.getJSONObject("adventure"), version),
                player = decodePlayer(root.getJSONObject("player")),
                world = WorldState(w.string("id"), w.string("name"), w.long("day"), w.string("currentLocation"),
                    w.string("startingCircumstances"), w.objects("journal", 200).onEach { it.only("id", "day", "text") }.map { JournalEntry(it.string("id"), it.long("day"), it.string("text")) }),
                rules = GameRules(r.string("difficulty"), r.double("cultivationSpeed"), r.double("breakthroughDifficulty"),
                    r.bool("lifespanEnabled"), r.bool("skipTribulations"), r.bool("passiveCultivation"), r.bool("godMode")),
                realms = root.objects("realms", 64).onEach { it.only("id", "name", "stages", "baseLifespan") }.map { Realm(it.string("id"), it.string("name"), it.int("stages"), it.int("baseLifespan")) },
            ))
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot load save: ${e.message?.take(240) ?: "invalid JSON"}", e)
        }
    }

    private fun encodePlayer(p: Character): JSONObject = obj(
        "id" to p.id, "name" to p.name, "gender" to p.gender, "appearance" to p.appearance,
        "age" to p.age, "race" to p.race, "birthplace" to p.birthplace, "background" to p.background,
        "personality" to p.personality, "spiritualRoot" to p.spiritualRoot, "constitution" to p.constitution,
        "bloodline" to p.bloodline, "potential" to p.potential, "talents" to array(p.talents), "hiddenTraits" to array(p.hiddenTraits),
        "health" to p.health, "maxHealth" to p.maxHealth, "energy" to p.energy, "maxEnergy" to p.maxEnergy, "spiritStones" to p.spiritStones,
        "attributes" to p.attributes.let { a -> obj(
            "strength" to a.strength, "constitution" to a.constitution, "agility" to a.agility, "vitality" to a.vitality,
            "spiritualPower" to a.spiritualPower, "spiritualSense" to a.spiritualSense, "comprehension" to a.comprehension,
            "talent" to a.talent, "luck" to a.luck, "soulStrength" to a.soulStrength, "techniqueMastery" to a.techniqueMastery,
        ) },
        "cultivation" to p.cultivation.let { c -> obj(
            "realmId" to c.realmId, "substage" to c.substage, "progress" to c.progress, "maximumLifespan" to c.maximumLifespan,
            "method" to c.method, "breakthroughs" to array(c.breakthroughs), "tribulations" to array(c.tribulations),
        ) },
        "techniques" to array(p.techniques.map { obj(
            "id" to it.id, "name" to it.name, "description" to it.description, "rank" to it.rank,
            "energyCost" to it.energyCost, "power" to it.power, "mastery" to it.mastery,
            "requiredRealmId" to it.requiredRealmId, "effect" to it.effect,
        ) }),
        "inventory" to array(p.inventory.map { obj(
            "id" to it.id, "name" to it.name, "description" to it.description, "kind" to it.kind, "rank" to it.rank,
            "quantity" to it.quantity, "maxStack" to it.maxStack, "power" to it.power, "equippedSlot" to it.equippedSlot,
        ) }),
    )
    private fun decodePlayer(p: JSONObject): Character {
        p.only("id", "name", "gender", "appearance", "age", "race", "birthplace", "background", "personality",
            "spiritualRoot", "constitution", "bloodline", "potential", "talents", "hiddenTraits", "health", "maxHealth",
            "energy", "maxEnergy", "spiritStones", "attributes", "cultivation", "techniques", "inventory")
        val a = p.getJSONObject("attributes"); val c = p.getJSONObject("cultivation")
        a.only("strength", "constitution", "agility", "vitality", "spiritualPower", "spiritualSense", "comprehension", "talent", "luck", "soulStrength", "techniqueMastery")
        c.only("realmId", "substage", "progress", "maximumLifespan", "method", "breakthroughs", "tribulations")
        return Character(
            id = p.string("id"), name = p.string("name"), gender = p.string("gender"), appearance = p.string("appearance"),
            age = p.int("age"), race = p.string("race"), birthplace = p.string("birthplace"), background = p.string("background"),
            personality = p.string("personality"), spiritualRoot = p.string("spiritualRoot"), constitution = p.string("constitution"),
            bloodline = p.string("bloodline"), potential = p.string("potential"), talents = p.strings("talents"), hiddenTraits = p.strings("hiddenTraits"),
            attributes = Attributes(a.int("strength"), a.int("constitution"), a.int("agility"), a.int("vitality"),
                a.int("spiritualPower"), a.int("spiritualSense"), a.int("comprehension"), a.int("talent"), a.int("luck"), a.int("soulStrength"), a.int("techniqueMastery")),
            cultivation = Cultivation(c.string("realmId"), c.int("substage"), c.double("progress"), c.int("maximumLifespan"),
                c.string("method"), c.strings("breakthroughs"), c.strings("tribulations")),
            techniques = p.objects("techniques", 200).onEach { it.only("id", "name", "description", "rank", "energyCost", "power", "mastery", "requiredRealmId", "effect") }.map { Technique(it.string("id"), it.string("name"), it.string("description"),
                it.string("rank"), it.int("energyCost"), it.int("power"), it.int("mastery"), it.string("requiredRealmId"), it.string("effect")) },
            inventory = p.objects("inventory", 500).onEach { it.only("id", "name", "description", "kind", "rank", "quantity", "maxStack", "power", "equippedSlot") }.map { Item(it.string("id"), it.string("name"), it.string("description"), it.string("kind"),
                it.string("rank"), it.int("quantity"), it.int("maxStack"), it.int("power"), it.nullableString("equippedSlot")) },
            health = p.int("health"), maxHealth = p.int("maxHealth"), energy = p.int("energy"), maxEnergy = p.int("maxEnergy"), spiritStones = p.int("spiritStones"),
        )
    }
    private fun encodeAdventure(a: AdventureState): JSONObject = obj(
        "locationId" to a.locationId, "hour" to a.hour, "simulationHour" to a.simulationHour, "simulationMode" to a.simulationMode,
        "injury" to a.injury, "sequence" to a.sequence,
        "visits" to JSONObject().apply { a.visits.forEach { (key, value) -> put(key, value) } },
        "locations" to array(a.locations.map { obj("id" to it.id, "name" to it.name, "description" to it.description, "danger" to it.danger, "discovered" to it.discovered) }),
        "npcs" to array(a.npcs.map { obj("character" to encodePlayer(it.character), "locationId" to it.locationId, "role" to it.role, "goal" to it.goal,
            "knowledge" to array(it.knowledge), "relationship" to it.relationship, "memories" to array(it.memories), "negotiated" to it.negotiated) }),
        "factions" to array(a.factions.map { obj("id" to it.id, "name" to it.name, "resources" to it.resources, "reputation" to it.reputation, "member" to it.member) }),
        "quests" to array(a.quests.map { obj("id" to it.id, "name" to it.name, "giverId" to it.giverId, "objective" to it.objective, "status" to it.status, "reward" to it.reward) }),
        "events" to array(a.events.map { obj("id" to it.id, "day" to it.day, "hour" to it.hour, "text" to it.text) }),
        "history" to array(a.history.map { obj("id" to it.id, "input" to it.input, "day" to it.day, "hour" to it.hour, "facts" to array(it.facts), "calculations" to array(it.calculations), "narration" to it.narration, "accepted" to it.accepted,
            "kind" to it.kind, "locationId" to it.locationId, "story" to it.story, "dialogue" to it.dialogue, "sceneContext" to it.sceneContext) }),
        "combat" to a.combat?.let { obj("enemy" to encodePlayer(it.enemy), "npcId" to it.npcId, "turn" to it.turn, "position" to it.position) },
        "idlePlan" to a.idlePlan?.let { obj("activity" to it.activity, "method" to it.method, "useHerb" to it.useHerb, "checkpointMillis" to it.checkpointMillis) },
        "searchCounts" to JSONObject().apply { a.searchCounts.forEach { (key, value) -> put(key, value) } }, "defeated" to array(a.defeated),
    )
    private fun decodeAdventure(a: JSONObject, version: Int): AdventureState {
        a.only("locationId", "hour", "simulationHour", "simulationMode", "injury", "sequence", "locations", "npcs", "factions", "quests", "events", "history", "combat", "idlePlan", "searchCounts", "defeated", *(if (version >= 3) arrayOf("visits") else emptyArray()))
        val visits = if (version >= 3) a.getJSONObject("visits") else JSONObject().put(a.string("locationId"), 1)
        require(visits.length() <= 128) { "Too many visit records." }
        val counts = a.getJSONObject("searchCounts")
        require(counts.length() <= 128) { "Too many search records." }
        return AdventureState(locationId = a.string("locationId"), hour = a.int("hour"), simulationHour = a.long("simulationHour"), simulationMode = a.string("simulationMode"),
            injury = a.int("injury"), sequence = a.long("sequence"),
            visits = visits.keys().asSequence().associateWith { visits.int(it) },
            locations = a.objects("locations", 128).map { it.only("id", "name", "description", "danger", "discovered"); Location(it.string("id"), it.string("name"), it.string("description"), it.int("danger"), it.bool("discovered")).let { location -> if (version < 3) SceneNarrator.upgradeLocation(location) else location } },
            npcs = a.objects("npcs", 100).map { it.only("character", "locationId", "role", "goal", "knowledge", "relationship", "memories", "negotiated");
                Npc(decodePlayer(it.getJSONObject("character")), it.string("locationId"), it.string("role"), it.string("goal"), it.strings("knowledge"), it.int("relationship"), it.strings("memories"), it.bool("negotiated")) },
            factions = a.objects("factions", 100).map { it.only("id", "name", "resources", "reputation", "member"); Faction(it.string("id"), it.string("name"), it.int("resources"), it.int("reputation"), it.bool("member")) },
            quests = a.objects("quests", 100).map { it.only("id", "name", "giverId", "objective", "status", "reward"); Quest(it.string("id"), it.string("name"), it.string("giverId"), it.string("objective"), it.string("status"), it.int("reward")) },
            events = a.objects("events", 200).map { it.only("id", "day", "hour", "text"); WorldEvent(it.string("id"), it.long("day"), it.int("hour"), it.string("text")) },
            history = a.objects("history", 100).map { it.only("id", "input", "day", "hour", "facts", "calculations", "narration", "accepted", *(if (version >= 3) arrayOf("kind", "locationId", "story", "dialogue", "sceneContext") else emptyArray()));
                ActionRecord(it.string("id"), it.string("input"), it.long("day"), it.int("hour"), it.strings("facts"), it.strings("calculations"), it.string("narration"), it.bool("accepted"),
                    if (version >= 3) it.string("kind") else "", if (version >= 3) it.string("locationId") else "",
                    if (version >= 3) it.string("story") else "", if (version >= 3) it.string("dialogue") else "", if (version >= 3) it.string("sceneContext") else "") },
            combat = if (a.get("combat") == JSONObject.NULL) null else a.getJSONObject("combat").let { it.only("enemy", "npcId", "turn", "position"); CombatState(decodePlayer(it.getJSONObject("enemy")), it.nullableString("npcId"), it.int("turn"), it.string("position")) },
            idlePlan = if (a.get("idlePlan") == JSONObject.NULL) null else a.getJSONObject("idlePlan").let { it.only("activity", "method", "useHerb", "checkpointMillis"); IdlePlan(it.string("activity"), it.string("method"), it.bool("useHerb"), it.long("checkpointMillis")) },
            searchCounts = counts.keys().asSequence().associateWith { counts.int(it) }, defeated = a.strings("defeated"),
        )
    }
    private fun JSONObject.only(vararg allowed: String) {
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(key in allowed) { "Unrecognized field '$key'. Import refused to avoid discarding unsupported data." }
        }
    }
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { put(it.first, it.second ?: JSONObject.NULL) } }
    private fun array(items: List<*>) = JSONArray().apply { items.forEach { put(it) } }
    private fun JSONObject.string(key: String): String = get(key).let { require(it is String) { "$key must be text." }; it }
    private fun JSONObject.nullableString(key: String): String? = if (get(key) == JSONObject.NULL) null else string(key)
    private fun JSONObject.long(key: String): Long = get(key).let { require(it is Int || it is Long) { "$key must be an integer." }; (it as Number).toLong() }
    private fun JSONObject.int(key: String): Int = long(key).let { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "$key is out of range." }; it.toInt() }
    private fun JSONObject.double(key: String): Double = get(key).let { require(it is Number && it.toDouble().isFinite()) { "$key must be a finite number." }; it.toDouble() }
    private fun JSONObject.bool(key: String): Boolean = get(key).let { require(it is Boolean) { "$key must be true or false." }; it }
    private fun JSONObject.objects(key: String, limit: Int): List<JSONObject> = getJSONArray(key).let { a ->
        require(a.length() <= limit) { "Too many $key." }; (0 until a.length()).map { a.getJSONObject(it) }
    }
    private fun JSONObject.strings(key: String): List<String> = getJSONArray(key).let { a ->
        require(a.length() <= 100) { "Too many $key." }; (0 until a.length()).map { index -> a.get(index).let { require(it is String) { "$key must be text." }; it } }
    }
    private fun checkNesting(text: String) {
        var depth = 0; var quoted = false; var escaped = false
        text.forEach { c ->
            if (quoted) {
                require(c >= ' ') { "Unescaped control character in JSON string." }
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "Save nesting is too deep." } }
                '}', ']' -> { depth--; require(depth >= 0) { "Unbalanced JSON." } }
                else -> require(c in " \t\r\n,:0123456789.-+eEtruefalsn") { "Invalid JSON character." }
            }
        }
        require(!quoted && depth == 0) { "Incomplete JSON save." }
    }
}
