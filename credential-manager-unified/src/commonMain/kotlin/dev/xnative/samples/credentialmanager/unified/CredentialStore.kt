package dev.xnative.samples.credentialmanager.unified

/**
 * Thin persistence surface for a single key/value credential entry.
 *
 * Mirrors `SharedPreferences` on Android and a lightweight read/write/delete shape on iOS:
 * the higher-level [MigratingCredentialManager] composes one primary (encrypted DataStore) and
 * one legacy store (EncryptedSharedPreferences on Android, Keychain on iOS) over this same
 * interface, so the migration orchestrator stays platform-agnostic and easy to test with
 * in-memory fakes.
 */
internal interface CredentialStore {

    /** Returns the stored string for [key], or `null` when the entry is missing. */
    suspend fun read(key: String): String?

    /**
     * Writes [value] under [key]. Returns `true` when the underlying store confirmed the write.
     * The boolean drives the migration's copy-before-delete decision in
     * [MigratingCredentialManager].
     */
    suspend fun write(key: String, value: String): Boolean

    /** Removes the entry for [key]. Returns `true` when the entry was removed or already absent. */
    suspend fun delete(key: String): Boolean
}
