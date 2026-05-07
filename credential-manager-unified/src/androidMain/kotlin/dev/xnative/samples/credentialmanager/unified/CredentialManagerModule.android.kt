package dev.xnative.samples.credentialmanager.unified

import android.content.Context
import org.koin.core.scope.Scope

internal actual fun Scope.provideCredentialManager(): CredentialManager {
    val context = get<Context>()
    val dataStore = AndroidCredentialDataStore.create(context)
    val encryption = TinkPayloadEncryption.create(context)
    val primary = EncryptedPreferencesDataStore(dataStore = dataStore, encryption = encryption)
    val legacy = LegacyEncryptedSharedPreferencesStore.create(context)
    val logger = getOrNull<CredentialMigrationLogger>() ?: NoOpCredentialMigrationLogger
    return MigratingCredentialManager(primary = primary, legacy = legacy, logger = logger)
}
