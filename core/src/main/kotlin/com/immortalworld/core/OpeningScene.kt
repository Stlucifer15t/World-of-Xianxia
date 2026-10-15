package com.immortalworld.core

/** A creation snapshot in the existing v3 receipt format, not an engine command or a new save schema. */
object OpeningScene {
    private const val PREFIX = "opening:"
    fun isOpening(record: ActionRecord): Boolean = record.kind.isEmpty() && record.id.startsWith(PREFIX)
    fun canAttach(save: GameSave): Boolean = save.adventure?.let {
        it.history.isEmpty() && it.sequence == 0L && it.simulationHour == 0L && it.hour == 8 &&
            it.combat == null && save.world.day == 1L
    } == true

    fun attach(save: GameSave): GameSave {
        val s = AdventureFactory.ensure(save)
        val a = s.adventure!!
        if (!canAttach(s)) return s
        val p = s.player
        val realm = s.realms.first { it.id == p.cultivation.realmId }
        val facts = listOf(
            "Name: ${p.name}",
            "Identity: ${p.race}; ${p.gender}; age ${p.age}.",
            "Appearance selected by the player: ${p.appearance}",
            "Birthplace: ${p.birthplace}",
            "Background selected by the player: ${p.background}",
            "Personality selected by the player (not permission to invent decisions or thoughts): ${p.personality}",
            "Starting location: ${s.world.currentLocation}",
            "Opening time: day ${s.world.day}, hour ${a.hour}.",
            "Starting scenario selected by the player (story framing, not extra powers or resources): ${s.world.startingCircumstances}",
            "Spiritual root: ${p.spiritualRoot}; constitution: ${p.constitution}; bloodline: ${p.bloodline}.",
            "Potential: ${p.potential}; revealed talents: ${p.talents.joinToString().ifBlank { "None" }}.",
            "Starting cultivation: ${realm.name}, stage ${p.cultivation.substage}; progress ${p.cultivation.progress}%; method ${p.cultivation.method}; lifespan ${p.cultivation.maximumLifespan} years.",
            "Attributes: ${p.attributes.entries().entries.joinToString { "${it.key} ${it.value}" }}.",
            "Health ${p.health}/${p.maxHealth}; qi ${p.energy}/${p.maxEnergy}; spirit stones ${p.spiritStones}.",
            "Learned arts: ${p.techniques.take(10).joinToString { "${it.name} (${it.rank}, mastery ${it.mastery})" }}.",
            "Starting possessions: ${p.inventory.take(12).joinToString { "${it.name} x${it.quantity}${it.equippedSlot?.let { slot -> " (equipped $slot)" }.orEmpty()}" }}.",
            "Difficulty: ${s.rules.difficulty}. No time, RNG, resources, relationships or quests have advanced for this opening.",
        ).map { it.take(2000) }
        val scene = SceneNarrator.scene(s).joinToString("\n\n")
        val record = ActionRecord(id = PREFIX + s.id.take(90), input = "Begin the journey", day = s.world.day,
            hour = a.hour, facts = facts, calculations = emptyList(), locationId = a.locationId,
            story = scene.take(5000), sceneContext = scene.take(3500))
        return SaveValidator.validate(s.copy(adventure = a.copy(history = listOf(record))))
    }

    fun context(record: ActionRecord): NarrativeContext {
        require(isOpening(record))
        // Do not consult live campaign fields: even a later rename must not rewrite the saved premise.
        fun fact(prefix: String, fallback: String) = record.facts.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix) ?: fallback
        return NarrativeContext(
            playerName = fact("Name: ", "The player described in the saved creation facts"),
            location = fact("Starting location: ", "The starting location described in the saved creation facts"),
            authoritativeFacts = record.facts.take(20), relevantMemories = emptyList(),
            scene = record.sceneContext, input = record.input,
            pace = "opening", playerSummary = "Saved character-creation snapshot. Use these selections to write the beginning, not a recap of actions. Keep all mechanical state unchanged.",
            recentOpenings = listOfNotNull(record.narration.takeIf { it.isNotBlank() }?.take(180)),
        )
    }

    /** Keep the origin when ordinary receipts reach their existing 100-record bound. */
    fun retainHistory(records: List<ActionRecord>): List<ActionRecord> {
        val opening = records.firstOrNull(::isOpening) ?: return records.takeLast(100)
        return listOf(opening) + records.filter { it.id != opening.id }.takeLast(99)
    }
}
