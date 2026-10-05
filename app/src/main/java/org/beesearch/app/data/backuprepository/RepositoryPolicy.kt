package org.beesearch.app.data.backuprepository

private const val MIN_SLACK_BYTES = 16L * 1024L * 1024L

internal class RepositoryCapacityPolicy(
    private val reserveBytes: Long = 20L * 1024L * 1024L * 1024L,
) {
    fun slack(size: Long): Long {
        if (size < 0L) failUnknown()
        val percent = try {
            Math.addExact(size / 100L, if (size % 100L == 0L) 0L else 1L)
        } catch (error: ArithmeticException) {
            failUnknown(error)
        }
        return maxOf(MIN_SLACK_BYTES, percent)
    }

    fun require(available: Long?, additional: Long) {
        if (available == null || available < 0L || additional < 0L || reserveBytes < 0L) failUnknown()
        val required = try {
            Math.addExact(additional, reserveBytes)
        } catch (error: ArithmeticException) {
            failUnknown(error)
        }
        if (available < required) throw RepositoryException(RepositoryError.CAPACITY_INSUFFICIENT)
    }

    fun budget(size: Long): Long = try {
        Math.addExact(size, slack(size))
    } catch (error: ArithmeticException) {
        failUnknown(error)
    }

    fun remainingBudget(size: Long, written: Long): Long {
        if (written < 0 || written > size) failUnknown()
        return try { Math.addExact(size - written, slack(size)) }
        catch (error: ArithmeticException) { failUnknown(error) }
    }

    private fun failUnknown(cause: Throwable? = null): Nothing =
        throw RepositoryException(RepositoryError.CAPACITY_UNKNOWN, cause)
}

internal object CanonicalExtension {
    fun resolve(hints: List<String?>): String {
        val recognized = hints.mapNotNull(::classify).distinct()
        if (recognized.size > 1) throw RepositoryException(RepositoryError.METADATA_INCONSISTENCY)
        return recognized.singleOrNull() ?: "bin"
    }

    private fun classify(raw: String?): String? {
        val value = raw?.trim()?.lowercase() ?: return null
        return when (value) {
            "image/jpeg" -> "jpg"
            "video/mp4" -> "mp4"
            else -> null
        }
    }
}
