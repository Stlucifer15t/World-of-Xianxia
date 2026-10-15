package com.immortalworld.ai

import okhttp3.*

/** Uses explicit server metadata only—never guesses context capacity from a model's name. */
class ModelDiscovery(private val client: OkHttpClient = AiTransport.metadataClient, private val allowHttpForTests: Boolean = false) {
    suspend fun fetch(config: AiConfig): ModelCatalog {
        CompatibleStoryteller.validate(config.copy(temperature = .6, contextTokens = 32768, maxOutputTokens = 1200, knowledgeEnabled = false, sources = emptyList(), searchEndpoint = ""), allowHttpForTests, requireModel = false)
        val routes = AiEndpoints.resolve(config.endpoint, allowHttpForTests)
        val gemini = routes.geminiCatalog
        val request = Request.Builder().url(routes.models).get().apply {
            if (config.apiKey.isNotBlank()) header(if (gemini) "x-goog-api-key" else "Authorization", if (gemini) config.apiKey else "Bearer ${config.apiKey}")
        }.build()
        return AiTransport.execute(client, request) { response ->
            if (!response.isSuccessful) throw AiTransport.failure(response.code)
            val body = AiTransport.readBody(response, 2 * 1024 * 1024)
            ModelCatalog(config.endpoint, if (gemini) parseGemini(body) else parse(body))
        }
    }
    companion object {
        fun parseGemini(text: String): List<ProviderModel> {
            val data = CompatibleStoryteller.boundedObject(text).optJSONArray("models")
                ?: throw AiFailure("Model list unavailable. Enter a model ID manually.")
            require(data.length() <= 5000) { "Model list is too large. Use manual entry." }
            fun limit(v: Any?): Int? = when (v) {
                is Int -> v.takeIf { it in 1..10_000_000 }
                is Long -> v.takeIf { it in 1..10_000_000 }?.toInt()
                else -> null
            }
            return (0 until data.length()).mapNotNull { i ->
                val m = data.optJSONObject(i) ?: return@mapNotNull null
                val methods = m.optJSONArray("supportedGenerationMethods") ?: return@mapNotNull null
                if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) return@mapNotNull null
                val id = (m.opt("name") as? String)?.removePrefix("models/") ?: return@mapNotNull null
                if (id.isBlank() || id.length > 200) return@mapNotNull null
                ProviderModel(id, limit(m.opt("inputTokenLimit")), limit(m.opt("outputTokenLimit")))
            }.distinctBy { it.id }.sortedBy { it.id }
        }
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
