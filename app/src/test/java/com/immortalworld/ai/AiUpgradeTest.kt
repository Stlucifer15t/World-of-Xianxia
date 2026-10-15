package com.immortalworld.ai

import com.immortalworld.core.NarrativeContext
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class AiUpgradeTest {
    private val context = NarrativeContext("Lin", "Village", listOf("The river is quiet."), emptyList())
    private fun config(s: MockWebServer) = AiConfig(true, s.url("/v1/chat/completions").toString(), "test", "test-only-key")
    private fun chunk(text: String? = null, stop: Boolean = false) = "data: " + JSONObject().put("choices", JSONArray().put(JSONObject()
        .put("delta", JSONObject().apply { if (text != null) put("content", text) }).put("finish_reason", if (stop) "stop" else JSONObject.NULL))) + "\n\n"
    private fun envelope(text: String) = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", text)))).toString()
    @Test fun sharedPoolIsReusedAcrossStorytellerInstances() = runBlocking {
        MockWebServer().use { s ->
            s.start(); repeat(2) { s.enqueue(MockResponse().setBody(envelope("The river is quiet."))) }
            repeat(2) { CompatibleStoryteller(config(s), allowHttpForTests = true).narrate(context) }
            assertEquals(0, s.takeRequest().sequenceNumber); assertEquals(1, s.takeRequest().sequenceNumber)
        }
    }
    @Test fun streamingDisplaysPrefixesAndOnlyReturnsACompleteValidatedResult() = runBlocking {
        MockWebServer().use { s ->
            s.start(); s.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(": heartbeat\n\n" + chunk("The river") + chunk(" is quiet.") + chunk(stop = true) + "data: [DONE]\n\n")
                .throttleBody(40, 5, TimeUnit.MILLISECONDS))
            val previews = mutableListOf<String>()
            val result = CompatibleStoryteller(config(s), allowHttpForTests = true).narrateStreaming(context) { previews += it }
            assertEquals("The river is quiet.", result); assertEquals("The river", previews.first())
            assertEquals(result, previews.last()); assertTrue(JSONObject(s.takeRequest().body.readUtf8()).getBoolean("stream"))
        }
    }
    @Test fun incompleteStreamsNeverReturnAcceptedNarration() {
        for (body in listOf(chunk("The river"), chunk("The river") + chunk(stop = true), "data: [DONE]\n\n", "data: {bad}\n\n")) {
            assertThrows(Exception::class.java) { ChatStream.read(Buffer().writeUtf8(body)) {} }
        }
    }
    @Test fun streamLimitsAndToolCallsAreRejected() {
        val tooLong = chunk("x".repeat(6001)) + chunk(stop = true) + "data: [DONE]\n\n"
        assertThrows(Exception::class.java) { ChatStream.read(Buffer().writeUtf8(tooLong)) {} }
        val tools = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[]},\"finish_reason\":null}]}\n\n"
        assertThrows(Exception::class.java) { ChatStream.read(Buffer().writeUtf8(tools)) {} }
        assertThrows(Exception::class.java) { ChatStream.read(Buffer().writeUtf8("data: " + "x".repeat(17000) + "\n\n")) {} }
    }
    @Test fun streamedAgencyViolationsAreNotAccepted() = runBlocking {
        MockWebServer().use { s ->
            s.start(); s.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(chunk("You decide to surrender.") + chunk(stop = true) + "data: [DONE]\n\n"))
            assertTrue(runCatching { CompatibleStoryteller(config(s), allowHttpForTests = true).narrateStreaming(context) {} }.isFailure)
        }
    }
    @Test fun cancellationStopsStreamingWithoutReplay() = runBlocking {
        MockWebServer().use { s ->
            s.start()
            val first = chunk("The river")
            // Dispatcher.peek() stays unthrottled while reading the POST; only the response is slow.
            s.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(first + chunk(" is quiet.") + chunk(stop = true) + "data: [DONE]\n\n")
                    .throttleBody(first.toByteArray(Charsets.UTF_8).size.toLong(), 2, TimeUnit.SECONDS)
            }
            val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).build()
            val narrator = CompatibleStoryteller(config(s), client, allowHttpForTests = true)
            val firstPreview = CompletableDeferred<String>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { narrator.narrateStreaming(context) { firstPreview.complete(it) } }
            withContext(Dispatchers.IO) { assertNotNull(s.takeRequest(5, TimeUnit.SECONDS)) }
            assertEquals("The river", withTimeout(5000) { firstPreview.await() })
            job.cancelAndJoin(); assertEquals(1, s.requestCount)
        }
    }
    @Test fun nonStreamingFallbackUsesOneRequestAndSeparateOutputCap() = runBlocking {
        MockWebServer().use { s ->
            s.start(); s.enqueue(MockResponse().setBody(envelope("The river is quiet.")))
            CompatibleStoryteller(config(s).copy(streaming = false, contextTokens = 1_000_000, maxOutputTokens = 80), allowHttpForTests = true).narrate(context)
            val body = JSONObject(s.takeRequest().body.readUtf8())
            assertFalse(body.getBoolean("stream")); assertEquals(80, body.getInt("max_tokens")); assertEquals(1, s.requestCount)
        }
    }
    @Test fun modelDiscoveryIsGetOnlyAndDoesNotRequireAModel() = runBlocking {
        MockWebServer().use { s ->
            s.start(); s.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"data":[{"id":"vendor/large","context_length":1000000,"top_provider":{"max_completion_tokens":8000}}]}"""))
            val c = config(s).copy(model = "")
            val result = ModelDiscovery(OkHttpClient(), true).fetch(c)
            assertEquals(ProviderModel("vendor/large", 1_000_000, 8000), result.models.single())
            val request = s.takeRequest(); assertEquals("GET", request.method); assertEquals("/v1/models", request.path)
            assertEquals("Bearer test-only-key", request.getHeader("Authorization")); assertEquals(0L, request.bodySize)
        }
    }
    @Test fun modelCapacityIsNeverGuessedOrStringCoerced() {
        val models = ModelDiscovery.parse("""{"data":[{"id":"m-1000000","context_length":"1000000"},{"id":"small","context_window":8192},{"id":"bad","context_length":-1},{"id":"huge","context_length":9999999999}]}""")
        assertNull(models.first { it.id == "m-1000000" }.contextTokens)
        assertEquals(8192, models.first { it.id == "small" }.contextTokens)
        assertNull(models.first { it.id == "bad" }.contextTokens); assertNull(models.first { it.id == "huge" }.contextTokens)
    }
    @Test fun unavailableCatalogDoesNotGenerateOrFollowCredentialRedirects() = runBlocking {
        MockWebServer().use { s ->
            s.start()
            for (code in listOf(404, 401, 429, 302)) {
                s.enqueue(MockResponse().setResponseCode(code).setHeader("Location", s.url("/secret-target")).setBody("SECRET_BODY"))
                val failed = runCatching { ModelDiscovery(OkHttpClient.Builder().followRedirects(false).build(), true).fetch(config(s)) }.exceptionOrNull()
                assertNotNull(failed); assertFalse(failed!!.message.orEmpty().contains("SECRET_BODY"))
            }
            assertEquals(4, s.requestCount)
        }
    }
    @Test fun contextBudgetDropsReferencesBeforeAuthorityAndRefusesImpossibleRequests() {
        val json = JSONObject().put("facts", JSONArray(listOf("Authoritative fact"))).put("input", "Actual intention").put("externalReferenceOnly", "x".repeat(10000)).put("scene", "Optional scenery")
        val fitted = JSONObject(ContextBudget.fit("System", json.toString(), 2048, 100))
        assertTrue(fitted.has("facts") && fitted.has("input")); assertFalse(fitted.has("externalReferenceOnly"))
        assertThrows(AiFailure::class.java) { ContextBudget.fit("x".repeat(5000), json.toString(), 2048, 100) }
    }
    @Test fun millionTokenContextRemainsSeparateFromOutputAndSecretsAreRedacted() {
        val c = AiConfig(endpoint = "https://example.com/v1/chat/completions", model = "m", apiKey = "SECRET_KEY", contextTokens = 1_000_000, maxOutputTokens = 1200)
        CompatibleStoryteller.validate(c)
        assertFalse(c.toString().contains("SECRET_KEY"))
        val unsafe = "SECRET_KEY_\u0001"
        val error = assertThrows(IllegalArgumentException::class.java) { CompatibleStoryteller.validate(c.copy(apiKey = unsafe)) }
        assertFalse(error.message.orEmpty().contains("SECRET_KEY"))
        assertThrows(Exception::class.java) { CompatibleStoryteller.validate(c.copy(contextTokens = 1000)) }
        assertThrows(Exception::class.java) { CompatibleStoryteller.validate(c.copy(maxOutputTokens = 20000)) }
    }
    @Test fun publicRetrievalRejectsUnsafeSchemesCredentialsAndAddresses() {
        for (url in listOf("file:///data/secret", "http://example.com/", "https://secret@example.com/", "https://localhost/", "https://127.0.0.1/", "https://169.254.169.254/", "https://10.0.0.1/", "https://[::1]/", "https://example.com/?token=secret")) {
            assertThrows(Exception::class.java) { PublicWeb.url(url) }
        }
        assertFalse(PublicWeb.publicAddress(InetAddress.getByName("192.168.1.1")))
        assertFalse(PublicWeb.publicAddress(InetAddress.getByName("fc00::1")))
        assertTrue(PublicWeb.publicAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(PublicWeb.publicAddress(InetAddress.getByName("64:ff9b::a00:1")))
        assertFalse(PublicWeb.publicAddress(InetAddress.getByName("2002:a00:1::")))
    }
    @Test fun automaticLookupQueriesExcludeNamesAndPrivateActionText() {
        assertEquals("cultivation meridians", KnowledgeQuery.from("Ask PRIVATE_NAME about cultivation and meridians, PRIVATE_DIARY"))
        assertEquals("", KnowledgeQuery.from("Attack the guard"))
    }
    @Test fun extractionIsRelevantBoundedAndDoesNotExecutePageScripts() {
        val html = """<title>Wiki</title><main><script>SECRET_SCRIPT</script><p>Unrelated material about boats and sailing across the distant ocean.</p><p>Cultivation circulates qi through meridians in many fictional traditions.</p></main>"""
        val ref = KnowledgeService.extract("https://example.com/wiki/Qi", html, "cultivation")!!
        assertTrue(ref.excerpt.contains("Cultivation")); assertFalse(ref.excerpt.contains("boats")); assertFalse(ref.excerpt.contains("SECRET_SCRIPT"))
        assertNull(KnowledgeService.extract("https://example.com", "<title>Just a moment</title><p>Cultivation challenge page asking to enable scripting.</p>", "cultivation"))
    }
    @Test fun websiteLookupIsOptInCachedAndNeverSendsProviderCredentials() = runBlocking {
        MockWebServer().use { s ->
            s.start(); s.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<title>Wiki</title><p>Cultivation is a fictional practice involving qi and spiritual refinement.</p>"))
            val service = KnowledgeService(OkHttpClient(), true)
            val c = config(s).copy(sources = listOf(s.url("/wiki/Cultivation").toString()))
            assertTrue(service.lookup(c, "cultivation").references.isEmpty()); assertEquals(0, s.requestCount)
            val result = service.lookup(c.copy(knowledgeEnabled = true), "cultivation")
            assertEquals(1, result.references.size); assertEquals(result, service.lookup(c.copy(knowledgeEnabled = true), "cultivation")); assertEquals(1, s.requestCount)
            assertNull(s.takeRequest().getHeader("Authorization"))
        }
    }
    @Test fun searchFetchesOnlyAResultPageAndTreatsItAsExternalReference() = runBlocking {
        MockWebServer().use { s ->
            s.start(); s.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(JSONObject().put("results", JSONArray().put(JSONObject().put("url", s.url("/page").toString()))).toString()))
            s.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<title>Reference</title><p>Meridians are discussed in cultivation fiction as channels for spiritual energy.</p>"))
            val result = KnowledgeService(OkHttpClient(), true).lookup(config(s).copy(knowledgeEnabled = true, searchEndpoint = s.url("/search").toString()), "meridians")
            assertEquals(1, result.references.size); assertTrue(result.references.single().context().contains("not campaign facts"))
            assertTrue(s.takeRequest().path!!.contains("format=json")); assertEquals("/page", s.takeRequest().path)
        }
    }
    @Test fun protectedSourcesFailGracefullyWithoutBypassOrRetries() = runBlocking {
        MockWebServer().use { s ->
            s.start(); s.enqueue(MockResponse().setResponseCode(403))
            val result = KnowledgeService(OkHttpClient(), true).lookup(config(s).copy(knowledgeEnabled = true, sources = listOf(s.url("/page").toString())), "cultivation")
            assertTrue(result.references.isEmpty()); assertTrue(result.warnings.isNotEmpty()); assertEquals(1, s.requestCount)
        }
    }
    @Test fun mediaWikiLookupUsesTheConfiguredInstallationNotAnInventedArticlePath() = runBlocking {
        MockWebServer().use { s ->
            s.start()
            s.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"query":{"search":[{"title":"Qi cultivation"}]}}"""))
            s.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<p>Cultivation stories describe qi refinement and spiritual practice in fictional worlds.</p>"))
            val result = KnowledgeService(OkHttpClient(), true).lookup(config(s).copy(knowledgeEnabled = true, sources = listOf(s.url("/custom/api.php").toString())), "cultivation")
            assertEquals(1, result.references.size)
            assertTrue(s.takeRequest().path!!.startsWith("/custom/api.php?"))
            assertTrue(s.takeRequest().path!!.startsWith("/custom/index.php?title=Qi"))
        }
    }

}
