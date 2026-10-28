package dev.alializadeh.orbit.backup

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
}
