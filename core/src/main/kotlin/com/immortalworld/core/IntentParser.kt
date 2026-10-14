package com.immortalworld.core

/** Offline convenience interpreter. Unknown wording goes to the AI or a bounded creative attempt,
 * never to a magical success/state patch. NPC quoted speech is not executed as a command. */
object IntentParser {
    fun parse(save: GameSave, input: String): GameAction? {
        val s = AdventureFactory.ensure(save); val a = s.adventure!!
        val t = input.trim().lowercase().removePrefix("i ").removePrefix("want to ").removePrefix("would like to ")
        fun starts(vararg verbs: String) = verbs.any { t == it || t.startsWith("$it ") }
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
            starts("look", "observe", "inspect", "status") -> GameAction(ActionKind.OBSERVE)
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
    fun build(s: GameSave, input: String, facts: List<String> = emptyList()): NarrativeContext {
        val a = s.adventure!!
        val local = a.npcs.filter { it.locationId == a.locationId }
        return NarrativeContext(s.player.name, s.world.currentLocation, facts.take(20),
            local.flatMap { n -> n.memories.takeLast(3).map { "${n.character.name}: $it" } }.takeLast(9),
            scene = a.locations.first { it.id == a.locationId }.description + "\n" + local.joinToString("\n") { n ->
                "NPC id=${n.character.id}; ${n.character.name}; personality=${n.character.personality}; opinion=${n.relationship}; goal=${n.goal}; knowledge=${n.knowledge.joinToString("; ")}" },
            input = input.take(2000), availableTargets = a.locations.filter { it.discovered }.map { "location ${it.id}: ${it.name}" } +
                local.map { "npc ${it.character.id}: ${it.character.name}" } + s.player.techniques.take(10).map { "technique ${it.id}: ${it.name}" } +
                s.player.inventory.take(12).map { "item ${it.id}: ${it.name} x${it.quantity}" } + a.quests.map { "quest ${it.id}: ${it.name} (${it.status})" } + listOf("faction azure: Azure Cloud Sect"),
            playerSummary = "${s.player.race}; realm=${s.player.cultivation.realmId} stage=${s.player.cultivation.substage}; HP=${s.player.health}/${s.player.maxHealth}; energy=${s.player.energy}; day=${s.world.day}, hour=${a.hour}; encounter=${a.combat?.enemy?.name ?: "none"}.")
    }
}
