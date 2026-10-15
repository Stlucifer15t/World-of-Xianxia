package com.immortalworld.ai

import com.immortalworld.core.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AiProviderTest {
    private val context = NarrativeContext("Lin", "Village", listOf("An attack dealt 12 damage."), emptyList(), input = "Attack")
    private fun envelope(text: String) = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", text)))).toString()
    private fun provider(server: MockWebServer) = CompatibleStoryteller(AiConfig(true, server.url("/v1/chat/completions").toString(), "test-model", "unit-test-only"), allowHttpForTests = true)
    @Test fun realOpeningRequestCarriesCreationSnapshotAndStreamsProseOnly() = runBlocking {
        val options = CreationOptions(name = "Yue", appearance = "A worn jade clasp", background = "Exiled cartographer", personality = "Patient and curious")
        val g = CharacterGenerator.generate(options, 42, "yue")
        val s = OpeningScene.attach(GameSave(id = "opening-wire", name = "Wire test", createdAt = 1, updatedAt = 1,
            randomState = g.randomState, player = g.character.copy(hiddenTraits = listOf("PRIVATE_TRAIT")),
            world = WorldState("world", currentLocation = "Reed Harbour", startingCircumstances = "A sealed ferry gate",
                journal = listOf(JournalEntry("private", 1, "PRIVATE_JOURNAL")))))
        val c = OpeningScene.context(s, s.adventure!!.history.single())
        MockWebServer().use { server ->
            server.start()
            val chunk = JSONObject().put("choices", JSONArray().put(JSONObject().put("delta", JSONObject().put("content", "River mist brushes the jade clasp."))))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: $chunk\n\ndata: [DONE]\n\n"))
            val drafts = mutableListOf<String>()
            assertEquals("River mist brushes the jade clasp.", provider(server).narrateStreaming(c) { drafts.add(it) })
            assertTrue(drafts.isNotEmpty())
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method); assertEquals("/v1/chat/completions", request.path)
            val body = JSONObject(request.body.readUtf8())
            assertEquals(1200, body.getInt("max_tokens")); assertTrue(body.getBoolean("stream"))
            val messages = body.getJSONArray("messages")
            assertTrue(messages.getJSONObject(0).getString("content").contains("For pace=opening"))
            val payload = JSONObject(messages.getJSONObject(1).getString("content"))
            assertEquals("opening", payload.getString("pace"))
            listOf("Yue", "A worn jade clasp", "Exiled cartographer", "Patient and curious", "A sealed ferry gate", g.character.spiritualRoot).forEach {
                assertTrue(payload.toString().contains(it))
            }
            assertFalse(body.toString().contains("PRIVATE_TRAIT")); assertFalse(body.toString().contains("PRIVATE_JOURNAL"))
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun parsesOnlyTypedActionsWithoutMechanicalPatches() {
        assertEquals(GameAction(ActionKind.TRAVEL, "crossing"), CompatibleStoryteller.parseAction("""{"kind":"TRAVEL","target":"crossing"}"""))
        listOf("""{"kind":"ATTACK","damage":999}""", """{"kind":"SIMULATION_MODE","target":"PAUSED"}""", """{"kind":"ACTIVITY","hours":999}""", """{"kind":"TRAVEL","target":4}""", """{"kind":"OBSERVE"} {}""").forEach {
            assertThrows(Exception::class.java) { CompatibleStoryteller.parseAction(it) }
        }
    }
    @Test fun invalidKeysAndRateLimitsAreSafeErrors() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (code in listOf(401, 403, 404, 429, 500)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("SECRET_PROVIDER_BODY"))
                val failure = runCatching { provider(server).narrate(context) }.exceptionOrNull()
                assertTrue(failure is AiFailure)
                assertFalse(failure!!.message.orEmpty().contains("SECRET_PROVIDER_BODY"))
                assertFalse(failure.message.orEmpty().contains("unit-test-only"))
            }
        }
    }
    @Test fun connectionFailureDoesNotReturnInventedNarration() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val result = runCatching { provider(server).narrate(context) }
            assertTrue(result.exceptionOrNull() is AiFailure)
        }
    }
    @Test fun malformedAndOversizedResponsesAreRejected() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            listOf("not JSON", "[".repeat(1000) + "]".repeat(1000), "x".repeat(70000)).forEach { body ->
                server.enqueue(MockResponse().setBody(body))
                assertTrue(runCatching { provider(server).narrate(context) }.isFailure)
            }
        }
    }
    @Test fun narrationCannotInventNumbersOrChoosePlayerDecisions() {
        assertEquals("The blow lands for 12 damage.", CompatibleStoryteller.validateNarration("The blow lands for 12 damage.", context.authoritativeFacts))
        listOf("You gain 999 levels.", "You decide to join the sect.", "You promise to surrender.").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { CompatibleStoryteller.validateNarration(text, context.authoritativeFacts) }
        }
    }
    @Test fun successfulProviderUsesOnlySuppliedContextAndAuthorizationHeader() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody(envelope("The blow lands for 12 damage.")))
            assertEquals("The blow lands for 12 damage.", provider(server).narrate(context))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("Bearer unit-test-only", request.getHeader("Authorization"))
            val body = request.body.readUtf8(); assertFalse(body.contains("unit-test-only")); assertFalse(body.contains("hiddenTraits"))
            assertTrue(body.contains("An attack dealt 12 damage."))
        }
    }
    @Test fun requestsCanBeCancelledWithoutAutomaticRetry() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody(envelope("late")).setBodyDelay(5, TimeUnit.SECONDS))
            val job = launch { provider(server).narrate(context) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            assertTrue(job.isCancelled); assertEquals(1, server.requestCount)
        }
    }
    @Test fun productionConfigRejectsPlaintextAndCredentialUrls() {
        listOf("http://example.com/chat", "https://key@example.com/chat", "https://example.com/chat?key=secret").forEach { endpoint ->
            assertThrows(Exception::class.java) { CompatibleStoryteller.validate(AiConfig(true, endpoint, "model")) }
        }
    }
    @Test fun sceneBudgetAndPromptSupportGroundedLongFormWithoutPrivateContext() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for ((pace, budget) in listOf("opening" to 1200, "scene" to 1200, "major" to 1200, "brief" to 650, "dialogue" to 650)) {
                server.enqueue(MockResponse().setBody(envelope("The river continues to flow.")))
                provider(server).narrate(context.copy(pace = pace, spokenDialogue = "Mei: Dewleaf grows near water.", recentOpenings = listOf("Avoid this opening.")))
                val body = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
                assertEquals(budget, body.getInt("max_tokens"))
                val messages = body.getJSONArray("messages")
                val prompt = messages.getJSONObject(0).getString("content")
                assertTrue(prompt.contains("2–4 substantial")); assertTrue(prompt.contains("Never choose")); assertTrue(prompt.contains("invent no ambush"))
                val supplied = JSONObject(messages.getJSONObject(1).getString("content"))
                assertEquals(pace, supplied.getString("pace")); assertTrue(supplied.getString("resolvedNpcDialogue").contains("Mei"))
            }
        }
    }
    @Test fun proseBudgetAndParagraphConsolidationRemainBounded() {
        val long = "The road is quiet. ".repeat(200)
        assertEquals(long.trim(), CompatibleStoryteller.validateNarration(long, emptyList()))
        assertThrows(IllegalArgumentException::class.java) { CompatibleStoryteller.validateNarration("x".repeat(6001), emptyList()) }
        val shortLines = List(8) { "A quiet breeze stirs the reeds." }.joinToString("\n\n")
        assertTrue(StoryText.paragraphs(CompatibleStoryteller.validateNarration(shortLines, emptyList())).size < 5)
        assertEquals(GameAction(ActionKind.SENSE, "wen"), CompatibleStoryteller.parseAction("""{"kind":"SENSE","target":"wen"}"""))
    }

}
