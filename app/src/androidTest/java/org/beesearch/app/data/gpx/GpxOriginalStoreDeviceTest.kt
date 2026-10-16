package org.beesearch.app.data.gpx

import android.content.Context
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side qualification of the managed original Store and Android POSIX boundary. */
@RunWith(AndroidJUnit4::class)
class GpxOriginalStoreDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun copyPreservesBytesHashAndLengthIncludingBomUtf16Binary() = withSandbox { root ->
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte(), 0xff.toByte(), 0x00, 0x41, 0x00, 0x42)
        val ids = ids()
        val identity = GpxOriginalStore(root).copyToStaging(ids.request, ids.original, { ByteArrayInputStream(bytes) })
        assertEquals(bytes.size.toLong(), identity.length)
        assertEquals(sha256(bytes), identity.sha256)
        val stored = GpxOriginalStore(root).openStaged(ids.request, ids.original, identity).use { it.readBytes() }
        assertArrayEquals(bytes, stored)
        assertEquals(setOf("owner", "${ids.original}.part"),
            File(root, "gpx-staging/${ids.request}").list()!!.toSet())
    }

    @Test
    fun copyAcceptsBelowAndExactSixteenMiBAndRejectsFirstByteOver() = withSandbox { root ->
        val limit = 16 * 1024 * 1024
        fun copy(size: Int): GpxOriginalIdentity {
            val ids = ids()
            return GpxOriginalStore(root).copyToStaging(ids.request, ids.original, {
                ByteArrayInputStream(ByteArray(size) { (it and 0xff).toByte() })
            })
        }
        assertEquals((limit - 1).toLong(), copy(limit - 1).length)
        assertEquals(limit.toLong(), copy(limit).length)
        val ids = ids()
        val error = assertThrows(GpxOriginalStoreException::class.java) {
            GpxOriginalStore(root).copyToStaging(ids.request, ids.original, {
                ByteArrayInputStream(ByteArray(limit + 1) { (it and 0xff).toByte() })
            })
        }
        assertEquals(GpxOriginalStoreError.INPUT_LIMIT_EXCEEDED, error.code)
        assertEquals(GpxOriginalStage.COPY, error.stage)
        assertEquals(null, GpxOriginalStore(root).inspectOperation(ids.request, ids.original).stagingBytes)
    }

    @Test
    fun ownedInputClosesOnceAndCancellationRetainsSameObjectWithCloseFailureSuppressed() = withSandbox { root ->
        val ids = ids()
        val marker = CancellationException("copy cancelled")
        val closeFailure = IOException("source close")
        val source = TrackedInputStream(byteArrayOf(1, 2, 3), closeFailure)
        var checks = 0
        val thrown = assertThrows(CancellationException::class.java) {
            GpxOriginalStore(root).copyToStaging(ids.request, ids.original, { source }) {
                if (++checks >= 4) throw marker
            }
        }
        assertSame(marker, thrown)
        assertEquals(1, source.closeCount)
        val suppressed = marker.suppressed.single()
        assertTrue(suppressed is GpxOriginalStoreException)
        assertSame(closeFailure, suppressed.cause)
    }

    @Test
    fun copyRetryWithExpectedCompletedDoesNotReopenSource() = withSandbox { root ->
        val ids = ids()
        val bytes = validGpx()
        var opens = 0
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, {
            opens++
            ByteArrayInputStream(bytes)
        })
        val retried = store.copyToStaging(ids.request, ids.original, {
            opens++
            error("completed retry must not open source")
        }, expectedCompleted = expected)
        assertEquals(expected, retried)
        assertEquals(1, opens)
    }

    @Test
    fun publishRejectsUnknownSameBytesFinalWithoutReplacement() = withSandbox { root ->
        val ids = ids()
        val bytes = validGpx()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(bytes) })
        val originals = File(root, "gpx-originals").apply { mkdirs() }
        val final = File(originals, "${ids.original}/original.gpx")
        final.parentFile!!.mkdirs()
        final.writeBytes(bytes)
        val error = assertThrows(GpxOriginalStoreException::class.java) {
            store.publish(ids.request, ids.original, expected, authorizeParsed = { true })
        }
        assertEquals(GpxOriginalStoreError.CONFLICT, error.code)
        assertArrayEquals(bytes, final.readBytes())
        assertFalse(sameInode(File(root, "gpx-staging/${ids.request}/${ids.original}.part"), final))
    }

    @Test
    fun repeatedPublishKeepsOneInodeAndReturnsVerifiedResult() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        assertEquals(expected, store.publish(ids.request, ids.original, expected, authorizeParsed = { true }))
        val final = File(root, "gpx-originals/${ids.original}/original.gpx")
        val first = inode(final)
        assertEquals(expected, store.publish(ids.request, ids.original, expected, authorizeParsed = { true }))
        assertEquals(first, inode(final))
        assertFalse(sameInode(File(root, "gpx-staging/${ids.request}/${ids.original}.part"), final))
        assertEquals(GpxOriginalPresence.PRESENT_VERIFIED, store.inspectFinal(ids.original, expected))
    }

    @Test
    fun parsedAuthorizationDeniedLeavesStagingAndFinalUntouched() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        val error = assertThrows(GpxOriginalStoreException::class.java) {
            store.publish(ids.request, ids.original, expected, authorizeParsed = { false })
        }
        assertEquals(GpxOriginalStoreError.OWNERSHIP_VIOLATION, error.code)
        assertTrue(File(root, "gpx-staging/${ids.request}/${ids.original}.part").exists())
        assertFalse(File(root, "gpx-originals/${ids.original}/original.gpx").exists())
    }

    @Test
    fun cleanupAuthorizationDeniedAndReadyFixtureRetainFiles() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        assertThrows(GpxOriginalStoreException::class.java) {
            store.cleanupStaging(ids.request, ids.original) { false }
        }
        assertTrue(File(root, "gpx-staging/${ids.request}/${ids.original}.part").exists())
        store.publish(ids.request, ids.original, expected, authorizeParsed = { true })
        assertThrows(GpxOriginalStoreException::class.java) {
            store.cleanupAbortedOriginal(ids.request, ids.original, expected) { false }
        }
        assertTrue(File(root, "gpx-originals/${ids.original}/original.gpx").exists())

        store.cleanupStaging(ids.request, ids.original, expectedPublished = expected) { true }
        assertFalse(File(root, "gpx-staging/${ids.request}").exists())
        assertThrows(GpxOriginalStoreException::class.java) {
            store.cleanupAbortedOriginal(ids.request, ids.original, expected,
                authorizeCurrentAbortWithoutReady = { false },
                confirmDurablePublicationOwnership = { true },
            )
        }
        assertTrue(File(root, "gpx-originals/${ids.original}/original.gpx").exists())
        store.cleanupAbortedOriginal(ids.request, ids.original, expected,
            authorizeCurrentAbortWithoutReady = { true },
            confirmDurablePublicationOwnership = { true },
        )
        assertFalse(File(root, "gpx-originals/${ids.original}/original.gpx").exists())
    }

    @Test
    fun unknownEntriesAreOwnershipViolation() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        File(root, "gpx-staging/${ids.request}/unexpected").writeText("x")
        val error = assertThrows(GpxOriginalStoreException::class.java) {
            store.openStaged(ids.request, ids.original, expected)
        }
        assertEquals(GpxOriginalStoreError.OWNERSHIP_VIOLATION, error.code)
    }

    @Test
    fun symlinkParentsAndLeavesAndNonRegularFilesFailClosed() = withSandbox { root ->
        val source = validGpx()

        val stagingRoot = File(root, "gpx-staging")
        Os.symlink(File(root, "outside-staging").path, stagingRoot.path)
        assertStoreError(root) { GpxOriginalStore(root).inspectOperation(UUID.randomUUID(), UUID.randomUUID()) }

        withSandbox { parentRoot ->
            val ids = ids()
            val store = GpxOriginalStore(parentRoot)
            store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(source) })
            val requestDir = File(parentRoot, "gpx-staging/${ids.request}")
            val moved = File(parentRoot, "request-real")
            requestDir.renameTo(moved)
            Os.symlink(moved.path, requestDir.path)
            assertStoreError(parentRoot) { store.inspectOperation(ids.request, ids.original) }
        }

        withSandbox { leafRoot ->
            val ids = ids()
            val store = GpxOriginalStore(leafRoot)
            val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(source) })
            val payload = File(leafRoot, "gpx-staging/${ids.request}/${ids.original}.part")
            val real = File(leafRoot, "payload-real").apply { writeBytes(source) }
            payload.delete()
            Os.symlink(real.path, payload.path)
            assertStoreError(leafRoot) { store.openStaged(ids.request, ids.original, expected) }
        }

        withSandbox { regularRoot ->
            File(regularRoot, "gpx-staging").writeText("directory required")
            assertStoreError(regularRoot) { GpxOriginalStore(regularRoot).inspectOperation(UUID.randomUUID(), UUID.randomUUID()) }
        }
    }

    @Test
    fun missingTruncatedAndSameLengthCorruptFinalAreNotVerified() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val bytes = validGpx()
        val expected = GpxOriginalIdentity(bytes.size.toLong(), sha256(bytes))
        assertEquals(GpxOriginalPresence.MISSING, store.inspectFinal(ids.original, expected))
        store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(bytes) })
        store.publish(ids.request, ids.original, expected, authorizeParsed = { true })
        val final = File(root, "gpx-originals/${ids.original}/original.gpx")
        final.writeBytes(bytes.copyOf(bytes.size - 1))
        assertEquals(GpxOriginalPresence.CORRUPT, store.inspectFinal(ids.original, expected))
        final.writeBytes(bytes.mapIndexed { index, byte -> if (index == 0) (byte.toInt() xor 1).toByte() else byte }.toByteArray())
        assertEquals(GpxOriginalPresence.CORRUPT, store.inspectFinal(ids.original, expected))

        val incomplete = ids()
        File(root, "gpx-originals/${incomplete.original}/original.gpx").apply {
            parentFile!!.mkdirs()
            writeBytes(bytes.copyOf(bytes.size - 1))
        }
        assertEquals(GpxOriginalPresence.INCOMPLETE,
            store.inspectFinal(incomplete.original, expected))
    }

    @Test
    fun checkpointFaultsAtFinalCopyProofDirectorySyncAndVerifyFailClosedRetainingStagingAndFinal() = withSandbox { root ->
        listOf(GpxOriginalStage.FINAL_COPY, GpxOriginalStage.PROOF,
            GpxOriginalStage.DIRECTORY_SYNC, GpxOriginalStage.VERIFY).forEach { failedStage ->
            withSandbox { caseRoot ->
                val ids = ids()
                val copyStore = GpxOriginalStore(caseRoot)
                val expected = copyStore.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
                val store = GpxOriginalStore(caseRoot) { stage ->
                    if (stage == failedStage) throw IOException("checkpoint $stage")
                }
                assertThrows(GpxOriginalStoreException::class.java) {
                    store.publish(ids.request, ids.original, expected, authorizeParsed = { true })
                }
                assertTrue(File(caseRoot, "gpx-staging/${ids.request}/${ids.original}.part").exists())
                if (failedStage == GpxOriginalStage.FINAL_COPY) {
                    assertEquals(GpxOriginalPresence.INCOMPLETE,
                        copyStore.inspectFinal(ids.original, expected))
                } else {
                    assertTrue(File(caseRoot, "gpx-originals/${ids.original}/original.gpx").exists())
                }
            }
        }
    }

    @Test
    fun simulatedPublishInterruptionCanReopenAndCompleteWithoutReparse() = withSandbox { root ->
        val ids = ids()
        val bytes = validGpx()
        val first = GpxOriginalStore(root)
        val expected = first.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(bytes) })
        val interrupted = GpxOriginalStore(root) { stage ->
            if (stage == GpxOriginalStage.FINAL_COPY) throw IOException("simulated interruption")
        }
        assertThrows(GpxOriginalStoreException::class.java) {
            interrupted.publish(ids.request, ids.original, expected, authorizeParsed = { true })
        }
        val reopened = GpxOriginalStore(root)
        assertEquals(expected.length, reopened.inspectOperation(ids.request, ids.original).stagingBytes)
        assertEquals(expected, reopened.publish(ids.request, ids.original, expected, authorizeParsed = { true }))
        assertEquals(GpxOriginalPresence.PRESENT_VERIFIED, reopened.inspectFinal(ids.original, expected))
    }

    @Test
    fun enospcCheckpointIsMappedToStorageExhaustedAndDoesNotPublish() = withSandbox { root ->
        val ids = ids()
        val error = assertThrows(GpxOriginalStoreException::class.java) {
            GpxOriginalStore(root) { stage ->
                if (stage == GpxOriginalStage.COPY) {
                    throw android.system.ErrnoException("checkpoint", android.system.OsConstants.ENOSPC)
                }
            }.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        }
        assertEquals(GpxOriginalStoreError.STORAGE_EXHAUSTED, error.code)
        assertFalse(File(root, "gpx-originals/${ids.original}/original.gpx").exists())
    }

    @Test
    fun storeOutputParsesThroughProductionProcessorAndMatchesDirectBytesCm1() = withSandbox { root ->
        val bytes = validGpx()
        val ids = ids()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(bytes) })
        val fromStore = GpxProcessor().process(
            open = { store.openStaged(ids.request, ids.original, expected) },
            expectedOriginalIdentity = expected,
        )
        val direct = GpxProcessor().process(
            open = { ByteArrayInputStream(bytes) },
            expectedOriginalIdentity = expected,
        )
        assertTrue(fromStore is GpxParseResult.Accepted)
        assertTrue(direct is GpxParseResult.Accepted)
        fromStore as GpxParseResult.Accepted
        direct as GpxParseResult.Accepted
        assertEquals(direct.draft, fromStore.draft)
        assertEquals(expected, fromStore.originalIdentity)
        val storedCm1 = ByteArrayOutputStream().also { GpxCm1Encoder.write(fromStore.draft, it) }.toByteArray()
        val directCm1 = ByteArrayOutputStream().also { GpxCm1Encoder.write(direct.draft, it) }.toByteArray()
        assertArrayEquals(directCm1, storedCm1)
        assertEquals(GpxCm1Encoder.size(fromStore.draft), storedCm1.size.toLong())
        assertTrue(storedCm1.copyOfRange(0, 8).contentEquals("BEEGPXCM".toByteArray()))
    }

    @Test
    fun standaloneCloseFailureIsTypedAndBeforeOpenCancellationDoesNotOpenSource() = withSandbox { root ->
        val ids = ids()
        val source = TrackedInputStream(validGpx(), IOException("close"))
        val store = GpxOriginalStore(root)
        val failure = assertThrows(GpxOriginalStoreException::class.java) {
            store.copyToStaging(ids.request, ids.original, { source })
        }
        assertEquals(GpxOriginalStage.CLOSE, failure.stage)
        assertEquals(1, source.closeCount)
        assertEquals(null, store.inspectOperation(ids.request, ids.original).stagingBytes)
        val marker = CancellationException("before open")
        var opened = false
        val next = ids()
        assertSame(marker, assertThrows(CancellationException::class.java) {
            store.copyToStaging(next.request, next.original, { opened = true; ByteArrayInputStream(validGpx()) }) { throw marker }
        })
        assertFalse(opened)
    }

    @Test
    fun measurementReportsIndependentStagingAndFinalAllocation() = withSandbox { root ->
        val ids = ids()
        val bytes = validGpx()
        var opens = 0
        var bytesRead = 0L
        val start = System.nanoTime()
        val store = GpxOriginalStore(root)
        val descriptor = store.copyToStaging(ids.request, ids.original, {
            opens++
            object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val count = super.read(buffer, offset, length)
                    if (count > 0) bytesRead += count
                    return count
                }
            }
        })
        store.publish(ids.request, ids.original, descriptor, { true })
        val staged = File(root, "gpx-staging/${ids.request}/${ids.original}.part")
        val final = File(root, "gpx-originals/${ids.original}/original.gpx")
        assertEquals(1, opens)
        assertEquals(descriptor.length, bytesRead)
        val result = GpxProcessor().process({ store.openStaged(ids.request, ids.original, descriptor) }, descriptor)
        assertTrue(result is GpxParseResult.Accepted)
        val stagedBlocks = Os.stat(staged.path).st_blocks
        val finalBlocks = Os.stat(final.path).st_blocks
        assertFalse(sameInode(staged, final))
        android.util.Log.i("GpxStoreMeasurement", "logicalBytes=${descriptor.length};sourceOpens=$opens;sourceBytes=$bytesRead;" +
            "parserPasses=2;elapsedNanos=${System.nanoTime() - start};stagingAllocatedBlocks=$stagedBlocks;finalAllocatedBlocks=$finalBlocks;" +
            "heapPeak=UNKNOWN;powerLoss=UNKNOWN;threshold=NONE")
        Unit
    }

    @Test
    fun cancellationAtCopyAndPublicationBoundariesRemainsPrimaryWithoutSuccess() = withSandbox { root ->
        for (boundary in listOf(GpxOriginalStage.COPY, GpxOriginalStage.RESERVE,
            GpxOriginalStage.FINAL_COPY, GpxOriginalStage.PROOF,
            GpxOriginalStage.DIRECTORY_SYNC, GpxOriginalStage.VERIFY)) {
            withSandbox { caseRoot ->
                val ids = ids()
                val marker = CancellationException("boundary cancellation")
                val store = GpxOriginalStore(caseRoot) { if (it == boundary) throw marker }
                if (boundary == GpxOriginalStage.COPY) {
                    assertSame(marker, assertThrows(CancellationException::class.java) {
                        store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
                    })
                    assertEquals(null, store.inspectOperation(ids.request, ids.original).stagingBytes)
                } else {
                    val identity = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
                    var success = false
                    assertSame(marker, assertThrows(CancellationException::class.java) {
                        store.publish(ids.request, ids.original, identity, { true })
                        success = true
                    })
                    assertFalse(success)
                    assertTrue(File(caseRoot, "gpx-staging/${ids.request}/${ids.original}.part").exists())
                    assertEquals(boundary == GpxOriginalStage.FINAL_COPY ||
                        boundary == GpxOriginalStage.PROOF ||
                        boundary == GpxOriginalStage.DIRECTORY_SYNC ||
                        boundary == GpxOriginalStage.VERIFY,
                        File(caseRoot, "gpx-originals/${ids.original}/original.gpx").exists())
                }
            }
        }
    }

    @Test
    fun changedStagingCannotBeRetriedOrPublishedAsAcceptedOriginal() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val identity = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        val part = File(root, "gpx-staging/${ids.request}/${ids.original}.part")
        val changed = part.readBytes().also { it[0] = (it[0].toInt() xor 1).toByte() }
        part.writeBytes(changed)
        for (retry in listOf(false, true)) {
            val failure = assertThrows(GpxOriginalStoreException::class.java) {
                if (retry) store.copyToStaging(ids.request, ids.original, { error("do not reopen") }, identity)
                else store.publish(ids.request, ids.original, identity, { true })
            }
            assertEquals(GpxOriginalStoreError.IDENTITY_MISMATCH, failure.code)
        }
        assertFalse(File(root, "gpx-originals/${ids.original}/original.gpx").exists())
        assertArrayEquals(changed, part.readBytes())
    }

    @Test
    fun existingDifferentBytesFinalIsUntouchedAndNeverAdopted() = withSandbox { root ->
        val ids = ids()
        val bytes = validGpx()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(bytes) })
        val final = File(root, "gpx-originals/${ids.original}/original.gpx").apply {
            parentFile!!.mkdirs()
            writeBytes(bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        }
        val before = final.readBytes()
        assertThrows(GpxOriginalStoreException::class.java) {
            store.publish(ids.request, ids.original, expected, { true })
        }
        assertArrayEquals(before, final.readBytes())
        assertEquals(GpxOriginalPresence.INCOMPLETE, store.inspectFinal(ids.original, expected))
        assertFalse(sameInode(File(root, "gpx-staging/${ids.request}/${ids.original}.part"), final))
    }

    @Test
    fun ownerRecordAloneAndUnknownFinalDirectoryAreNotOwnershipProof() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        val unknown = File(root, "gpx-originals/${ids.original}/original.gpx").apply {
            parentFile!!.mkdirs()
            writeBytes(validGpx())
        }
        File(unknown.parentFile, "owner").writeText("BEESEARCH-GPX-OWNER-1\n${ids.request}\n${ids.original}\n")
        assertThrows(GpxOriginalStoreException::class.java) {
            store.publish(ids.request, ids.original, expected, { true })
        }
        assertThrows(GpxOriginalStoreException::class.java) {
            store.cleanupAbortedOriginal(ids.request, ids.original, expected,
                confirmDurablePublicationOwnership = { true },
                authorizeCurrentAbortWithoutReady = { true })
        }
        assertTrue(unknown.exists())
    }

    @Test
    fun replacedPayloadInodeCannotBeAdoptedOrDeleted() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        store.publish(ids.request, ids.original, expected, { true })
        val final = File(root, "gpx-originals/${ids.original}/original.gpx")
        val oldInode = inode(final)
        val replacement = File(root, "replacement.part").apply { writeBytes(final.readBytes()) }
        Os.rename(replacement.path, final.path) // Test-only replacement, never a Store fallback.
        assertNotEquals(oldInode, inode(final))
        assertThrows(GpxOriginalStoreException::class.java) {
            store.publish(ids.request, ids.original, expected, { true })
        }
        assertTrue(final.exists())
        assertThrows(GpxOriginalStoreException::class.java) {
            store.cleanupAbortedOriginal(ids.request, ids.original, expected,
                confirmDurablePublicationOwnership = { false },
                authorizeCurrentAbortWithoutReady = { true })
        }
        assertTrue(final.exists())
    }

    @Test
    fun replacedPublicationWitnessCannotAdoptOrDeletePayload() = withSandbox { root ->
        val ids = ids()
        val store = GpxOriginalStore(root)
        val expected = store.copyToStaging(ids.request, ids.original, { ByteArrayInputStream(validGpx()) })
        store.publish(ids.request, ids.original, expected, authorizeParsed = { true })
        val witness = File(root, "gpx-staging/${ids.request}/publication")
        val originalWitness = witness.readBytes()
        witness.writeBytes(originalWitness.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        assertThrows(GpxOriginalStoreException::class.java) {
            store.cleanupStaging(ids.request, ids.original, expectedPublished = expected) { true }
        }
        assertTrue(File(root, "gpx-originals/${ids.original}/original.gpx").exists())
        assertTrue(witness.exists())
    }

    @Test
    fun incompleteFinalCannotBeRemovedByCleanupStaging() = withSandbox { root ->
        val ids = ids()
        val copyStore = GpxOriginalStore(root)
        val expected = copyStore.copyToStaging(ids.request, ids.original,
            { ByteArrayInputStream(validGpx()) })
        val interrupted = GpxOriginalStore(root) { stage ->
            if (stage == GpxOriginalStage.FINAL_COPY) throw IOException("partial final")
        }
        assertThrows(GpxOriginalStoreException::class.java) {
            interrupted.publish(ids.request, ids.original, expected, { true })
        }
        assertEquals(GpxOriginalPresence.INCOMPLETE,
            interrupted.inspectFinal(ids.original, expected))
        val witness = File(root, "gpx-staging/${ids.request}/publication")
        assertTrue(witness.exists())
        assertThrows(GpxOriginalStoreException::class.java) {
            interrupted.cleanupStaging(ids.request, ids.original,
                expectedPublished = expected) { true }
        }
        assertTrue(witness.exists())
        interrupted.cleanupAbortedOriginal(ids.request, ids.original, expected,
            authorizeCurrentAbortWithoutReady = { true })
        assertFalse(witness.exists())
    }

    private data class Ids(val request: UUID, val original: UUID)

    private fun ids() = Ids(UUID.randomUUID(), UUID.randomUUID())

    private fun <T> withSandbox(block: (File) -> T): T {
        val sandbox = File(context.filesDir, "gpx-original-store-device-tests/${UUID.randomUUID()}")
        check(sandbox.mkdirs()) { "test sandbox was not created" }
        return try { block(sandbox) } finally { sandbox.deleteRecursively() }
    }

    private fun assertStoreError(root: File, action: () -> Unit) {
        val error = assertThrows(GpxOriginalStoreException::class.java) { action() }
        assertTrue(error.code == GpxOriginalStoreError.UNSAFE_PATH_OR_FILE_TYPE ||
            error.code == GpxOriginalStoreError.OWNERSHIP_VIOLATION)
    }

    private fun sameInode(a: File, b: File): Boolean = inode(a) == inode(b)

    private fun inode(file: File): String {
        val stat = Os.stat(file.path)
        return "${stat.st_dev}:${stat.st_ino}"
    }

    private fun validGpx() = "<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"fixture\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg><trkpt lat=\"0\" lon=\"0\"/></trkseg></trk></gpx>"
        .toByteArray(Charsets.UTF_8)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class TrackedInputStream(bytes: ByteArray, private val closeFailure: IOException) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var closeCount = 0
            private set

        override fun read(): Int = delegate.read()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)
        override fun close() {
            closeCount++
            throw closeFailure
        }
    }
}
