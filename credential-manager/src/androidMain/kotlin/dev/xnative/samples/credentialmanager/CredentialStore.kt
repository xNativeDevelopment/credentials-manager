package dev.xnative.samples.credentialmanager

/**
 * Thin persistence surface for a single key/value credential entry.
 *
 * Mirrors `SharedPreferences`. The higher-level [MigratingCredentialManager] composes one
 * primary store (encrypted DataStore wrapped by Tink AEAD) and one legacy store
 * (`EncryptedSharedPreferences`) over this same interface, so the migration orchestrator stays
 * easy to test with in-memory fakes. iOS is platform-divergent and does not use this contract —
 * see [IosCredentialManager] for the Keychain-direct path.
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
