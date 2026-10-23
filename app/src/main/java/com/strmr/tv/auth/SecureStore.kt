package com.strmr.tv.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The tvOS Keychain's counterpart: small secrets (the session, profile unlocks, the iroh
 * pairing) encrypted with an AES-GCM key that never leaves the Android Keystore, stored in
 * a private SharedPreferences file. Excluded from backups (res/xml/backup_rules.xml), so a
 * restore never carries a session to another device.
 *
 * Some TV Keystores fail to load a key they hold ("Failed to obtain information about key"),
 * which blocked signing in and pairing. A key that won't load is replaced; a Keystore that
 * can't encrypt at all leaves the value in the private file unencrypted rather than unsaved.
 */
object SecureStore {
    private const val KEY_ALIAS = "strmr.secure"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    /** Marks a value stored unencrypted because the Keystore couldn't encrypt it. */
    private const val PLAIN = "plain:"
    private val prefs by lazy { App.context.getSharedPreferences("strmr.secure", Context.MODE_PRIVATE) }

    fun read(account: String): String? {
        val stored = prefs.getString(account, null) ?: return null
        if (stored.startsWith(PLAIN)) return stored.removePrefix(PLAIN)
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrElse {
            // A key lost with an app-data wipe, or a value from another key: unreadable, drop it.
            delete(account)
            null
        }
    }

    fun write(account: String, value: String) {
        val stored = runCatching { seal(value) }.getOrElse {
            AppLog.w("SecureStore", "Keystore can't encrypt (${it.message}); storing $account unencrypted")
            PLAIN + value
        }
        prefs.edit().putString(account, stored).apply()
    }

    private fun seal(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun delete(account: String) {
        prefs.edit().remove(account).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        } catch (e: Exception) {
            // Held but unloadable: values sealed with it are lost either way, so start over.
            AppLog.w("SecureStore", "Keystore key unreadable (${e.message}); replacing it")
            runCatching { store.deleteEntry(KEY_ALIAS) }
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
