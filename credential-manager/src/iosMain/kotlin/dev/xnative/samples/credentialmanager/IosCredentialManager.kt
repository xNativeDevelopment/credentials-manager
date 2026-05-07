package dev.xnative.samples.credentialmanager

import dev.xnative.samples.keychain.interop.KeychainProviderInterop
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
internal class IosCredentialManager : CredentialManager {

    private val keychain: KeychainProviderInterop = KeychainProviderInterop()

    override suspend fun save(key: String, value: String): Boolean =
        keychain.writeString(value, forKey = key)

    override suspend fun retrieve(key: String): String? =
        keychain.readStringForKey(key = key)

    override suspend fun delete(key: String): Boolean =
        keychain.deleteValueForKey(key = key)
}
