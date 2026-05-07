// Kotlin/Native doesn't statically know about Objective-C ↔ CoreFoundation toll-free
// bridging — `NSData`/`NSString` ARE bit-equivalent to `CFData`/`CFString` at runtime, but
// the K/N type system can't see it. The "cast can never succeed" warnings on those
// bridges are spurious for the same reason any iOS Objective-C ↔ C bridge is.
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "CAST_NEVER_SUCCEEDS")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.xnative.samples.credentialmanager.unified

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCKeySizeAES256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.UInt8Var
import platform.posix.memcpy
import platform.posix.size_tVar

/**
 * iOS `actual` for [PayloadEncryption], implemented in pure Kotlin/Native against
 * [platform.CoreCrypto] and [platform.Security] — no Swift bridge, no Tink, no third-party
 * dependency.
 *
 * Algorithm (AES-256-CBC + HMAC-SHA-256, encrypt-then-MAC):
 *  1. A 32-byte random *master* key is generated once via `SecRandomCopyBytes` and stored in the
 *     Apple Keychain under [MASTER_KEY_SERVICE], with
 *     `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. Subsequent runs read it back.
 *  2. From the master we derive two sub-keys via `HMAC-SHA-256(master, "enc"|"mac")` so the same
 *     bytes never serve both confidentiality and authentication.
 *  3. To encrypt: random 16-byte IV, AES-256-CBC with `Ke` and PKCS7 padding,
 *     `tag = HMAC-SHA-256(Km, AAD || IV || ciphertext)`, output `IV(16) || ciphertext(N) || tag(32)`
 *     Base64-encoded.
 *  4. To decrypt: split components, recompute MAC, constant-time compare, then `CCCrypt(kCCDecrypt, ...)`.
 *
 * Naming note: the class is named after its **encryption library** (CommonCrypto, exposed to
 * Kotlin/Native as `platform.CoreCrypto`), mirroring Android's [TinkPayloadEncryption]. The
 * Apple Keychain is involved only as the master-key vault — the ciphertext itself lands in
 * DataStore via [EncryptedPreferencesDataStore]. CryptoKit (Apple's modern equivalent) is
 * Swift-only with no Objective-C surface, so it is not reachable from Kotlin/Native without a
 * Swift bridge — CommonCrypto is the cleanest Kotlin-native answer.
 */
internal class CommonCryptoPayloadEncryption private constructor(
    private val encryptionKey: ByteArray,
    private val authenticationKey: ByteArray
) {

    fun encrypt(plaintext: String): String {
        val plaintextBytes = plaintext.encodeToByteArray()
        val iv = secureRandomBytes(BLOCK_SIZE)
        val ciphertext = aesCbcCrypt(
            operation = kCCEncrypt,
            key = encryptionKey,
            iv = iv,
            input = plaintextBytes
        )
        val tag = hmacSha256(authenticationKey, ASSOCIATED_DATA + iv + ciphertext)
        return (iv + ciphertext + tag).base64Encode()
    }

    fun decrypt(ciphertext: String): String {
        val payload = ciphertext.base64Decode()
        require(payload.size > BLOCK_SIZE + HMAC_SIZE) {
            "Ciphertext too short to contain IV + tag"
        }
        val iv = payload.copyOfRange(0, BLOCK_SIZE)
        val cipherBody = payload.copyOfRange(BLOCK_SIZE, payload.size - HMAC_SIZE)
        val tag = payload.copyOfRange(payload.size - HMAC_SIZE, payload.size)

        val expectedTag = hmacSha256(authenticationKey, ASSOCIATED_DATA + iv + cipherBody)
        require(constantTimeEquals(tag, expectedTag)) { "MAC verification failed" }

        val plaintextBytes = aesCbcCrypt(
            operation = kCCDecrypt,
            key = encryptionKey,
            iv = iv,
            input = cipherBody
        )
        return plaintextBytes.decodeToString()
    }

    companion object {
        // Stable AAD binds every ciphertext to this sample's namespace, mirroring the Tink AEAD
        // associated-data on the Android side.
        private val ASSOCIATED_DATA: ByteArray =
            "dev.xnative.samples.credentials.unified.v1".encodeToByteArray()

        private const val BLOCK_SIZE: Int = 16
        private const val HMAC_SIZE: Int = 32
        private const val MASTER_KEY_SIZE: Int = 32

        // Distinct from the cinterop bridge's service so the master key cannot collide with a
        // legacy entry, and a fresh-install with no legacy data has nothing to read from the
        // bridge's namespace.
        private const val MASTER_KEY_SERVICE = "dev.xnative.samples.credentials.unified.master"
        private const val MASTER_KEY_ACCOUNT = "v1"

        fun create(): CommonCryptoPayloadEncryption {
            val master = readOrCreateMasterKey()
            val encryptionKey = hmacSha256(master, "enc".encodeToByteArray())
            val authenticationKey = hmacSha256(master, "mac".encodeToByteArray())
            return CommonCryptoPayloadEncryption(
                encryptionKey = encryptionKey,
                authenticationKey = authenticationKey
            )
        }

        private fun readOrCreateMasterKey(): ByteArray {
            readMasterKey()?.let { return it }
            val freshKey = secureRandomBytes(MASTER_KEY_SIZE)
            check(writeMasterKey(freshKey)) {
                "Could not persist credential master key in the iOS Keychain"
            }
            return freshKey
        }

        private fun readMasterKey(): ByteArray? = memScoped {
            val query = mutableKeychainQuery()
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)

            val resultVar = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, resultVar.ptr)
            CFRelease(query)

            if (status == errSecItemNotFound) return@memScoped null
            require(status == errSecSuccess) {
                "Keychain read for credential master key failed: status=$status"
            }
            val cfData = resultVar.value ?: return@memScoped null
            try {
                @Suppress("UNCHECKED_CAST")
                (cfData as NSData).toByteArray()
            } finally {
                CFRelease(cfData)
            }
        }

        private fun writeMasterKey(key: ByteArray): Boolean = memScoped {
            // Best-effort cleanup of any stale entry, then add. We don't use SecItemUpdate
            // here: a fresh master key never collides with a previous one in practice, and
            // delete-then-add is the simplest predictable shape.
            val deleteQuery = mutableKeychainQuery()
            SecItemDelete(deleteQuery)
            CFRelease(deleteQuery)

            val addQuery = mutableKeychainQuery()
            CFDictionaryAddValue(addQuery, kSecValueData, key.toNSData().asCFTypeRef())
            CFDictionaryAddValue(
                addQuery,
                kSecAttrAccessible,
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            )

            val status = SecItemAdd(addQuery, null)
            CFRelease(addQuery)
            status == errSecSuccess
        }

        private fun mutableKeychainQuery(): CFMutableDictionaryRef {
            val query = CFDictionaryCreateMutable(
                allocator = kCFAllocatorDefault,
                capacity = 0,
                keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
                valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr
            )!!
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, MASTER_KEY_SERVICE.toNSStringCFRef())
            CFDictionaryAddValue(query, kSecAttrAccount, MASTER_KEY_ACCOUNT.toNSStringCFRef())
            return query
        }
    }
}

/**
 * `actual` declaration paired with `expect class PayloadEncryption` in `commonMain`.
 * Mirrors the Android typealias style.
 */
internal actual typealias PayloadEncryption = CommonCryptoPayloadEncryption

// ---------- internal crypto + interop helpers ----------

private fun secureRandomBytes(size: Int): ByteArray {
    val buffer = ByteArray(size)
    val status = buffer.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
    }
    require(status == 0) { "SecRandomCopyBytes failed with status=$status" }
    return buffer
}

private fun aesCbcCrypt(
    operation: UInt,
    key: ByteArray,
    iv: ByteArray,
    input: ByteArray
): ByteArray = memScoped {
    val outputCapacity = input.size + 16 // one extra block for PKCS7 padding
    val output = allocArray<ByteVar>(outputCapacity)
    val outputLengthVar = alloc<size_tVar>()

    val status = key.usePinned { pinnedKey ->
        iv.usePinned { pinnedIv ->
            input.usePinned { pinnedInput ->
                CCCrypt(
                    op = operation,
                    alg = kCCAlgorithmAES.convert(),
                    options = kCCOptionPKCS7Padding.convert(),
                    key = pinnedKey.addressOf(0),
                    keyLength = kCCKeySizeAES256.convert(),
                    iv = pinnedIv.addressOf(0),
                    dataIn = if (input.isEmpty()) null else pinnedInput.addressOf(0),
                    dataInLength = input.size.convert(),
                    dataOut = output,
                    dataOutAvailable = outputCapacity.convert(),
                    dataOutMoved = outputLengthVar.ptr
                )
            }
        }
    }
    require(status == kCCSuccess) { "CCCrypt failed with status=$status" }

    output.readBytes(outputLengthVar.value.toInt())
}

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = memScoped {
    val output = allocArray<UInt8Var>(CC_SHA256_DIGEST_LENGTH)
    key.usePinned { pinnedKey ->
        data.usePinned { pinnedData ->
            CCHmac(
                algorithm = kCCHmacAlgSHA256.convert(),
                key = pinnedKey.addressOf(0),
                keyLength = key.size.convert(),
                data = if (data.isEmpty()) null else pinnedData.addressOf(0),
                dataLength = data.size.convert(),
                macOut = output
            )
        }
    }
    output.readBytes(CC_SHA256_DIGEST_LENGTH)
}

private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) {
        diff = diff or (a[i].toInt() xor b[i].toInt())
    }
    return diff == 0
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.convert())
    }
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val out = ByteArray(length)
    val src: COpaquePointer? = this.bytes
    if (src != null) {
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), src, length.convert())
        }
    }
    return out
}

@Suppress("UNCHECKED_CAST")
private fun NSData.asCFTypeRef(): CPointer<*> = this as CPointer<*>

@Suppress("UNCHECKED_CAST")
private fun String.toNSStringCFRef(): CPointer<*> =
    platform.Foundation.NSString.create(string = this) as CPointer<*>

private fun ByteArray.base64Encode(): String =
    toNSData().base64EncodedStringWithOptions(0u)

private fun String.base64Decode(): ByteArray {
    val data = NSData.create(base64EncodedString = this, options = 0u)
        ?: error("Invalid Base64 input")
    return data.toByteArray()
}
