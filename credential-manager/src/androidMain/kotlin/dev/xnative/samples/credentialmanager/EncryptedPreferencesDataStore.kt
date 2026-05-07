package dev.xnative.samples.credentialmanager

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Android-side [CredentialStore] backed by Preferences DataStore plus the Tink-AEAD encryption
 * layer.
 *
 * Originally authored as `commonMain` code under the unified-DataStore design, this class is
 * Android-only after the platform-divergent revision: iOS persists credentials directly in the
 * Apple Keychain via cinterop, so neither DataStore nor [TinkPayloadEncryption] is involved on
 * that side. The class itself is unchanged in behavior — it just lives in `androidMain` now and
 * names its encryption strategy concretely instead of through an `expect class`.
 *
 * Schema note: each credential is stored under its key as an opaque Base64 string produced by
 * [TinkPayloadEncryption]. We never store the plain token in the Preferences file — only its
 * encrypted form — so even a sandbox file leak does not yield the secret.
 */
internal class EncryptedPreferencesDataStore(
    private val dataStore: DataStore<Preferences>,
    private val encryption: TinkPayloadEncryption
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
