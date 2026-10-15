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
        val gemini = endpoint.host == "generativelanguage.googleapis.com" && endpoint.encodedPath == "/v1beta/openai/chat/completions"
        val url = endpoint.newBuilder().encodedPath(if (gemini) "/v1beta/models" else path).apply { if (gemini) addQueryParameter("pageSize", "1000") }.build()
        val request = Request.Builder().url(url).get().apply {
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
