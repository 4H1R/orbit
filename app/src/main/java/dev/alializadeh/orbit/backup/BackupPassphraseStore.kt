package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.data.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The remembered Backup passphrase: device-bound, sealed by [PassphraseCipher]. It's never in a
 * Backup, and nothing but Back up now, Show passphrase, Change passphrase and Settings → Backup's
 * check that it still opens ever opens it. The Keystore is reached off the main thread.
 */
class BackupPassphraseStore @Inject constructor(
    private val preferences: PreferencesRepository,
    private val cipher: PassphraseCipher,
) {

    /**
     * The remembered passphrase, or null when none is set up. One that can't be opened (the key is
     * gone or unusable, the stored value is damaged) counts as none and is forgotten, so the next
     * Backup sets up a new one and Change passphrase isn't offered for it.
     */
    suspend fun current(): String? {
        val sealed = preferences.deviceState.first().wrappedBackupPassphrase ?: return null
        return try {
            withContext(Dispatchers.IO) { cipher.open(sealed) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // GeneralSecurityException, but also the Keystore's runtime ones (ProviderException...).
            forget(sealed)
            null
        }
    }

    /**
     * Remembers [passphrase] for future Backups. It was just typed, so it also counts as checked.
     * Returns false when the Keystore can't seal it even with a new key (nothing is stored then).
     */
    suspend fun remember(passphrase: String): Boolean {
        val sealed = seal(passphrase) ?: run {
            // An unusable key (invalidated, damaged): replace it once.
            resetKey()
            seal(passphrase)
        } ?: return false
        preferences.updateDeviceState { BackupRules.passphraseTyped(it.copy(wrappedBackupPassphrase = sealed)) }
        return true
    }

    /** Whether [typed] is the remembered passphrase (after the same digit conversion as typing). */
    suspend fun matches(typed: String): Boolean = current() == PassphraseRules.normalise(typed)

    private suspend fun seal(passphrase: String): String? = try {
        withContext(Dispatchers.IO) { cipher.seal(passphrase) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun forget(sealed: String) {
        preferences.updateDeviceState {
            if (it.wrappedBackupPassphrase == sealed) it.copy(wrappedBackupPassphrase = null) else it
        }
        resetKey()
    }

    private suspend fun resetKey() {
        try {
            withContext(Dispatchers.IO) { cipher.reset() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The next seal tries the key as it is.
        }
    }
}
