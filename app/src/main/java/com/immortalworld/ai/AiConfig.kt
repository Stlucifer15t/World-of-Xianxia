package com.immortalworld.ai

/** Device configuration only. Secrets never enter a campaign or a Compose saved-state bundle. */
data class AiConfig(
    val enabled: Boolean = false, val endpoint: String = "", val model: String = "", val apiKey: String = "", val temperature: Double = .6,
    val autoConnect: Boolean = true, val streaming: Boolean = true,
    val contextTokens: Int = 32768, val manualContext: Boolean = false, val maxOutputTokens: Int = 1200,
    val knowledgeEnabled: Boolean = false, val sources: List<String> = emptyList(), val searchEndpoint: String = "",
) {
    override fun toString() = "AiConfig(enabled=$enabled, model=$model, credentials=REDACTED)"
}
data class AiProfile(val id: String, val name: String, val config: AiConfig)
data class AiProfileStore(val activeId: String, val profiles: List<AiProfile>, val warning: String? = null, val unreadableIds: Set<String> = emptySet()) {
    val active: AiProfile get() = profiles.first { it.id == activeId }
}
data class ProviderModel(val id: String, val contextTokens: Int? = null, val maxOutputTokens: Int? = null)
data class ModelCatalog(val endpoint: String, val models: List<ProviderModel>)
