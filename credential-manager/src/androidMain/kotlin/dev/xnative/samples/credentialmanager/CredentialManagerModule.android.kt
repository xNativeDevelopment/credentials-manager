package dev.xnative.samples.credentialmanager

import android.content.Context
import org.koin.core.scope.Scope

internal actual fun Scope.provideCredentialManager(): CredentialManager =
    AndroidCredentialManager(context = get<Context>())
