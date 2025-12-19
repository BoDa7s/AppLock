package com.example.adamapplock.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class PasswordRepository private constructor(prefsName: String, context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "adam_app_lock_aes_key"
        private const val KEY_HASH = "master_pw_hash_enc" // encrypted blob
        private const val KEY_SALT = "master_pw_salt_enc" // encrypted blob

        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        @Volatile private var INSTANCE: PasswordRepository? = null
        fun get(context: Context, prefsName: String = "adam_app_lock_secure_prefs"): PasswordRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PasswordRepository(prefsName, context).also { INSTANCE = it }
            }
    }

    // ---- Public API (unchanged signatures)

    fun isPasswordSet(): Boolean =
        prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun setNewPassword(newPassword: CharArray) {
        val salt = randomSalt()
        val hash = pbkdf2(newPassword, salt)
        val encSalt = encrypt(salt)
        val encHash = encrypt(hash)

        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(encSalt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(encHash, Base64.NO_WRAP))
            .commit()

        wipe(newPassword); hash.fill(0)
    }

    fun verifyPassword(password: CharArray): Boolean {
        val saltEncB64 = prefs.getString(KEY_SALT, null) ?: return false
        val hashEncB64 = prefs.getString(KEY_HASH, null) ?: return false

        val salt = decrypt(Base64.decode(saltEncB64, Base64.NO_WRAP)) ?: return false
        val expected = decrypt(Base64.decode(hashEncB64, Base64.NO_WRAP)) ?: return false

        val actual = pbkdf2(password, salt)
        val ok = constantTimeEquals(expected, actual)

        wipe(password); actual.fill(0)
        return ok
    }

    fun changePassword(current: CharArray?, new: CharArray): Boolean {
        if (!isPasswordSet()) { setNewPassword(new); return true }
        val cur = current ?: return false
        val verified = verifyPassword(cur)
        if (!verified) { wipe(new); return false }
        setNewPassword(new)
        return true
    }

    // ---- Crypto helpers (AES-GCM via Android Keystore)

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_LENGTH_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Add .setUserAuthenticationRequired(true) if you want biometrics/credential gating
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // 12 bytes
        val ct = cipher.doFinal(plain)
        // Store IV || ciphertext
        return iv + ct
    }

    private fun decrypt(blob: ByteArray): ByteArray? {
        if (blob.size <= GCM_IV_BYTES) return null
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val ct = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(ct)
        } catch (_: Exception) {
            null // Key lost or data corrupted
        }
    }

    // ---- Hashing (same as before)

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun randomSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    private fun wipe(chars: CharArray) { for (i in chars.indices) chars[i] = '\u0000' }

    fun clearAll() {
        prefs.edit().clear().commit()
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.deleteEntry(KEY_ALIAS)
        }
    }
}
