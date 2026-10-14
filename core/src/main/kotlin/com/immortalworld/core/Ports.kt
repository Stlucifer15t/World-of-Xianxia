package com.immortalworld.core

/** Future gameplay modules return validated state transitions; providers never receive a mutator. */
data class EngineResult(val state: GameSave, val facts: List<String>, val calculations: List<String>, val dialogue: String = "")
data class GameAction(val kind: ActionKind, val target: String = "", val detail: String = "", val hours: Int = 1,
    val method: String = "Still Water Breathing", val useHerb: Boolean = false)
interface GameEngine { fun apply(state: GameSave, action: GameAction): EngineResult }
data class NarrativeContext(val playerName: String, val location: String, val authoritativeFacts: List<String>, val relevantMemories: List<String>, val scene: String = "", val input: String = "",
    val availableTargets: List<String> = emptyList(), val playerSummary: String = "", val pace: String = "brief", val spokenDialogue: String = "", val recentOpenings: List<String> = emptyList(), val referenceNotes: List<String> = emptyList())
interface Storyteller {
    suspend fun proposeAction(input: String, context: NarrativeContext): GameAction
    suspend fun narrate(context: NarrativeContext): String
}
interface WorldGenerator { fun generate(seed: Long, startingLocation: String): WorldState }
interface CombatEngine { fun resolve(state: GameSave, encounterId: String, actionId: String): EngineResult }
