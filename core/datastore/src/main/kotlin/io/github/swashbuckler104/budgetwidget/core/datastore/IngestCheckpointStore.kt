package io.github.swashbuckler104.budgetwidget.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Newest SMS inbox timestamp (epoch ms, UTC) the reconcile scan has processed. The scan re-reads
 * from a little before it (SCOPE.md F2), so the checkpoint only has to be monotonic.
 */
@Singleton
class IngestCheckpointStore @Inject constructor(@IngestPreferences private val dataStore: DataStore<Preferences>) {
    suspend fun get(): Long? = dataStore.data.map { it[KEY] }.first()

    /** Moves the checkpoint forward to [epochMillis]; an older value is ignored. */
    suspend fun advanceTo(epochMillis: Long) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]
            if (current == null || epochMillis > current) prefs[KEY] = epochMillis
        }
    }

    /**
     * The oldest inbox timestamp (epoch ms, UTC) a normal scan may import, or null for no limit.
     * "Start fresh" sets it, so the scan's look-back overlap can't bring older SMS back.
     */
    suspend fun floor(): Long? = dataStore.data.map { it[FLOOR] }.first()

    /** Sets the floor and moves the checkpoint up to it. */
    suspend fun startAt(epochMillis: Long) {
        dataStore.edit { prefs ->
            prefs[FLOOR] = epochMillis
            val current = prefs[KEY]
            if (current == null || epochMillis > current) prefs[KEY] = epochMillis
        }
    }

    private companion object {
        val KEY = longPreferencesKey("reconcile_checkpoint_ms")
        val FLOOR = longPreferencesKey("reconcile_floor_ms")
    }
}
