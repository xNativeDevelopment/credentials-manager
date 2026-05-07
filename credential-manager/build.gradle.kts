import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {

    jvmToolchain(21)

    android {
        namespace = "dev.xnative.samples.credentialmanager"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_3)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CredentialManager"
            isStatic = true
        }
        iosTarget.compilations.getByName("main") {
            cinterops.create("KeychainProviderInterop") {
                definitionFile.set(
                    file(rootDir.absolutePath + "/iosApp/Interop/Keychain/KeychainProviderInterop.def")
                )
                includeDirs.allHeaders(rootDir.absolutePath + "/iosApp/Interop/Keychain/")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Only the Koin module entry point lives in commonMain. The `suspend` modifier on
            // [CredentialManager] is a Kotlin-language feature and does NOT require
            // kotlinx-coroutines-core in commonMain; coroutines is pulled in on the Android side
            // for the DataStore Flow consumption only.
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            // Coroutines is needed on Android for the `Flow.first()` call in
            // EncryptedPreferencesDataStore. iOS has no DataStore and does not need it here.
            implementation(libs.kotlinx.coroutines.core)
            // Preferences DataStore (KMP-published, but we only consume it on Android in this
            // module). See "Set up DataStore for KMP" —
            // developer.android.com/kotlin/multiplatform/datastore.
            implementation(libs.androidx.datastore.preferences.core)
            // OkioStorage is the official KMP storage backend the Android docs recommend; we use
            // it to keep parity with the Android KMP DataStore guide (FileStorage would also work
            // here, but Okio keeps the code shape closest to the official sample).
            implementation(libs.okio)
            // Legacy migration source. EncryptedSharedPreferences is officially deprecated
            // as of androidx.security:security-crypto 1.1.0; we keep it only to read tokens
            // written by the previous version of this sample.
            implementation(libs.androidx.security.crypto)
            // Tink Java provides the AEAD primitive that wraps the encrypted DataStore payload
            // on Android. Tink's keyset is itself wrapped by an AndroidKeystore-backed master
            // key (AndroidKeysetManager).
            implementation(libs.tink.android)
        }
    }
}
