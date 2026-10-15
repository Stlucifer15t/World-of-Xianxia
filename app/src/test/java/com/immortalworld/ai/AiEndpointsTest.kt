package com.immortalworld.ai

import com.immortalworld.core.NarrativeContext
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AiEndpointsTest {
    private val paths = listOf("/v1", "/v1/", "/v1/chat/completions", "/v1/chat/completions/")
    private fun reply(text: String) = JSONObject().put("choices", org.json.JSONArray().put(
        JSONObject().put("message", JSONObject().put("content", text)))).toString()
    private fun config(server: MockWebServer, path: String) = AiConfig(
        endpoint = server.url(path).toString(), model = "fixture-model", apiKey = "fixture-key", streaming = false)

    @Test fun screenshotUrlAndFullChatUrlResolveIdenticallyWithoutGuessingVersions() {
        val base = AiEndpoints.resolve("https://crowllm.com/v1")
        assertEquals("https://crowllm.com/v1/models", base.models.toString())
        assertEquals("https://crowllm.com/v1/chat/completions", base.chat.toString())
        assertEquals(base, AiEndpoints.resolve("https://crowllm.com/v1/chat/completions/"))
        for (path in listOf("", "/", "/gateway/v2/", "/gateway%20name/v1/")) {
            val c = AiEndpoints.resolve("https://example.invalid:8443$path")
            assertEquals("${path.trimEnd('/')}/models", c.models.encodedPath)
            assertEquals("${path.trimEnd('/')}/chat/completions", c.chat.encodedPath)
            assertEquals(8443, c.chat.port); assertEquals("example.invalid", c.models.host)
        }
    }

    @Test fun modelLoadingAcceptsBothUrlFormsBeforeAModelIsSelected() = runBlocking {
        MockWebServer().use { s ->
            s.start()
            for (path in paths) {
                s.enqueue(MockResponse().setBody("""{"data":[{"id":"fixture-model","context_length":1000000}]}"""))
                val c = config(s, path).copy(model = "")
                val catalog = ModelDiscovery(OkHttpClient(), true).fetch(c)
                assertEquals(c.endpoint, catalog.endpoint) // Bind metadata to the original saved/draft configuration.
                assertEquals(ProviderModel("fixture-model", 1_000_000), catalog.models.single())
                val request = s.takeRequest(2, TimeUnit.SECONDS)!!
                assertEquals("GET", request.method); assertEquals("/v1/models", request.path)
                assertEquals("Bearer fixture-key", request.getHeader("Authorization"))
                assertEquals(0L, request.bodySize)
            }
            assertEquals(paths.size, s.requestCount)
        }
    }

    @Test fun connectionTestPostsToChatForBothUrlFormsExactlyOnce() = runBlocking {
        MockWebServer().use { s ->
            s.start()
            for (path in paths) {
                s.enqueue(MockResponse().setBody(reply("connected")))
                CompatibleStoryteller(config(s, path), allowHttpForTests = true).testConnection()
                val request = s.takeRequest(2, TimeUnit.SECONDS)!!
                assertEquals("POST", request.method); assertEquals("/v1/chat/completions", request.path)
                assertEquals("Bearer fixture-key", request.getHeader("Authorization"))
                val body = JSONObject(request.body.readUtf8())
                assertEquals("fixture-model", body.getString("model")); assertEquals(8, body.getInt("max_tokens"))
                assertFalse(body.toString().contains("fixture-key"))
            }
            assertEquals(paths.size, s.requestCount)
        }
    }

    @Test fun baseUrlIsAlsoUsedForStreamingNarrationAndActionProposals() = runBlocking {
        MockWebServer().use { s ->
            s.start()
            val frame = """data: {"choices":[{"delta":{"content":"The road is quiet."},"finish_reason":"stop"}]}"""
            s.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("$frame\n\ndata: [DONE]\n\n"))
            val storyteller = CompatibleStoryteller(config(s, "/custom/v2/").copy(streaming = true), allowHttpForTests = true)
            assertEquals("The road is quiet.", storyteller.narrate(NarrativeContext("Lin", "Village", emptyList(), emptyList())))
            val streamed = s.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/custom/v2/chat/completions", streamed.path)
            assertTrue(JSONObject(streamed.body.readUtf8()).getBoolean("stream"))
            s.enqueue(MockResponse().setBody(reply("""{"kind":"OBSERVE"}""")))
            storyteller.proposeAction("Look around", NarrativeContext("Lin", "Village", emptyList(), emptyList()))
            val action = s.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/custom/v2/chat/completions", action.path)
            assertFalse(JSONObject(action.body.readUtf8()).getBoolean("stream"))
            assertEquals(2, s.requestCount)
        }
    }

    @Test fun unsafeUrlFormsStillFailBeforeSendingCredentials() {
        for (url in listOf("http://example.invalid/v1", "https://key@example.invalid/v1", "https://example.invalid/v1?key=secret",
            "https://example.invalid/v1#secret", "file:///private", "not a URL")) {
            val failure = runCatching { AiEndpoints.resolve(url) }.exceptionOrNull()
            assertNotNull(failure)
            assertFalse(failure!!.message.orEmpty().contains("secret"))
        }
    }

    @Test fun baseUrlErrorsNeverProbeAnotherHostOrFollowCredentialRedirects() = runBlocking {
        MockWebServer().use { s ->
            s.start()
            for (status in listOf(401, 404, 429, 302)) {
                s.enqueue(MockResponse().setResponseCode(status).setHeader("Location", s.url("/should-not-be-called")).setBody("PRIVATE_BODY"))
                val failure = runCatching { ModelDiscovery(AiTransport.metadataClient, true).fetch(config(s, "/v1")) }.exceptionOrNull()
                assertTrue(failure is AiFailure)
                assertFalse(failure!!.message.orEmpty().contains("PRIVATE_BODY"))
                assertEquals("/v1/models", s.takeRequest(2, TimeUnit.SECONDS)!!.path)
            }
            assertEquals(4, s.requestCount)
        }
    }

    @Test fun geminiKeepsItsNativeCatalogAndKeyHeaderForBaseAndFullUrls() = runBlocking {
        MockWebServer().use { s ->
            s.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val original = chain.request()
                assertEquals("generativelanguage.googleapis.com", original.url.host)
                assertEquals("/v1beta/models", original.url.encodedPath)
                assertEquals("1000", original.url.queryParameter("pageSize"))
                assertEquals("fixture-key", original.header("x-goog-api-key")); assertNull(original.header("Authorization"))
                chain.proceed(original.newBuilder().url(s.url("/fixture")).build())
            }.build()
            for (path in listOf("/v1beta/openai", "/v1beta/openai/chat/completions/")) {
                s.enqueue(MockResponse().setBody("""{"models":[{"name":"models/gemini-fixture","supportedGenerationMethods":["generateContent"]}]}"""))
                val c = AiConfig(endpoint = "https://generativelanguage.googleapis.com$path", apiKey = "fixture-key")
                val result = ModelDiscovery(client).fetch(c)
                assertEquals("gemini-fixture", result.models.single().id)
                assertEquals("/v1beta/openai/chat/completions", AiEndpoints.resolve(c.endpoint).chat.encodedPath)
            }
            assertEquals(2, s.requestCount)
        }
    }
}
