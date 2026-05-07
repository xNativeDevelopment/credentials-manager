package dev.xnative.samples.credentialmanager.unified

import org.koin.core.scope.Scope
import org.koin.dsl.module

val credentialManagerUnifiedModule = module {
    single<CredentialManager> { provideCredentialManager() }
}

internal expect fun Scope.provideCredentialManager(): CredentialManager
