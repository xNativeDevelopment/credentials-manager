import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {

    jvmToolchain(21)

    android {
        namespace = "dev.xnative.samples.credentialmanager.unified"
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
            baseName = "CredentialManagerUnified"
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
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            // Preferences DataStore is now KMP-published; the per-platform .klibs are pulled in
            // automatically through the multiplatform metadata of this single coordinate.
            // See "Set up DataStore for KMP" — developer.android.com/kotlin/multiplatform/datastore.
            implementation(libs.androidx.datastore.preferences.core)
            // OkioStorage is the official KMP storage backend the Android docs recommend on iOS.
            implementation(libs.okio)
        }

        androidMain.dependencies {
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
