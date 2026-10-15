package com.immortalworld.ai

import okio.BufferedSource
import org.json.JSONObject
import java.io.IOException

/** Bounded SSE. Explicit model completion is required; a bare disconnect or [DONE] is not enough. */
object ChatStream {
    private const val MAX_FRAME_BYTES = 65_536
    private const val MAX_TRANSFER_BYTES = 512 * 1024

    fun read(source: BufferedSource, preview: (String) -> Unit): String = try {
        readFrames(source, preview)
    } catch (e: AiFailure) { throw e }
    catch (_: IOException) { throw AiFailure(ChatResponse.INTERRUPTED) }
    catch (_: Exception) { throw AiFailure(ChatResponse.FORMAT) }

    private fun readFrames(source: BufferedSource, preview: (String) -> Unit): String {
        val text = StringBuilder(); val data = StringBuilder()
        var bytes = 0; var frameBytes = 0; var done = false; var finished = false
        var lastUpdate = 0L
        fun frame() {
            if (data.isEmpty()) return
            val value = data.toString().trim(); data.setLength(0); frameBytes = 0
            if (value == "[DONE]") { done = true; return }
            val obj = CompatibleStoryteller.boundedObject(value)
            if (obj.has("error") && !obj.isNull("error")) throw AiFailure(ChatResponse.FORMAT)
            val choices = obj.optJSONArray("choices") ?: throw AiFailure(ChatResponse.FORMAT)
            if (choices.length() == 0) return // optional usage-only frame
            if (choices.length() != 1) throw AiFailure(ChatResponse.FORMAT)
            val choice = choices.getJSONObject(0)
            if (choice.has("index") && choice.get("index") != 0) throw AiFailure(ChatResponse.FORMAT)
            if (choice.has("message") && !choice.isNull("message")) throw AiFailure(ChatResponse.FORMAT)
            val alreadyFinished = finished
            val reason = ChatResponse.finish(choice)
            if (reason == "stop") finished = true
            // Completion-only events need not carry a delta. Nonterminal events still must.
            val delta = choice.optJSONObject("delta") ?: if (reason == "stop" &&
                (!choice.has("delta") || choice.isNull("delta")) && !choice.has("message")) JSONObject()
                else throw AiFailure(ChatResponse.FORMAT)
            ChatResponse.checkTools(delta)
            val content = delta.opt("content")
            if (content is String) {
                if (alreadyFinished && content.isNotEmpty()) throw AiFailure(ChatResponse.FORMAT)
                text.append(content)
                if (text.length > 6000) throw AiFailure(ChatResponse.TOO_LARGE)
                val now = System.nanoTime()
                if (now - lastUpdate >= 75_000_000L) { preview(text.toString()); lastUpdate = now }
            } else if (content != null && content != JSONObject.NULL) throw AiFailure(ChatResponse.FORMAT)
        }
        // Test `done` first: asking exhausted() after [DONE] can wait on a still-open socket.
        while (!done && !source.exhausted()) {
            val newline = source.indexOf('\n'.code.toByte(), 0, MAX_FRAME_BYTES + 1L)
            val line = if (newline >= 0) source.readUtf8LineStrict(MAX_FRAME_BYTES.toLong()) else {
                if (source.buffer.size > MAX_FRAME_BYTES) throw AiFailure(ChatResponse.TOO_LARGE)
                source.readUtf8() // bounded, clean EOF; allows an unterminated final SSE line
            }
            val size = if (newline >= 0) (newline + 1).toInt() else line.toByteArray(Charsets.UTF_8).size
            bytes += size
            if (bytes > MAX_TRANSFER_BYTES) throw AiFailure(ChatResponse.TOO_LARGE)
            if (line.isEmpty()) frame()
            else if (line.startsWith("data:")) {
                frameBytes += size
                if (frameBytes > MAX_FRAME_BYTES) throw AiFailure(ChatResponse.TOO_LARGE)
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
        }
        if (!done && data.isNotEmpty()) frame()
        // A clean EOF after finish_reason=stop is a complete compatible response, even without
        // OpenAI's optional [DONE] sentinel. I/O failure still throws; EOF before stop is rejected.
        if (!finished) throw AiFailure(ChatResponse.INCOMPLETE)
        if (text.isBlank()) throw AiFailure(ChatResponse.EMPTY)
        preview(text.toString())
        return text.toString()
    }
}
