package dev.xnative.samples.credentialmanager.unified

import dev.xnative.samples.keychain.interop.KeychainProviderInterop
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Read-mostly legacy [CredentialStore] over the Apple Keychain via the cinterop bridge from
 * the published article (`iosApp/Interop/Keychain/KeychainProviderInterop.swift`).
 *
 * Only legacy reads/cleanups go through here. Fresh writes targeting the Keychain are
 * structurally avoided: the new architecture stores credentials in the cross-platform
 * encrypted DataStore, and the cinterop bridge is now consumed for one purpose only — pulling
 * tokens that the previous version of this sample wrote — until the migration finishes.
 *
 * The cinterop bridge file itself is intentionally left byte-for-byte unchanged so the
 * published article's snippet still compiles 1:1 in any reader's project.
 */
@OptIn(ExperimentalForeignApi::class)
internal class LegacyKeychainStore(
    private val keychain: KeychainProviderInterop = KeychainProviderInterop()
) : CredentialStore {

    override suspend fun read(key: String): String? = keychain.readStringForKey(key = key)

    override suspend fun write(key: String, value: String): Boolean =
        keychain.writeString(value, forKey = key)

    override suspend fun delete(key: String): Boolean = keychain.deleteValueForKey(key = key)
}
