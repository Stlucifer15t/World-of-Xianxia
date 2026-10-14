package com.immortalworld.core

/** Read-only presentation of the existing world. Rendering never rolls RNG or advances time. */
object SceneNarrator {
    private val oldDescriptions = mapOf(
        "village" to "A herbalist's stall faces a sect noticeboard. Mei tends her stock; Elder Wen receives applicants.",
        "crossing" to "Caravans pause at the ford. The water and tracks offer clues to the surrounding hills.",
        "mountain" to "A spiritual spring lies beneath ancient pines. Scout Ren patrols the mountain trail.",
        "ruins" to "Broken wards shelter medicinal herbs and ore. A territorial spirit wolf guards the inner courtyard.",
    )
    private val descriptions = mapOf(
        "village" to "Azure Cloud Village gathers around the stone-paved mountain road. Timber shopfronts lean over the lane, their deep eaves sheltering a herbalist's counter and the broad sect noticeboard opposite it. The smell of dried medicinal roots hangs close to the stalls; beyond the roofs, the road climbs into the foothills. Faded strips of red paper cling to a lintel, and a stone gutter runs from the higher end of the street toward the road. The counters stand close enough for quiet bargaining to be heard without carrying across the whole lane.",
        "crossing" to "At Jade River Crossing, the mountain road opens onto a gravel bank. Cart ruts converge beside the ford, and wooden rails mark the firm approaches to the water. The river carries the scent of wet stone and reeds; its steady rush softens sounds from the far bank.",
        "mountain" to "The path on Silent Pine Mountain narrows between old trunks and exposed roots. Beneath the pines, a spiritual spring gathers in a shallow stone basin before slipping away through the undergrowth. Resin scents the air. The trail to the crossing falls away behind the trees, while broken stonework lies farther along the ridge.",
        "ruins" to "Moonfall Ruins break the line of the wooded ridge. A damaged arch opens onto an outer court, where faded ward-marks run across weathered stone. Loose fragments lie between the paving slabs; beyond them, a second threshold leads into the enclosed courtyard. Wind moves through gaps where doors once hung.",
    )
    /** Only upgrade exact stock descriptions. An edited/custom location is never overwritten. */
    fun upgradeLocation(location: Location): Location = if (location.description == oldDescriptions[location.id])
        location.copy(description = descriptions.getValue(location.id)) else location

    fun timeOfDay(hour: Int): String = when (hour) { in 5..8 -> "Early morning"; in 9..16 -> "Daylight"; in 17..20 -> "Evening"; else -> "Night" }
    /** Cosmetic weather follows the saved world ID and six-hour clock, not mutable generation RNG. */
    fun weather(s: GameSave): String {
        val phase = (s.adventure?.hour ?: 8) / 6
        return listOf("Clear", "Breeze", "Drizzle", "Mist")[Math.floorMod(s.world.id.hashCode().toLong() + s.world.day * 31 + phase * 17, 4L).toInt()]
    }
    private fun atmosphere(s: GameSave): String {
        val hour = s.adventure!!.hour
        val light = when (hour) {
            in 5..8 -> "Morning light lies low across the open ground, catching edges that will fall into shadow later in the day."
            in 9..16 -> "Daylight reaches the open ground, leaving the sheltered corners in softer shadow."
            in 17..20 -> "The light is thinning; long shadows reach across the open ground."
            else -> "Darkness leaves the distant outlines indistinct, and small sounds carry clearly."
        }
        return light + " " + when (weather(s)) {
            "Clear" -> "No rain touches the exposed ground; the air carries the scents of the place without a damp haze."
            "Breeze" -> "A light breeze moves loose cloth and foliage, bringing the scents of the surrounding country."
            "Drizzle" -> "Fine rain darkens the exposed stone. Drops gather along sheltered edges and fall at uneven intervals."
            else -> "Mist blurs the farther outlines. Nearby sounds remain distinct, but the distance is veiled."
        }
    }
    /** Unnamed background activity, not spawned quest-givers or fabricated persistent characters. */
    fun ambientActivity(s: GameSave): String {
        val a = s.adventure!!; val day = a.hour in 6..19
        if (a.combat != null) return "The space around ${a.combat.enemy.name} is occupied by the encounter. Nearby sounds give way to the movements of the opposing combatants."
        return when (a.locationId) {
            "village" -> if (day) when ((s.world.day + a.hour / 3) % 3) {
                0L -> "Porters guide a handcart between the storefronts while prospective disciples linger near the posted notices. A worker pauses to steady a loose awning as people pass beneath it."
                1L -> "The lane is busy without being hurried: travellers compare supplies under the eaves, and workers clear a narrow passage for loaded baskets. Conversation rises and falls around the counters."
                else -> "A few cultivators in travel-worn robes pass along the mountain road. Near the shops, the scrape of a broom and the clink of weighed goods mingle with low bargaining voices."
            } else "The daytime crowd has thinned. A watch lantern moves along the lane, and the occasional traveller's footsteps sound against the stone. Voices near the lit counters are quieter now."
            "crossing" -> if (day) "Travellers wait for a clear passage at the ford while a cart handler checks the bank before guiding a load forward. Their conversation is partly lost beneath the river's rush." else "The ford is quiet at this hour. The water remains audible, but the usual traffic has thinned to an occasional light along the approaches."
            "mountain" -> if (day) "An occasional traveller's footfall carries along the lower path, then fades beneath the pines. Around the spring, falling water fills the pauses between sounds from the trail." else "The trail is quiet. Water continues to slip through the stone basin, and branches stir beyond the reach of the available light."
            "ruins" -> if ("wolf" in a.defeated) "The courtyard lies quiet. Its guardian has been defeated, leaving the broken thresholds unobstructed." else "A low growl carries from beyond the inner threshold. The outer ward-stones can be examined without crossing into the guarded courtyard."
            else -> "The refuge meets the roads beyond it. No crowd is visible at present, and nearby sounds are easy to pick out."
        }
    }
    fun npcActivity(s: GameSave, n: Npc): String {
        val a = s.adventure!!; val name = n.character.name
        if (a.combat?.npcId == n.character.id) return "$name remains engaged in the duel, watching the space between the combatants rather than attending to other work."
        if (n.character.health < n.character.maxHealth / 4) return "$name moves carefully, the effects of earlier injuries still visible. The usual work has slowed to small, guarded movements."
        val night = a.hour !in 6..19
        val manner = when {
            n.relationship <= -20 -> " The greeting, if any, is guarded."
            n.relationship >= 5 -> " A familiar nod acknowledges the earlier dealings without interrupting the work."
            n.character.personality.contains("Proud", true) -> " The movements are deliberate, with little time wasted on idle conversation."
            n.character.personality.contains("Cautious", true) -> " Each small task receives a second, careful look."
            else -> " The work continues at an unhurried pace."
        }
        val work = when (n.role) {
            "merchant" -> {
                val herbs = n.character.inventory.any { it.kind == "herb" && it.quantity > 0 }
                when {
                    n.character.inventory.isEmpty() -> "$name studies a ledger beside an empty counter; the stock has been exhausted."
                    !herbs -> "$name checks an empty herb tray before turning to the remaining supplies."
                    night -> "$name has set a lamp beside the medicines and is checking the remaining bundles against a ledger."
                    else -> "$name separates medicinal roots into shallow trays, straightening the bundles before checking their labels."
                }
            }
            "elder" -> {
                val member = a.factions.any { it.member }
                if (night) "$name checks a small stack of papers by lantern-light beside the sect noticeboard."
                else if (member) "$name reviews sect business near the noticeboard. Your enrolment remains listed among the sect's records."
                else "$name compares papers with the requirements on the sect noticeboard, occasionally looking up from the work."
            }
            "scout" -> if (a.locationId == "mountain") "$name pauses along the pine trail to check the approaches to the spring, then turns an attentive gaze back down the path."
                else "$name watches the crossing from the bank, checking the passing traffic and the road beyond it."
            else -> "$name is here, visible among the surroundings. ${n.character.appearance}"
        }
        return work + manner
    }
    fun scene(s: GameSave): List<String> {
        val a = s.adventure!!; val loc = a.locations.first { it.id == a.locationId }
        val visits = a.visits[a.locationId] ?: 1
        val opening = if (visits <= 1 || loc.description != descriptions[loc.id]) loc.description else when ((visits.toLong() + s.world.day + a.hour / 6) % 3) {
            0L -> "The familiar approach to ${loc.name} opens into view again. Small movements and sounds draw attention among the established landmarks."
            1L -> "${loc.name} is familiar ground now. The same landmarks frame a different moment in the day, with the small sounds of the place coming forward before its wider details."
            else -> "Back at ${loc.name}, the setting is recognizable, but the passing hours have left their own impression on the light and the activity around it."
        }
        val present = a.npcs.filter { it.locationId == a.locationId }
        val people = present.take(4).joinToString(" ") { npcActivity(s, it) }
        val developments = if (a.locationId == "village" && a.factions.any { it.resources > 100 } && present.any { it.role == "elder" })
            " Fresh supply entries have been added to the visible section of the sect ledger." else ""
        val last = listOf(ambientActivity(s) + developments,
            if ((a.searchCounts[a.locationId] ?: 0) >= 3) "The searched ground bears the marks of earlier gathering. Its accessible herbs and ore have already been collected." else "")
            .filter { it.isNotEmpty() }.joinToString(" ")
        return listOf(opening + " " + atmosphere(s), people, last).filter { it.isNotBlank() }
    }
    fun route(from: String, to: String): String? = when (setOf(from, to)) {
        setOf("village", "crossing") -> "The broad mountain road follows the cultivated foothills down toward the river; cart ruts and stretches of paving mark the approach to the ford."
        setOf("crossing", "mountain") -> "A narrower trail connects the river bank to the pine-covered slope, winding between exposed roots and stretches of stony ground."
        setOf("mountain", "ruins") -> "The ridge path threads through the pines toward broken stonework; the last stretch passes weathered fragments before reaching the outer arch."
        setOf("origin", "village") -> "The established refuge road connects to the village's mountain road without passing through another mapped settlement."
        else -> null
    }?.let { description ->
        when {
            from == "crossing" && to == "village" -> "The broad road climbs away from the ford, following the cultivated foothills toward the village's timber shopfronts. Cart ruts and stretches of paving mark the route."
            from == "ruins" && to == "mountain" -> "The ridge path leads away from the ruined arch, passing weathered fragments before returning to the shelter of the pines and the spring."
            else -> description
        }
    }
    fun action(before: GameSave, request: GameAction, result: EngineResult): String {
        val s = result.state; val a = s.adventure!!
        val primary = result.facts.filterNot { it.startsWith("Scheduled day ") }
        val description = when (request.kind) {
            ActionKind.TRAVEL -> {
                val road = route(before.adventure!!.locationId, a.locationId)
                val travel = if (road == null && a.locationId !in AdventureEngine.neighbors(before.adventure!!.locationId)) "The surroundings change to ${s.world.currentLocation}. This passage has no established connecting road; it was permitted by the enabled sandbox travel rules and takes two hours."
                    else if (road == null) "The recorded road connects ${before.world.currentLocation} to ${s.world.currentLocation}. The journey takes two hours; the land along this custom route has no recorded description."
                    else "$road The journey from ${before.world.currentLocation} to ${s.world.currentLocation} takes two hours."
                listOf(travel) + scene(s)
            }
            ActionKind.OBSERVE -> if (request.target.isBlank()) scene(s) else listOf(primary.joinToString(" "))
            ActionKind.TALK -> {
                val n = a.npcs.firstOrNull { it.character.id == request.target }
                listOf(if (n == null) primary.joinToString(" ") else if (n.locationId != a.locationId) "${n.character.name} gives the reply before leaving along the patrol route. The place where the conversation took place is quiet again." else npcActivity(s, n), primary.filter { it != result.dialogue }.joinToString(" "))
            }
            ActionKind.ACTIVITY, ActionKind.CLAIM_OFFLINE -> {
                val activity = if (request.kind == ActionKind.CLAIM_OFFLINE) before.adventure!!.idlePlan?.activity else request.target
                val work = when (activity) {
                    "Cultivation" -> "The measured circulation of ${s.player.cultivation.method} gives the practice its rhythm. The surrounding sounds continue while the allotted practice draws to its close."
                    "Meditation" -> "The allotted quiet passes without demanding a new commitment. Nearby sounds become distinct again as the period of meditation ends."
                    "Technique practice" -> "Repetition gives the practice its shape: familiar forms, corrected timing, then another measured attempt. The training ends with the learned techniques still your own to choose in an encounter."
                    "Body training" -> "The exercise is plain, patient work rather than a spectacle. The allotted training ends against the ordinary sounds of the location."
                    "Alchemy" -> "The sharp scent of prepared herbs lingers after the batch is finished. The mending pills made in this session are now among your possessions."
                    else -> "The work is finished, leaving new armor ready for inspection. It has not yet replaced the equipment already being worn."
                }
                listOf(work + " " + if (s.player.cultivation.progress >= 100) "At the edge of the stage, the circulation cannot carry progress any farther. Further advancement calls for a deliberate breakthrough." else "")
            }
            ActionKind.REST -> listOf("The period of rest passes against the ordinary sounds of the surroundings. ${primary.firstOrNull().orEmpty()} ${atmosphere(s)}")
            ActionKind.BREAKTHROUGH -> listOf(primary.joinToString(" "), "The unsettled qi gradually steadies. Around it, the place continues to follow its ordinary rhythms. ${ambientActivity(s)}")
            ActionKind.CHALLENGE -> listOf(primary.joinToString(" "), "The immediate space is now a duelling ground. The opponent holds position across the open distance, ready for the first exchange.")
            ActionKind.INVESTIGATE, ActionKind.SEARCH -> listOf(primary.joinToString(" "), if (a.combat != null) "The guarded ground is occupied by an opponent. Beyond the immediate movements, the broken outlines of the ruins remain still." else ambientActivity(s))
            else -> listOf(primary.joinToString(" "))
        }
        return description.filter { it.isNotBlank() }.joinToString("\n\n").take(5000)
    }
}

/** Keeps ordinary prose in paragraphs instead of preserving a sentence-per-line/poetry layout. */
object StoryText {
    fun paragraphs(text: String): List<String> {
        val blocks = text.trim().split(Regex("\\n\\s*\\n+")).map { it.replace(Regex("\\s*\\n\\s*"), " ").trim() }.filter { it.isNotEmpty() }
        if (blocks.size < 2 || blocks.count { it.length < 120 } * 3 < blocks.size * 2) return blocks
        val grouped = mutableListOf<String>(); var buffer = ""
        for (block in blocks) {
            buffer = if (buffer.isEmpty()) block else "$buffer $block"
            if (buffer.length >= 240) { grouped += buffer; buffer = "" }
        }
        if (buffer.isNotEmpty()) grouped += buffer
        return grouped
    }
    fun consolidate(text: String) = paragraphs(text).joinToString("\n\n")
}
