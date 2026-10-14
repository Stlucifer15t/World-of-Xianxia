package com.immortalworld.ai

import okio.BufferedSource

/** Bounded SSE framing. A disconnected/truncated draft is never accepted as completed narration. */
object ChatStream {
    fun read(source: BufferedSource, preview: (String) -> Unit): String {
        val text = StringBuilder(); val data = StringBuilder()
        var bytes = 0; var done = false; var finished = false
        var lastUpdate = 0L
        fun frame() {
            if (data.isEmpty()) return
            val value = data.toString().trim(); data.setLength(0)
            if (value == "[DONE]") { done = true; return }
            val obj = CompatibleStoryteller.boundedObject(value)
            require(!obj.has("error")) { "Provider stream reported an error." }
            val choices = obj.optJSONArray("choices") ?: throw AiFailure("Incompatible stream frame.")
            if (choices.length() == 0) return // optional usage-only frame
            val choice = choices.getJSONObject(0)
            val alreadyFinished = finished
            val reason = choice.opt("finish_reason") as? String
            if (reason != null) {
                require(reason == "stop") { "Narration ended incompletely. The offline story is still saved." }
                finished = true
            }
            val delta = choice.optJSONObject("delta") ?: throw AiFailure("Missing stream delta.")
            val content = delta.opt("content")
            if (content is String) {
                require(!alreadyFinished || content.isEmpty()) { "Content arrived after stream completion." }
                text.append(content)
                require(text.length <= 6000) { "Narration exceeded its size limit." }
                val now = System.nanoTime()
                if (now - lastUpdate >= 75_000_000L) { preview(text.toString()); lastUpdate = now }
            } else require(content == null || content == org.json.JSONObject.NULL) { "Invalid stream content." }
            require(!delta.has("tool_calls")) { "Narration cannot invoke tools or actions." }
        }
        while (!source.exhausted() && !done) {
            val line = source.readUtf8LineStrict(16_384)
            bytes += line.toByteArray(Charsets.UTF_8).size + 1
            require(bytes <= 512 * 1024) { "Stream exceeded its transfer limit." }
            if (line.isEmpty()) frame()
            else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
                require(data.length <= 16_384) { "Stream frame is too large." }
            }
        }
        if (!done && data.isNotEmpty()) frame()
        require(done && finished && text.isNotBlank()) { "Stream ended without a complete response. Retry narration only; no action was replayed." }
        preview(text.toString())
        return text.toString()
    }
}
