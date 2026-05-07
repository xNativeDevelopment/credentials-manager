# KMP CredentialManager — sample

Companion code for the migration article *"From `EncryptedSharedPreferences` to a suspending,
DataStore-backed KMP credential store — and why iOS stays on Keychain"*. A KMP library that
exposes a single **suspending** `CredentialManager` interface, with a deliberately
**platform-divergent** implementation:

- **Android** — `androidx.datastore:datastore-preferences` + Tink AEAD wrapped by an
  Android-Keystore-backed master key (`AndroidKeysetManager`), with a transparent migration
  away from the deprecated `EncryptedSharedPreferences` legacy file.
- **iOS** — keeps the original Apple Keychain via Swift cinterop — the same architecture as the
  published article, now exposed through a suspending interface. No DataStore, no separate
  encryption layer, no master key plumbing on iOS: the Keychain *is* the primary store.

The previous-generation Android store stays in the module as a **legacy migration source only**:

- Android: `EncryptedSharedPreferences` (`androidx.security:security-crypto`, deprecated as of
  1.1.0 stable).

iOS has no migration in this module — the Keychain bridge has always been the primary store on
that platform in this sample.

The Swift / `.h` / `.def` triple at `iosApp/Interop/Keychain/` is byte-for-byte unchanged from
the published article.

This repo is intentionally **not an app** — it's a single library module so each file matches
the article one-for-one and can be linked as a gist.

## Why platform-divergent?

Tink Java is JVM/Android-only and not reachable from Kotlin/Native, so a single shared
encryption layer is impossible without rewriting one platform's primitives. Apple's recommended
credential primitive *is* the Keychain — using it directly via cinterop is the lower-friction,
more idiomatic answer than rolling our own CommonCrypto AES + HMAC + Keychain-stored master key
just to satisfy a "unified" goal. The suspending interface is the contract that crosses
platforms; what's behind it stays platform-idiomatic.

A unified-DataStore alternative — same `commonMain` orchestrator on both platforms, with
`OkioStorage` + `NSDocumentDirectory` + a CommonCrypto encryption layer on iOS — is preserved at
`credential-manager-unified/` for readers who want to study that shape. See
`article/orchestration/divergence-flags/01-ios-architecture-revision.md` for the rationale.

## Layout

```
credential-manager/                        single KMP library module
└─ src/
   ├─ commonMain/kotlin/.../
   │   ├─ CredentialManager.kt              suspending public interface
   │   ├─ CredentialMigrationLogger.kt      adaptable logger contract + no-op default
   │   └─ CredentialManagerModule.kt        Koin module entry point + expect provider
   ├─ androidMain/kotlin/.../
   │   ├─ AndroidCredentialDataStore.kt              OkioStorage + Context.filesDir
   │   ├─ CredentialStore.kt                         internal backing-store contract (Android)
   │   ├─ EncryptedPreferencesDataStore.kt           primary store: DataStore + Tink
   │   ├─ LegacyEncryptedSharedPreferencesStore.kt   legacy migration source
   │   ├─ MigratingCredentialManager.kt              5-step copy-before-delete orchestrator
   │   ├─ TinkPayloadEncryption.kt                   Tink AEAD + AndroidKeysetManager
   │   └─ CredentialManagerModule.android.kt
   └─ iosMain/kotlin/.../
       ├─ IosCredentialManager.kt           suspending Keychain wrapper over the cinterop bridge
       └─ CredentialManagerModule.ios.kt

iosApp/Interop/Keychain/                    Swift cinterop bridge — shared, byte-for-byte frozen
├─ KeychainProviderInterop.swift
├─ KeychainProviderInterop.h                 what cinterop reads
└─ KeychainProviderInterop.def               cinterop config

credential-manager-unified/                 archived alternative: unified DataStore on both
                                            platforms with CommonCrypto encryption on iOS.
                                            Builds, but not the recommended shape.
```

The Swift / `.h` / `.def` triple lives at the article's literal path
(`iosApp/Interop/Keychain/`) so the Gradle snippet from the article still compiles as-is. Both
modules consume the same files — the bridge is the shared frozen reference.

## Targets

`androidTarget`, `iosArm64`, `iosSimulatorArm64`. No Compose, no Web, no Desktop — this is a
library, not an app.

- Kotlin **2.3.10**
- AGP **9.0.1** (`com.android.kotlin.multiplatform.library`)
- `androidx.datastore:datastore-preferences-core` **1.2.1** *(Android-only in this module)*
- `com.google.crypto.tink:tink-android` **1.21.0** *(Android-only)*
- `androidx.security:security-crypto` **1.1.0-alpha06** *(Android-only, legacy migration source)*
- `com.squareup.okio:okio` **3.10.2** *(Android-only in this module)*
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` **1.10.2** *(Android-only — the iOS side is
  pure cinterop and Kotlin/Native built-ins; the `suspend` modifier on the common interface is
  a Kotlin-language feature and does not need this library in `commonMain`)*
- Koin **4.1.1**

## Public API

```kotlin
interface CredentialManager {
    suspend fun save(key: String, value: String): Boolean
    suspend fun retrieve(key: String): String?
    suspend fun delete(key: String): Boolean
}
```

Migration is **Android-only**: on cold reads, `retrieve` checks the new encrypted DataStore
first and migrates legacy `EncryptedSharedPreferences` tokens lazily, validating each migration
with a byte-for-byte readback before deleting the legacy entry. iOS has nothing to migrate —
the Keychain bridge has always been the primary store there.

## Using it from your project

1. Copy `credential-manager/` and `iosApp/Interop/Keychain/` into your repo (preserve relative
   paths — the cinterop block in `credential-manager/build.gradle.kts` reads
   `rootDir/iosApp/Interop/Keychain/`).
2. `include(":credential-manager")` in your `settings.gradle.kts`.
3. In Xcode, add `KeychainProviderInterop.swift` to your iOS app target's *Compile Sources*
   phase — see the article for why this step is required.
4. Wire `credentialManagerModule` into your Koin setup. Optionally bind your own
   `CredentialMigrationLogger`; the default is a no-op. (Only Android invokes the logger today,
   but the type is shared so a single logging adapter still applies cross-platform.)
5. Inject `CredentialManager` wherever you need it. All three operations are suspending.

```kotlin
class AuthRepository(
    private val credentials: CredentialManager,
) {
    suspend fun saveSession(token: String) =
        credentials.save("oauth_access_token", token)

    suspend fun currentToken(): String? =
        credentials.retrieve("oauth_access_token")
}
```

## Building

```
./gradlew :credential-manager:assemble
```

Useful per-target commands:

```
./gradlew :credential-manager:compileAndroidMain
./gradlew :credential-manager:compileKotlinIosSimulatorArm64
./gradlew :credential-manager:cinteropKeychainProviderInteropIosSimulatorArm64
```

## Article

The migration article walks through every line — the Android-side Tink + DataStore migration
that retires `EncryptedSharedPreferences`, the deliberate choice to keep iOS on Keychain via
cinterop, and why a suspending interface plus platform-divergent storage is the narrowest
honest answer in 2026. The unified alternative is preserved next door at
`credential-manager-unified/` for readers who want to compare. Issues and PRs welcome.
