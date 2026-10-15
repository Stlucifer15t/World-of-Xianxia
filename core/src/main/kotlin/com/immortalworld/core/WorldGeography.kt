package com.immortalworld.core

/** Undirected, persisted foot routes. No screen or narrator is allowed to infer shortcuts. */
data class TravelRoute(val id: String, val from: String, val to: String, val hours: Int, val description: String) {
    fun other(location: String): String? = when (location) { from -> to; to -> from; else -> null }
}

object WorldGeography {
    fun route(a: AdventureState, from: String, to: String): TravelRoute? = a.routes.firstOrNull { it.other(from) == to }
    fun neighbors(a: AdventureState, id: String): List<String> = a.routes.mapNotNull { it.other(id) }
    fun unexploredNeighbors(a: AdventureState): List<Location> = a.locations.filter {
        !it.discovered && it.id in neighbors(a, a.locationId)
    }

    /** Preserve the established starter roads and old custom-refuge connection, not a new region. */
    fun legacyRoutes(locations: List<Location>): List<TravelRoute> {
        val ids = locations.map { it.id }.toSet()
        val pairs = listOf("village" to "crossing", "crossing" to "mountain", "mountain" to "ruins") +
            locations.filter { it.id !in setOf("village", "crossing", "mountain", "ruins") }.map { "village" to it.id }
        return pairs.filter { it.first in ids && it.second in ids }.mapIndexed { i, (from, to) ->
            TravelRoute("legacy_road_$i", from, to, 2, SceneNarrator.route(from, to)
                ?: "The recorded refuge road joins these locations. No other settlement lies on this connecting route.")
        }
    }

    /** A small connected region, generated once on its own RNG stream and then stored in full. */
    fun expand(save: GameSave): GameSave {
        val s = AdventureFactory.ensure(save)
        val a = s.adventure!!
        require(a.regionSeed == null) { "This campaign already has its frontier. Explore it; it cannot be rerolled." }
        require(a.locations.size <= 122 && a.routes.size <= 250) { "This map has no capacity for another six locations." }
        require((1..6).none { i -> a.locations.any { it.id == "frontier_$i" } || a.routes.any { it.id == "frontier_road_$i" } }) {
            "Reserved frontier IDs already exist. Existing locations and roads were not changed."
        }
        val seed = s.randomState xor s.world.id.hashCode().toLong() xor 0x5849414E584941L
        val rng = WorldRandom(seed)
        val prefixes = mutableListOf("Amber", "White Crane", "Cloudshadow", "Copper", "Autumn", "Moonreed", "Jade Mist", "Red Willow", "Silver Rain", "Dawn", "Hollow Bell", "Blue Heron")
        val terrain = listOf(
            Triple("Grove", "herbs", "Bamboo stems crowd a shallow hollow. Dew gathers beneath the leaves, and soft earth holds the marks of small animals. Medicinal growth spreads along the shaded margins."),
            Triple("Stone Basin", "ore", "Bands of exposed mineral cross the floor of a dry stone basin. Loose fragments collect below the rock faces; wind whistles through narrow cracks."),
            Triple("Wetland", "herbs", "Reeds fringe pools of shallow water. Firm patches of ground join the raised banks, where low medicinal plants grow among the roots."),
            Triple("Terraces", "herbs_and_ore", "Weathered terrace walls step across a sheltered slope. Plants root in the seams between mineral-streaked stones; broken drainage channels carry the sound of running water."),
        )
        val paths = listOf("A firm earth trail follows the contour of the slope, with exposed roots marking its bends.",
            "A gravel footpath runs beside a drainage channel. Flat stones mark the crossings over the water.",
            "A ridge track winds between low rock outcrops. Sheltered turns break the line of the climb.")
        val added = mutableListOf<Location>(); val roads = mutableListOf<TravelRoute>()
        val anchors = listOf("village", "crossing", "mountain").filter { id -> a.locations.any { it.id == id } }.ifEmpty { listOf(a.locationId) }
        repeat(3) { branch ->
            var from = anchors[branch % anchors.size]
            repeat(2) { depth ->
                val i = branch * 2 + depth + 1
                val prefix = prefixes.removeAt(rng.nextInt(prefixes.size))
                val type = rng.pick(terrain)
                val location = Location("frontier_$i", "$prefix ${type.first}", type.third, discovered = false, forage = type.second)
                added += location
                roads += TravelRoute("frontier_road_$i", from, location.id, 1 + rng.nextInt(4), rng.pick(paths))
                from = location.id
            }
        }
        return SaveValidator.validate(s.copy(adventure = a.copy(locations = a.locations + added, routes = a.routes + roads, regionSeed = seed)))
    }
}
