package com.immortalworld.core

/** Stable, explicit RNG algorithm: saves retain state, not an implementation-specific Random. */
class WorldRandom(var state: Long) {
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        state = state * 6364136223846793005L + 1442695040888963407L
        return ((state ushr 1) % bound.toLong()).toInt()
    }
    fun <T> pick(values: List<T>): T = values[nextInt(values.size)]
}
object CreationCatalog {
    val races = listOf("Human", "Demon", "Beastkin", "Dragon", "Spirit", "Celestial", "Ancient")
    val backgrounds = listOf("Village herbalist", "Wandering scholar", "Fallen clan heir", "Mountain hunter")
    val personalities = listOf("Patient and observant", "Proud and ambitious", "Compassionate", "Cautious and pragmatic")
    val roots = listOf("Wood", "Fire", "Earth", "Metal", "Water", "Wind", "Lightning")
    val locations = listOf("Azure Cloud Village", "Jade River Crossing", "Silent Pine Mountain")
    val difficulties = listOf("Gentle", "Standard", "Harsh")
}
data class CreationOptions(
    val name: String = "Lin Yue", val gender: String = "Unspecified",
    val appearance: String = "Dark hair, travel-worn robes, and a steady gaze.",
    val age: Int = 18, val race: String = "Human", val birthplace: String = "Jade River Valley",
    val background: String = "Village herbalist", val personality: String = "Patient and observant",
    val location: String = "Azure Cloud Village", val circumstances: String = "A new path, with modest supplies.",
    val difficulty: String = "Standard", val root: String? = null,
)
data class GeneratedCharacter(val character: Character, val randomState: Long)
object CharacterGenerator {
    fun randomOptions(seed: Long): CreationOptions {
        val r = WorldRandom(seed)
        return CreationOptions(
            name = r.pick(listOf("Lin Yue", "Shen Rui", "Bai Lian", "Mo Chen", "Su Yan", "Jiang Yun")),
            gender = r.pick(listOf("Woman", "Man", "Nonbinary", "Unspecified")),
            age = 16 + r.nextInt(35), race = r.pick(CreationCatalog.races),
            appearance = r.pick(listOf("Silver hair and quiet, watchful eyes.", "A sun-browned face and ink-stained hands.", "Braided hair, a jade clasp, and practical robes.")),
            birthplace = r.pick(listOf("Jade River Valley", "Northern Wastes", "Red Lotus Coast")),
            background = r.pick(CreationCatalog.backgrounds), personality = r.pick(CreationCatalog.personalities),
            location = r.pick(CreationCatalog.locations),
        )
    }
    /** Shared identity/attribute generator; NPC role/realm distributions are deferred to phase 4. */
    fun generate(options: CreationOptions, seed: Long, id: String): GeneratedCharacter {
        require(options.age in 16..100) { "Starting age must be between 16 and 100." }
        listOf(options.name, options.race, options.gender, options.birthplace, options.background,
            options.appearance, options.personality, options.location, options.circumstances).forEach {
            require(it.isNotBlank() && it.length <= 500) { "Character fields must contain 1–500 characters." }
        }
        require(options.name.trim().length <= 60) { "Name must be at most 60 characters." }
        require(options.difficulty in CreationCatalog.difficulties)
        require(options.root == null || options.root in CreationCatalog.roots)
        val r = WorldRandom(seed)
        val rarity = r.nextInt(1000)
        val potential = when { rarity < 5 -> "Heaven-touched"; rarity < 50 -> "Exceptional"; rarity < 250 -> "Gifted"; else -> "Ordinary" }
        fun stat() = 8 + r.nextInt(13)
        val base = Attributes(stat(), stat(), stat(), stat(), stat(), stat(), stat(), stat(), stat(), stat(), 0)
        val a = when (options.background) {
            "Village herbalist" -> base.copy(vitality = base.vitality + 4)
            "Wandering scholar" -> base.copy(comprehension = base.comprehension + 4)
            "Fallen clan heir" -> base.copy(spiritualPower = base.spiritualPower + 4)
            "Mountain hunter" -> base.copy(agility = base.agility + 4)
            else -> base
        }.let { it.copy(talent = it.talent + when (potential) { "Heaven-touched" -> 30; "Exceptional" -> 15; "Gifted" -> 5; else -> 0 }) }
        val maxHealth = 80 + a.vitality * 5
        val maxEnergy = 40 + a.spiritualPower * 4
        val character = Character(
            id = id, name = options.name.trim(), gender = options.gender.trim(), appearance = options.appearance.trim(),
            age = options.age, race = options.race.trim(), birthplace = options.birthplace.trim(),
            background = options.background.trim(), personality = options.personality.trim(),
            spiritualRoot = options.root ?: r.pick(CreationCatalog.roots),
            constitution = if (rarity < 5) "Jade Marrow Constitution" else "Ordinary physique",
            bloodline = if (rarity in 5..14) "Dormant Azure Serpent" else "Unawakened",
            potential = potential, talents = listOf(when(options.background) {
                "Village herbalist" -> "Herb recognition"; "Wandering scholar" -> "Ancient script literacy"
                "Fallen clan heir" -> "Clan etiquette"; "Mountain hunter" -> "Tracking"; else -> "Adaptability"
            }),
            hiddenTraits = if (rarity < 15) listOf("Echo of an ancient inheritance") else emptyList(),
            attributes = a, cultivation = Cultivation(),
            techniques = listOf(Technique("${id}_palm", "Flowing Stream Palm", "A measured palm strike taught to beginning cultivators.")),
            inventory = listOf(
                Item("${id}_sword", "Iron practice sword", "An unadorned, well-balanced blade.", "weapon", power = 5, equippedSlot = "weapon"),
                Item("${id}_herb", "Dewleaf herb", "A common medicinal ingredient.", "herb", quantity = 3, maxStack = 99),
            ), health = maxHealth, maxHealth = maxHealth, energy = maxEnergy, maxEnergy = maxEnergy,
        )
        return GeneratedCharacter(character, r.state)
    }
}
