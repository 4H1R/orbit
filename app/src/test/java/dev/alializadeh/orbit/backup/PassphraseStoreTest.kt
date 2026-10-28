package dev.alializadeh.orbit.backup

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import dev.alializadeh.orbit.testing.FakePassphraseCipher
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import java.security.ProviderException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 9: the Keystore's runtime failures (not only `GeneralSecurityException`) mean
 * "no remembered passphrase", which is then forgotten, so nothing is a dead end.
 */
class PassphraseStoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()
    private val cipher = FakePassphraseCipher()
    private val store = BackupPassphraseStore(preferences, cipher)

    @Test
    fun aKeystoreThatThrowsAtRuntimeMeansNoPassphraseAndIsForgotten() = runTest {
        store.remember(PASSPHRASE)
        cipher.failing = ProviderException("Keystore unavailable")

        assertNull(store.current())

        assertNull(preferences.device.value.wrappedBackupPassphrase)
        assertEquals(1, cipher.resets)
        // So Settings → Backup no longer offers Change passphrase for it.
        assertFalse(BackupStatus.of(preferences.device.value, 0, 0, BackupNudgeInterval.DEFAULT).hasPassphrase)
    }

    @Test
    fun aLostKeyIsForgottenAndANewPassphraseCanBeSetUp() = runTest {
        store.remember(PASSPHRASE)
        cipher.lose()

        assertNull(store.current())
        assertNull(preferences.device.value.wrappedBackupPassphrase)
        assertTrue(store.remember("another passphrase"))
        assertEquals("another passphrase", store.current())
    }

    @Test
    fun rememberingFailsCleanlyWhenTheKeystoreCantSeal() = runTest {
        cipher.failing = ProviderException("Keystore unavailable")

        assertFalse(store.remember(PASSPHRASE))

        assertNull(preferences.device.value.wrappedBackupPassphrase)
        assertEquals(1, cipher.resets)
    }

    @Test
    fun settingsForgetsAPassphraseThatNoLongerOpens() = runTest {
        store.remember(PASSPHRASE)
        cipher.lose()
        val statuses = RoomlessStatus(preferences)

        BackupSettingsViewModel(statuses, preferences, store, java.time.Clock.systemUTC())

        assertNull(preferences.device.first { it.wrappedBackupPassphrase == null }.wrappedBackupPassphrase)
    }

    @Test
    fun changePassphraseIsNoDeadEndWhenTheOldOneCantBeOpened() = runTest {
        store.remember(PASSPHRASE)
        cipher.lose()
        val viewModel = ChangePassphraseViewModel(store)
        viewModel.form.current.setTextAndPlaceCursorAtEnd("whatever I remember")
        viewModel.form.passphrase.setTextAndPlaceCursorAtEnd("a brand new passphrase")
        viewModel.form.repeat.setTextAndPlaceCursorAtEnd("a brand new passphrase")
        viewModel.form.acknowledged = true

        viewModel.save()
        viewModel.isSaving.first { !it } // The Keystore is reached on Dispatchers.IO.

        assertTrue(viewModel.isSaved.value)
        assertEquals("a brand new passphrase", store.current())
    }

    @Test
    fun changePassphraseStillNeedsTheCurrentOneWhenItOpens() = runTest {
        store.remember(PASSPHRASE)
        val viewModel = ChangePassphraseViewModel(store)
        viewModel.form.current.setTextAndPlaceCursorAtEnd("not the passphrase")
        viewModel.form.passphrase.setTextAndPlaceCursorAtEnd("a brand new passphrase")
        viewModel.form.repeat.setTextAndPlaceCursorAtEnd("a brand new passphrase")
        viewModel.form.acknowledged = true

        viewModel.save()
        viewModel.isSaving.first { !it }

        assertFalse(viewModel.isSaved.value)
        assertTrue(viewModel.form.currentWrong)
        assertEquals(PASSPHRASE, store.current())
    }

    /** Only the device-bound half matters here. */
    private class RoomlessStatus(preferences: FakePreferencesRepository) : BackupStatusRepository {
        override val status = kotlinx.coroutines.flow.flowOf(BackupStatus.of(preferences.device.value, 0, 0, BackupNudgeInterval.DEFAULT))
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery staple"
    }
}
