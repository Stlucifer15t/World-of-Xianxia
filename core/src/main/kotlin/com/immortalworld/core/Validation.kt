package com.immortalworld.core

object SaveValidator {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_JOURNAL = 200
    fun validate(s: GameSave): GameSave {
        require(s.format == "xianxia-save") { "This is not an Immortal World save." }
        require(s.version == 3) { "Unsupported save version ${s.version}. Update the application; the original file was not changed." }
        fun text(v: String, label: String, max: Int = 500) {
            require(v.isNotBlank() && v.length <= max && '\u0000' !in v) { "$label must contain 1–$max characters." }
        }
        fun strings(v: List<String>, label: String, max: Int = 100) {
            require(v.size <= max) { "Too many $label." }; v.forEach { text(it, label) }
        }
        text(s.id, "Save ID", 100); text(s.name, "Save name", 80)
        require(s.createdAt >= 0 && s.updatedAt >= s.createdAt) { "Invalid save timestamps." }
        require(s.realms.size in 1..64 && s.realms.map { it.id }.distinct().size == s.realms.size) { "Invalid or duplicate realms." }
        s.realms.forEach {
            text(it.id, "Realm ID", 80); text(it.name, "Realm name", 80)
            require(it.stages in 1..99 && it.baseLifespan in 1..1_000_000_000) { "Invalid realm configuration." }
        }
        val p = s.player
        text(p.id, "Player ID", 100); text(p.name, "Character name", 60)
        listOf(p.gender, p.appearance, p.race, p.birthplace, p.background, p.personality,
            p.spiritualRoot, p.constitution, p.bloodline, p.potential).forEach { text(it, "Character field") }
        require(p.age in 0..1_000_000_000) { "Invalid age." }
        p.attributes.entries().forEach { (name, value) -> require(value in 0..1_000_000) { "$name must be in 0–1,000,000." } }
        require(p.maxHealth in 1..1_000_000_000 && p.health in 0..p.maxHealth) { "Invalid health." }
        require(p.maxEnergy in 1..1_000_000_000 && p.energy in 0..p.maxEnergy) { "Invalid spiritual energy." }
        require(p.spiritStones in 0..1_000_000_000) { "Invalid currency." }
        strings(p.talents, "talents"); strings(p.hiddenTraits, "hidden traits")
        val c = p.cultivation
        val realm = s.realms.find { it.id == c.realmId } ?: error("Unknown cultivation realm.")
        require(c.substage in 1..realm.stages) { "Invalid cultivation substage." }
        require(c.progress.isFinite() && c.progress in 0.0..100.0) { "Invalid cultivation progress." }
        require(c.maximumLifespan in 1..1_000_000_000) { "Invalid lifespan." }
        text(c.method, "Cultivation method"); strings(c.breakthroughs, "breakthrough records"); strings(c.tribulations, "tribulation records")
        require(p.techniques.size <= 200 && p.techniques.map { it.id }.distinct().size == p.techniques.size) { "Invalid or duplicate techniques." }
        p.techniques.forEach {
            text(it.id, "Technique ID", 100); text(it.name, "Technique name", 100)
            text(it.description, "Technique description", 1000); text(it.rank, "Technique rank", 80)
            require(it.energyCost in 0..1_000_000 && it.power in 0..1_000_000 && it.mastery in 0..100) { "Invalid technique values." }
            require(s.realms.any { realm -> realm.id == it.requiredRealmId }) { "Unknown technique realm requirement." }
            require(it.effect in setOf("physical_damage", "lightning_damage", "heal", "defend")) { "Unsupported technique effect." }
        }
        require(p.inventory.size <= 500 && p.inventory.map { it.id }.distinct().size == p.inventory.size) { "Invalid or duplicate inventory IDs." }
        val equipment = p.inventory.mapNotNull { it.equippedSlot }
        require(equipment.distinct().size == equipment.size) { "An equipment slot is occupied twice." }
        p.inventory.forEach {
            text(it.id, "Item ID", 100); text(it.name, "Item name", 100); text(it.description, "Item description", 1000)
            text(it.kind, "Item kind", 80); text(it.rank, "Item rank", 80)
            require(it.maxStack in 1..9999 && it.quantity in 1..it.maxStack && it.power in 0..1_000_000) { "Invalid item values." }
            require(it.equippedSlot == null || (it.equippedSlot in setOf("weapon", "armor", "artifact") && it.quantity == 1)) { "Invalid equipped item." }
        }
        text(s.world.id, "World ID", 100); text(s.world.name, "World name", 100)
        text(s.world.currentLocation, "Location"); text(s.world.startingCircumstances, "Starting circumstances")
        require(s.world.day in 1..1_000_000_000L) { "Invalid world day." }
        require(s.world.journal.size <= MAX_JOURNAL && s.world.journal.map { it.id }.distinct().size == s.world.journal.size) { "Invalid journal." }
        s.world.journal.forEach {
            text(it.id, "Journal ID", 100); text(it.text, "Journal entry", 2000)
            require(it.day in 1..s.world.day) { "Invalid journal day." }
        }
        require(s.rules.difficulty in CreationCatalog.difficulties) { "Unknown difficulty." }
        require(s.rules.cultivationSpeed.isFinite() && s.rules.cultivationSpeed in 0.1..100.0) { "Invalid cultivation speed." }
        require(s.rules.breakthroughDifficulty.isFinite() && s.rules.breakthroughDifficulty in 0.1..10.0) { "Invalid breakthrough difficulty." }
        AdventureValidator.validate(s)
        return s
    }
}
