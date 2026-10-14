package com.immortalworld.ai

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Public-document transport is isolated from the authenticated provider pool, cookies and headers. */
object PublicWeb {
    fun publicAddress(ip: InetAddress): Boolean {
        if (ip.isAnyLocalAddress || ip.isLoopbackAddress || ip.isLinkLocalAddress || ip.isSiteLocalAddress || ip.isMulticastAddress) return false
        val b = ip.address.map { it.toInt() and 255 }
        if (b.size == 16) return b[0] and 0xfe != 0xfc // IPv6 unique-local
        return b[0] !in listOf(0, 127) && b[0] < 224 && !(b[0] == 100 && b[1] in 64..127) &&
            !(b[0] == 169 && b[1] == 254) && !(b[0] == 198 && b[1] in 18..19) && !(b[0] == 192 && b[1] == 0)
    }
    fun url(value: String, allowHttp: Boolean = false, allowQuery: Boolean = true): HttpUrl {
        val u = value.toHttpUrlOrNull() ?: throw AiFailure("Knowledge sources must be complete public HTTPS URLs.")
        require(value.length <= 1000 && (u.isHttps || allowHttp) && u.username.isEmpty() && u.password.isEmpty() && u.fragment == null) { "Use public HTTPS URLs without credentials or fragments." }
        require(allowQuery || u.query == null) { "The search endpoint must not contain query parameters. Queries are added separately." }
        require(u.queryParameterNames.none { it.lowercase() in listOf("key", "api_key", "apikey", "token", "access_token", "password") }) { "Do not put secrets in knowledge URLs." }
        if (!allowHttp) {
            require(u.port == 443 && u.host != "localhost" && !u.host.endsWith(".localhost") && !u.host.endsWith(".local") && !u.host.endsWith(".internal")) { "Knowledge retrieval accepts public HTTPS sites only." }
            if (u.host.contains(':') || u.host.matches(Regex("[0-9.]+"))) require(publicAddress(InetAddress.getByName(u.host))) { "Private/reserved knowledge addresses are blocked." }
        }
        return u
    }
    val client: OkHttpClient = OkHttpClient.Builder().dns(object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
            if (addresses.isEmpty() || addresses.any { !publicAddress(it) }) throw UnknownHostException("Non-public knowledge address blocked")
        }
    }).connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
}

data class KnowledgeReference(val title: String, val url: String, val excerpt: String) {
    fun context() = "EXTERNAL REFERENCE ONLY (not campaign facts): $title\nSource: $url\n$excerpt"
}
data class KnowledgeResult(val references: List<KnowledgeReference> = emptyList(), val warnings: List<String> = emptyList())

object KnowledgeQuery {
    private val topics = linkedMapOf("cultivat" to "cultivation", "spiritual root" to "spiritual roots", "foundation" to "foundation establishment",
        "nascent soul" to "nascent soul", "meridian" to "meridians", "tribulation" to "heavenly tribulation", "alchemy" to "alchemy", "qi" to "qi", "sect" to "cultivation sects")
    /** Automatic lookups send generic concepts, never names, full actions, journals, or saved memories. */
    fun from(input: String): String = topics.filter { (term, _) ->
        if (term == "qi") Regex("\\bqi\\b", RegexOption.IGNORE_CASE).containsMatchIn(input) else input.contains(term, true)
    }.values.take(3).joinToString(" ")
}

class KnowledgeService(private val client: OkHttpClient = PublicWeb.client, private val allowHttpForTests: Boolean = false) {
    private data class Cached(val time: Long, val result: KnowledgeResult)
    private val cache = LinkedHashMap<String, Cached>(16, .75f, true)
    fun clear() = synchronized(cache) { cache.clear() }
    suspend fun lookup(config: AiConfig, query: String): KnowledgeResult = withContext(Dispatchers.IO) { lookupInBackground(config, query) }
    private suspend fun lookupInBackground(config: AiConfig, query: String): KnowledgeResult {
        if (!config.knowledgeEnabled || query.isBlank()) return KnowledgeResult()
        require(query.length <= 200) { "Keep knowledge queries within 200 characters." }
        val key = (config.sources + config.searchEndpoint + query).joinToString("\n")
        synchronized(cache) { cache[key] }?.takeIf { System.currentTimeMillis() - it.time < 30 * 60_000L }?.let { return it.result }
        val result = withTimeoutOrNull(6000) {
            supervisorScope {
                val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
                val chosen = config.sources.sortedByDescending { url -> terms.count { url.contains(it, true) } }.take(2)
                val jobs = chosen.map { url -> async { timed { source(url, query) } } }.toMutableList()
                if (config.searchEndpoint.isNotBlank()) jobs += async { timed { search(config.searchEndpoint, query) } }
                val all = jobs.awaitAll()
                KnowledgeResult(all.flatMap { it.references }.distinctBy { it.url }.take(3), all.flatMap { it.warnings }.distinct())
            }
        } ?: KnowledgeResult(warnings = listOf("Knowledge lookup timed out. Narration continues with saved facts only."))
        synchronized(cache) {
            if (result.references.isNotEmpty()) cache[key] = Cached(System.currentTimeMillis(), result)
            while (cache.size > 16) cache.remove(cache.keys.first())
        }
        return result
    }
    private suspend fun timed(block: suspend () -> KnowledgeReference?): KnowledgeResult = withTimeoutOrNull(4500) { safely(block) }
        ?: KnowledgeResult(warnings = listOf("A source timed out; other available references are retained."))
    private suspend fun safely(block: suspend () -> KnowledgeReference?): KnowledgeResult = try {
        KnowledgeResult(listOfNotNull(block()))
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { KnowledgeResult(warnings = listOf("A source/search service was unavailable, protected, too large or unsupported. No access restrictions were bypassed.")) }

    private suspend fun get(value: String): Pair<String, String> {
        var url = PublicWeb.url(value, allowHttpForTests)
        repeat(4) { hop ->
            val received = AiTransport.execute(client, Request.Builder().url(url).header("User-Agent", "ImmortalWorld/0.4 (optional, bounded reference lookup)").get().build()) { response ->
                if (response.isRedirect) {
                    require(hop < 3) { "Too many redirects." }
                    val next = response.header("Location")?.let { url.resolve(it) } ?: throw AiFailure("Invalid redirect.")
                    Triple(PublicWeb.url(next.toString(), allowHttpForTests), "", "")
                } else {
                    require(response.isSuccessful) { "Knowledge source unavailable." }
                    val type = response.header("Content-Type").orEmpty().lowercase()
                    require(type.contains("text/html") || type.contains("text/plain") || type.contains("json")) { "Unsupported document type." }
                    Triple(null, type, AiTransport.readBody(response, 1024 * 1024))
                }
            }
            if (received.first != null) url = received.first!! else return url.toString() to received.third
        }
        throw AiFailure("Redirect limit reached.")
    }
    private suspend fun source(value: String, query: String): KnowledgeReference? {
        val u = PublicWeb.url(value, allowHttpForTests)
        val fandom = u.host.endsWith(".fandom.com")
        if (fandom || u.encodedPath.endsWith("api.php")) {
            try {
                val api = u.newBuilder().query(null).apply { if (fandom) encodedPath("/api.php") }
                    .addQueryParameter("action", "query").addQueryParameter("format", "json").addQueryParameter("list", "search")
                    .addQueryParameter("srsearch", query).addQueryParameter("srlimit", "1").build()
                val root = CompatibleStoryteller.boundedObject(get(api.toString()).second)
                val title = root.getJSONObject("query").getJSONArray("search").optJSONObject(0)?.optString("title") ?: return null
                val pageUrl = if (fandom) u.newBuilder().query(null).encodedPath("/wiki/").addPathSegment(title.replace(' ', '_')).build()
                    else u.newBuilder().query(null).encodedPath(u.encodedPath.removeSuffix("api.php") + "index.php").addQueryParameter("title", title).build()
                return page(pageUrl.toString(), query)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { if (!fandom || !u.encodedPath.startsWith("/wiki/")) throw e }
            // Some accessible Fandom pages have a disabled API. Try only the explicitly supplied page.
        }
        return page(value, query)
    }
    private suspend fun search(endpoint: String, query: String): KnowledgeReference? {
        val url = PublicWeb.url(endpoint, allowHttpForTests, allowQuery = false).newBuilder()
            .addQueryParameter("q", "$query xianxia").addQueryParameter("format", "json").build()
        val root = CompatibleStoryteller.boundedObject(get(url.toString()).second)
        val results = root.optJSONArray("results") ?: throw AiFailure("Search needs a SearXNG-compatible JSON endpoint.")
        for (i in 0 until minOf(results.length(), 5)) {
            val link = results.optJSONObject(i)?.optString("url").orEmpty()
            if (runCatching { PublicWeb.url(link, allowHttpForTests) }.isSuccess) return page(link, query)
        }
        return null
    }
    private suspend fun page(value: String, query: String): KnowledgeReference? {
        val (url, content) = get(value)
        return extract(url, content, query)
    }
    companion object {
        fun extract(url: String, html: String, query: String): KnowledgeReference? {
            val doc = Jsoup.parse(html, url)
            val title = doc.title().take(180).ifBlank { "Knowledge page" }
            if (title.contains("just a moment", true) || title.contains("access denied", true)) return null
            doc.select("script,style,nav,footer,header,form,aside,noscript,.reference,.reflist,.toc").remove()
            val main = doc.selectFirst(".mw-parser-output") ?: doc.selectFirst("main,article") ?: doc.body()
            val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
            val paragraphs = main.select("p,li").map { it.text().trim() }.ifEmpty { listOf(main.text().take(6000)) }
            val candidates = paragraphs.filter { it.length >= 40 }.distinct()
                .mapIndexed { index, text -> Triple(index, text, terms.count { text.contains(it, true) }) }
                .filter { it.third > 0 }.sortedByDescending { it.third }.take(3).sortedBy { it.first }
            val excerpt = candidates.joinToString("\n\n") { it.second.take(800) }.take(1800)
            return if (excerpt.isBlank()) null else KnowledgeReference(title, url, excerpt)
        }
    }
}
