package dev.xnative.samples.credentialmanager

import org.koin.core.scope.Scope

internal actual fun Scope.provideCredentialManager(): CredentialManager =
    IosCredentialManager()
