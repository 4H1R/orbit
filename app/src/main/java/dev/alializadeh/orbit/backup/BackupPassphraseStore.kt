package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.data.PreferencesRepository
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The remembered Backup passphrase: device-bound, sealed by [PassphraseCipher]. It's never in a
 * Backup, and nothing but Back up now and Show passphrase ever opens it.
 */
class BackupPassphraseStore @Inject constructor(
    private val preferences: PreferencesRepository,
    private val cipher: PassphraseCipher,
) {

    /**
     * The remembered passphrase, or null when none is set up. One whose key is gone (it can't be
     * opened) counts as none, so the next Backup sets up a new one.
     */
    suspend fun current(): String? {
        val sealed = preferences.deviceState.first().wrappedBackupPassphrase ?: return null
        return try {
            cipher.open(sealed)
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    /** Remembers [passphrase] for future Backups. It was just typed, so it also counts as checked. */
    suspend fun remember(passphrase: String) {
        val sealed = cipher.seal(passphrase)
        preferences.updateDeviceState { BackupRules.passphraseTyped(it.copy(wrappedBackupPassphrase = sealed)) }
    }

    /** Whether [typed] is the remembered passphrase (after the same digit conversion as typing). */
    suspend fun matches(typed: String): Boolean = current() == PassphraseRules.normalise(typed)
}
