package dev.xnative.samples.credentialmanager

import dev.xnative.samples.keychain.interop.KeychainProviderInterop
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class IosCredentialManager(
    private val keychain: KeychainProviderInterop = KeychainProviderInterop()
) : CredentialManager {

    override fun save(key: String, value: String): Boolean =
        keychain.writeString(value, forKey = key)

    override fun retrieve(key: String): String? =
        keychain.readStringForKey(key = key)

    override fun delete(key: String): Boolean =
        keychain.deleteValueForKey(key = key)
}
