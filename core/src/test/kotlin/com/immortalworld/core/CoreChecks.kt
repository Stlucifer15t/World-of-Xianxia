package com.immortalworld.core

/** The same cases run under JUnit and the dependency-free command-line runner. */
object CoreChecks {
    fun sample(seed: Long = 42): GameSave {
        val generated = CharacterGenerator.generate(CreationOptions(), seed, "player_1")
        return GameSave(id = "save_1", name = "Jade path", createdAt = 1000, updatedAt = 1000,
            randomState = generated.randomState, player = generated.character,
            world = WorldState("world_1", currentLocation = "Azure Cloud Village", startingCircumstances = "A new beginning."))
    }
    private fun rejected(block: () -> Unit) { check(runCatching(block).isFailure) { "Expected invalid input to be rejected" } }
    val cases: Map<String, () -> Unit> = linkedMapOf(
        "valid new campaign" to { check(SaveValidator.validate(sample()) == sample()) },
        "reproducible character generation" to { check(sample(234) == sample(234)) },
        "different seeds vary attributes" to { check((1L..100L).map { sample(it).player.attributes }.distinct().size > 90) },
        "stable random state continuation" to {
            val r = WorldRandom(77); repeat(12) { r.nextInt(100) }; val restored = WorldRandom(r.state)
            repeat(100) { check(r.nextInt(999) == restored.nextInt(999)) }
        },
        "RNG handles negative and extreme seeds" to {
            listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0, -1, 42).forEach { seed ->
                val r = WorldRandom(seed); repeat(1000) { check(r.nextInt(37) in 0..36) }
            }
        },
        "RNG rejects zero bound" to { rejected { WorldRandom(1).nextInt(0) } },
        "all generated presets validate" to {
            CreationCatalog.backgrounds.forEach { background -> CreationCatalog.races.forEach { race ->
                val p = CharacterGenerator.generate(CreationOptions(background = background, race = race), 33, "p").character
                SaveValidator.validate(sample().copy(player = p))
            } }
        },
        "custom identity survives generation" to {
            val options = CreationOptions(name = "  岚月  ", gender = "Agender", race = "Moon Fox", background = "Wandering poet", root = "Fire")
            val p = CharacterGenerator.generate(options, 9, "custom").character
            check(p.name == "岚月" && p.race == "Moon Fox" && p.background == "Wandering poet" && p.spiritualRoot == "Fire")
        },
        "background mechanically affects attributes" to {
            val herbalist = CharacterGenerator.generate(CreationOptions(background = "Village herbalist"), 8, "p").character
            val custom = CharacterGenerator.generate(CreationOptions(background = "Other"), 8, "p").character
            check(herbalist.attributes.vitality == custom.attributes.vitality + 4)
            check(herbalist.maxHealth == custom.maxHealth + 20)
        },
        "rare traits remain rare" to {
            val players = (1L..10000L).map { sample(it).player }
            val ordinary = players.count { it.potential == "Ordinary" }
            val heavenly = players.count { it.potential == "Heaven-touched" }
            check(ordinary in 7000..8000 && heavenly in 20..90) { "Rarity counts: ordinary=$ordinary, heavenly=$heavenly" }
            check(players.count { it.bloodline != "Unawakened" } in 50..160)
        },
        "creation age bounds" to { rejected { CharacterGenerator.generate(CreationOptions(age = 0), 1, "p") } },
        "creation name bounds" to { rejected { CharacterGenerator.generate(CreationOptions(name = " "), 1, "p") } },
        "future versions refused" to { rejected { SaveValidator.validate(sample().copy(version = 999)) } },
        "wrong format refused" to { rejected { SaveValidator.validate(sample().copy(format = "other")) } },
        "negative timestamp refused" to { rejected { SaveValidator.validate(sample().copy(createdAt = -1)) } },
        "time reversal refused" to { rejected { SaveValidator.validate(sample().copy(updatedAt = 0)) } },
        "nonfinite progress refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(cultivation = s.player.cultivation.copy(progress = Double.NaN)))) } },
        "unknown realm refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(cultivation = s.player.cultivation.copy(realmId = "missing")))) } },
        "out of bounds substage refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(cultivation = s.player.cultivation.copy(substage = 10)))) } },
        "negative attributes refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(attributes = s.player.attributes.copy(luck = -1)))) } },
        "million strength allowed without overflow" to { val s = sample(); SaveValidator.validate(s.copy(player = s.player.copy(attributes = s.player.attributes.copy(strength = 1_000_000)))) },
        "attribute overflow refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(attributes = s.player.attributes.copy(strength = Int.MAX_VALUE)))) } },
        "overfilled health refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(health = s.player.maxHealth + 1))) } },
        "invalid stacks refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(inventory = listOf(s.player.inventory.first().copy(quantity = 9))))) } },
        "duplicate item IDs refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(inventory = s.player.inventory + s.player.inventory.first()))) } },
        "duplicate equipment slots refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(inventory = s.player.inventory + s.player.inventory.first().copy(id = "other")))) } },
        "unknown technique effects refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(techniques = listOf(s.player.techniques.first().copy(effect = "grant_infinite_power"))))) } },
        "technique power bounds enforced" to { val s = sample(); rejected { SaveValidator.validate(s.copy(player = s.player.copy(techniques = listOf(s.player.techniques.first().copy(power = Int.MAX_VALUE))))) } },
        "journal bounded" to { val s = sample(); rejected { SaveValidator.validate(s.copy(world = s.world.copy(journal = (1..201).map { JournalEntry("$it", 1, "entry") }))) } },
        "future journal dates refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(world = s.world.copy(journal = listOf(JournalEntry("j", 2, "Tomorrow"))))) } },
        "duplicate realm definitions refused" to { val s = sample(); rejected { SaveValidator.validate(s.copy(realms = s.realms + s.realms.first())) } },
        "invalid speed refused even in god mode" to { val s = sample(); rejected { SaveValidator.validate(s.copy(rules = s.rules.copy(cultivationSpeed = Double.POSITIVE_INFINITY, godMode = true))) } },
        "generation never enables god mode" to { repeat(100) { check(!sample(it.toLong()).rules.godMode) } },
        "custom realm hierarchy can validate" to {
            val s = sample(); SaveValidator.validate(s.copy(realms = s.realms + Realm("beyond", "Beyond the Heavens", 3, 50000000),
                player = s.player.copy(cultivation = s.player.cultivation.copy(realmId = "beyond", substage = 3))))
        },
    )
    @JvmStatic fun main(args: Array<String>) {
        cases.forEach { (name, test) -> test(); println("PASS $name") }
        println("${cases.size} core checks passed")
    }
}
