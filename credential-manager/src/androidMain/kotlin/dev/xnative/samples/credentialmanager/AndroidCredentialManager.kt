package dev.xnative.samples.credentialmanager

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidCredentialManager(context: Context) : CredentialManager {

    private val prefs = run {
        val applicationContext = context.applicationContext
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            applicationContext,
            "sample_credential_manager",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun save(key: String, value: String): Boolean =
        prefs.edit().putString(key, value).commit()

    override fun retrieve(key: String): String? = prefs.getString(key, null)

    override fun delete(key: String): Boolean =
        prefs.edit().remove(key).commit()
}
