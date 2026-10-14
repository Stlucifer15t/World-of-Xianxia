package com.immortalworld.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.preferencesStore by preferencesDataStore("preferences")
data class AppPreferences(val autosave: Boolean = true, val lastSlot: String? = null)
class Preferences(context: Context) {
    private val store = context.applicationContext.preferencesStore
    private val autosaveKey = booleanPreferencesKey("autosave")
    private val lastSlotKey = stringPreferencesKey("last_slot")
    val values = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { AppPreferences(it[autosaveKey] ?: true, it[lastSlotKey]) }
    suspend fun setAutosave(value: Boolean) { store.edit { it[autosaveKey] = value } }
    suspend fun setLastSlot(id: String?) { store.edit { if (id == null) it.remove(lastSlotKey) else it[lastSlotKey] = id } }
}
