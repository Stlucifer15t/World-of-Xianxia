package com.immortalworld.core

import kotlin.math.roundToInt

/** All mutations happen on immutable local copies. A rejected operation commits nothing. */
class AdventureEngine(private val clock: () -> Long = System::currentTimeMillis) : GameEngine {
    fun execute(save: GameSave, action: GameAction, input: String, id: String): EngineResult {
        val original = AdventureFactory.ensure(save)
        original.adventure!!.history.find { it.id == id }?.let { return EngineResult(original, it.facts, it.calculations) }
        require(input.isNotBlank() && input.length <= 2000 && id.length in 1..100)
        var accepted = true
        val result = try { apply(original, action) } catch (e: IllegalArgumentException) {
            accepted = false; EngineResult(original, listOf(e.message ?: "That action is not valid here."), emptyList())
        }
        val a = result.state.adventure!!
        val record = ActionRecord(id, input, result.state.world.day, a.hour, result.facts, result.calculations, accepted = accepted)
        return result.copy(state = SaveValidator.validate(result.state.copy(adventure = a.copy(history = (a.history + record).takeLast(100)))))
    }

    override fun apply(state: GameSave, action: GameAction): EngineResult {
        var s = AdventureFactory.ensure(SaveValidator.validate(state))
        var a = s.adventure!!
        var p = s.player
        val rng = WorldRandom(s.randomState)
        val facts = mutableListOf<String>(); val calculations = mutableListOf<String>(); val developments = mutableListOf<String>()
        var elapsed = 0
        fun fact(text: String) { facts += text }
        fun npc(id: String) = a.npcs.firstOrNull { it.character.id == id && it.locationId == a.locationId }
            ?: throw IllegalArgumentException("That NPC is not at your location. Inspect the NPC list for their current whereabouts.")
        fun updateNpc(n: Npc) { a = a.copy(npcs = a.npcs.map { if (it.character.id == n.character.id) n else it }) }
        fun remember(n: Npc, text: String, delta: Int = 0) = n.copy(relationship = (n.relationship + delta).coerceIn(-100, 100), memories = (n.memories + text.take(1000)).takeLast(20))
        fun stones(cost: Int) {
            require(s.rules.godMode || p.spiritStones >= cost) { "You need $cost spirit stones." }
            if (!s.rules.godMode) p = p.copy(spiritStones = p.spiritStones - cost)
        }
        fun consume(kind: String, count: Int) {
            if (count == 0 || s.rules.godMode) return
            require(p.inventory.filter { it.kind == kind }.sumOf { it.quantity } >= count) { "You need $count $kind resources." }
            var remaining = count
            p = p.copy(inventory = p.inventory.mapNotNull { item ->
                if (item.kind != kind || remaining == 0) item else {
                    val used = minOf(item.quantity, remaining); remaining -= used
                    item.copy(quantity = item.quantity - used).takeIf { it.quantity > 0 }
                }
            })
        }
        fun grant(item: Item) {
            val existing = p.inventory.firstOrNull { it.kind == item.kind && it.name == item.name && it.power == item.power && it.equippedSlot == null && it.maxStack > 1 && it.quantity + item.quantity <= it.maxStack }
            p = if (existing != null) p.copy(inventory = p.inventory.map { if (it.id == existing.id) it.copy(quantity = it.quantity + item.quantity) else it })
            else p.copy(inventory = p.inventory + item.copy(id = "${p.id.take(50)}_${a.sequence}_${p.inventory.size}_${item.kind}"))
            require(p.inventory.size <= 500) { "Your inventory is full." }
        }
        fun revealRuins() { a = a.copy(locations = a.locations.map { if (it.id == "ruins") it.copy(discovered = true) else it }) }
        fun useItem(id: String) {
            val item = p.inventory.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Choose an item in your inventory.")
            require(item.kind == "pill") { "Only medicinal pills can be consumed directly. Herbs/materials are used by activities; equipment can be equipped." }
            val amount = minOf(item.power, p.maxHealth - p.health)
            require(amount > 0) { "Your health is already full." }
            p = p.copy(health = p.health + amount, inventory = p.inventory.mapNotNull { if (it.id != id) it else it.copy(quantity = it.quantity - 1).takeIf { it.quantity > 0 } })
            fact("Consumed ${item.name}; restored $amount health.")
        }
        fun activity(request: GameAction) {
            require(a.combat == null) { "Finish the encounter before starting an activity." }
            val estimate = Activities.estimate(s.copy(player = p, adventure = a), request)
            require(request.target != "Cultivation" || p.cultivation.progress < 100) { "Progress is full. Choose whether to attempt a breakthrough." }
            require(request.target != "Technique practice" || p.techniques.isNotEmpty()) { "You have no techniques to practice." }
            require(s.rules.godMode || p.energy + estimate.energy >= 0) { "Insufficient energy for that method and duration. Meditate or reduce the duration." }
            consume("herb", estimate.herbs); consume("material", estimate.materials); stones(estimate.stones)
            val hpGain = minOf(estimate.health, p.maxHealth - p.health)
            val energyGain = if (estimate.energy > 0) minOf(estimate.energy, p.maxEnergy - p.energy) else if (s.rules.godMode) 0 else estimate.energy
            p = p.copy(health = p.health + hpGain, energy = p.energy + energyGain,
                cultivation = p.cultivation.copy(progress = (p.cultivation.progress + estimate.progress).coerceAtMost(100.0), method = if (request.target == "Cultivation") request.method else p.cultivation.method),
                techniques = p.techniques.map { it.copy(mastery = (it.mastery + estimate.mastery).coerceAtMost(100)) },
                attributes = p.attributes.copy(strength = (p.attributes.strength + estimate.strength).coerceAtMost(1_000_000), techniqueMastery = (p.attributes.techniqueMastery + estimate.mastery).coerceAtMost(1_000_000)))
            if (request.target == "Meditation") a = a.copy(injury = (a.injury - request.hours * 5).coerceAtLeast(0))
            if (request.target == "Alchemy") grant(Item("new", "Mending pill", "Restores 40 health.", "pill", quantity = request.hours, maxStack = 99, power = 40))
            if (request.target == "Crafting") repeat(request.hours) { grant(Item("new", "Reinforced travel armor", "Provides 5 armor when equipped.", "armor", power = 5)) }
            elapsed = request.hours
            fact("${request.target}: ${request.hours} in-game hours completed. Progress +${"%.2f".format(java.util.Locale.ROOT, estimate.progress)}, health +$hpGain, energy ${if (energyGain >= 0) "+" else ""}$energyGain, strength +${estimate.strength}, mastery training +${estimate.mastery} (capped at 100 per technique).")
            calculations += "Costs: ${estimate.herbs} herbs, ${estimate.materials} materials, ${estimate.stones} stones. Method=${request.method}; spring bonus=${a.locationId == "mountain"}; speed=${s.rules.cultivationSpeed}. Sandbox bypass=${s.rules.godMode}."
            if (request.target == "Alchemy" || request.target == "Crafting") fact("Created ${request.hours} ${if (request.target == "Alchemy") "mending pills" else "armor pieces"}.")
        }
        fun hit(attacker: Character, defender: Character, technique: Technique?, hitModifier: Int = 0, defenseFactor: Double = 1.0, injury: Int = 0): Int {
            val magic = technique?.effect == "lightning_damage"
            val attackStat = if (magic) attacker.attributes.spiritualPower else attacker.attributes.strength
            val weapon = attacker.inventory.filter { it.equippedSlot in setOf("weapon", "artifact") }.sumOf { it.power }
            val armor = defender.inventory.filter { it.equippedSlot == "armor" }.sumOf { it.power }
            val tier = s.realms.indexOfFirst { it.id == attacker.cultivation.realmId }
            val defenseTier = s.realms.indexOfFirst { it.id == defender.cultivation.realmId }
            val chance = (75 + (attacker.attributes.agility - defender.attributes.agility) / 3 + attacker.attributes.spiritualSense / 10 + hitModifier).coerceIn(10, 95)
            val roll = rng.nextInt(100)
            if (roll >= chance) { calculations += "${attacker.name}: hit roll $roll >= $chance; miss, damage 0."; return 0 }
            val critical = rng.nextInt(100) < (3 + attacker.attributes.luck / 8).coerceAtMost(25)
            val offense = (attackStat * 1.4 + weapon + (technique?.power ?: 4)) * (1 + tier * .35 + attacker.cultivation.substage * .04) *
                (1 + (technique?.mastery ?: 0) / 200.0 + attacker.attributes.techniqueMastery / 500.0) * (1 - injury / 100.0)
            val defense = (if (magic) defender.attributes.soulStrength / 4.0 + defender.attributes.spiritualPower / 6.0 else defender.attributes.constitution / 3.0 + armor) * (1 + defenseTier * .3)
            val resistance = if (magic && defender.spiritualRoot == "Lightning") .75 else 1.0
            val damage = ((offense - defense).coerceAtLeast(1.0) * (if (critical) 1.5 else 1.0) * defenseFactor * resistance).coerceIn(1.0, 1_000_000_000.0).roundToInt().coerceAtMost(defender.health)
            calculations += "${attacker.name}: roll $roll < $chance; offense=${offense.roundToInt()}, defense=${defense.roundToInt()}, critical=$critical, mitigation=$defenseFactor, resistance=$resistance; health damage=$damage."
            return damage
        }
        val fighting = a.combat
        val combatKinds = setOf(ActionKind.ATTACK, ActionKind.TECHNIQUE, ActionKind.DEFEND, ActionKind.DODGE, ActionKind.MOVE, ActionKind.RETREAT, ActionKind.NEGOTIATE, ActionKind.USE_ITEM, ActionKind.ATTEMPT)
        require(fighting == null || action.kind in combatKinds || action.kind == ActionKind.OBSERVE) { "Resolve the active encounter first. You can attack, defend, dodge, use an item/technique, move, negotiate, or retreat." }
        if (fighting != null && action.kind in combatKinds) {
            var combat: CombatState? = fighting
            var enemy = fighting.enemy
            require(action.kind != ActionKind.ATTACK || action.target.isBlank() || action.target == enemy.id) { "That is not the active opponent." }
            var incomingHit = 0; var incomingFactor = if (fighting.position == "cover") .8 else 1.0
            when (action.kind) {
                ActionKind.ATTACK, ActionKind.TECHNIQUE -> {
                    val technique = if (action.kind == ActionKind.TECHNIQUE) p.techniques.firstOrNull { it.id == action.target }
                        ?: throw IllegalArgumentException("You do not know that technique.") else null
                    if (technique != null) {
                        require(s.rules.godMode || s.realms.indexOfFirst { it.id == p.cultivation.realmId } >= s.realms.indexOfFirst { it.id == technique.requiredRealmId }) { "Your realm does not meet this technique's requirement." }
                        require(s.rules.godMode || p.energy >= technique.energyCost) { "Not enough spiritual energy." }
                        if (!s.rules.godMode) p = p.copy(energy = p.energy - technique.energyCost)
                    }
                    when (technique?.effect) {
                        "heal" -> { val heal = minOf(technique.power, p.maxHealth - p.health); p = p.copy(health = p.health + heal); fact("${technique.name} restored $heal health.") }
                        "defend" -> { incomingFactor *= .5; fact("${technique.name} raises a defensive ward for this turn.") }
                        else -> { val damage = hit(p, enemy, technique, if (fighting.position == "high_ground") 10 else 0, injury = a.injury); enemy = enemy.copy(health = enemy.health - damage); fact("${technique?.name ?: "Your attack"} dealt $damage damage to ${enemy.name}.") }
                    }
                }
                ActionKind.DEFEND -> { incomingFactor *= .45; fact("You brace for the next attack; incoming damage is reduced this turn.") }
                ActionKind.DODGE -> { incomingHit -= 35; fact("You focus on evasion this turn.") }
                ActionKind.MOVE -> { require(action.target in listOf("cover", "high_ground", "close")) { "Choose cover, high_ground, or close." }; combat = fighting.copy(position = action.target); if (action.target == "cover") incomingFactor = .8; fact("You move to ${action.target.replace('_', ' ')}.") }
                ActionKind.USE_ITEM -> useItem(action.target)
                ActionKind.RETREAT -> {
                    val chance = (55 + (p.attributes.agility - enemy.attributes.agility) / 2).coerceIn(20, 90)
                    val roll = rng.nextInt(100); calculations += "Retreat roll $roll; success below $chance."
                    if (roll < chance) { combat = null; fact("You disengage successfully. You remain at this location; no travel was chosen for you.") } else fact("Your retreat was blocked.")
                }
                ActionKind.NEGOTIATE -> {
                    val n = fighting.npcId?.let { id -> a.npcs.firstOrNull { it.character.id == id } }
                    if (n != null && n.relationship >= 0) { combat = null; fact("${n.character.name} agrees to end the duel without a victory or reward.") }
                    else fact("Your opponent refuses to negotiate.")
                }
                ActionKind.ATTEMPT -> {
                    val chance = (35 + p.attributes.comprehension / 3 + p.attributes.agility / 3 - a.injury).coerceIn(10, 85)
                    val roll = rng.nextInt(100); calculations += "Creative maneuver roll $roll; success below $chance."
                    if (roll < chance) { combat = fighting.copy(position = "high_ground"); fact("Your maneuver creates a positional advantage (+10 hit chance). It does not grant unearned damage or abilities.") }
                    else fact("The maneuver fails to create an opening.")
                }
                else -> Unit
            }
            if (combat != null && enemy.health <= 0) {
                val firstWin = enemy.id !in a.defeated
                a = a.copy(defeated = (a.defeated + enemy.id).distinct().takeLast(100))
                if (firstWin) p = p.copy(spiritStones = (p.spiritStones + 12).coerceAtMost(1_000_000_000), cultivation = p.cultivation.copy(progress = (p.cultivation.progress + 8).coerceAtMost(100.0)))
                fighting.npcId?.let { id -> a.npcs.firstOrNull { it.character.id == id }?.let { updateNpc(remember(it, "Lost a nonlethal duel to ${p.name}.", 2)) } }
                fact("Victory over ${enemy.name}.${if (firstWin) " Earned 12 stones and 8 cultivation progress (capped at 100)." else " No repeat victory reward."} NPC duels are nonlethal.")
                combat = null
            }
            if (combat != null) {
                val skill = enemy.techniques.firstOrNull { it.effect in setOf("physical_damage", "lightning_damage") && it.energyCost <= enemy.energy && s.realms.indexOfFirst { r -> r.id == enemy.cultivation.realmId } >= s.realms.indexOfFirst { r -> r.id == it.requiredRealmId } }?.takeIf { fighting.turn % 3 == 0 }
                if (skill != null) enemy = enemy.copy(energy = enemy.energy - skill.energyCost)
                val difficulty = when (s.rules.difficulty) { "Gentle" -> .8; "Harsh" -> 1.2; else -> 1.0 }
                val damage = if (s.rules.godMode) 0 else hit(enemy, p, skill, incomingHit, incomingFactor * difficulty)
                p = p.copy(health = p.health - damage)
                fact("${enemy.name} ${if (skill == null) "attacks" else "uses ${skill.name}"}: $damage health damage. Difficulty modifier=$difficulty; God Mode protection=${s.rules.godMode}.")
                if (p.health <= 0) {
                    p = p.copy(health = 1); a = a.copy(injury = 35); combat = null
                    fact("Defeated. You are left wounded (35% attack penalty) at your current location. Rest or meditate when you choose; no dialogue or travel was chosen for you.")
                } else combat = combat.copy(enemy = enemy, turn = combat.turn + 1)
            }
            a = a.copy(combat = combat); elapsed = 1
        } else when (action.kind) {
            ActionKind.OBSERVE -> {
                val loc = a.locations.first { it.id == a.locationId }
                fact("${loc.name}: ${loc.description}")
                fact("Nearby: ${a.npcs.filter { it.locationId == a.locationId }.joinToString { it.character.name }.ifEmpty { "no known NPCs" }}.")
                a.combat?.let { fact("${it.enemy.name}: ${it.enemy.health}/${it.enemy.maxHealth} health. Turn ${it.turn}, position ${it.position}.") }
            }
            ActionKind.TRAVEL -> {
                val dest = a.locations.firstOrNull { it.id == action.target } ?: throw IllegalArgumentException("Unknown destination. Inspect the map for valid locations.")
                require(dest.id != a.locationId) { "You are already there." }
                require(s.rules.godMode || (dest.discovered && dest.id in neighbors(a.locationId))) { "That destination is undiscovered or not adjacent. Follow the roads shown on the map." }
                a = a.copy(locationId = dest.id); s = s.copy(world = s.world.copy(currentLocation = dest.name)); elapsed = 2
                fact("You travel to ${dest.name}. ${dest.description}")
            }
            ActionKind.INVESTIGATE -> {
                elapsed = 1
                if (a.locationId == "ruins") {
                    a = a.copy(quests = a.quests.map { if (it.id == "survey" && it.status == "ACTIVE") it.copy(status = "READY") else it })
                    fact("The outer wards bear water-worn formation marks. You can report this survey without entering the wolf's courtyard.")
                } else { if (a.locationId in listOf("crossing", "mountain")) revealRuins(); fact("You examine the ground and routes. ${if (a.locationId in listOf("crossing", "mountain")) "The trail to Moonfall Ruins is now marked on your map." else "A noticeboard lists local quests; Mei trades herbs and pills."}") }
            }
            ActionKind.SEARCH -> {
                require((a.searchCounts[a.locationId] ?: 0) < 3) { "You have exhausted this location's three search opportunities. No resources were spent." }
                elapsed = 1
                if (a.locationId == "ruins" && "wolf" !in a.defeated) {
                    val wolf = CharacterGenerator.generate(CreationOptions(name = "Spirit wolf", race = "Beastkin", background = "Mountain hunter"), s.randomState xor 741L, "wolf").character
                    a = a.copy(combat = CombatState(wolf.copy(cultivation = wolf.cultivation.copy(realmId = s.realms.first().id), techniques = wolf.techniques.map { it.copy(requiredRealmId = s.realms.first().id) }, health = 80, maxHealth = 80, attributes = wolf.attributes.copy(strength = 12, constitution = 10), inventory = emptyList())))
                    fact("The spirit wolf blocks the courtyard. An encounter begins; you choose the first turn.")
                } else {
                    a = a.copy(searchCounts = a.searchCounts + (a.locationId to ((a.searchCounts[a.locationId] ?: 0) + 1)))
                    grant(Item("new", "Dewleaf herb", "A medicinal ingredient.", "herb", quantity = 2, maxStack = 99))
                    if (a.locationId in listOf("mountain", "ruins")) { grant(Item("new", "Iron ore", "Used to craft armor.", "material", quantity = 2, maxStack = 99)); revealRuins() }
                    fact("Found 2 Dewleaf herbs${if (a.locationId in listOf("mountain", "ruins")) " and 2 iron ore; the ruins route is marked" else ""}. Searches remaining here: ${3 - (a.searchCounts[a.locationId] ?: 0)}.")
                }
            }
            ActionKind.TALK -> {
                val n = npc(action.target); elapsed = 1
                val query = action.detail.lowercase()
                val words = query.split(Regex("\\W+")).filter { it.length > 3 }
                val known = n.knowledge.firstOrNull { k -> words.any { k.contains(it, true) } }
                val answer = known ?: if (query.isBlank() || query in listOf("hello", "greetings")) n.goal else "I have no established knowledge about that. ${n.goal}"
                fact("${n.character.name} (${n.character.personality}; opinion ${n.relationship}): $answer")
                updateNpc(remember(n, "${p.name} said: ${action.detail.take(600)}. Response: $answer"))
                if (known?.contains("Moonfall", true) == true) { revealRuins(); fact("The Moonfall route is marked on your map.") }
            }
            ActionKind.NEGOTIATE -> {
                val n = npc(action.target); require(n.role == "merchant") { "This NPC does not trade." }
                require(!n.negotiated) { "A favorable price has already been agreed." }
                val chance = (30 + p.attributes.comprehension / 3 + n.relationship / 2).coerceIn(10, 85)
                val roll = rng.nextInt(100); calculations += "Negotiation roll=$roll, success below $chance."
                updateNpc(remember(n.copy(negotiated = roll < chance), "A price negotiation ${if (roll < chance) "succeeded" else "failed"}.", if (roll < chance) 1 else -1))
                fact(if (roll < chance) "${n.character.name} agrees to a one-stone discount on purchases." else "${n.character.name} declines. No discount was granted."); elapsed = 1
            }
            ActionKind.BUY, ActionKind.SELL -> {
                val n = npc(action.target); require(n.role == "merchant") { "That NPC is not a merchant." }
                if (action.kind == ActionKind.BUY) {
                    val item = n.character.inventory.firstOrNull { it.id == action.detail || it.kind == action.detail } ?: throw IllegalArgumentException("That item is not in the merchant's stock. Buy herb or pill.")
                    val price = (if (item.kind == "pill") 12 else 4) - if (n.negotiated) 1 else 0
                    stones(price); grant(item.copy(quantity = 1, equippedSlot = null))
                    val inv = n.character.inventory.mapNotNull { if (it.id != item.id) it else it.copy(quantity = it.quantity - 1).takeIf { it.quantity > 0 } }
                    updateNpc(remember(n.copy(character = n.character.copy(inventory = inv, spiritStones = (n.character.spiritStones + price).coerceAtMost(1_000_000_000))), "Sold ${item.name} to ${p.name} for $price stones."))
                    fact("Bought one ${item.name} for $price stones.")
                } else {
                    val item = p.inventory.firstOrNull { it.id == action.detail || it.kind == action.detail } ?: throw IllegalArgumentException("You do not possess that item.")
                    require(item.kind in listOf("herb", "material", "pill") && item.equippedSlot == null) { "The merchant buys unequipped herbs, materials, and pills only." }
                    val price = if (item.kind == "pill") 6 else 2
                    require(n.character.spiritStones >= price) { "The merchant cannot afford that trade." }
                    p = p.copy(inventory = p.inventory.mapNotNull { if (it.id != item.id) it else it.copy(quantity = it.quantity - 1).takeIf { it.quantity > 0 } }); p = p.copy(spiritStones = (p.spiritStones + price).coerceAtMost(1_000_000_000))
                    val existing = n.character.inventory.firstOrNull { it.kind == item.kind && it.quantity < it.maxStack }
                    require(existing != null || n.character.inventory.size < 500) { "The merchant has no storage space." }
                    val inventory = if (existing != null) n.character.inventory.map { if (it.id == existing.id) it.copy(quantity = it.quantity + 1) else it } else n.character.inventory + item.copy(id = "trade_${a.sequence}", quantity = 1)
                    updateNpc(remember(n.copy(character = n.character.copy(spiritStones = n.character.spiritStones - price, inventory = inventory)), "Bought ${item.name} from ${p.name}."))
                    fact("Sold one ${item.name} for $price stones.")
                }; elapsed = 1
            }
            ActionKind.ACCEPT_QUEST, ActionKind.TURN_IN -> {
                val q = a.quests.firstOrNull { it.id == action.target } ?: throw IllegalArgumentException("Choose a named quest from the quest log.")
                val n = npc(q.giverId)
                if (action.kind == ActionKind.ACCEPT_QUEST) {
                    require(q.status == "AVAILABLE") { "That quest is already accepted or completed." }
                    a = a.copy(quests = a.quests.map { if (it.id == q.id) it.copy(status = "ACTIVE") else it }); fact("Accepted ${q.name}. ${q.objective}")
                } else {
                    require(q.status in listOf("ACTIVE", "READY")) { "That quest is not active, or its reward was already claimed." }
                    if (q.id == "dewleaf") consume("herb", 2) else require(q.status == "READY") { "Investigate Moonfall Ruins before reporting back." }
                    a = a.copy(quests = a.quests.map { if (it.id == q.id) it.copy(status = "COMPLETE") else it })
                    p = p.copy(spiritStones = (p.spiritStones + q.reward).coerceAtMost(1_000_000_000))
                    updateNpc(remember(n, "${p.name} fulfilled ${q.name}.", 5)); a = a.copy(factions = a.factions.map { it.copy(reputation = (it.reputation + 5).coerceAtMost(100)) })
                    fact("Completed ${q.name}; received ${q.reward} stones and 5 local sect reputation. Reward cannot be claimed twice.")
                }; elapsed = 1
            }
            ActionKind.JOIN -> {
                npc("wen")
                val f = a.factions.firstOrNull { it.id == action.target } ?: throw IllegalArgumentException("Unknown faction.")
                require(!f.member) { "You already belong to this faction." }
                require(s.rules.godMode || ((s.realms.indexOfFirst { it.id == p.cultivation.realmId } > 0 || p.cultivation.substage >= 2) && f.reputation >= 5)) { "Elder Wen requires Qi Refining stage 2 or higher and 5 sect reputation. Complete a local quest to prove service." }
                a = a.copy(factions = a.factions.map { if (it.id == f.id) it.copy(member = true) else it }); elapsed = 1; fact("You join ${f.name} as an outer disciple, by your explicit request.")
            }
            ActionKind.CHALLENGE -> {
                val n = npc(action.target); require(n.character.health > 0) { "That NPC cannot duel now." }
                a = a.copy(combat = CombatState(n.character, n.character.id)); updateNpc(remember(n, "${p.name} challenged me to a nonlethal duel.")); fact("A nonlethal duel with ${n.character.name} begins. You choose the first turn.")
            }
            ActionKind.USE_ITEM -> { useItem(action.target); elapsed = 1 }
            ActionKind.EQUIP -> {
                val item = p.inventory.firstOrNull { it.id == action.target } ?: throw IllegalArgumentException("Item not found.")
                val slot = item.kind.takeIf { it in listOf("weapon", "armor", "artifact") } ?: throw IllegalArgumentException("That item cannot be equipped.")
                require(item.quantity == 1)
                p = p.copy(inventory = p.inventory.map { when { it.id == item.id -> it.copy(equippedSlot = slot); it.equippedSlot == slot -> it.copy(equippedSlot = null); else -> it } })
                fact("Equipped ${item.name} in $slot. Its power is used by combat calculations.")
            }
            ActionKind.REST -> { p = p.copy(health = p.maxHealth, energy = p.maxEnergy); a = a.copy(injury = 0); elapsed = 2; fact("You rest for two in-game hours. Health and energy are restored; injuries recover.") }
            ActionKind.ACTIVITY -> activity(action)
            ActionKind.BREAKTHROUGH -> {
                require(s.rules.godMode || p.cultivation.progress >= 100) { "Reach 100% cultivation progress before attempting a breakthrough." }
                val realm = s.realms.indexOfFirst { it.id == p.cultivation.realmId }
                val ascension = p.cultivation.substage == s.realms[realm].stages
                require(!ascension || realm < s.realms.lastIndex) { "No higher realm is defined in this campaign." }
                stones(10 * (realm + 1))
                val chance = if (s.rules.godMode) 100 else ((65 + p.attributes.talent / 4 + p.attributes.comprehension / 8 - if (ascension && !s.rules.skipTribulations) 15 else 0) / s.rules.breakthroughDifficulty).toInt().coerceIn(5, 95)
                val roll = rng.nextInt(100); calculations += "Breakthrough roll $roll; success below $chance. Cost ${10 * (realm + 1)} stones; sandbox=${s.rules.godMode}."
                if (roll < chance) {
                    val next = s.realms[if (ascension) realm + 1 else realm]
                    p = p.copy(cultivation = p.cultivation.copy(realmId = next.id, substage = if (ascension) 1 else p.cultivation.substage + 1,
                        progress = 0.0, maximumLifespan = maxOf(p.cultivation.maximumLifespan, next.baseLifespan),
                        breakthroughs = (p.cultivation.breakthroughs + "Day ${s.world.day}: reached ${next.name}").takeLast(100),
                        tribulations = if (ascension && !s.rules.skipTribulations) (p.cultivation.tribulations + "Day ${s.world.day}: survived realm tribulation").takeLast(100) else p.cultivation.tribulations))
                    fact("Breakthrough succeeded: ${next.name}, stage ${p.cultivation.substage}.")
                } else { p = p.copy(health = (p.health - p.maxHealth / 10).coerceAtLeast(1), cultivation = p.cultivation.copy(progress = 75.0)); a = a.copy(injury = (a.injury + 10).coerceAtMost(80)); fact("Breakthrough failed. Progress falls to 75%; you suffer injury and lose 10% maximum health (minimum 1 health remains).") }; elapsed = 1
            }
            ActionKind.ATTEMPT -> {
                elapsed = 1
                val chance = (35 + p.attributes.comprehension / 3 + p.attributes.spiritualSense / 4 + p.attributes.luck / 5).coerceIn(10, 85)
                val roll = rng.nextInt(100); calculations += "Investigation/creative attempt roll $roll; success below $chance."
                if (roll < chance) { if (a.locationId in listOf("mountain", "crossing")) revealRuins(); fact("Your approach reveals a useful detail: ${a.locations.first { it.id == a.locationId }.description} No items, victories, NPC promises, or realm changes were assumed from your wording.") }
                else fact("Your attempt yields no reliable new information. No claim of success in your description overrides the rules.")
            }
            ActionKind.SIMULATION_MODE -> { require(action.target in listOf("PAUSED", "NORMAL", "ACCELERATED")); a = a.copy(simulationMode = action.target); fact("Background simulation set to ${action.target}. Actions still consume their normal world time; no player decisions are automated.") }
            ActionKind.ENABLE_OFFLINE -> {
                Activities.estimate(s.copy(adventure = a), action.copy(kind = ActionKind.ACTIVITY))
                a = a.copy(idlePlan = IdlePlan(action.target, action.method, action.useHerb, clock()))
                s = s.copy(rules = s.rules.copy(passiveCultivation = true)); fact("Offline activity armed: ${action.target}. Elapsed whole hours (maximum 24) can be claimed explicitly later. Nothing starts or makes decisions automatically.")
            }
            ActionKind.DISABLE_OFFLINE -> { a = a.copy(idlePlan = null); s = s.copy(rules = s.rules.copy(passiveCultivation = false)); fact("Offline activity disabled. No unclaimed time was simulated.") }
            ActionKind.CLAIM_OFFLINE -> {
                val plan = a.idlePlan ?: throw IllegalArgumentException("No offline activity is armed.")
                val now = clock(); val hours = ((now - plan.checkpointMillis).coerceAtLeast(0) / 3_600_000).coerceAtMost(24).toInt()
                require(s.rules.passiveCultivation && hours > 0) { "No whole offline hour is available yet. You may simulate an activity instantly instead." }
                activity(GameAction(ActionKind.ACTIVITY, plan.activity, hours = hours, method = plan.method, useHerb = plan.useHerb))
                a = a.copy(idlePlan = plan.copy(checkpointMillis = now)); fact("Claimed $hours offline hours. The checkpoint prevents claiming this interval twice; excess beyond 24 hours is discarded.")
            }
            else -> throw IllegalArgumentException("That command requires an active encounter. Challenge a nearby NPC or explore the guarded ruins.")
        }
        require(s.world.day + (a.hour + elapsed) / 24 <= 1_000_000_000L) { "The configured world-time limit has been reached." }
        val days = (a.hour + elapsed) / 24
        s = s.copy(world = s.world.copy(day = s.world.day + days))
        val simElapsed = elapsed * when (a.simulationMode) { "PAUSED" -> 0; "ACCELERATED" -> 3; else -> 1 }
        val previousSimDay = a.simulationHour / 24; val nextSimHour = a.simulationHour + simElapsed
        for (day in previousSimDay + 1..nextSimHour / 24) {
            a = a.copy(factions = a.factions.map { it.copy(resources = (it.resources + 5).coerceAtMost(1_000_000)) },
                npcs = a.npcs.map { n -> when (n.role) {
                    "scout" -> n.copy(locationId = if (day % 2L == 0L) "mountain" else "crossing")
                    "merchant" -> if (day % 3L == 0L) n.copy(character = n.character.copy(inventory = n.character.inventory.map { it.copy(quantity = (it.quantity + 2).coerceAtMost(it.maxStack)) })) else n
                    else -> n
                } })
            developments += "Scheduled day $day: the Azure Cloud Sect receives 5 resources; Ren patrols ${if (day % 2L == 0L) "Silent Pine Mountain" else "Jade River Crossing"}.${if (day % 3L == 0L) " Mei replenishes existing stock by two." else ""}"
        }
        val ageDelta = ((s.world.day - 1) / 365 - (state.world.day - 1) / 365).toInt()
        if (s.rules.lifespanEnabled && ageDelta > 0) p = p.copy(age = (p.age + ageDelta).coerceAtMost(1_000_000_000))
        a = a.copy(hour = (a.hour + elapsed) % 24, simulationHour = nextSimHour, sequence = a.sequence + 1)
        if (elapsed > 0) calculations += "World time +$elapsed hour(s); background simulation +$simElapsed hour(s)."
        val events = (facts + developments).mapIndexed { index, text -> WorldEvent("event_${a.sequence}_$index", s.world.day, a.hour, text.take(2000)) }
        a = a.copy(events = (a.events + events).takeLast(200))
        return EngineResult(SaveValidator.validate(s.copy(player = p, adventure = a, randomState = rng.state)), facts + developments, calculations)
    }

    companion object {
        fun neighbors(id: String): List<String> = when (id) {
            "village" -> listOf("crossing", "origin"); "crossing" -> listOf("village", "mountain")
            "mountain" -> listOf("crossing", "ruins"); "ruins" -> listOf("mountain"); else -> listOf("village")
        }
    }
}
