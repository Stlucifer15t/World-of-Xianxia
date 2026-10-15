package com.immortalworld.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.aiStore by preferencesDataStore("ai-provider")
/** Versioned device profiles; only the key field is encrypted. No provider configuration enters saves. */
class AiPreferences(context: Context, private val store: DataStore<Preferences> = context.aiStore,
    private val keyAlias: String = "immortal-world-ai") {
    companion object { private val lock = Mutex() }
    private val profilesKey = stringPreferencesKey("profiles_v1")
    private fun key(): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP); require(bytes.size >= 28)
        return Cipher.getInstance("AES/GCM/NoPadding").let { cipher ->
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    }
    private fun encrypt(value: String): String = if (value.isEmpty()) "" else Cipher.getInstance("AES/GCM/NoPadding").let { cipher ->
        cipher.init(Cipher.ENCRYPT_MODE, key())
        Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    suspend fun load(): AiConfig = loadProfiles().active.config
    suspend fun loadProfiles(): AiProfileStore = withContext(Dispatchers.IO) { lock.withLock { read() } }
    private suspend fun read(): AiProfileStore {
        val p = store.data.first(); var warning: String? = null
        fun secret(value: String): String = try { decrypt(value) } catch (_: Exception) {
            warning = "A saved API key could not be decrypted. Its profile is disabled for this session; endpoint/model settings are retained. Re-enter and save the key."
            ""
        }
        val raw = p[profilesKey]
        if (raw == null) {
            val cipher = p[stringPreferencesKey("encrypted_key")].orEmpty()
            val plain = secret(cipher)
            val config = AiConfig(enabled = warning == null && (p[booleanPreferencesKey("enabled")] ?: false),
                endpoint = p[stringPreferencesKey("endpoint")].orEmpty(), model = p[stringPreferencesKey("model")].orEmpty(),
                apiKey = plain, temperature = p[doublePreferencesKey("temperature")] ?: .6)
            return AiProfileStore("default", listOf(AiProfile("default", "Default provider", config)), warning, if (warning != null) setOf("default") else emptySet())
        }
        require(raw.length <= 100_000) { "Saved provider profiles are too large. Device settings were not overwritten." }
        val root = CompatibleStoryteller.boundedObject(raw)
        require(root.getInt("version") == 1) { "Unsupported device profile version. Settings were not changed." }
        val array = root.getJSONArray("profiles"); require(array.length() in 1..8)
        val unreadable = mutableSetOf<String>()
        val profiles = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i); val cipher = o.getString("keyCipher")
            val plain = secret(cipher)
            val failed = cipher.isNotBlank() && plain.isEmpty()
            if (failed) unreadable += o.getString("id")
            val sources = o.getJSONArray("sources"); require(sources.length() <= 8)
            AiProfile(o.getString("id"), o.getString("name"), AiConfig(
                enabled = o.getBoolean("enabled") && !failed, endpoint = o.getString("endpoint"), model = o.getString("model"), apiKey = plain,
                temperature = o.getDouble("temperature"), autoConnect = o.getBoolean("autoConnect"), streaming = o.getBoolean("streaming"),
                contextTokens = o.getInt("contextTokens"), manualContext = o.getBoolean("manualContext"), maxOutputTokens = o.getInt("maxOutputTokens"),
                knowledgeEnabled = o.getBoolean("knowledgeEnabled"), sources = (0 until sources.length()).map { sources.getString(it) }, searchEndpoint = o.getString("searchEndpoint"),
            ))
        }
        require(profiles.map { it.id }.distinct().size == profiles.size)
        val selected = root.getString("activeId"); require(profiles.any { it.id == selected })
        return AiProfileStore(selected, profiles, warning, unreadable)
    }
    private suspend fun write(profiles: AiProfileStore) {
        // Never silently discard a different profile's unreadable ciphertext when saving another one.
        val stored = store.data.first()
        val oldRaw = stored[profilesKey]?.let { JSONObject(it).getJSONArray("profiles") }
        fun previous(id: String): Pair<String, Boolean> {
            if (oldRaw == null && id == "default") return stored[stringPreferencesKey("encrypted_key")].orEmpty() to (stored[booleanPreferencesKey("enabled")] ?: false)
            val old = (0 until (oldRaw?.length() ?: 0)).map { oldRaw!!.getJSONObject(it) }.first { it.getString("id") == id }
            return old.getString("keyCipher") to old.getBoolean("enabled")
        }
        val array = JSONArray(profiles.profiles.map { profile ->
            val c = profile.config
            val retained = if (profile.id in profiles.unreadableIds) previous(profile.id) else null
            JSONObject().put("id", profile.id).put("name", profile.name).put("enabled", retained?.second ?: c.enabled)
                .put("endpoint", c.endpoint).put("model", c.model).put("keyCipher", retained?.first ?: encrypt(c.apiKey)).put("temperature", c.temperature)
                .put("autoConnect", c.autoConnect).put("streaming", c.streaming).put("contextTokens", c.contextTokens).put("manualContext", c.manualContext)
                .put("maxOutputTokens", c.maxOutputTokens).put("knowledgeEnabled", c.knowledgeEnabled).put("sources", JSONArray(c.sources)).put("searchEndpoint", c.searchEndpoint)
        })
        val json = JSONObject().put("version", 1).put("activeId", profiles.activeId).put("profiles", array).toString()
        store.edit {
            it[profilesKey] = json
            // Legacy single-profile values are removed only after successful, atomic replacement.
            listOf("endpoint", "model", "encrypted_key").forEach { key -> it.remove(stringPreferencesKey(key)) }
            it.remove(booleanPreferencesKey("enabled")); it.remove(doublePreferencesKey("temperature"))
        }
    }
    suspend fun save(config: AiConfig): AiProfileStore = withContext(Dispatchers.IO) { lock.withLock {
        validateStored(config)
        val current = read()
        val remaining = current.unreadableIds - current.activeId
        val next = current.copy(profiles = current.profiles.map { if (it.id == current.activeId) it.copy(config = config) else it },
            unreadableIds = remaining, warning = current.warning.takeIf { remaining.isNotEmpty() })
        write(next); next
    } }
    suspend fun add(name: String, config: AiConfig): AiProfileStore = withContext(Dispatchers.IO) { lock.withLock {
        validateStored(config); val current = read()
        require(current.profiles.size < 8) { "Up to eight provider profiles are supported." }
        require(name.trim().length in 1..60) { "Use a profile name of 1–60 characters." }
        require(current.profiles.none { it.name.equals(name.trim(), true) }) { "That profile name is already in use." }
        val id = UUID.randomUUID().toString()
        val next = current.copy(activeId = id, profiles = current.profiles + AiProfile(id, name.trim(), config))
        write(next); next
    } }
    suspend fun rename(id: String, name: String): AiProfileStore = withContext(Dispatchers.IO) { lock.withLock {
        val current = read()
        require(name.trim().length in 1..60) { "Use a profile name of 1–60 characters." }
        require(current.profiles.any { it.id == id })
        require(current.profiles.none { it.id != id && it.name.equals(name.trim(), true) }) { "That profile name is already in use." }
        current.copy(profiles = current.profiles.map { if (it.id == id) it.copy(name = name.trim()) else it }).also { write(it) }
    } }
    suspend fun select(id: String): AiProfileStore = withContext(Dispatchers.IO) { lock.withLock {
        val current = read(); require(current.profiles.any { it.id == id })
        current.copy(activeId = id).also { write(it) }
    } }
    suspend fun remove(id: String): AiProfileStore = withContext(Dispatchers.IO) { lock.withLock {
        val current = read(); require(current.profiles.size > 1) { "Keep at least one provider profile." }
        require(current.profiles.any { it.id == id })
        val kept = current.profiles.filterNot { it.id == id }
        current.copy(activeId = if (current.activeId == id) kept.first().id else current.activeId, profiles = kept, unreadableIds = current.unreadableIds - id, warning = current.warning.takeIf { (current.unreadableIds - id).isNotEmpty() }).also { write(it) }
    } }
    private fun validateStored(c: AiConfig) {
        if (c.enabled || c.endpoint.isNotBlank()) CompatibleStoryteller.validate(c, requireModel = c.enabled)
        else CompatibleStoryteller.validate(c.copy(endpoint = "https://example.invalid/v1/chat/completions", model = "unconfigured"))
    }
}
