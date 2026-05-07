package dev.xnative.samples.credentialmanager.unified

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Read-mostly legacy [CredentialStore] over `EncryptedSharedPreferences`.
 *
 * This is the *previous* version of this sample's Android storage. It exists solely to seed
 * the migration path: cold reads pull tokens from here when the new encrypted DataStore is
 * still empty, [MigratingCredentialManager] re-writes them into the new store, and the entry
 * is then removed from this file.
 *
 * The file name and the encryption schemes match what the published article described
 * (master key with `KeyScheme.AES256_GCM`, prefs encrypted with `AES256_SIV` keys and
 * `AES256_GCM` values), so consumers who shipped the article's code on a previous app version
 * will find their tokens migrated transparently on the next launch.
 *
 * `androidx.security:security-crypto` 1.1.0 stable is officially deprecated; we keep it pinned
 * at `1.1.0-alpha06` only to read what was previously written and never to issue new
 * application-level writes through it.
 */
internal class LegacyEncryptedSharedPreferencesStore(
    private val prefs: SharedPreferences
) : CredentialStore {

    override suspend fun read(key: String): String? = prefs.getString(key, null)

    override suspend fun write(key: String, value: String): Boolean {
        // Production traffic always goes through the new encrypted DataStore. The only
        // legitimate write here is the cleanup performed by [MigratingCredentialManager.delete],
        // and the orchestrator instead calls [delete] for that.
        return prefs.edit().putString(key, value).commit()
    }

    override suspend fun delete(key: String): Boolean = prefs.edit().remove(key).commit()

    companion object {
        private const val LEGACY_PREFERENCES_FILE = "sample_credential_manager_unified"

        fun create(context: Context): LegacyEncryptedSharedPreferencesStore {
            val applicationContext = context.applicationContext
            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                applicationContext,
                LEGACY_PREFERENCES_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return LegacyEncryptedSharedPreferencesStore(prefs)
        }
    }
}
