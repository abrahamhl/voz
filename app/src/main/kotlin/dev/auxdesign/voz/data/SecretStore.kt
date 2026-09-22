package dev.auxdesign.voz.data

import android.content.SharedPreferences
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores user secrets (the BYOK cloud API key). Values are encrypted at rest and never logged. */
interface SecretStore {
    fun get(name: String): String?
    /** Returns false if the value could not be encrypted/stored (e.g. Keystore failure). */
    fun put(name: String, value: String): Boolean
    fun remove(name: String)
    fun has(name: String): Boolean = get(name) != null

    companion object {
        const val GEMINI_API_KEY = "gemini_api_key"
    }
}

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

class EncryptedSecretStore(private val kv: KeyValueStore, private val cipher: SecretCipher) : SecretStore {

    override fun get(name: String): String? {
        val stored = kv.getString(name) ?: return null
        return runCatching { String(cipher.decrypt(Base64.getDecoder().decode(stored)), Charsets.UTF_8) }.getOrNull()
    }

    override fun put(name: String, value: String): Boolean = runCatching {
        kv.putString(name, Base64.getEncoder().encodeToString(cipher.encrypt(value.toByteArray(Charsets.UTF_8))))
    }.isSuccess

    override fun remove(name: String) = kv.putString(name, null)
}

/** AES-256-GCM. The blob is IV (12 bytes) followed by ciphertext+tag. */
class AesGcmCipher(private val key: () -> SecretKey) : SecretCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "unexpected IV size" }
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** Non-exportable AES key living in the Android Keystore. */
object KeystoreKeys {
    private const val PROVIDER = "AndroidKeyStore"

    fun getOrCreate(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

class SharedPrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit { if (value == null) remove(key) else putString(key, value) }
    }
}
