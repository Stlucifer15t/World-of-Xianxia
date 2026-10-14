package com.immortalworld.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immortalworld.core.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class SaveRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: GameDatabase
    private lateinit var repository: SaveRepository
    private lateinit var filename: String
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        filename = "test-${UUID.randomUUID()}.db"
        open()
    }
    private fun open() {
        db = Room.databaseBuilder(context, GameDatabase::class.java, filename).build()
        repository = SaveRepository(db)
    }
    @After fun teardown() { db.close(); context.deleteDatabase(filename) }
    private fun campaign(id: String = UUID.randomUUID().toString()): GameSave {
        val g = CharacterGenerator.generate(CreationOptions(), 12, "player")
        return GameSave(id = id, name = "My campaign", createdAt = 1, updatedAt = 1, randomState = g.randomState,
            player = g.character, world = WorldState("world", currentLocation = "Azure Cloud", startingCircumstances = "Beginning"))
    }
    @Test fun survivesDatabaseCloseAndReopen() = runBlocking {
        val original = repository.create(campaign())
        db.close(); open()
        assertEquals(original, repository.load(original.save.id))
    }
    @Test fun slotsAreIndependent() = runBlocking {
        val a = repository.create(campaign()); val b = repository.create(campaign())
        repository.save(a.save.copy(player = a.save.player.copy(name = "Changed")), a.revision)
        assertEquals(b, repository.load(b.save.id))
    }
    @Test fun duplicateUsesNewIdAndDoesNotShareMutations() = runBlocking {
        val a = repository.create(campaign()); val b = repository.duplicate(a.save.id)
        assertNotEquals(a.save.id, b.save.id)
        assertEquals(a.save.player, b.save.player)
        repository.save(b.save.copy(player = b.save.player.copy(name = "Copy")), b.revision)
        assertEquals(a.save.player, repository.load(a.save.id).save.player)
    }
    @Test fun importingSameFileTwiceAlwaysCreatesNewSlots() = runBlocking {
        val original = repository.create(campaign())
        val text = SaveCodec.encode(original.save)
        val a = repository.importJson(text); val b = repository.importJson(text)
        assertEquals(3, setOf(original.save.id, a.save.id, b.save.id).size)
        assertEquals(original, repository.load(original.save.id))
        assertEquals(original.save.player, a.save.player)
    }
    @Test fun invalidImportCannotChangeDatabase() = runBlocking {
        val a = repository.create(campaign())
        assertTrue(runCatching { repository.importJson("{bad json") }.isFailure)
        assertEquals(a, repository.load(a.save.id))
        assertEquals(1, repository.slots.first().size)
    }
    @Test fun invalidOverwriteIsAtomic() = runBlocking {
        val a = repository.create(campaign())
        assertTrue(runCatching { repository.save(a.save.copy(name = ""), a.revision) }.isFailure)
        assertEquals(a, repository.load(a.save.id))
        assertEquals(0, repository.backups(a.save.id).size)
    }
    @Test fun staleRevisionCannotOverwriteLatestState() = runBlocking {
        val a = repository.create(campaign())
        val b = repository.save(a.save.copy(name = "Latest"), a.revision)
        assertTrue(runCatching { repository.save(a.save.copy(name = "Stale"), a.revision) }.isFailure)
        assertEquals(b, repository.load(a.save.id))
    }
    @Test fun concurrentWritersCannotLoseAnUpdate() = runBlocking {
        val a = repository.create(campaign())
        val outcomes = coroutineScope {
            (1..2).map { index -> async { runCatching { repository.save(a.save.copy(name = "Update $index"), a.revision) }.isSuccess } }.map { it.await() }
        }
        assertEquals(1, outcomes.count { it })
        assertEquals(2L, repository.load(a.save.id).revision)
    }
    @Test fun overwriteKeepsOnlyFiveBackupsAndRestoreBacksUpCurrent() = runBlocking {
        var a = repository.create(campaign())
        repeat(8) { a = repository.save(a.save.copy(name = "Revision $it"), a.revision) }
        val backups = repository.backups(a.save.id)
        assertEquals(5, backups.size)
        val restored = repository.restore(a.save.id, backups.last().id, a.revision)
        assertNotEquals(a.save.name, restored.save.name)
        val beforeRestore = repository.backups(a.save.id).first()
        assertEquals("Before restore", beforeRestore.reason)
        val undone = repository.restore(a.save.id, beforeRestore.id, restored.revision)
        assertEquals(a.save.name, undone.save.name)
    }
    @Test fun backupCannotBeRestoredToAnotherSlot() = runBlocking {
        val a = repository.create(campaign()); val b = repository.create(campaign())
        repository.save(a.save, a.revision)
        assertTrue(runCatching { repository.restore(b.save.id, repository.backups(a.save.id).first().id, b.revision) }.isFailure)
        assertEquals(b, repository.load(b.save.id))
    }
    @Test fun corruptedCurrentStateCanRecoverFromBackup() = runBlocking {
        val original = repository.create(campaign())
        val updated = repository.save(original.save.copy(name = "Changed"), original.revision)
        db.saves().putState(StateEntity(original.save.id, "broken"))
        assertTrue(runCatching { repository.load(original.save.id) }.isFailure)
        val restored = repository.restore(original.save.id, repository.backups(original.save.id).first().id, updated.revision)
        assertEquals(original.save.player, restored.save.player)
        assertEquals(original.save.name, restored.save.name)
    }
    @Test fun renameKeepsMetadataAndPayloadTogether() = runBlocking {
        val a = repository.create(campaign())
        val renamed = repository.rename(a.save.id, "  New title  ", a.revision)
        assertEquals("New title", repository.slots.first().single().name)
        assertEquals("New title", repository.load(a.save.id).save.name)
        assertEquals(2L, renamed.revision)
    }
    @Test fun deletionCascadesOnlyWithinSelectedSlot() = runBlocking {
        val a = repository.create(campaign()); val b = repository.create(campaign())
        val updated = repository.save(a.save, a.revision)
        repository.delete(a.save.id, updated.revision)
        assertNull(db.saves().state(a.save.id))
        assertTrue(repository.backups(a.save.id).isEmpty())
        assertEquals(b, repository.load(b.save.id))
    }
    @Test fun staleDeleteIsRefused() = runBlocking {
        val a = repository.create(campaign()); val updated = repository.save(a.save, a.revision)
        assertTrue(runCatching { repository.delete(a.save.id, a.revision) }.isFailure)
        assertEquals(updated, repository.load(a.save.id))
    }
    @Test fun oldSaveMigrationKeepsOriginalBackupAndPlayer() = runBlocking {
        val original = repository.create(campaign())
        val oldJson = JSONObject(SaveCodec.encode(original.save)).put("version", 1).apply { remove("adventure") }.toString()
        db.saves().putState(StateEntity(original.save.id, oldJson))
        val loaded = repository.load(original.save.id)
        assertEquals(original.save.player, loaded.save.player)
        val upgraded = repository.save(AdventureFactory.ensure(loaded.save), loaded.revision, "Before adventure initialization / v1 migration")
        assertEquals(3, upgraded.save.version)
        assertNotNull(upgraded.save.adventure)
        val backup = db.saves().backup(repository.backups(original.save.id).first().id)!!
        assertEquals(1, JSONObject(backup.json).getInt("version"))
    }
    @Test fun gameplayCombatAndMemoriesSurviveCloseReopenAndReplay() = runBlocking {
        val original = campaign()
        var loaded = repository.create(AdventureFactory.ensure(original.copy(world = original.world.copy(currentLocation = "Azure Cloud Village"))))
        val engine = AdventureEngine()
        listOf(GameAction(ActionKind.TALK, "mei", "Where are the ruins?"), GameAction(ActionKind.CHALLENGE, "wen"), GameAction(ActionKind.DEFEND)).forEachIndexed { index, command ->
            val result = engine.execute(loaded.save, command, "player request $index", "request-$index")
            loaded = repository.save(result.state, loaded.revision)
        }
        db.close(); open()
        val restored = repository.load(loaded.save.id)
        assertEquals(loaded, restored)
        assertNotNull(restored.save.adventure!!.combat)
        assertFalse(restored.save.adventure!!.npcs.first { it.character.id == "mei" }.memories.isEmpty())
        assertEquals(engine.apply(loaded.save, GameAction(ActionKind.ATTACK)), engine.apply(restored.save, GameAction(ActionKind.ATTACK)))
        assertEquals(restored.save, engine.execute(restored.save, GameAction(ActionKind.DEFEND), "retry", "request-2").state)
    }

}
