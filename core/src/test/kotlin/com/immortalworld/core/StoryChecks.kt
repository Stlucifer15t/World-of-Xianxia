package com.immortalworld.core

object StoryChecks {
    private val engine = AdventureEngine { 1000L }
    private fun sample() = AdventureFactory.ensure(CoreChecks.sample())
    private fun execute(s: GameSave, input: String, id: String) = engine.execute(s, IntentParser.parse(s, input)!!, input, id).state
    val cases: Map<String, () -> Unit> = linkedMapOf(
        "opening scene is substantial consolidated read-only prose" to {
            val s = sample(); val scene = SceneNarrator.scene(s)
            check(scene.size in 2..4 && scene.sumOf { it.length } > 1000)
            check(scene == SceneNarrator.scene(s) && s == sample())
            check(scene.joinToString().contains("Mei separates") && scene.joinToString().contains("Elder Wen"))
        },
        "night and cosmetic weather follow saved clock without RNG" to {
            val s = sample(); val night = s.copy(adventure = s.adventure!!.copy(hour = 23))
            check(SceneNarrator.scene(s) != SceneNarrator.scene(night))
            check(SceneNarrator.scene(night).joinToString().contains("lantern-light"))
            check(SceneNarrator.weather(s) == SceneNarrator.weather(s.copy(randomState = Long.MIN_VALUE)))
            check(SceneNarrator.weather(s) != SceneNarrator.weather(s.copy(adventure = s.adventure!!.copy(hour = 14))))
        },
        "absent NPCs and depleted merchant stock are not fabricated" to {
            val s = sample(); val a = s.adventure!!
            val empty = s.copy(adventure = a.copy(npcs = a.npcs.map { if (it.role == "merchant") it.copy(character = it.character.copy(inventory = emptyList())) else it.copy(locationId = "mountain") }))
            val prose = SceneNarrator.scene(empty).joinToString()
            check("empty counter" in prose && "Elder Wen" !in prose && "Scout Ren" !in prose && "separates medicinal" !in prose)
        },
        "scene reflects simulation patrol and faction supplies" to {
            val s = sample(); val moved = engine.apply(s, GameAction(ActionKind.ACTIVITY, "Meditation", hours = 24)).state
            check(moved.adventure!!.npcs.first { it.character.id == "ren" }.locationId == "crossing")
            check(SceneNarrator.scene(moved).joinToString().contains("Fresh supply entries"))
        },
        "duel scene never puts the opponent back to work" to {
            val s = execute(sample(), "Challenge Mei", "duel")
            val scene = SceneNarrator.scene(s).joinToString()
            check("Mei remains engaged" in scene && "Mei separates" !in scene)
        },
        "resolved guardian stays absent from ruin scene" to {
            val s = sample(); val a = s.adventure!!
            val ruins = s.copy(world = s.world.copy(currentLocation = "Moonfall Ruins"), adventure = a.copy(locationId = "ruins", defeated = listOf("wolf")))
            check("growl" !in SceneNarrator.scene(ruins).joinToString())
            check("guardian has been defeated" in SceneNarrator.scene(ruins).joinToString())
        },
        "travel prose uses actual road time arrival and revisit state" to {
            val initial = sample(); val travelled = execute(initial, "travel to Jade River Crossing", "out")
            val record = travelled.adventure!!.history.last()
            check(travelled.adventure!!.hour == 10 && travelled.adventure!!.visits["crossing"] == 1)
            check(record.story.contains("two hours") && record.story.contains("gravel bank"))
            check(StoryText.paragraphs(record.story).size in 2..4)
            val returned = execute(travelled, "travel to Azure Cloud Village", "back")
            check(returned.adventure!!.visits["village"] == 2)
            check(SceneNarrator.scene(returned).first() != SceneNarrator.scene(initial).first())
            check(SceneNarrator.route("village", "ruins") == null)
        },
        "story snapshot survives later changes without historical inference" to {
            val spoken = execute(sample(), "Ask Mei about cultivation resources", "talk")
            val record = spoken.adventure!!.history.last()
            val later = execute(spoken, "travel to Jade River Crossing", "go")
            val context = ContextBuilder.build(later, record.input, record.facts, record)
            check(context.scene == record.sceneContext && context.location == "Azure Cloud Village")
            check(context.availableTargets.isEmpty() && context.relevantMemories.isEmpty())
            check(context.spokenDialogue == record.dialogue && context.pace == "dialogue")
            val legacy = record.copy(sceneContext = "", locationId = "", kind = "")
            check("Historical action" in ContextBuilder.build(later, legacy.input, legacy.facts, legacy).scene)
        },
        "narration context excludes unspoken knowledge and private goals" to {
            val s = sample(); val a = s.adventure!!
            val hidden = s.copy(adventure = a.copy(npcs = a.npcs.map { it.copy(goal = "SECRET_GOAL", knowledge = listOf("SECRET_KNOWLEDGE")) }))
            val context = ContextBuilder.build(hidden, "look around").toString()
            check("SECRET_GOAL" !in context && "SECRET_KNOWLEDGE" !in context)
        },
        "resource dialogue shares only established knowledge with distinct delivery" to {
            val s = execute(sample(), "Ask Mei about cultivation resources", "talk")
            val record = s.adventure!!.history.last()
            check("Mei:" in record.dialogue && "Dewleaf grows" in record.dialogue)
            check(record.dialogue in record.facts && "Keep the village supplied" !in record.dialogue)
            check(s.player == sample().player && s.adventure!!.hour == 9)
            check(s.adventure!!.npcs.first { it.character.id == "mei" }.memories.last().contains("Ask Mei about cultivation resources"))
        },
        "recruitment notice describes the real gate without enrolling" to {
            val s = execute(sample(), "inspect the recruitment notice", "notice")
            check(s.adventure!!.history.last().facts.single().contains("5 sect reputation"))
            check(!s.adventure!!.factions.single().member && s.player == sample().player && s.adventure!!.hour == 8)
            val away = execute(s, "travel to Jade River Crossing", "go")
            val refused = execute(away, "read recruitment notice", "wrong_place")
            check(!refused.adventure!!.history.last().accepted && refused.randomState == away.randomState)
        },
        "spiritual sense is seeded costs energy and cannot reveal hidden traits" to {
            val s = sample(); val request = IntentParser.parse(s, "examine Elder Wen with spiritual sense")!!
            check(request.kind == ActionKind.SENSE && request.target == "wen")
            check(IntentParser.parse(s, "Do not examine Elder Wen with spiritual sense") == null)
            check(IntentParser.parse(s, "Examine Elder Wen with spiritual sense if he allows it") == null)
            check(IntentParser.parse(s, "Think about Elder Wen and spiritual sense") == null)
            val result = engine.apply(s, request)
            check(result == engine.apply(s, request))
            check(result.state.player.energy == s.player.energy - 3 && result.state.adventure!!.hour == 9)
            check(result.state.randomState != s.randomState && result.state.player.cultivation == s.player.cultivation)
            val hidden = s.adventure!!.npcs.first { it.character.id == "wen" }.character.hiddenTraits
            hidden.forEach { check(it !in result.facts.joinToString()) }
            val low = s.copy(player = s.player.copy(energy = 0))
            val refused = engine.execute(low, request, "examine Wen with spiritual sense", "refused")
            check(!refused.state.adventure!!.history.last().accepted && refused.state.randomState == low.randomState && refused.state.player == low.player)
        },
        "natural seated practice does not invent extra voluntary actions" to {
            val s = sample(); val action = IntentParser.parse(s, "sit beneath a tree and cultivate for 2 hours")!!
            check(action.kind == ActionKind.ACTIVITY && action.target == "Cultivation" && action.hours == 2)
            check(IntentParser.parse(s, "Ask Mei whether spiritual sense can examine Elder Wen")!!.kind == ActionKind.TALK)
            val practiced = engine.execute(s, action, "sit beneath a tree and cultivate for 2 hours", "practice").state
            check(practiced.adventure!!.hour == 10 && practiced.player.cultivation.progress > s.player.cultivation.progress)
            check(practiced.adventure!!.history.last().story.length < 1000)
        },
        "custom descriptions survive upgrades and revisits" to {
            val s = sample(); val a = s.adventure!!
            val custom = a.locations.first().copy(description = "CUSTOM_OBSERVABLE_ARCHITECTURE")
            check(SceneNarrator.upgradeLocation(custom) == custom)
            val edited = s.copy(adventure = a.copy(locations = a.locations.map { if (it.id == custom.id) custom else it }, visits = mapOf(custom.id to 5)))
            check("CUSTOM_OBSERVABLE_ARCHITECTURE" in SceneNarrator.scene(edited).first())
        },
        "sentence-per-line narration is consolidated without deleting words" to {
            val lines = (1..9).map { "A sentence about the road." }
            val text = lines.joinToString("\n\n")
            val result = StoryText.paragraphs(text)
            check(result.size < 5 && result.joinToString(" ") == lines.joinToString(" "))
            val paragraph = "The winding path follows the river bank and the low hills. ".repeat(4).trim()
            check(StoryText.paragraphs("$paragraph\nIt continues.\n\n$paragraph") == listOf("$paragraph It continues.", paragraph))
            check(StoryText.paragraphs("A single sentence.\n\nAnother sentence.").single() == "A single sentence. Another sentence.")
        },
    )
    @JvmStatic fun main(args: Array<String>) {
        cases.forEach { (name, test) -> test(); println("PASS $name") }
        println("${cases.size} story checks passed")
    }
}
