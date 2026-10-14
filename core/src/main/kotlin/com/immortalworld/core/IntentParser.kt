package com.immortalworld.core

/** Offline convenience interpreter. Unknown wording goes to the AI or a bounded creative attempt,
 * never to a magical success/state patch. NPC quoted speech is not executed as a command. */
object IntentParser {
    fun parse(save: GameSave, input: String): GameAction? {
        val s = AdventureFactory.ensure(save); val a = s.adventure!!
        val t = input.trim().lowercase().removePrefix("i ").removePrefix("want to ").removePrefix("would like to ")
        fun starts(vararg verbs: String) = verbs.any { t == it || t.startsWith("$it ") }
        // Conditional/negated instructions need interpretation, not an unconditional local command.
        if (!starts("say", "ask", "talk", "speak", "tell") && Regex("\\b(do not|don't|never|unless|if|might|could)\\b").containsMatchIn(t)) return null
        val npc = a.npcs.mapNotNull { n ->
            val full = t.indexOf(n.character.name.lowercase()).takeIf { it >= 0 }
            val short = Regex("\\b${Regex.escape(n.character.id)}\\b").find(t)?.range?.first
            listOfNotNull(full, short).minOrNull()?.let { n to it }
        }.minByOrNull { it.second }?.first
        val item = s.player.inventory.firstOrNull { t.contains(it.name.lowercase()) || t.contains(it.id.lowercase()) }
        val technique = s.player.techniques.firstOrNull { t.contains(it.name.lowercase()) || t.contains(it.id.lowercase()) }
        val quest = a.quests.firstOrNull { t.contains(it.id) || t.contains(it.name.lowercase()) }?.id ?: when { "medicine" in t -> "dewleaf"; "survey" in t -> "survey"; else -> "" }
        val hours = Regex("(\\d+)\\s*(hours?|days?)").find(t)?.let { m ->
            ((m.groupValues[1].toLongOrNull() ?: 1000L).coerceAtMost(1000L) * if (m.groupValues[2].startsWith("day")) 24 else 1).toInt()
        } ?: 1
        return when {
            starts("say", "ask", "talk", "speak", "tell") -> {
                val target = npc ?: a.npcs.filter { it.locationId == a.locationId }.singleOrNull() ?: return null
                GameAction(ActionKind.TALK, target.character.id, input.take(1000))
            }
            starts("go", "travel", "walk", "journey", "visit") -> {
                val dest = a.locations.firstOrNull { t.contains(it.name.lowercase()) || Regex("\\b${Regex.escape(it.id)}\\b").containsMatchIn(t) } ?: return null
                GameAction(ActionKind.TRAVEL, dest.id)
            }
            starts("examine", "inspect", "sense", "probe", "scan", "study", "use", "look", "extend", "reach") && ("spiritual sense" in t || "sense his aura" in t || "sense her aura" in t) && npc != null -> GameAction(ActionKind.SENSE, npc.character.id)
            starts("inspect", "read", "examine", "look") && ("notice" in t || "recruitment" in t) -> GameAction(ActionKind.OBSERVE, "noticeboard")
            starts("examine", "inspect") && npc != null -> GameAction(ActionKind.OBSERVE, npc.character.id)
            starts("look", "observe", "inspect", "status") -> GameAction(ActionKind.OBSERVE, item?.id ?: technique?.id ?: npc?.character?.id ?: if (starts("status") || "myself" in t) "self" else "")
            starts("investigate", "examine", "survey") -> GameAction(ActionKind.INVESTIGATE)
            starts("search", "gather", "forage", "explore") -> GameAction(ActionKind.SEARCH)
            starts("defend", "block", "guard") -> GameAction(ActionKind.DEFEND)
            starts("dodge", "evade") -> GameAction(ActionKind.DODGE)
            starts("retreat", "flee", "escape") -> GameAction(ActionKind.RETREAT)
            starts("challenge", "duel") -> npc?.let { GameAction(ActionKind.CHALLENGE, it.character.id) }
            starts("attack", "strike", "punch", "slash") -> if (a.combat != null) GameAction(ActionKind.ATTACK, a.combat.enemy.id) else null
            starts("move", "take cover", "hide", "climb") && a.combat != null -> GameAction(ActionKind.MOVE, when { "cover" in t || "hide" in t -> "cover"; "high" in t || "climb" in t -> "high_ground"; else -> "close" })
            starts("use", "cast", "perform") -> when { technique != null -> GameAction(ActionKind.TECHNIQUE, technique.id); item != null -> GameAction(ActionKind.USE_ITEM, item.id); else -> null }
            starts("equip", "wear", "wield") -> item?.let { GameAction(ActionKind.EQUIP, it.id) }
            starts("buy", "purchase", "sell") -> GameAction(if (starts("sell")) ActionKind.SELL else ActionKind.BUY,
                npc?.character?.id ?: "mei", item?.id?.takeIf { starts("sell") } ?: when { "pill" in t -> "pill"; "ore" in t || "material" in t -> "material"; "herb" in t -> "herb"; else -> "" })
            starts("negotiate", "bargain", "haggle") -> GameAction(ActionKind.NEGOTIATE, npc?.character?.id ?: "mei")
            starts("join") -> GameAction(ActionKind.JOIN, "azure")
            starts("accept") -> GameAction(ActionKind.ACCEPT_QUEST, quest)
            starts("turn in", "deliver", "report", "complete quest") -> GameAction(ActionKind.TURN_IN, quest)
            starts("sit", "settle", "kneel") && (Regex("\\bcultivat(?:e|ion)\\b").containsMatchIn(t) || "meditate" in t) -> GameAction(ActionKind.ACTIVITY,
                if ("meditate" in t) "Meditation" else "Cultivation", hours = hours, method = s.player.cultivation.method, useHerb = "with herbs" in t)
            starts("rest", "sleep", "recover") -> GameAction(ActionKind.REST)
            starts("breakthrough", "break through", "advance realm") -> GameAction(ActionKind.BREAKTHROUGH)
            starts("cultivate", "meditate", "practice", "train", "brew", "craft") -> GameAction(ActionKind.ACTIVITY,
                when { starts("meditate") -> "Meditation"; starts("practice") -> "Technique practice"; starts("train") -> "Body training"; starts("brew") -> "Alchemy"; starts("craft") -> "Crafting"; else -> "Cultivation" },
                hours = hours, method = s.player.cultivation.method, useHerb = "with herbs" in t)
            else -> null
        }
    }
}

object ContextBuilder {
    fun build(s: GameSave, input: String, facts: List<String> = emptyList(), record: ActionRecord? = null): NarrativeContext {
        val a = s.adventure!!
        val local = a.npcs.filter { it.locationId == a.locationId }
        val observedScene = if (record == null) SceneNarrator.scene(s).joinToString("\n\n")
            else record.sceneContext.ifBlank { "Historical action without an observed-scene snapshot. Narrate only its recorded facts; do not reconstruct weather, crowds or unrecorded NPC activity." }
        val kind = record?.kind?.let { name -> ActionKind.entries.firstOrNull { it.name == name } }
        val pace = when {
            kind == ActionKind.TRAVEL || kind == ActionKind.OBSERVE && record.input.trim().lowercase() in listOf("observe", "look", "look around") -> "scene"
            kind in setOf(ActionKind.BREAKTHROUGH, ActionKind.CHALLENGE, ActionKind.JOIN) -> "major"
            kind == ActionKind.TALK -> "dialogue"
            else -> "brief"
        }
        return NarrativeContext(s.player.name, if (record == null) s.world.currentLocation else a.locations.firstOrNull { it.id == record.locationId }?.name ?: "Location not recorded for this historical action",
            facts.take(20), if (record != null) emptyList() else local.flatMap { n -> n.memories.takeLast(3).map { "${n.character.name}: $it" } }.takeLast(9),
            scene = observedScene.take(3500), input = input.take(2000),
            availableTargets = if (record != null) emptyList() else a.locations.filter { it.discovered }.map { "location ${it.id}: ${it.name}" } +
                local.map { "npc ${it.character.id}: ${it.character.name}" } + listOfNotNull(a.combat?.let { "opponent ${it.enemy.id}: ${it.enemy.name}, health ${it.enemy.health}; position=${it.position}" }) +
                s.player.techniques.take(10).map { "technique ${it.id}: ${it.name}" } + s.player.inventory.take(12).map { "item ${it.id}: ${it.name} x${it.quantity}" } +
                a.quests.map { "quest ${it.id}: ${it.name} (${it.status})" } + listOf("faction azure: Azure Cloud Sect") + (if (a.locationId == "village") listOf("noticeboard: the public sect recruitment notice") else emptyList()),
            playerSummary = if (record != null) "Recorded action at day ${record.day}, hour ${record.hour}. Mechanics are only those in the authoritative facts; do not infer historical stats from the current campaign."
                else "${s.player.race}; realm=${s.player.cultivation.realmId} stage=${s.player.cultivation.substage}; HP=${s.player.health}/${s.player.maxHealth}; energy=${s.player.energy}; day=${s.world.day}, hour=${a.hour}; encounter=${a.combat?.enemy?.name ?: "none"}.",
            pace = pace, spokenDialogue = record?.dialogue.orEmpty(),
            recentOpenings = a.history.filter { it.id != record?.id }.takeLast(2).map { (it.narration.ifBlank { it.story }).take(180) },
        )
    }
}
