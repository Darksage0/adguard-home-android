package com.adguard.home.data.local.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TinkKeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyUri = "android-keystore://adguard_home_master_key"
    private val keysetName = "adguard_keyset"
    private val prefFileName = "adguard_tink_prefs"

    private val aead: Aead by lazy {
        AeadConfig.register()
        try {
            AndroidKeysetManager.Builder()
                .withSharedPref(context, keysetName, prefFileName)
                .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
                .withMasterKeyUri(masterKeyUri)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        } catch (e: GeneralSecurityException) {
            // In case of corrupt keyset on OEM or Keystore invalidation, clear and re-initialize
            context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE).edit().clear().apply()
            AndroidKeysetManager.Builder()
                .withSharedPref(context, keysetName, prefFileName)
                .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
                .withMasterKeyUri(masterKeyUri)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        }
    }

    fun encrypt(plainText: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        return aead.encrypt(plainText, associatedData)
    }

    fun decrypt(cipherText: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        return aead.decrypt(cipherText, associatedData)
    }
}
