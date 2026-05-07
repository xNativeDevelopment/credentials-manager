@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.xnative.samples.credentialmanager.unified

import org.koin.core.scope.Scope

internal actual fun Scope.provideCredentialManager(): CredentialManager {
    val dataStore = IosCredentialDataStore.create()
    val encryption = CommonCryptoPayloadEncryption.create()
    val primary = EncryptedPreferencesDataStore(dataStore = dataStore, encryption = encryption)
    val legacy = LegacyKeychainStore()
    val logger = getOrNull<CredentialMigrationLogger>() ?: NoOpCredentialMigrationLogger
    return MigratingCredentialManager(primary = primary, legacy = legacy, logger = logger)
}
