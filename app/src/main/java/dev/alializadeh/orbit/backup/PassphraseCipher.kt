package dev.alializadeh.orbit.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * Encrypts the remembered Backup passphrase for device-bound storage. The phone's implementation
 * is [KeystorePassphraseCipher]; JVM tests use a fake.
 */
interface PassphraseCipher {
    fun seal(passphrase: String): String

    /** @throws GeneralSecurityException when it can't be decrypted (the key is gone). */
    fun open(sealed: String): String
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore and never leaves it. The key isn't
 * bound to user authentication, so "Back up now" needs no unlock; revealing the passphrase is gated
 * by the screen lock in the UI instead.
 *
 * Sealed form: Base64 of the 12-byte IV, a colon, then Base64 of the ciphertext with its tag.
 */
class KeystorePassphraseCipher @Inject constructor() : PassphraseCipher {

    override fun seal(passphrase: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        return cipher.iv.base64() + SEPARATOR + sealed.base64()
    }

    override fun open(sealed: String): String {
        val (iv, ciphertext) = sealed.split(SEPARATOR).takeIf { it.size == 2 }
            ?: throw GeneralSecurityException("Not a sealed passphrase")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "orbit_backup_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
