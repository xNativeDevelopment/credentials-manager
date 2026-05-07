package dev.xnative.samples.credentialmanager.unified

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * `commonMain` [CredentialStore] backed by Preferences DataStore plus a platform-specific
 * encryption layer.
 *
 * The DataStore type `DataStore<Preferences>` is multiplatform (`androidx.datastore` 1.2.x
 * publishes iosArm64 / iosSimulatorArm64 KMP variants — see "Set up DataStore for KMP",
 * developer.android.com/kotlin/multiplatform/datastore). What is platform-specific is:
 *  - the storage backend that produces the file path (FileStorage on Android,
 *    OkioStorage + NSDocumentDirectory on iOS); see [provideCredentialManager] actuals;
 *  - the encryption applied to each value before it lands in the file ([PayloadEncryption]).
 *
 * The class itself stays oblivious to both: it just composes a DataStore instance and an
 * encryption strategy, and exposes the credential-store contract that
 * [MigratingCredentialManager] consumes.
 *
 * Schema note: each credential is stored under its key as an opaque Base64 string produced by
 * [PayloadEncryption]. We never store the plain token in the Preferences file — only its
 * encrypted form — so even a sandbox file leak does not yield the secret.
 */
internal class EncryptedPreferencesDataStore(
    private val dataStore: DataStore<Preferences>,
    private val encryption: PayloadEncryption
) : CredentialStore {

    override suspend fun read(key: String): String? {
        val ciphertext = dataStore.data.first()[stringPreferencesKey(key)] ?: return null
        // A decrypt failure on a stored entry is a hard data-integrity error, not a normal
        // missing-token. We let it propagate so the migration orchestrator records it under
        // the "primary.read" stage and falls back to the legacy store.
        return encryption.decrypt(ciphertext)
    }

    override suspend fun write(key: String, value: String): Boolean {
        val ciphertext = encryption.encrypt(value)
        dataStore.edit { prefs -> prefs[stringPreferencesKey(key)] = ciphertext }
        return true
    }

    override suspend fun delete(key: String): Boolean {
        dataStore.edit { prefs -> prefs.remove(stringPreferencesKey(key)) }
        return true
    }
}
