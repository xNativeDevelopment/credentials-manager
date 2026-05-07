package dev.xnative.samples.credentialmanager.unified

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS factory for the `DataStore<Preferences>` consumed by [EncryptedPreferencesDataStore].
 *
 * Mirrors the shape published in the official KMP DataStore guide:
 * developer.android.com/kotlin/multiplatform/datastore — `OkioStorage` + `PreferencesSerializer`
 * + a `producePath` that resolves a file under `NSDocumentDirectory`. The persistence is
 * sandbox-private, included in iCloud / device-encrypted backups, and survives launches.
 *
 * Defense in depth: even though the file is sandboxed and protected by the device-level data
 * protection class, every value in it is independently encrypted by [PayloadEncryption]
 * before it ever reaches Okio.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosCredentialDataStore {

    private const val DATA_STORE_FILE_NAME = "dev_xnative_samples_credentials_unified.preferences_pb"

    fun create(): DataStore<Preferences> = DataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = PreferencesSerializer,
            producePath = {
                val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null
                )
                val basePath = requireNotNull(documentDirectory?.path) {
                    "NSDocumentDirectory unavailable; cannot create credentials DataStore"
                }
                "$basePath/$DATA_STORE_FILE_NAME".toPath()
            }
        )
    )
}
