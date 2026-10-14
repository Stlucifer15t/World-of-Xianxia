package com.immortalworld.data

import androidx.room.withTransaction
import com.immortalworld.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class LoadedGame(val save: GameSave, val revision: Long)
class SaveRepository(private val db: GameDatabase, private val now: () -> Long = System::currentTimeMillis) {
    private val dao = db.saves()
    val slots = dao.observeSlots()
    suspend fun create(save: GameSave): LoadedGame = withContext(Dispatchers.IO) {
        val json = SaveCodec.encode(save)
        db.withTransaction {
            require(dao.slot(save.id) == null) { "That save ID already exists. Use import to create a separate slot." }
            dao.insertSlot(metadata(save, 1)); dao.putState(StateEntity(save.id, json))
            LoadedGame(save, 1)
        }
    }
    suspend fun load(id: String): LoadedGame = withContext(Dispatchers.IO) {
        db.withTransaction {
            val meta = dao.slot(id) ?: error("Save slot no longer exists.")
            val state = dao.state(id) ?: error("Save state is missing. Try restoring a backup.")
            val save = SaveCodec.decode(state.json)
            require(save.id == id && save.name == meta.name) { "Save metadata is inconsistent. Try restoring a backup." }
            LoadedGame(save, meta.revision)
        }
    }
    suspend fun save(save: GameSave, expectedRevision: Long, reason: String = "Before save"): LoadedGame = withContext(Dispatchers.IO) {
        SaveValidator.validate(save)
        db.withTransaction { overwrite(save, expectedRevision, reason) }
    }
    private suspend fun overwrite(save: GameSave, expectedRevision: Long, reason: String): LoadedGame {
        val current = dao.slot(save.id) ?: error("Save slot no longer exists.")
        require(current.revision == expectedRevision) { "This slot changed elsewhere. Reload it before saving; your changes were not written." }
        val oldState = dao.state(save.id) ?: error("Save state is missing.")
        val timestamp = maxOf(now(), save.updatedAt, current.updatedAt, save.createdAt)
        val updated = save.copy(updatedAt = timestamp)
        val json = SaveCodec.encode(updated)
        dao.insertBackup(BackupEntity(UUID.randomUUID().toString(), save.id, timestamp, current.revision, reason, oldState.json))
        val revision = current.revision + 1
        dao.updateSlot(metadata(updated, revision)); dao.putState(StateEntity(updated.id, json))
        dao.pruneBackups(updated.id)
        return LoadedGame(updated, revision)
    }
    /** External IDs never select local destinations: import always creates a new slot. */
    suspend fun importJson(json: String): LoadedGame = withContext(Dispatchers.IO) {
        val source = SaveCodec.decode(json)
        create(fork(source, "${source.name.take(68)} (imported)"))
    }
    suspend fun duplicate(id: String): LoadedGame = withContext(Dispatchers.IO) {
        val source = load(id).save
        create(fork(source, "${source.name.take(72)} (copy)"))
    }
    private fun fork(source: GameSave, name: String): GameSave {
        val timestamp = maxOf(now(), 0)
        return source.copy(id = UUID.randomUUID().toString(), name = name, createdAt = timestamp, updatedAt = timestamp)
    }
    suspend fun rename(id: String, name: String, expectedRevision: Long): LoadedGame = withContext(Dispatchers.IO) {
        val current = load(id)
        save(current.save.copy(name = name.trim()), expectedRevision, "Before rename")
    }
    suspend fun delete(id: String, expectedRevision: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            require(dao.slot(id)?.revision == expectedRevision) { "Slot changed; refresh before deleting." }
            dao.deleteSlot(id)
        }
    }
    suspend fun backups(id: String): List<BackupSummary> = withContext(Dispatchers.IO) { dao.backups(id) }
    suspend fun restore(id: String, backupId: String, expectedRevision: Long): LoadedGame = withContext(Dispatchers.IO) {
        db.withTransaction {
            val backup = dao.backup(backupId) ?: error("Backup no longer exists.")
            require(backup.slotId == id) { "Backup belongs to another slot." }
            val state = SaveCodec.decode(backup.json)
            require(state.id == id) { "Backup identity is invalid." }
            overwrite(state, expectedRevision, "Before restore")
        }
    }
    private fun metadata(s: GameSave, revision: Long) = SlotEntity(
        s.id, s.name, s.player.name, s.player.race, s.realms.first { it.id == s.player.cultivation.realmId }.name,
        s.player.cultivation.substage, s.world.day, s.createdAt, s.updatedAt, revision,
    )
}
