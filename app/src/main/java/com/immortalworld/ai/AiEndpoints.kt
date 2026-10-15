package com.immortalworld.ai

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Resolve once by convention, never probe hosts or retry a paid request at guessed paths. */
internal object AiEndpoints {
    data class Routes(val chat: HttpUrl, val models: HttpUrl, val geminiCatalog: Boolean)

    fun resolve(value: String, allowHttpForTests: Boolean = false): Routes {
        require(value.length <= 500) { "API URL must be at most 500 characters." }
        val url = value.toHttpUrlOrNull() ?: throw AiFailure("Enter an HTTPS API base URL or full chat/completions URL.")
        require((url.isHttps || allowHttpForTests) && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "Use an HTTPS API URL without credentials, query parameters, or a fragment."
        }
        val path = url.encodedPath.trimEnd('/')
        val base = path.removeSuffix("/chat/completions")
        val chat = url.newBuilder().encodedPath("$base/chat/completions").build()
        val gemini = chat.host == "generativelanguage.googleapis.com" && chat.encodedPath == "/v1beta/openai/chat/completions"
        val models = url.newBuilder().encodedPath(if (gemini) "/v1beta/models" else "$base/models")
            .apply { if (gemini) addQueryParameter("pageSize", "1000") }.build()
        return Routes(chat, models, gemini)
    }
}
