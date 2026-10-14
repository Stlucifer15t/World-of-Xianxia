package com.immortalworld.ai

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Uses explicit server metadata only—never guesses context capacity from a model's name. */
class ModelDiscovery(private val client: OkHttpClient = AiTransport.metadataClient, private val allowHttpForTests: Boolean = false) {
    suspend fun fetch(config: AiConfig): ModelCatalog {
        CompatibleStoryteller.validate(config.copy(temperature = .6, contextTokens = 32768, maxOutputTokens = 1200, knowledgeEnabled = false, sources = emptyList(), searchEndpoint = ""), allowHttpForTests, requireModel = false)
        val endpoint = config.endpoint.toHttpUrl()
        require(endpoint.encodedPath.trimEnd('/').endsWith("/chat/completions")) { "Automatic listing needs a compatible /chat/completions endpoint. Enter a model and context limit manually." }
        val path = endpoint.encodedPath.trimEnd('/').removeSuffix("/chat/completions") + "/models"
        val url = endpoint.newBuilder().encodedPath(path).build()
        val request = Request.Builder().url(url).get().apply {
            if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
        }.build()
        return AiTransport.execute(client, request) { response ->
            if (!response.isSuccessful) throw AiTransport.failure(response.code)
            ModelCatalog(config.endpoint, parse(AiTransport.readBody(response, 2 * 1024 * 1024)))
        }
    }
    companion object {
        fun parse(text: String): List<ProviderModel> {
            val root = CompatibleStoryteller.boundedObject(text)
            val data = root.optJSONArray("data") ?: throw AiFailure("Model listing unavailable. Enter the model manually.")
            require(data.length() <= 5000) { "Model catalog is too large. Use manual entry." }
            fun positive(value: Any?): Int? = when (value) {
                is Int -> value.takeIf { it in 1..10_000_000 }
                is Long -> value.takeIf { it in 1..10_000_000 }?.toInt()
                else -> null
            }
            return (0 until data.length()).mapNotNull { index ->
                val m = data.optJSONObject(index) ?: return@mapNotNull null
                val id = m.opt("id") as? String ?: return@mapNotNull null
                if (id.isBlank() || id.length > 200) return@mapNotNull null
                val capacity = positive(m.opt("context_length")) ?: positive(m.opt("context_window")) ?: positive(m.opt("max_context_tokens"))
                val output = positive(m.opt("max_output_tokens")) ?: positive(m.optJSONObject("top_provider")?.opt("max_completion_tokens"))
                ProviderModel(id, capacity, output)
            }.distinctBy { it.id }.sortedBy { it.id }
        }
    }
}
