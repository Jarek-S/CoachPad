package pl.net.orlik.stt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Speech-to-text settings: on/off and the API key. The key is stored encrypted with an AES key
 * that lives in the Android Keystore (design doc, section 6): the AES key never leaves the
 * phone's secure hardware, so a copy of the app's files does not reveal the API key. The file is
 * left out of backups (res/xml), because a restored copy could not be decrypted on another phone.
 */
class SttSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, false)
        set(value) = prefs.edit().putBoolean(ENABLED, value).apply()

    /** Null when no key is saved, or it can no longer be decrypted (e.g. the Keystore key is gone). */
    suspend fun apiKey(): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(API_KEY, null) ?: return@withContext null
        runCatching {
            val (iv, encrypted) = stored.split(SEPARATOR).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted))
        }.getOrNull()
    }

    suspend fun setApiKey(key: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(key.toByteArray())
        val stored = listOf(cipher.iv, encrypted).joinToString(SEPARATOR) { Base64.encodeToString(it, Base64.NO_WRAP) }
        prefs.edit().putString(API_KEY, stored).commit()
    }

    fun clearApiKey() = prefs.edit().remove(API_KEY).apply()

    /** The AES key in the Keystore, created on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        /** Also named in res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val PREFS = "stt"
        const val ENABLED = "enabled"
        const val API_KEY = "api_key"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "stt_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}

/** A pasted key without spaces or line breaks (a QR code or a web page may add them). */
fun cleanApiKey(pasted: String): String = pasted.filterNot { it.isWhitespace() }

/** Only the start and the last 4 characters are shown: "sk-…a1B2". */
fun maskedApiKey(key: String): String =
    if (key.length <= 10) "…" + key.takeLast(2) else key.take(3) + "…" + key.takeLast(4)
