package com.immortalworld.ai

import org.json.JSONArray
import org.json.JSONObject

/** Safe local diagnostics: never echo a provider body, model-supplied error or credential. */
internal object ChatResponse {
    const val FORMAT = "The provider returned an incompatible response. Try turning off ‘Show responses as they arrive’ in Limits, then save and retry."
    const val INCOMPLETE = "The stream ended before the model confirmed completion. Retry, or turn off ‘Show responses as they arrive’ in Limits and save."
    const val INTERRUPTED = "The response connection was interrupted or timed out. Your previous story is saved. Retry when the connection is stable."
    const val TOO_LARGE = "The response exceeded the safe story or transfer limit. Reduce Maximum output in Limits or request a shorter response, then retry."
    const val OUTPUT_LIMIT = "The model hit its output limit before finishing. Increase Maximum output in Limits, save, then retry. Thinking tokens may also use this budget."
    const val EMPTY = "The model finished without story text. Check the model and Maximum output in Limits, save, then retry."

    fun finish(choice: JSONObject): String? {
        val reason = choice.opt("finish_reason")
        if (reason == null || reason == JSONObject.NULL) return null
        if (reason !is String) throw AiFailure(FORMAT)
        return when (reason) {
            "stop" -> reason
            "length" -> throw AiFailure(OUTPUT_LIMIT)
            "content_filter" -> throw AiFailure("The provider blocked this response. Revise the scenario or request before retrying; your saved story is unchanged.")
            "tool_calls", "function_call" -> throw AiFailure("The model tried to call a tool instead of returning text. Choose a text-generation model and retry.")
            else -> throw AiFailure(FORMAT)
        }
    }

    fun checkTools(message: JSONObject) {
        val calls = message.opt("tool_calls")
        // Some compatible servers serialize unused optional fields instead of omitting them.
        if (calls != null && calls != JSONObject.NULL && !(calls is JSONArray && calls.length() == 0))
            throw AiFailure("The model tried to call a tool instead of returning text. Choose a text-generation model and retry.")
        val function = message.opt("function_call")
        if (function != null && function != JSONObject.NULL) throw AiFailure(FORMAT)
    }

    fun json(text: String): String = try {
        val obj = CompatibleStoryteller.boundedObject(text)
        if (obj.has("error") && !obj.isNull("error")) throw AiFailure(FORMAT)
        val choice = obj.getJSONArray("choices").getJSONObject(0)
        finish(choice) // Older non-streaming compatible servers omit this optional field.
        val message = choice.getJSONObject("message")
        checkTools(message)
        val content = message.opt("content")
        if (content == null || content == JSONObject.NULL || content == "") throw AiFailure(EMPTY)
        if (content !is String) throw AiFailure(FORMAT)
        if (content.length > 8000) throw AiFailure(TOO_LARGE)
        if (content.isBlank()) throw AiFailure(EMPTY)
        content
    } catch (e: AiFailure) { throw e }
    catch (_: Exception) { throw AiFailure(FORMAT) }
}
