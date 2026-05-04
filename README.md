# KMP CredentialManager — sample

Companion code for the Medium article *"Kotlin Multiplatform: iOS Keychain via
Swift cinterop"*. A minimal, gist-friendly KMP library that exposes a single
`CredentialManager` interface backed by:

- **Android** — `EncryptedSharedPreferences` (AndroidX `security-crypto`),
  master key from the Android Keystore.
- **iOS** — Apple Keychain (`Security.framework`) wrapped in a tiny Swift class
  exposed to Kotlin/Native through cinterop. No third-party library.

This repo is intentionally **not an app** — it's a single library module so each
file matches the article one-for-one and can be linked as a gist.

## Layout

```
credential-manager/                 single KMP library module
└─ src/
   ├─ commonMain/kotlin/.../        CredentialManager interface + Koin module
   ├─ androidMain/kotlin/.../       AndroidCredentialManager (security-crypto)
   └─ iosMain/kotlin/.../           IosCredentialManager (calls cinterop)

iosApp/Interop/Keychain/            iOS interop sources, referenced from Gradle
├─ KeychainProviderInterop.swift     actual Keychain calls
├─ KeychainProviderInterop.h         what cinterop reads
└─ KeychainProviderInterop.def       cinterop config
```

The Swift / `.h` / `.def` triple lives at the article's literal path
(`iosApp/Interop/Keychain/`) so the Gradle snippet from the article compiles
as-is.

## Targets

`androidTarget`, `iosArm64`, `iosSimulatorArm64`. No Compose, no Web, no
Desktop — this is a library, not an app.

- Kotlin **2.3.10**
- AGP **9.0.1** (`com.android.kotlin.multiplatform.library`)
- `androidx.security:security-crypto` **1.1.0-alpha06**
- Koin **4.1.1**

## Using it from your project

1. Copy `credential-manager/` and `iosApp/Interop/Keychain/` into your repo
   (preserve the relative paths — the cinterop block in
   `credential-manager/build.gradle.kts` reads `rootDir/iosApp/Interop/Keychain/`).
2. `include(":credential-manager")` in your `settings.gradle.kts`.
3. In Xcode, add `KeychainProviderInterop.swift` to your iOS app target's
   *Compile Sources* phase — see the article for why this step is required.
4. Wire `credentialManagerModule` into your Koin setup.
5. Inject `CredentialManager` wherever you need it.

```kotlin
class AuthRepository(
    private val credentials: CredentialManager,
) {
    fun saveSession(token: String) = credentials.save("oauth_access_token", token)
    fun currentToken(): String? = credentials.retrieve("oauth_access_token")
}
```

## Building

```
./gradlew :credential-manager:assemble
```

Useful per-target commands:

```
./gradlew :credential-manager:compileDebugKotlinAndroid
./gradlew :credential-manager:compileKotlinIosSimulatorArm64
./gradlew :credential-manager:cinteropKeychainProviderInteropIosSimulatorArm64
```

## Article

The Medium article walks through every line — including the cinterop step
every other tutorial skips. Issues and PRs welcome.
