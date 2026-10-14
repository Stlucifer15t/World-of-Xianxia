package com.immortalworld.core

/** Engine-owned data. Neither Android nor an AI provider is a dependency of this module. */
data class Attributes(
    val strength: Int, val constitution: Int, val agility: Int, val vitality: Int,
    val spiritualPower: Int, val spiritualSense: Int, val comprehension: Int,
    val talent: Int, val luck: Int, val soulStrength: Int, val techniqueMastery: Int,
) {
    fun entries(): Map<String, Int> = linkedMapOf(
        "Strength" to strength, "Constitution" to constitution, "Agility" to agility,
        "Vitality" to vitality, "Spiritual power" to spiritualPower,
        "Spiritual sense" to spiritualSense, "Comprehension" to comprehension,
        "Cultivation talent" to talent, "Luck" to luck, "Soul strength" to soulStrength,
        "Technique mastery" to techniqueMastery,
    )
}

data class Realm(val id: String, val name: String, val stages: Int, val baseLifespan: Int)
object Realms {
    val defaults = listOf(
        Realm("qi_refining", "Qi Refining", 9, 120),
        Realm("foundation", "Foundation Establishment", 9, 250),
        Realm("core", "Core Formation", 9, 500),
        Realm("nascent_soul", "Nascent Soul", 9, 1000),
        Realm("soul_transformation", "Soul Transformation", 9, 2000),
        Realm("void_refinement", "Void Refinement", 9, 5000),
        Realm("body_integration", "Body Integration", 9, 10000),
        Realm("mahayana", "Mahayana", 9, 20000),
        Realm("tribulation", "Tribulation Transcendence", 9, 50000),
        Realm("immortal", "Immortal", 9, 100000),
        Realm("true_immortal", "True Immortal", 9, 250000),
        Realm("golden_immortal", "Golden Immortal", 9, 500000),
        Realm("immortal_king", "Immortal King", 9, 1000000),
        Realm("immortal_emperor", "Immortal Emperor", 9, 10000000),
    )
}
data class Cultivation(
    val realmId: String = "qi_refining", val substage: Int = 1,
    val progress: Double = 0.0, val maximumLifespan: Int = 120,
    val method: String = "Still Water Breathing", val breakthroughs: List<String> = emptyList(),
    val tribulations: List<String> = emptyList(),
)
data class Technique(
    val id: String, val name: String, val description: String, val rank: String = "Mortal",
    val energyCost: Int = 5, val power: Int = 10, val mastery: Int = 0,
    val requiredRealmId: String = "qi_refining", val effect: String = "physical_damage",
)
data class Item(
    val id: String, val name: String, val description: String, val kind: String,
    val rank: String = "Mortal", val quantity: Int = 1, val maxStack: Int = 1,
    val power: Int = 0, val equippedSlot: String? = null,
)
data class Character(
    val id: String, val name: String, val gender: String, val appearance: String,
    val age: Int, val race: String, val birthplace: String, val background: String,
    val personality: String, val spiritualRoot: String, val constitution: String,
    val bloodline: String, val potential: String, val talents: List<String>,
    val hiddenTraits: List<String>, val attributes: Attributes,
    val cultivation: Cultivation, val techniques: List<Technique>, val inventory: List<Item>,
    val health: Int, val maxHealth: Int, val energy: Int, val maxEnergy: Int,
    val spiritStones: Int = 20,
)
data class GameRules(
    val difficulty: String = "Standard", val cultivationSpeed: Double = 1.0,
    val breakthroughDifficulty: Double = 1.0, val lifespanEnabled: Boolean = true,
    val skipTribulations: Boolean = false, val passiveCultivation: Boolean = false,
    val godMode: Boolean = false,
)
data class JournalEntry(val id: String, val day: Long, val text: String)
data class WorldState(
    val id: String, val name: String = "The Jade Expanse", val day: Long = 1,
    val currentLocation: String, val startingCircumstances: String,
    val journal: List<JournalEntry> = emptyList(),
)
data class GameSave(
    val format: String = "xianxia-save", val version: Int = 2,
    val id: String, val name: String, val createdAt: Long, val updatedAt: Long,
    val randomState: Long, val player: Character, val world: WorldState,
    val rules: GameRules = GameRules(), val realms: List<Realm> = Realms.defaults,
    val adventure: AdventureState? = null,
)
