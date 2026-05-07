package dev.xnative.samples.credentialmanager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Android factory for the `DataStore<Preferences>` consumed by
 * [EncryptedPreferencesDataStore].
 *
 * Uses the same `OkioStorage` + `PreferencesSerializer` shape the official KMP DataStore guide
 * ships on iOS (developer.android.com/kotlin/multiplatform/datastore). We could equally use
 * `FileStorage` here, but keeping both platforms on Okio means the `commonMain` orchestrator
 * sees a single cross-platform persistence layer with no surprises.
 *
 * The file lives under `Context.filesDir` — i.e. the app's private internal storage — so it
 * benefits from the OS-level file-based encryption as a defense-in-depth on top of the
 * Tink-AEAD payload encryption we apply per value.
 */
internal object AndroidCredentialDataStore {

    private const val DATA_STORE_FILE_NAME = "dev_xnative_samples_credentials.preferences_pb"

    fun create(context: Context): DataStore<Preferences> {
        val applicationContext = context.applicationContext
        return DataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = {
                    applicationContext.filesDir
                        .resolve(DATA_STORE_FILE_NAME)
                        .absolutePath
                        .toPath()
                }
            )
        )
    }
}
