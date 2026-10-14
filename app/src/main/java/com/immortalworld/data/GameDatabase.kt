package com.immortalworld.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "slots")
data class SlotEntity(
    @PrimaryKey val id: String, val name: String, val playerName: String, val race: String,
    val realmName: String, val substage: Int, val day: Long,
    val createdAt: Long, val updatedAt: Long, val revision: Long,
)
/** A complete, validated campaign aggregate, updated atomically with its indexed metadata. */
@Entity(tableName = "states", foreignKeys = [ForeignKey(entity = SlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.CASCADE)])
data class StateEntity(@PrimaryKey val slotId: String, val json: String)
@Entity(tableName = "backups", indices = [Index("slotId")], foreignKeys = [ForeignKey(entity = SlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.CASCADE)])
data class BackupEntity(@PrimaryKey val id: String, val slotId: String, val createdAt: Long, val revision: Long, val reason: String, val json: String)
data class BackupSummary(val id: String, val slotId: String, val createdAt: Long, val revision: Long, val reason: String)

@Dao
interface SaveDao {
    @Query("SELECT * FROM slots ORDER BY updatedAt DESC") fun observeSlots(): Flow<List<SlotEntity>>
    @Query("SELECT * FROM slots WHERE id = :id") suspend fun slot(id: String): SlotEntity?
    @Query("SELECT * FROM states WHERE slotId = :id") suspend fun state(id: String): StateEntity?
    @Insert suspend fun insertSlot(slot: SlotEntity)
    @Update suspend fun updateSlot(slot: SlotEntity)
    @Upsert suspend fun putState(state: StateEntity)
    @Insert suspend fun insertBackup(backup: BackupEntity)
    @Query("SELECT id, slotId, createdAt, revision, reason FROM backups WHERE slotId = :slotId ORDER BY revision DESC")
    suspend fun backups(slotId: String): List<BackupSummary>
    @Query("SELECT * FROM backups WHERE id = :id") suspend fun backup(id: String): BackupEntity?
    @Query("DELETE FROM backups WHERE slotId = :slotId AND id NOT IN (SELECT id FROM backups WHERE slotId = :slotId ORDER BY revision DESC LIMIT 5)")
    suspend fun pruneBackups(slotId: String)
    @Query("DELETE FROM slots WHERE id = :id") suspend fun deleteSlot(id: String)
}
@Database(entities = [SlotEntity::class, StateEntity::class, BackupEntity::class], version = 1, exportSchema = true)
abstract class GameDatabase : RoomDatabase() { abstract fun saves(): SaveDao }
