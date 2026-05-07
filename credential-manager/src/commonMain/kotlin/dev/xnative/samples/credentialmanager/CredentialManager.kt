package dev.xnative.samples.credentialmanager

/**
 * Suspending KMP credential store.
 *
 * Operations are suspending because the underlying DataStore (`androidx.datastore`) exposes a
 * suspending API on every platform — encrypted reads/writes touch the file system and must not
 * run on the calling thread. The shape mirrors the legacy synchronous API one-to-one so call
 * sites only need to add `withContext` / coroutine boundaries, not rethink their data flow.
 */
interface CredentialManager {

    /** Persists [value] under [key]. Returns true when the secure store confirmed the write. */
    suspend fun save(key: String, value: String): Boolean

    /** Returns the stored string for [key], or null when no entry exists. */
    suspend fun retrieve(key: String): String?

    /** Removes the entry for [key]. Returns true if the key was removed or absent. */
    suspend fun delete(key: String): Boolean
}
