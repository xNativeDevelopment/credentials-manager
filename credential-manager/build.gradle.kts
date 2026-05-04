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
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
        }
    }
}
