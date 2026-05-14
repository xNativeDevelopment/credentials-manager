package dev.xnative.samples.credentialmanager

/**
 * Suspending KMP credential store.
 *
 * Operations are suspending because encrypted reads and writes cross disk, crypto, or OS-backed
 * credential storage. Android uses DataStore and Tink, while iOS delegates to Keychain through
 * cinterop. The shape mirrors the legacy synchronous API one-to-one so call sites only need to
 * add coroutine boundaries, not rethink their data flow.
 */
interface CredentialManager {

    /** Persists [value] under [key]. Returns true when the secure store confirmed the write. */
    suspend fun save(key: String, value: String): Boolean

    /** Returns the stored string for [key], or null when no entry exists. */
    suspend fun retrieve(key: String): String?

    /** Removes the entry for [key]. Returns true if the key was removed or absent. */
    suspend fun delete(key: String): Boolean
}
