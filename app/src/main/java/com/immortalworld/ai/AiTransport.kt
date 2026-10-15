package com.immortalworld.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One application-wide pool/dispatcher; authentication is attached to each request, never the client. */
object AiTransport {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    // Shares the API pool, but metadata checks have a shorter deadline and never generate tokens.
    val metadataClient = client.newBuilder().callTimeout(12, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    fun failure(code: Int): AiFailure = AiFailure(when (code) {
        401, 403 -> "Provider credentials/access were rejected. Re-enter the key or check account permissions."
        404, 405 -> "This endpoint does not expose the requested API. Manual model entry remains available."
        429 -> "Provider rate limit reached. Retry later; no action is replayed."
        400, 422 -> "Provider rejected the request options. Check the model/limits, or disable streaming and retry narration only."
        else -> "Provider returned HTTP $code. No response body or credentials are displayed."
    })
    suspend fun <T> execute(client: OkHttpClient, request: Request, parse: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(AiFailure("Connection failed or timed out. Saved settings and offline gameplay remain available.", e))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use { parse(it) } }.recoverCatching {
                    if (it is AiFailure) throw it
                    if (it is IOException) throw AiFailure(ChatResponse.INTERRUPTED)
                    throw AiFailure(ChatResponse.FORMAT)
                }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
    fun readBody(response: Response, limit: Int): String {
        val source = response.body?.source() ?: throw AiFailure("Response has no body.")
        return readBounded(source, limit)
    }

    private fun readBounded(source: okio.BufferedSource, limit: Int): String {
        val buffer = okio.Buffer(); var total = 0L
        while (true) {
            val n = source.read(buffer, minOf(8192L, limit + 1L - total))
            if (n < 0) break
            total += n
            if (total > limit) throw AiFailure(ChatResponse.TOO_LARGE)
        }
        return buffer.readUtf8()
    }
}
