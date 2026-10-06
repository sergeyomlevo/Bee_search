package org.beesearch.app.data.backuprepository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.util.UUID

/** Typed SAF adapter for the validated external-storage repository root. */
internal class SafRepositoryStorage(
    private val context: android.content.Context,
    private val treeUri: Uri,
    private val mappedPublicRoot: File,
    private val privateFilesDir: File,
) : RepositoryStorage {
    private data class Doc(
        val uri: Uri,
        val parentUri: Uri?,
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
        val flags: Int,
    ) {
        val isDirectory: Boolean get() = mime == Document.MIME_TYPE_DIR
    }

    private data class OwnedStage(
        val operationId: UUID,
        val path: String,
        var uri: Uri,
        var parentUri: Uri,
        var name: String,
    )

    private val resolver: ContentResolver = context.contentResolver
    private val ownedStages = linkedMapOf<UUID, OwnedStage>()
    private val authority = treeUri.authority
    private val rootDocumentId: String

    init {
        if (authority != EXTERNAL_STORAGE_AUTHORITY || !DocumentsContract.isTreeUri(treeUri)) {
            throw RepositoryException(RepositoryError.UNSUPPORTED_PUBLICATION_PATH)
        }
        rootDocumentId = runCatching {
            if (DocumentsContract.isDocumentUri(context, treeUri)) DocumentsContract.getDocumentId(treeUri)
            else DocumentsContract.getTreeDocumentId(treeUri)
        }
            .getOrElse { throw RepositoryException(RepositoryError.PROVIDER_FAILURE, it) }
        if (!treeMatchesMappedRoot()) throw RepositoryException(RepositoryError.ROOT_IDENTITY_MISMATCH)
    }

    override fun ensureDirectory(path: String) {
        var parent = rootDoc()
        if (!parent.isDirectory) throw RepositoryException(RepositoryError.DIRECTORY_CONFLICT)
        pathParts(path).forEach { name ->
            val existing = child(parent, name)
            parent = when {
                existing == null -> createDirectory(parent, name)
                !existing.isDirectory -> throw RepositoryException(RepositoryError.DIRECTORY_CONFLICT)
                else -> existing
            }
        }
    }

    override fun inspect(path: String): RepositoryEntry? = find(path)?.let {
        RepositoryEntry(path = normalizePath(path), isDirectory = it.isDirectory, byteSize = it.size)
    }

    override fun list(path: String): List<RepositoryEntry> {
        val parent = find(path) ?: throw RepositoryException(RepositoryError.NOT_FOUND)
        if (!parent.isDirectory) throw RepositoryException(RepositoryError.DIRECTORY_CONFLICT)
        return queryChildren(parent).map {
            RepositoryEntry(
                path = joinPath(normalizePath(path), it.name),
                isDirectory = it.isDirectory,
                byteSize = it.size,
            )
        }
    }

    override fun createOwnedStage(operationId: UUID): String {
        if (ownedStages.containsKey(operationId)) throw RepositoryException(RepositoryError.METADATA_INCONSISTENCY)
        val folderPath = "Staging/$operationId"
        if (find(folderPath) != null) throw RepositoryException(RepositoryError.METADATA_INCONSISTENCY)
        ensureDirectory(folderPath)
        val parent = find(folderPath) ?: throw RepositoryException(RepositoryError.NOT_FOUND)
        val name = "candidate.part"
        if (child(parent, name) != null) throw RepositoryException(RepositoryError.METADATA_INCONSISTENCY)
        val uri = createDocument(parent, "application/octet-stream", name)
        val created = queryDocument(uri)
        if (created == null || created.name != name || created.isDirectory) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        }
        val path = "$folderPath/$name"
        ownedStages[operationId] = OwnedStage(operationId, path, uri, parent.uri, name)
        return path
    }

    override fun openTruncatedWriter(path: String): OutputStream {
        val owned = ownedStages.values.singleOrNull { it.path == normalizePath(path) }
            ?: throw RepositoryException(RepositoryError.PERMISSION_LOST)
        requirePublicationCapability()
        if (!owned.flagsForRename() || !owned.flagsForMove()) {
            throw RepositoryException(RepositoryError.UNSUPPORTED_PUBLICATION_PATH)
        }
        val output = mapped(RepositoryError.WRITE_FAILED) {
            resolver.openOutputStream(owned.uri, "wt") ?: throw RepositoryException(RepositoryError.WRITE_FAILED)
        }
        return object : FilterOutputStream(output) {
            override fun write(b: Int) = mapped(RepositoryError.WRITE_FAILED) { out.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) = mapped(RepositoryError.WRITE_FAILED) { out.write(b, off, len) }
            override fun flush() = mapped(RepositoryError.WRITE_FAILED) { out.flush() }
            override fun close() = mapped(RepositoryError.WRITE_FAILED) { out.close() }
        }
    }

    override fun openReader(path: String): InputStream {
        val doc = find(path) ?: throw RepositoryException(RepositoryError.NOT_FOUND)
        val input = mapped(RepositoryError.PROVIDER_FAILURE) {
            resolver.openInputStream(doc.uri) ?: throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        }
        return object : FilterInputStream(input) {
            override fun read(): Int = mapped(RepositoryError.PROVIDER_FAILURE) { `in`.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int = mapped(RepositoryError.PROVIDER_FAILURE) { `in`.read(b, off, len) }
            override fun close() = mapped(RepositoryError.PROVIDER_FAILURE) { `in`.close() }
        }
    }

    override fun sync(path: String) {
        val owned = ownedStages.values.singleOrNull { it.path == normalizePath(path) }
            ?: throw RepositoryException(RepositoryError.PERMISSION_LOST)
        mapped(RepositoryError.SYNC_FAILED) {
            resolver.openFileDescriptor(owned.uri, "rw")?.use { descriptor ->
                descriptor.fileDescriptor.sync()
            } ?: throw IllegalStateException("NULL_DESCRIPTOR")
        }
    }

    override fun requirePublicationCapability() {
        if (authority != EXTERNAL_STORAGE_AUTHORITY || !mappedPublicRoot.isDirectory || !treeMatchesMappedRoot()) {
            throw RepositoryException(RepositoryError.UNSUPPORTED_PUBLICATION_PATH)
        }
        if (!AndroidRepositoryCapacity(context, privateFilesDir, mappedPublicRoot).hasKnownSameVolume()) {
            throw RepositoryException(RepositoryError.UNSUPPORTED_PUBLICATION_PATH)
        }
    }

    override fun moveOwnedStage(stage: String, target: String) {
        val owned = ownedStages.values.singleOrNull { it.path == normalizePath(stage) }
            ?: throw RepositoryException(RepositoryError.PERMISSION_LOST)
        requirePublicationCapability()
        if (find(target) != null) throw RepositoryException(RepositoryError.PUBLISH_FAILED)
        val targetPath = normalizePath(target)
        val targetParentPath = targetPath.substringBeforeLast('/', "")
        val targetName = targetPath.substringAfterLast('/')
        val targetParent = find(targetParentPath) ?: throw RepositoryException(RepositoryError.NOT_FOUND)
        if (!targetParent.isDirectory) throw RepositoryException(RepositoryError.DIRECTORY_CONFLICT)
        if (!owned.flagsForRename() || !owned.flagsForMove()) throw RepositoryException(RepositoryError.UNSUPPORTED_PUBLICATION_PATH)
        val renamed = mapped(RepositoryError.PUBLISH_FAILED) {
            DocumentsContract.renameDocument(resolver, owned.uri, targetName)
                ?: throw RepositoryException(RepositoryError.PUBLISH_FAILED)
        }
        owned.uri = renamed
        owned.name = targetName
        if (queryDocument(renamed)?.name != targetName) throw RepositoryException(RepositoryError.PUBLISH_FAILED)
        if (!owned.flagsForMove()) throw RepositoryException(RepositoryError.PUBLISH_FAILED)
        mapped(RepositoryError.PUBLISH_FAILED) {
            DocumentsContract.moveDocument(resolver, renamed, owned.parentUri, targetParent.uri)
                ?: throw RepositoryException(RepositoryError.PUBLISH_FAILED)
        }
        // Keep the owned directory record. Cleanup must prove the file is STILL in Staging;
        // document IDs/URIs may stay valid after a move, including a failed provider response.
    }

    override fun deleteOwnedStage(operationId: UUID) {
        val owned = ownedStages[operationId] ?: return
        val parent = queryDocument(owned.parentUri) ?: return
        val staged = queryChildren(parent).singleOrNull { it.uri == owned.uri && it.name == owned.name }
        if (staged != null) mapped(RepositoryError.DELETE_FAILED) {
            if (!DocumentsContract.deleteDocument(resolver, staged.uri)) throw RepositoryException(RepositoryError.DELETE_FAILED)
        }
        if (queryChildren(parent).isEmpty() && parent.name == operationId.toString()) {
            mapped(RepositoryError.DELETE_FAILED) {
                if (!DocumentsContract.deleteDocument(resolver, parent.uri)) throw RepositoryException(RepositoryError.DELETE_FAILED)
            }
        }
        ownedStages.remove(operationId)
    }

    override fun availableBytes(): Long? = if (treeMatchesMappedRoot()) {
        AndroidRepositoryCapacity(context, privateFilesDir, mappedPublicRoot).availableBytes()
    } else null

    private fun OwnedStage.flagsForRename(): Boolean = queryDocument(uri)?.let { it.flags and Document.FLAG_SUPPORTS_RENAME != 0 } == true
    private fun OwnedStage.flagsForMove(): Boolean = queryDocument(uri)?.let { it.flags and Document.FLAG_SUPPORTS_MOVE != 0 } == true

    private fun rootDoc(): Doc = queryDocument(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId))
        ?: throw RepositoryException(RepositoryError.NOT_FOUND)

    private fun find(path: String): Doc? {
        val parts = pathParts(path)
        var current = rootDoc()
        for (part in parts) current = child(current, part) ?: return null
        return current
    }

    private fun child(parent: Doc, name: String): Doc? = queryChildren(parent).filter { it.name == name }.let {
        when (it.size) {
            0 -> null
            1 -> it.single()
            else -> throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        }
    }

    private fun createDirectory(parent: Doc, name: String): Doc = createDocument(parent, Document.MIME_TYPE_DIR, name).let {
        queryDocument(it)?.also { created ->
            if (created.name != name || !created.isDirectory) throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        } ?: throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
    }

    private fun createDocument(parent: Doc, mime: String, name: String): Uri = mapped(RepositoryError.WRITE_FAILED) {
        DocumentsContract.createDocument(resolver, parent.uri, mime, name)
            ?: throw RepositoryException(RepositoryError.WRITE_FAILED)
    }

    private fun queryDocument(uri: Uri): Doc? = query(uri, null).use { cursor ->
        if (!cursor.moveToFirst()) null else readDoc(cursor, uri, null).also {
            if (cursor.moveToNext()) throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        }
    }

    private fun queryChildren(parent: Doc): List<Doc> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.id)
        return query(childrenUri, parent.uri).use { cursor ->
            val result = mutableListOf<Doc>()
            while (cursor.moveToNext()) result += readDoc(cursor, DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID))), parent.uri)
            if (result.map { it.name }.toSet().size != result.size || result.map { it.id }.toSet().size != result.size) {
                throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
            }
            result
        }
    }

    private fun query(uri: Uri, parentUri: Uri?): Cursor = mapped(RepositoryError.PROVIDER_FAILURE) {
        resolver.query(uri, PROJECTION, null, null, null)
            ?: throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
    }

    private fun <T> mapped(failure: RepositoryError, block: () -> T): T = try { block() }
    catch (e: RepositoryException) { throw e }
    catch (e: SecurityException) { throw RepositoryException(RepositoryError.PERMISSION_LOST, e) }
    catch (e: Exception) { throw RepositoryException(failure, e) }

    private fun readDoc(cursor: Cursor, uri: Uri, parentUri: Uri?): Doc {
        val id = cursor.requiredString(Document.COLUMN_DOCUMENT_ID)
        val name = cursor.requiredString(Document.COLUMN_DISPLAY_NAME)
        if (name.isEmpty() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' }) {
            throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        }
        val mime = cursor.requiredString(Document.COLUMN_MIME_TYPE)
        val sizeIndex = cursor.getColumnIndex(Document.COLUMN_SIZE)
        val size = if (sizeIndex < 0 || cursor.isNull(sizeIndex)) {
            if (mime == Document.MIME_TYPE_DIR) -1L else throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        } else cursor.getLong(sizeIndex)
        val flagsIndex = cursor.getColumnIndex(Document.COLUMN_FLAGS)
        val flags = if (flagsIndex < 0 || cursor.isNull(flagsIndex)) throw RepositoryException(RepositoryError.PROVIDER_FAILURE) else cursor.getInt(flagsIndex)
        return Doc(uri, parentUri, id, name, mime, size, flags)
    }

    private fun Cursor.requiredString(column: String): String {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        return getString(index)
    }

    private fun pathParts(path: String): List<String> {
        val normalized = normalizePath(path)
        if (normalized.isEmpty()) return emptyList()
        return normalized.split('/').also { parts ->
            if (parts.any { it.isEmpty() || it == "." || it == ".." || it.contains('\u0000') }) throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        }
    }

    private fun normalizePath(path: String): String {
        if (path.startsWith('/') || path.contains('\\') || path.contains(':')) {
            throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        }
        return path.trim('/')
    }

    private fun joinPath(parent: String, child: String) = if (parent.isEmpty()) child else "$parent/$child"

    private fun treeMatchesMappedRoot(): Boolean = runCatching {
        val external = Environment.getExternalStorageDirectory().canonicalFile.toPath()
        val mapped = mappedPublicRoot.canonicalFile.toPath()
        val relative = external.relativize(mapped).toString().replace(File.separatorChar, '/')
        !relative.startsWith("../") && relative != ".." && rootDocumentId == "primary:$relative"
    }.getOrDefault(false)

    private companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS,
        )
    }
}
