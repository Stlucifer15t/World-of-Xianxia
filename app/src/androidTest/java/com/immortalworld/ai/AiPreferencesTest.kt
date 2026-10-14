package com.immortalworld.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AiPreferencesTest {
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: AiPreferences
    private lateinit var file: File
    private lateinit var alias: String
    private lateinit var context: Context
    private val profilesKey = stringPreferencesKey("profiles_v1")
    private fun sample(key: String = "instrumentation-only-secret") = AiConfig(enabled = true,
        endpoint = "https://example.invalid/v1/chat/completions", model = "test-model", apiKey = key,
        contextTokens = 1_000_000, manualContext = true, maxOutputTokens = 900, streaming = false, autoConnect = false,
        knowledgeEnabled = true, sources = listOf("https://example.invalid/wiki/Cultivation"))
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val id = UUID.randomUUID().toString(); alias = "test-ai-$id"
        file = context.preferencesDataStoreFile("test-ai-$id")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PreferenceDataStoreFactory.create(scope = scope) { file }
        prefs = AiPreferences(context, store, alias)
    }
    @After fun teardown() = runBlocking {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        file.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) }
        Unit
    }
    @Test fun encryptedConfigurationReloadsWithMillionTokenWindow() = runBlocking {
        val c = sample(); prefs.save(c)
        assertEquals(c, AiPreferences(context, store, alias).load())
        assertFalse(store.data.first()[profilesKey]!!.contains(c.apiKey))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(c.apiKey))
    }
    @Test fun legacySingleProviderMigratesOnlyOnSuccessfulSave() = runBlocking {
        prefs.save(sample())
        val cipher = JSONObject(store.data.first()[profilesKey]!!).getJSONArray("profiles").getJSONObject(0).getString("keyCipher")
        store.edit {
            it.remove(profilesKey); it[booleanPreferencesKey("enabled")] = true
            it[stringPreferencesKey("endpoint")] = sample().endpoint; it[stringPreferencesKey("model")] = sample().model
            it[stringPreferencesKey("encrypted_key")] = cipher; it[doublePreferencesKey("temperature")] = .8
        }
        val migrated = prefs.load()
        assertTrue(migrated.enabled); assertEquals(sample().apiKey, migrated.apiKey); assertEquals(sample().endpoint, migrated.endpoint)
        assertEquals(.8, migrated.temperature, .0); assertNull(store.data.first()[profilesKey])
        prefs.save(migrated)
        assertNotNull(store.data.first()[profilesKey]); assertNull(store.data.first()[stringPreferencesKey("encrypted_key")])
        assertEquals(migrated, prefs.load())
    }
    @Test fun profilesStayIndependentAndSelectedProfilePersists() = runBlocking {
        val first = prefs.save(sample("first-secret"))
        val second = prefs.add("Second", sample("second-secret").copy(model = "other-model"))
        assertEquals(2, second.profiles.size)
        prefs.select(first.activeId)
        assertEquals("first-secret", prefs.load().apiKey)
        assertEquals(first.activeId, AiPreferences(context, store, alias).loadProfiles().activeId)
        prefs.remove(second.activeId); assertEquals(1, prefs.loadProfiles().profiles.size)
        assertTrue(runCatching { prefs.remove(first.activeId) }.isFailure)
    }
    @Test fun brokenKeyRetainsMetadataAndCanBeRepairedWithoutErasingAnotherKey() = runBlocking {
        val first = prefs.save(sample("first-secret"))
        prefs.add("Healthy", sample("healthy-secret"))
        store.edit {
            val root = JSONObject(it[profilesKey]!!)
            root.getJSONArray("profiles").getJSONObject(0).put("keyCipher", "INVALID_CIPHER")
            root.put("activeId", first.activeId); it[profilesKey] = root.toString()
        }
        val broken = prefs.loadProfiles()
        assertFalse(broken.active.config.enabled); assertEquals(sample().model, broken.active.config.model)
        assertNotNull(broken.warning)
        val other = broken.profiles.first { it.id != first.activeId }
        prefs.select(other.id); prefs.save(other.config.copy(apiKey = "updated-healthy"))
        assertEquals("INVALID_CIPHER", JSONObject(store.data.first()[profilesKey]!!).getJSONArray("profiles").getJSONObject(0).getString("keyCipher"))
        prefs.select(first.activeId); prefs.save(sample("repaired-secret"))
        assertNull(prefs.loadProfiles().warning); assertEquals("repaired-secret", prefs.load().apiKey)
        prefs.select(other.id); assertEquals("updated-healthy", prefs.load().apiKey)
    }
}
