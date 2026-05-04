package dev.xnative.samples.credentialmanager

interface CredentialManager {

    /** Persists [value] under [key]. Returns true when the secure store confirmed the write. */
    fun save(key: String, value: String): Boolean

    /** Returns the stored string for [key], or null when no entry exists. */
    fun retrieve(key: String): String?

    /** Removes the entry for [key]. Returns true if the key was removed or absent. */
    fun delete(key: String): Boolean
}
