package org.beesearch.app.data.local.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.MarkPosition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The standing invariant behind "no object is deleted while something still points at it".
 *
 * Every persisted reference to `physical_objects` must be `ON DELETE RESTRICT`, so no row can
 * outlive the object it references. Owned rows (subtype, media) use the same rule and are removed by
 * the explicit deletion; working/historical rows (`bees.source_object_id`) block the deletion
 * instead. A reference that silently cascaded or nulled itself would destroy historical linkage
 * without any user-visible reason, which is why this property is asserted for every table of the
 * live schema, not only for the tables that exist today.
 *
 * The check reads the foreign keys of the database that Room actually created, so it does not
 * depend on the exact text of any DDL statement.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalObjectReferenceRestrictTest {
    private lateinit var database: BeeSearchDatabase
    private val territoryId = UUID.randomUUID()
    private val observerId = UUID.randomUUID()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.backupDao().insertTerritories(
            listOf(TerritoryEntity(territoryId, "T", "Territory", "R", "D", NOW, NOW)),
        )
        database.backupDao().insertObservers(
            listOf(ObserverEntity(observerId, "O", "Last", "First", null, null, NOW, NOW)),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun everyReferenceToPhysicalObjectsIsRestrict() {
        val references = foreignKeys()
            .filter { it.parentTable == "physical_objects" }

        assertTrue("no reference to physical_objects was found", references.isNotEmpty())
        references.forEach { reference ->
            assertEquals(
                "reference ${reference.table}.${reference.childColumn} must block the deletion",
                "RESTRICT",
                reference.onDelete,
            )
        }
    }

    /** The protected working/historical reference is exactly the Bee link, and it blocks deletion. */
    @Test
    fun beeSourceReferenceBlocksADeletionPerformedOutsideTheRepository() = runBlocking {
        val beeReference = foreignKeys()
            .first { it.table == "bees" && it.parentTable == "physical_objects" }

        assertEquals("source_object_id", beeReference.childColumn)
        assertEquals("RESTRICT", beeReference.onDelete)

        val objectId = insertObject()
        val pointId = UUID.randomUUID()
        val beeId = UUID.randomUUID()
        database.backupDao().insertObservationPoints(
            listOf(
                ObservationPointEntity(
                    pointId, territoryId, observerId, 2026, 1, BeePresenceResult.BEES_FOUND,
                    null, 56.0, 42.0, null, null, null, NOW, NOW, null,
                ),
            ),
        )
        database.backupDao().insertBees(listOf(BeeEntity(beeId, pointId, "WHITE", MarkPosition.THORAX, NOW, objectId)))

        val sqlite = database.openHelper.writableDatabase
        assertThrows(SQLiteConstraintException::class.java) {
            sqlite.execSQL("DELETE FROM physical_objects WHERE id = ?", arrayOf(objectId.toString()))
        }
        // The protected link itself survives: the object is still referenced by the Bee.
        database.backupDao().bees().single { it.id == beeId }.also {
            assertEquals(objectId, it.sourceObjectId)
        }
        Unit
    }

    /** Owned dependencies never cascade: the explicit deletion removes them, nothing else may. */
    @Test
    fun ownedDependenciesNeverDisappearOnTheirOwn() = runBlocking {
        val owned = foreignKeys().filter { it.parentTable == "physical_objects" && it.table != "bees" }
        assertTrue(owned.isNotEmpty())
        owned.forEach { assertEquals("RESTRICT", it.onDelete) }

        val objectId = insertObject()
        database.backupDao().insertHollows(listOf(HollowEntity(objectId, "дуб", 180.0, 123, 40.0, 25.0, "note")))
        database.backupDao().insertPhysicalObjectMedia(
            listOf(
                PhysicalObjectMediaEntity(
                    UUID.randomUUID(), objectId, org.beesearch.app.domain.model.PhysicalObjectMediaType.IMAGE,
                    "physical-object-media/$objectId/photo", "photo.jpg", "image/jpeg", 10, "a".repeat(64), NOW,
                ),
            ),
        )

        val sqlite = database.openHelper.writableDatabase
        assertThrows(SQLiteConstraintException::class.java) {
            sqlite.execSQL("DELETE FROM physical_objects WHERE id = ?", arrayOf(objectId.toString()))
        }

        assertEquals(1, database.physicalObjectDao().getMedia(objectId).size)
        assertTrue(database.physicalObjectDao().getHollow(objectId) != null)
        // The same object is removable through the explicit deletion, which owns those rows.
        assertTrue(repository().deleteHollow(objectId).mediaRelativePaths.isNotEmpty())
        assertEquals(0, database.physicalObjectDao().countInScope(territoryId, org.beesearch.app.domain.model.PhysicalObjectType.HOLLOW))
    }

    private suspend fun insertObject(): UUID {
        val id = UUID.randomUUID()
        database.backupDao().insertPhysicalObjects(
            listOf(
                PhysicalObjectEntity(
                    id, territoryId, org.beesearch.app.domain.model.PhysicalObjectType.HOLLOW, 1, 56.1, 42.7, NOW, observerId,
                ),
            ),
        )
        return id
    }

    private fun repository() = org.beesearch.app.data.repository.RoomPhysicalObjectRepository(
        database,
        database.physicalObjectDao(),
        database.physicalObjectSequenceDao(),
        database.territoryDao(),
        database.observerDao(),
        database.beeDao(),
        java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC),
    )

    private data class ForeignKeyRow(
        val table: String,
        val childColumn: String,
        val parentTable: String,
        val onDelete: String,
    )

    private fun foreignKeys(): List<ForeignKeyRow> {
        val sqlite = database.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
        ).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        return tables.flatMap { table ->
            val rows = mutableListOf<ForeignKeyRow>()
            sqlite.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
                val parentTable = cursor.getColumnIndexOrThrow("table")
                val childColumn = cursor.getColumnIndexOrThrow("from")
                val onDelete = cursor.getColumnIndexOrThrow("on_delete")
                while (cursor.moveToNext()) {
                    rows += ForeignKeyRow(
                        table = table,
                        childColumn = cursor.getString(childColumn),
                        parentTable = cursor.getString(parentTable),
                        onDelete = cursor.getString(onDelete),
                    )
                }
            }
            rows
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-24T09:30:00Z")
    }
}
