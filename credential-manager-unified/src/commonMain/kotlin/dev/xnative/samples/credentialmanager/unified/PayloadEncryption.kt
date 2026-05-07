@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.xnative.samples.credentialmanager.unified

/**
 * Platform-specific payload encryption used by [EncryptedPreferencesDataStore].
 *
 * Why this is `expect` and not unified: Tink Java is JVM/Android only, so a "single Tink call
 * shared from `commonMain`" is not possible today. The narrowest documented portable answer is
 * the official one — DataStore Preferences runs in `commonMain`, the encryption it wraps stays
 * platform-specific:
 *  - Android `actual` uses Tink AEAD with a key wrapped by the AndroidKeystore-backed
 *    `AndroidKeysetManager` (recommended in the Android security guide:
 *    `developer.android.com/topic/security/data` — "encrypt stored keys using a robust tool
 *    such as Tink Java").
 *  - iOS `actual` uses CommonCrypto AES-256-CBC + HMAC-SHA-256 (encrypt-then-MAC), with the
 *    256-bit master key stored in the Keychain under a service distinct from the legacy
 *    bridge's service.
 *
 * Both produce a Base64 string so the encrypted form fits a `Preferences` `String` key without
 * any external serializer.
 *
 * Token integrity hard rule: `decrypt(encrypt(s)) == s` byte-for-byte for any UTF-8 [String]
 * `s`. The migration orchestrator relies on this to validate every readback against the
 * original legacy token before deleting it.
 */
internal expect class PayloadEncryption {
    /** Returns the Base64 form of the encrypted UTF-8 bytes of [plaintext]. */
    fun encrypt(plaintext: String): String

    /** Reverses [encrypt]. Throws if [ciphertext] is malformed or fails authentication. */
    fun decrypt(ciphertext: String): String
}
