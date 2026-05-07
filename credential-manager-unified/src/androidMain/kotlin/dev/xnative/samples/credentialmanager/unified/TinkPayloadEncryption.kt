@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.xnative.samples.credentialmanager.unified

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Android `actual` for [PayloadEncryption].
 *
 * Layered as the official 2026 Android security guide recommends
 * (`developer.android.com/topic/security/data` —
 *  *"For optimal key management security, use the Android Keystore, and encrypt stored keys
 *  using a robust tool such as Tink Java"*):
 *
 *  - Tink generates and rotates an `AEAD_AES_256_GCM` symmetric keyset.
 *  - The keyset itself is wrapped on-disk by an Android-Keystore-backed master key
 *    (`AndroidKeysetManager.withMasterKeyUri("android-keystore://...")`) so the keyset blob
 *    on the SharedPreferences file is never usable without unlocking the device.
 *  - We use the keyset to AEAD-encrypt each credential value into a Base64 string that
 *    `EncryptedPreferencesDataStore` puts under its `Preferences.String` key.
 *
 * This replaces the deprecated `EncryptedSharedPreferences` path: as of
 * `androidx.security:security-crypto` 1.1.0 stable, *all* APIs in that module are deprecated,
 * with the release notes pointing to "platform APIs and direct use of Android Keystore". Tink
 * AEAD is the bridge from "platform APIs" to a usable application-level cipher.
 */
internal class TinkPayloadEncryption private constructor(
    private val aead: Aead
) {

    /** Visible to the `actual` typealias below. */
    fun encrypt(plaintext: String): String {
        val ciphertextBytes = aead.encrypt(plaintext.encodeToByteArray(), ASSOCIATED_DATA)
        return Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP)
    }

    fun decrypt(ciphertext: String): String {
        val ciphertextBytes = Base64.decode(ciphertext, Base64.NO_WRAP)
        val plaintextBytes = aead.decrypt(ciphertextBytes, ASSOCIATED_DATA)
        return plaintextBytes.decodeToString()
    }

    companion object {
        // Stable AAD binds every ciphertext to this sample's namespace, so a token cannot be
        // replayed against an unrelated Tink keyset that happens to share the master key alias.
        private val ASSOCIATED_DATA: ByteArray =
            "dev.xnative.samples.credentials.unified.v1".encodeToByteArray()

        private const val MASTER_KEY_URI = "android-keystore://dev.xnative.samples.credentials.unified.master_key"
        private const val KEYSET_PREFS = "dev_xnative_samples_credentials_unified_tink_keyset"
        private const val KEYSET_NAME = "credential_payload_aead_unified"

        fun create(context: Context): TinkPayloadEncryption {
            // Idempotent: AeadConfig.register() is safe to call repeatedly.
            AeadConfig.register()

            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context.applicationContext, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle

            val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            return TinkPayloadEncryption(aead)
        }
    }
}

/**
 * `actual` declaration paired with the `expect class PayloadEncryption` in `commonMain`.
 *
 * We expose the Tink-backed type through a typealias rather than re-declaring an `actual class`
 * so the Android-side wiring (factory + companion) stays in a single readable file.
 */
internal actual typealias PayloadEncryption = TinkPayloadEncryption
