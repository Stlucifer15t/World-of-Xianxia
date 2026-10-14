package com.immortalworld.core

/** Future gameplay modules return validated state transitions; providers never receive a mutator. */
data class EngineResult(val state: GameSave, val facts: List<String>, val calculations: List<String>)
sealed interface GameAction {
    data class Cultivate(val days: Int) : GameAction
    data class Travel(val locationId: String) : GameAction
    data class Inspect(val entityId: String) : GameAction
}
interface GameEngine { fun apply(state: GameSave, action: GameAction): EngineResult }
data class NarrativeContext(val playerName: String, val location: String, val authoritativeFacts: List<String>, val relevantMemories: List<String>)
interface Storyteller {
    suspend fun proposeAction(input: String, context: NarrativeContext): GameAction
    suspend fun narrate(context: NarrativeContext): String
}
interface WorldGenerator { fun generate(seed: Long, startingLocation: String): WorldState }
interface CombatEngine { fun resolve(state: GameSave, encounterId: String, actionId: String): EngineResult }
