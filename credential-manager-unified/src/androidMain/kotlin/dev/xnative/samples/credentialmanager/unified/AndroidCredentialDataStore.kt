package dev.xnative.samples.credentialmanager.unified

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer

/**
 * Android factory for the `DataStore<Preferences>` consumed by
 * [EncryptedPreferencesDataStore].
 *
 * Uses the Android `FileStorage` + preferences file serializer shape from the official KMP
 * DataStore guide (developer.android.com/kotlin/multiplatform/datastore). The unified module
 * still keeps Okio for the iOS DataStore path.
 *
 * The file lives under `Context.filesDir` — i.e. the app's private internal storage — so it
 * benefits from the OS-level file-based encryption as a defense-in-depth on top of the
 * Tink-AEAD payload encryption we apply per value.
 */
internal object AndroidCredentialDataStore {

    private const val DATA_STORE_FILE_NAME = "dev_xnative_samples_credentials_unified.preferences_pb"

    fun create(context: Context): DataStore<Preferences> {
        val applicationContext = context.applicationContext
        return DataStoreFactory.create(
            storage = FileStorage(
                serializer = PreferencesFileSerializer,
                produceFile = { applicationContext.filesDir.resolve(DATA_STORE_FILE_NAME) }
            )
        )
    }
}
