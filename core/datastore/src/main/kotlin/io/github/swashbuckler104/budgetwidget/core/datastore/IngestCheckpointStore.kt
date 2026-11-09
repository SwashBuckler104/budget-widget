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

    private companion object {
        val KEY = longPreferencesKey("reconcile_checkpoint_ms")
    }
}
