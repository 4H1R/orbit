package dev.alializadeh.orbit.backup

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.testing.FakePassphraseCipher
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.FileBackupDocuments
import dev.alializadeh.orbit.testing.MainDispatcherRule
import dev.alializadeh.orbit.testing.TestAppLocales
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Back up now's flow, around [BackupMaker]. Robolectric for `android.util.Log`. */
@RunWith(RobolectricTestRunner::class)
class BackupFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temp = TemporaryFolder()

    /** Review finding 3: out of memory is its own clear failure, not a crash. */
    @Test
    fun runningOutOfMemoryEndsInTheMemoryFailure() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)
        val preferences = FakePreferencesRepository()
        val passphrases = BackupPassphraseStore(preferences, FakePassphraseCipher())
        passphrases.remember("correct horse battery staple")
        val greedy = BackupWriter(
            snapshotter = { throw OutOfMemoryError("Failed to allocate 134217728 bytes") },
            clock = clock,
            appVersion = AppVersion("0.1.0", 1),
            appLocales = TestAppLocales(),
            workDir = temp.newFolder("work"),
            photosDir = temp.newFolder("photos"),
        )
        val documents = FileBackupDocuments()
        val maker = BackupMaker(greedy, BackupReader(OrbitDatabase.VERSION), documents, preferences)
        val viewModel = BackupViewModel(passphrases, maker, preferences, RestoreNotices(), clock)
        val target = File(temp.root, "backup.age")

        viewModel.start()
        viewModel.state.first { it is BackupFlowState.PickingDocument }
        viewModel.onDocumentPicked(target.path)
        val result = viewModel.state.first { it is BackupFlowState.Failed }

        assertEquals(BackupFlowState.Failed(needsMoreMemory = true), result)
        assertFalse(target.exists())
    }

    /** Review finding 3: a shown passphrase is hidden again once Orbit leaves the screen. */
    @Test
    fun theShownPassphraseIsHiddenWhenOrbitGoesToTheBackground() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)
        val preferences = FakePreferencesRepository()
        val passphrases = BackupPassphraseStore(preferences, FakePassphraseCipher())
        passphrases.remember("correct horse battery staple")
        // The third Backup: the check comes first.
        preferences.device.value = preferences.device.value.copy(backupCount = 2)
        val writer = BackupWriter(
            snapshotter = { error("not backing up") },
            clock = clock,
            appVersion = AppVersion("0.1.0", 1),
            appLocales = TestAppLocales(),
            workDir = temp.newFolder("work"),
            photosDir = temp.newFolder("photos"),
        )
        val maker = BackupMaker(writer, BackupReader(OrbitDatabase.VERSION), FileBackupDocuments(), preferences)
        val viewModel = BackupViewModel(passphrases, maker, preferences, RestoreNotices(), clock)

        viewModel.start()
        viewModel.state.first { it is BackupFlowState.CheckingPassphrase }
        viewModel.checkField.setTextAndPlaceCursorAtEnd("not the passphrase")
        viewModel.check()
        viewModel.state.first { it == BackupFlowState.CheckingPassphrase(wrong = true) }
        viewModel.reveal() // after the screen lock
        val shown = viewModel.state.first { (it as? BackupFlowState.CheckingPassphrase)?.revealed != null }
        assertEquals("correct horse battery staple", (shown as BackupFlowState.CheckingPassphrase).revealed)

        viewModel.hidePassphrase()

        val hidden = viewModel.state.value as BackupFlowState.CheckingPassphrase
        assertNull(hidden.revealed)
        // Still the failed check: Show passphrase again (behind the screen lock again) or go on.
        assertEquals(BackupFlowState.CheckingPassphrase(wrong = true), hidden)
    }
}
