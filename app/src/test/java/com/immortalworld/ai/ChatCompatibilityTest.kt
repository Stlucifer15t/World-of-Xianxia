package com.immortalworld.ai

import com.immortalworld.core.NarrativeContext
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Compatible-protocol fixtures, not captured account traffic or proof about a particular live model. */
class ChatCompatibilityTest {
    private val story = "River mist curls around the gate."
    private val context = NarrativeContext("Yue", "Village", emptyList(), emptyList(), pace = "opening")
    private fun event(choice: JSONObject) = "data: " + JSONObject().put("choices", JSONArray().put(choice)) + "\n\n"
    private fun chunk(text: String = story) = event(JSONObject().put("index", 0).put("delta", JSONObject().put("content", text)))
    private fun finish(reason: Any = "stop", delta: Boolean = false) = event(JSONObject().put("index", 0).put("finish_reason", reason).apply { if (delta) put("delta", JSONObject()) })
    private fun json(reason: Any = "stop", content: String = story) = JSONObject().put("choices", JSONArray().put(JSONObject()
        .put("finish_reason", reason).put("message", JSONObject().put("content", content)))).toString()
    private fun config(server: MockWebServer) = AiConfig(true, server.url("/v1").toString(), "fixture-model", "private-test-key", maxOutputTokens = 4096)
    private fun failure(body: String): AiFailure = assertThrows(AiFailure::class.java) { ChatStream.read(Buffer().writeUtf8(body)) {} }
    private fun sourceEndingIn(prefix: String, tail: () -> Long): Source = object : Source {
        var first = true
        override fun read(sink: Buffer, byteCount: Long): Long = if (first) {
            first = false; sink.writeUtf8(prefix); prefix.toByteArray().size.toLong()
        } else tail()
        override fun timeout() = Timeout.NONE
        override fun close() {}
    }

    @Test fun explicitStopAcceptsCleanEofWithOrWithoutDeltaDoneAndFinalNewline() {
        val frames = listOf(chunk() + finish(), chunk() + finish(delta = true), chunk() + finish().trimEnd(),
            chunk() + finish() + "data: [DONE]", chunk() + finish() + "data: {\"choices\":[],\"usage\":{}}\n\n")
        for (body in frames + frames.map { it.replace("\n", "\r\n") })
            assertEquals(story, ChatStream.read(Buffer().writeUtf8(body)) {})
    }
    @Test fun doneDoesNotReadFromAStillOpenSocketAgain() {
        val source = sourceEndingIn(chunk() + finish() + "data: [DONE]\n\n") { throw AssertionError("Read after DONE") }.buffer()
        assertEquals(story, ChatStream.read(source) {})
    }
    @Test fun eofOrDoneWithoutModelCompletionStillRejectsPartialNarration() {
        for (body in listOf(chunk(), chunk() + "data: [DONE]\n\n", "data: [DONE]\n\n"))
            assertEquals(ChatResponse.INCOMPLETE, failure(body).message)
        assertEquals(ChatResponse.FORMAT, failure(chunk() + "data: {\"choices\":[").message)
        assertEquals(ChatResponse.FORMAT, failure(chunk() + finish() + chunk("late text")).message)
    }
    @Test fun outputLimitBlockedAndEmptyAreDistinctSafeDiagnostics() {
        assertEquals(ChatResponse.OUTPUT_LIMIT, failure(chunk() + finish("length")).message)
        assertTrue(failure(chunk() + finish("content_filter")).message!!.contains("blocked"))
        assertEquals(ChatResponse.EMPTY, failure(finish()).message)
        assertFalse(failure(chunk() + finish("PRIVATE_PROVIDER_ERROR")).message!!.contains("PRIVATE_PROVIDER_ERROR"))
        assertEquals(ChatResponse.FORMAT, failure(chunk() + finish(17)).message)
    }
    @Test fun nullAndEmptyOptionalToolFieldsAreNotToolInvocations() {
        for (unused in listOf(JSONObject.NULL, JSONArray())) {
            val delta = JSONObject().put("content", story).put("tool_calls", unused).put("function_call", JSONObject.NULL)
            assertEquals(story, ChatStream.read(Buffer().writeUtf8(event(JSONObject().put("delta", delta)) + finish())) {})
        }
        val delta = JSONObject().put("content", story).put("tool_calls", JSONArray().put(JSONObject().put("function", JSONObject().put("name", "PRIVATE_TOOL"))))
        val error = failure(event(JSONObject().put("delta", delta)) + finish())
        assertTrue(error.message!!.contains("tool")); assertFalse(error.message!!.contains("PRIVATE_TOOL"))
    }
    @Test fun escapedUnicodeFitsBoundedFramesWithoutRaisingStorySizeLimit() {
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"" + "\\u4e91".repeat(4000) + "\"},\"finish_reason\":\"stop\"}]}\n\n"
        assertEquals("云".repeat(4000), ChatStream.read(Buffer().writeUtf8(body)) {})
        assertEquals(ChatResponse.TOO_LARGE, failure("data: " + "x".repeat(65537) + "\n\n").message)
        assertEquals(ChatResponse.TOO_LARGE, failure(chunk("x".repeat(6001)) + finish()).message)
        assertEquals(ChatResponse.TOO_LARGE, failure(": ".repeat(1000).plus("\r\n").repeat(300)).message)
    }
    @Test fun socketFailureEvenAfterStopIsNotMistakenForCleanEof() {
        val source = sourceEndingIn(chunk() + finish()) { throw IOException("PRIVATE_NETWORK_DETAIL") }.buffer()
        val error = assertThrows(AiFailure::class.java) { ChatStream.read(source) {} }
        assertEquals(ChatResponse.INTERRUPTED, error.message)
        assertFalse(error.message!!.contains("PRIVATE_NETWORK_DETAIL"))
    }
    @Test fun reasoningAndUsageAreNotDisplayedAsStoryText() {
        val delta = JSONObject().put("role", "assistant").put("content", story).put("reasoning_content", "PRIVATE_THINKING")
        val previews = mutableListOf<String>()
        val body = event(JSONObject().put("delta", delta)) + finish() + "data: {\"choices\":[],\"usage\":{\"completion_tokens\":100}}\n\n"
        assertEquals(story, ChatStream.read(Buffer().writeUtf8(body)) { previews += it })
        assertTrue(previews.all { it == story })
    }
    @Test fun realHttpOpeningStreamWithoutDoneUsesOnePostAndConfiguredOutput() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream; charset=utf-8").setBody(chunk() + finish()))
            val actual = CompatibleStoryteller(config(server), allowHttpForTests = true).narrate(context)
            assertEquals(story, actual)
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer private-test-key", request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals(4096, body.getInt("max_tokens")); assertTrue(body.getBoolean("stream"))
            assertFalse(body.toString().contains("private-test-key")); assertEquals(1, server.requestCount)
        }
    }
    @Test fun streamAndJsonLimitFailuresKeepSpecificGuidanceWithoutAutomaticFallback() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (stream in listOf(true, false)) {
                server.enqueue(MockResponse().setHeader("Content-Type", if (stream) "text/event-stream" else "application/json")
                    .setBody(if (stream) chunk() + finish("length") else json("length")))
                val error = runCatching { CompatibleStoryteller(config(server).copy(streaming = stream), allowHttpForTests = true).narrate(context) }.exceptionOrNull()
                assertTrue(error is AiFailure); assertEquals(ChatResponse.OUTPUT_LIMIT, error!!.message)
            }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun savedOutputBudgetIsHonoredForEveryPaceAndActionWhileConnectionStaysShort() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val provider = CompatibleStoryteller(config(server).copy(streaming = false), allowHttpForTests = true)
            for (pace in listOf("opening", "scene", "major", "brief", "dialogue")) {
                server.enqueue(MockResponse().setBody(json()))
                provider.narrate(context.copy(pace = pace))
                assertEquals(4096, JSONObject(server.takeRequest().body.readUtf8()).getInt("max_tokens"))
            }
            server.enqueue(MockResponse().setBody(json(content = "{\"kind\":\"OBSERVE\"}")))
            provider.proposeAction("Look", context)
            assertEquals(4096, JSONObject(server.takeRequest().body.readUtf8()).getInt("max_tokens"))
            server.enqueue(MockResponse().setBody(json(content = "connected")))
            provider.testConnection()
            assertEquals(8, JSONObject(server.takeRequest().body.readUtf8()).getInt("max_tokens"))
            assertEquals(7, server.requestCount)
        }
    }
    @Test fun nonStreamingAlsoRejectsMalformedCompletionAndToolInvocations() {
        assertEquals(ChatResponse.FORMAT, assertThrows(AiFailure::class.java) { ChatResponse.json(json(12)) }.message)
        assertEquals(ChatResponse.EMPTY, assertThrows(AiFailure::class.java) { ChatResponse.json(json(content = "")) }.message)
        val root = JSONObject(json()); root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            .put("tool_calls", JSONArray().put(JSONObject().put("function", "PRIVATE_FUNCTION")))
        val error = assertThrows(AiFailure::class.java) { ChatResponse.json(root.toString()) }
        assertFalse(error.message!!.contains("PRIVATE_FUNCTION"))
    }
}
