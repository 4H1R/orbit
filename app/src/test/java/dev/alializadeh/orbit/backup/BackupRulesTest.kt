package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.data.DeviceState
import dev.alializadeh.orbit.data.DeviceStateSerializer
import dev.alializadeh.orbit.data.LastBackup
import dev.alializadeh.orbit.testing.FakePassphraseCipher
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Backup nudge, the every-third passphrase check, the passphrase rules and its storage. */
class BackupRulesTest {

    private val tehran = ZoneId.of("Asia/Tehran")
    private val today = LocalDate.of(2026, 10, 28)

    private fun status(
        lastBackup: LocalDate? = null,
        dataChanged: Boolean = true,
        people: Int = 3,
        interval: BackupNudgeInterval = BackupNudgeInterval.Days30,
        hiddenUntil: LocalDate? = null,
    ) = BackupStatus(
        lastBackupAt = lastBackup?.atTime(10, 0)?.atZone(tehran)?.toInstant(),
        dataChanged = dataChanged,
        peopleCount = people,
        nudgeInterval = interval,
        nudgeHiddenUntil = hiddenUntil,
        hasPassphrase = lastBackup != null,
    )

    private fun shows(status: BackupStatus) = BackupRules.showsNudge(status, today, tehran)

    // --- The nudge ---------------------------------------------------------------------------

    @Test
    fun neverBackedUpNudgesOnceThereIsSomeone() {
        assertTrue(shows(status(people = 1)))
        assertFalse(shows(status(people = 0)))
    }

    @Test
    fun aBackupNudgesOnceItIsTheIntervalOldAndTheDataChanged() {
        assertFalse(shows(status(lastBackup = today.minusDays(29))))
        assertTrue(shows(status(lastBackup = today.minusDays(30))))
        assertFalse("unchanged data never nudges", shows(status(lastBackup = today.minusDays(400), dataChanged = false)))
    }

    @Test
    fun eachIntervalHasItsOwnThreshold() {
        for (interval in listOf(BackupNudgeInterval.Days14, BackupNudgeInterval.Days60, BackupNudgeInterval.Days90)) {
            val days = interval.days!!.toLong()
            assertFalse(shows(status(lastBackup = today.minusDays(days - 1), interval = interval)))
            assertTrue(shows(status(lastBackup = today.minusDays(days), interval = interval)))
        }
    }

    @Test
    fun offNeverNudges() {
        assertFalse(shows(status(interval = BackupNudgeInterval.Off)))
        assertFalse(shows(status(lastBackup = today.minusDays(400), interval = BackupNudgeInterval.Off)))
    }

    @Test
    fun notNowHidesItForSevenDays() {
        val hidden = BackupRules.hideNudge(DeviceState(), today)
        val until = LocalDate.ofEpochDay(hidden.backupNudgeHiddenUntilEpochDay!!)
        assertEquals(today.plusDays(7), until)

        val stale = status(lastBackup = today.minusDays(60), hiddenUntil = until)
        assertFalse(BackupRules.showsNudge(stale, today.plusDays(6), tehran))
        assertTrue(BackupRules.showsNudge(stale, today.plusDays(7), tehran))
    }

    @Test
    fun theDayOfTheLastBackupIsInTheLocalZone() {
        // 22:00 UTC on 28 Sep is already 29 Sep in Tehran: 30 days before today in UTC, 29 in Tehran.
        val status = status().copy(lastBackupAt = Instant.parse("2026-09-28T22:00:00Z"), dataChanged = true)
        assertTrue(BackupRules.showsNudge(status, today, ZoneId.of("UTC")))
        assertFalse(BackupRules.showsNudge(status, today, tehran))
    }

    @Test
    fun aStatusComesFromDeviceStateAndTheDataVersion() {
        val device = DeviceState(
            lastBackup = LastBackup(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), dataVersion = 42),
            wrappedBackupPassphrase = "sealed",
        )
        assertFalse(BackupStatus.of(device, 42, 1, BackupNudgeInterval.Days30).dataChanged)
        assertTrue(BackupStatus.of(device, 43, 1, BackupNudgeInterval.Days30).dataChanged)
        assertTrue(BackupStatus.of(DeviceState(), 0, 1, BackupNudgeInterval.Days30).dataChanged)
        assertTrue(BackupStatus.of(device, 42, 1, BackupNudgeInterval.Days30).hasPassphrase)
    }

    // --- Every third Backup asks for the passphrase -------------------------------------------

    @Test
    fun everyThirdBackupAsks() {
        var device = DeviceState()
        val asked = mutableListOf<Int>()
        for (backup in 1..9) {
            if (BackupRules.passphraseCheckDue(device)) {
                asked += backup
                device = BackupRules.passphraseTyped(device)
            }
            device = device.copy(backupCount = device.backupCount + 1)
        }
        assertEquals(listOf(3, 6, 9), asked)
    }

    @Test
    fun aCheckPassedBeforeACancelledPickerIsNotAskedAgain() {
        val third = DeviceState(backupCount = 2)
        assertTrue(BackupRules.passphraseCheckDue(third))
        assertFalse(BackupRules.passphraseCheckDue(BackupRules.passphraseTyped(third)))
    }

    // --- Device-bound storage -----------------------------------------------------------------

    @Test
    fun theNewDeviceStateFieldsRoundTripAndOldStateStillReads() = runTest {
        val state = DeviceState(
            firstRunFinished = true,
            wrappedBackupPassphrase = "sealed:abc",
            lastBackup = LastBackup(1_700_000_000_000, 12),
            backupCount = 5,
            passphraseTypedAtBackupCount = 5,
            backupNudgeHiddenUntilEpochDay = 20_000,
        )
        val out = ByteArrayOutputStream()
        DeviceStateSerializer.writeTo(state, out)
        assertEquals(state, DeviceStateSerializer.readFrom(out.toByteArray().inputStream()))

        val beforeSlice5 = DeviceStateSerializer.readFrom("""{"firstRunFinished":true}""".byteInputStream())
        assertTrue(beforeSlice5.firstRunFinished)
        assertNull(beforeSlice5.lastBackup)
        assertNull(beforeSlice5.wrappedBackupPassphrase)
    }

    @Test
    fun theRememberedPassphraseIsSealedAndAKeyLossMeansNone() = runTest {
        val preferences = FakePreferencesRepository()
        val cipher = FakePassphraseCipher()
        val store = BackupPassphraseStore(preferences, cipher)
        assertNull(store.current())

        store.remember("correct horse battery staple")

        val sealed = preferences.device.value.wrappedBackupPassphrase!!
        assertFalse(sealed.contains("correct horse"))
        assertEquals("correct horse battery staple", store.current())
        assertTrue(store.matches("correct horse battery staple"))
        assertFalse(store.matches("correct horse battery stapl"))

        cipher.lose()
        assertNull(store.current())
    }

    @Test
    fun typingAPassphraseMarksItChecked() = runTest {
        val preferences = FakePreferencesRepository(initialDeviceState = DeviceState(backupCount = 2))
        BackupPassphraseStore(preferences, FakePassphraseCipher()).remember("a new passphrase!")

        assertFalse(BackupRules.passphraseCheckDue(preferences.device.value))
    }

    // --- Passphrase rules ---------------------------------------------------------------------

    @Test
    fun atLeastTwelvePrintableAsciiCharacters() {
        assertEquals(PassphraseRules.Problem.TooShort, PassphraseRules.problem("eleven char"))
        assertNull(PassphraseRules.problem("twelve chars"))
        assertNull(PassphraseRules.problem("""~!@#$%^&*()_+{}|:"<>? `-=[]\;',./ AZaz09"""))
        assertNull(PassphraseRules.problem(" spaces count  "))
    }

    @Test
    fun anythingElseIsRefusedEvenWhenLong() {
        assertEquals(PassphraseRules.Problem.NotAscii, PassphraseRules.problem("سلام دنیا خوبی؟"))
        assertEquals(PassphraseRules.Problem.NotAscii, PassphraseRules.problem("passphrase with é"))
        assertEquals(PassphraseRules.Problem.NotAscii, PassphraseRules.problem("tab\tinside it ok"))
        assertEquals(PassphraseRules.Problem.NotAscii, PassphraseRules.problem("ی"))
    }

    @Test
    fun persianAndArabicDigitsBecomeAscii() {
        assertEquals("orbit 2026 ٫x", PassphraseRules.normalise("orbit ۲۰۲۶ ٫x"))
        assertEquals("pin 0123456789", PassphraseRules.normalise("pin ٠١٢٣٤٥٦٧٨٩"))
        assertNull(PassphraseRules.problem(PassphraseRules.normalise("passphrase ۱۲۳۴")))
    }
}
