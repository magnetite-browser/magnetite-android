package org.magnetitebrowser.android.model

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps one secret (the sync code) in `prefs`, encrypted with a key that
 * never leaves the device's key store.
 */
class SecretStore(private val prefs: SharedPreferences, private val name: String) {
    private val alias = "magnetite.$name"

    fun read(): String? = runCatching {
        val stored = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key() ?: return null, GCMParameterSpec(128, stored, 0, IV))
        String(cipher.doFinal(stored, IV, stored.size - IV))
    }.getOrNull()

    /** False when the device's key store refused. */
    fun write(secret: String): Boolean = runCatching {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key() ?: newKey())
        val stored = cipher.iv + cipher.doFinal(secret.toByteArray())
        prefs.edit { putString(name, Base64.encodeToString(stored, Base64.NO_WRAP)) }
    }.isSuccess

    fun remove() {
        prefs.edit { remove(name) }
        runCatching { store().deleteEntry(alias) }
    }

    private fun store() = KeyStore.getInstance(STORE).apply { load(null) }

    private fun key() = store().getKey(alias, null) as? SecretKey

    private fun newKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).run {
            init(spec)
            generateKey()
        }
    }

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val AES_GCM = "AES/GCM/NoPadding"
        /** Bytes of the nonce the key store picks, stored before the text. */
        const val IV = 12
    }
}
