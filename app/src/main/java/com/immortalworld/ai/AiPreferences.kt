package com.immortalworld.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.aiStore by preferencesDataStore("ai-provider")
/** Keys are encrypted with an Android Keystore key; never included in portable saves. */
class AiPreferences(private val context: Context) {
    private val enabled = booleanPreferencesKey("enabled")
    private val endpoint = stringPreferencesKey("endpoint")
    private val model = stringPreferencesKey("model")
    private val secret = stringPreferencesKey("encrypted_key")
    private val temperature = doublePreferencesKey("temperature")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("immortal-world-ai", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("immortal-world-ai", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    suspend fun load(): AiConfig = withContext(Dispatchers.IO) {
        val p = context.aiStore.data.first()
        val encrypted = p[secret].orEmpty()
        val plain = if (encrypted.isBlank()) "" else try {
            val data = Base64.decode(encrypted, Base64.NO_WRAP)
            require(data.size >= 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
        } catch (_: Exception) { throw AiFailure("The saved API key could not be decrypted. Re-enter and save the provider settings; offline gameplay remains available.") }
        AiConfig(p[enabled] ?: false, p[endpoint].orEmpty(), p[model].orEmpty(), plain, p[temperature] ?: .6)
    }
    suspend fun save(config: AiConfig) = withContext(Dispatchers.IO) {
        require(config.temperature.isFinite() && config.temperature in 0.0..2.0) { "Temperature must be between 0 and 2." }
        require(config.apiKey.length <= 1000 && '\n' !in config.apiKey && '\r' !in config.apiKey) { "Invalid API key format." }
        if (config.enabled || config.endpoint.isNotBlank()) CompatibleStoryteller.validate(config)
        val encrypted = if (config.apiKey.isEmpty()) "" else Cipher.getInstance("AES/GCM/NoPadding").let { cipher ->
            cipher.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(cipher.iv + cipher.doFinal(config.apiKey.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }
        context.aiStore.edit {
            it[enabled] = config.enabled; it[endpoint] = config.endpoint; it[model] = config.model; it[secret] = encrypted; it[temperature] = config.temperature
        }
    }
}
