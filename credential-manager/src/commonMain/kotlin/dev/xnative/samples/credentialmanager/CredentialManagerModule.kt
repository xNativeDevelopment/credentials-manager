package dev.xnative.samples.credentialmanager

import org.koin.core.scope.Scope
import org.koin.dsl.module

val credentialManagerModule = module {
    single<CredentialManager> { provideCredentialManager() }
}

internal expect fun Scope.provideCredentialManager(): CredentialManager
