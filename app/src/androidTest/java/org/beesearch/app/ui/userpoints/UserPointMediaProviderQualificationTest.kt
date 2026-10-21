package org.beesearch.app.ui.userpoints

import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.data.repository.RoomUserPointRepository
import org.beesearch.app.domain.model.UserPoint
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserPointMediaProviderQualificationTest {
    @get:Rule val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var media: UserPointMediaStore
    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomUserPointRepository
    private val territory = UUID.randomUUID()
    private val ownedFiles = mutableListOf<File>()

    @Before
    fun setUp() = runBlocking {
        media = UserPointMediaStore(context.filesDir, context.cacheDir)
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).build()
        repository = RoomUserPointRepository(database)
        database.backupDao().insertTerritories(
            listOf(TerritoryEntity(territory, "Provider test", "Provider test", "R", "D", Instant.EPOCH, Instant.EPOCH)),
        )
    }

    @After
    fun tearDown() {
        database.close()
        ownedFiles.forEach { it.delete() }
    }

    @Test
    fun fileProviderReadsDraftAndActivatedManagedBytesAndRejectsOtherPaths(): Unit = runBlocking {
        val draftSession = UUID.randomUUID()
        val draft = media.stageDraftMedia(
            draftSession, UUID.randomUUID(), "draft.jpg", "image/jpeg", Instant.now(),
        ) { "draft-bytes".byteInputStream() }
        val draftFile = media.resolveDraft(draft.relativePath)
        ownedFiles += draftFile
        assertContentBytes(draftFile, "draft-bytes".toByteArray())

        val owner = UUID.randomUUID()
        val activation = media.prepareDraftActivation(draftSession, owner, listOf(draft))
        val managed = media.resolve(activation.photos.single().relativePath)
        ownedFiles += managed
        activation.commit()
        assertContentBytes(managed, "draft-bytes".toByteArray())

        val privateFile = File(context.filesDir, "provider-private-${UUID.randomUUID()}.jpg").apply {
            writeBytes("private".toByteArray())
        }
        val sibling = File(context.cacheDir, "user-point-media-staging-sibling-${UUID.randomUUID()}.jpg").apply {
            writeBytes("sibling".toByteArray())
        }
        val filesSibling = File(context.filesDir, "user-point-media-staging-sibling-${UUID.randomUUID()}.jpg").apply {
            writeBytes("files-sibling".toByteArray())
        }
        ownedFiles += privateFile
        ownedFiles += sibling
        ownedFiles += filesSibling
        assertThrows(IllegalArgumentException::class.java) { fileProviderUri(privateFile) }
        assertThrows(IllegalArgumentException::class.java) { fileProviderUri(sibling) }
        assertThrows(IllegalArgumentException::class.java) { fileProviderUri(filesSibling) }
        assertThrows(IllegalArgumentException::class.java) {
            fileProviderUri(File(context.filesDir, "user-point-media-staging/../provider-private-${UUID.randomUUID()}.jpg"))
        }

        val otherSession = UUID.randomUUID()
        val otherDraft = media.stageDraftMedia(
            otherSession, UUID.randomUUID(), "other.jpg", "image/jpeg", Instant.now(),
        ) { "other-draft".byteInputStream() }
        val otherDraftFile = media.resolveDraft(otherDraft.relativePath)
        ownedFiles += otherDraftFile
        assertContentBytes(otherDraftFile, "other-draft".toByteArray())
        val otherManagedSession = UUID.randomUUID()
        val otherManagedDraft = media.stageDraftMedia(
            otherManagedSession, UUID.randomUUID(), "other-managed.jpg", "image/jpeg", Instant.now(),
        ) { "other-managed".byteInputStream() }
        val otherActivation = media.prepareDraftActivation(otherManagedSession, UUID.randomUUID(), listOf(otherManagedDraft))
        val otherManagedFile = media.resolve(otherActivation.photos.single().relativePath)
        ownedFiles += otherManagedFile
        otherActivation.commit()

        // Discard a live draft, while keeping another session and both managed owners intact.
        val cancelledDraft = media.stageDraftMedia(
            draftSession, UUID.randomUUID(), "cancelled.jpg", "image/jpeg", Instant.now(),
        ) { "cancelled-draft".byteInputStream() }
        val cancelledFile = media.resolveDraft(cancelledDraft.relativePath)
        ownedFiles += cancelledFile
        assertTrue(cancelledFile.isFile)
        assertTrue(media.discardDraft(draftSession))
        assertFalse(cancelledFile.exists())
        assertFalse(draftFile.exists())
        assertTrue(managed.isFile)
        assertTrue(otherDraftFile.isFile)
        assertTrue(managed.delete())
        assertTrue(otherDraftFile.isFile)
        assertTrue(otherManagedFile.isFile)
        media.discardDraft(otherSession)
        assertTrue(otherManagedFile.delete())
        media.discardDraft(otherManagedSession)
    }

    @Test
    fun editorCameraSuccessWritesThroughProviderAndSavesPhotoToRoomAndCard() {
        val registry = FakeTakePictureRegistry(context, saveResult = true)
        val owner = TestActivityResultOwner(registry)
        var saved by mutableStateOf<UserPoint?>(null)
        val target = UserPointEditorTarget(UUID.randomUUID(), territory, latitude = 56.8, longitude = 60.6)
        composeRule.setContent {
            TestRegistryContent(owner) {
                if (saved == null) {
                    UserPointEditorRoute(target, repository, media, onCancel = {}, onSaved = { saved = it }, onChangeLocation = { _, _ -> })
                } else {
                    UserPointDetailRoute(territory, saved!!.id, repository, media, onBack = {})
                }
            }
        }
        composeRule.onNodeWithText("Сделать фото").performScrollTo().performClick()
        composeRule.onNodeWithTag("physical-object-media-thumbnails").assertIsDisplayed()
        composeRule.runOnIdle { assertNotNull(registry.lastUri) }
        composeRule.onNodeWithTag("user-point-title").performScrollTo().performTextInput("Сохранённая точка")
        composeRule.onNodeWithTag("user-point-save").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { saved != null }
        val point = runBlocking { repository.getPoint(territory, saved!!.id) }
        assertNotNull(point)
        assertEquals(1, point!!.photos.size)
        val managedFile = media.resolve(point.photos.single().relativePath)
        ownedFiles += managedFile
        assertArrayEquals(validJpegBytes(), managedFile.readBytes())
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("user-point-card").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("user-point-card").assertIsDisplayed()
        // The card container exists during its asynchronous read; wait for loaded content.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Сохранённая точка").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Сохранённая точка").assertIsDisplayed()
        owner.destroy()
    }

    @Test
    fun editorCameraCancelDoesNotStagePhotoOrCreateRoomPoint() {
        val registry = FakeTakePictureRegistry(context, saveResult = false)
        val owner = TestActivityResultOwner(registry)
        var cancelled = false
        val target = UserPointEditorTarget(UUID.randomUUID(), territory, latitude = 56.8, longitude = 60.6)
        composeRule.setContent {
            TestRegistryContent(owner) {
                UserPointEditorRoute(target, repository, media, onCancel = { cancelled = true }, onSaved = {}, onChangeLocation = { _, _ -> })
            }
        }
        composeRule.onNodeWithText("Сделать фото").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { registry.lastUri != null }
        composeRule.onNodeWithTag("physical-object-media-thumbnails").assertDoesNotExist()
        assertEquals(0, runBlocking { database.userPointDao().countForTerritory(territory) })
        assertThrows(FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(requireNotNull(registry.lastUri))!!.use { it.readBytes() }
        }
        composeRule.onNodeWithTag("user-point-cancel").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { cancelled }
        owner.destroy()
        assertTrue(cancelled)
    }

    private fun assertContentBytes(file: File, expected: ByteArray) {
        val uri = fileProviderUri(file)
        assertEquals("content", uri.scheme)
        val actual = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertArrayEquals(expected, actual)
    }

    private fun fileProviderUri(file: File): Uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file,
    )


}

@Composable
private fun TestRegistryContent(owner: TestActivityResultOwner, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner, content = content)
}

private class TestActivityResultOwner(
    override val activityResultRegistry: ActivityResultRegistry,
) : ActivityResultRegistryOwner {
    fun destroy() = Unit
}

private class FakeTakePictureRegistry(
    private val context: Context,
    private val saveResult: Boolean,
) : ActivityResultRegistry() {
    var lastUri: Uri? = null

    @Suppress("UNCHECKED_CAST")
    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
        input: I,
        options: androidx.core.app.ActivityOptionsCompat?,
    ) {
        val uri = input as Uri
        lastUri = uri
        if (saveResult) {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(validJpegBytes()) }
        }
        dispatchResult(requestCode, saveResult as O)
    }
}

private fun validJpegBytes(): ByteArray {
    val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
    return java.io.ByteArrayOutputStream().use { output ->
        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, output))
        bitmap.recycle()
        output.toByteArray()
    }
}
