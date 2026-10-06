package org.beesearch.app.data.backuprepository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Strict atomic pair, without the portable-settings IOException -> empty fallback. */
internal class DataStoreRepositoryBindingStore(
    private val dataStore: DataStore<Preferences>,
    variant: String,
) : RepositoryBindingStore {
    init { require(variant in setOf("Dev", "Beta", "Stable")) }
    private val idKey = stringPreferencesKey("expected_repository_uuid_$variant")
    private val locatorKey = stringPreferencesKey("repository_root_locator_$variant")

    override suspend fun read(): RepositoryBinding? = persistence { decode(dataStore.data.first()) }

    override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) = persistence {
        next?.let(::validate)
        dataStore.edit { prefs ->
            if (decode(prefs) != expected) throw RepositoryException(RepositoryError.BINDING_CHANGED)
            if (next == null) {
                prefs.remove(idKey); prefs.remove(locatorKey)
            } else {
                prefs[idKey] = next.expectedRepositoryId.toString()
                prefs[locatorKey] = next.rootLocator
            }
        }
        Unit
    }

    private fun decode(prefs: Preferences): RepositoryBinding? {
        val id = prefs[idKey]; val locator = prefs[locatorKey]
        if (id == null && locator == null) return null
        if (id == null || locator == null) throw RepositoryException(RepositoryError.BINDING_INVALID)
        val uuid = try { UUID.fromString(id) } catch (e: IllegalArgumentException) {
            throw RepositoryException(RepositoryError.BINDING_INVALID, e)
        }
        if (uuid.toString() != id) throw RepositoryException(RepositoryError.BINDING_INVALID)
        return RepositoryBinding(uuid, locator).also(::validate)
    }

    private fun validate(binding: RepositoryBinding) {
        if (binding.rootLocator.isBlank() || binding.rootLocator.length > 4096 ||
            binding.rootLocator.any { it.code < 32 }) throw RepositoryException(RepositoryError.BINDING_INVALID)
    }

    private suspend fun <T> persistence(block: suspend () -> T): T = try { block() }
    catch (e: RepositoryException) { throw e }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { throw RepositoryException(RepositoryError.BINDING_PERSISTENCE_FAILED, e) }
}
