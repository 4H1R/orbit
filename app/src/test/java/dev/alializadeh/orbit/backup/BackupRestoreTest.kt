package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.data.DeviceState
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.interaction.Interaction
import dev.alializadeh.orbit.interaction.InteractionPerson
import dev.alializadeh.orbit.interaction.InteractionType
import dev.alializadeh.orbit.lock.AppLockSettings
import dev.alializadeh.orbit.lock.RelockAfter
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.PersonItem
import dev.alializadeh.orbit.person.PersonItemKind
import dev.alializadeh.orbit.person.PersonTag
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.testing.FakePassphraseCipher
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.FileBackupDocuments
import dev.alializadeh.orbit.testing.TestAppLocales
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.io.File
import java.io.IOException
import java.security.ProviderException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Back up now and Restore end to end on the JVM, between two "phones" (each a real on-disk Room
 * database, a photos folder and device-bound state): everything comes back, the swap replaces
 * rather than merges, and a Restore counts as a Backup.
 */
class BackupRestoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val backedUpAt = Instant.parse("2026-10-12T09:00:00Z")
    private val phones = mutableListOf<Phone>()

    @After
    fun tearDown() {
        phones.forEach { it.database.close() }
    }

    /** One device: its database, photos, device-bound state, and Backup/Restore wired to them. */
    private inner class Phone(name: String, locale: String? = null) {
        val root: File = temp.newFolder(name)
        val database: OrbitDatabase = TestDatabases.onDisk(File(root, "orbit.db"))
        val photos = File(root, "photos").apply { mkdirs() }
        val preferences = FakePreferencesRepository()
        val cipher = FakePassphraseCipher()
        val passphrases = BackupPassphraseStore(preferences, cipher)
        val locales = TestAppLocales(locale)
        val notices = RestoreNotices()
        var listenerRuns = 0
        var onRestored: suspend () -> Unit = { listenerRuns++ }
        var aroundSwap: suspend (swap: suspend () -> Unit) -> Unit = { it() }
        val logged = mutableListOf<String>()
        val documents = FileBackupDocuments()
        val reader = BackupReader(OrbitDatabase.VERSION)
        val writer = BackupWriter(
            snapshotter = RoomDatabaseSnapshotter(database),
            clock = Clock.fixed(backedUpAt, ZoneOffset.UTC),
            appVersion = AppVersion("0.1.0", 1),
            appLocales = locales,
            workDir = File(root, "backup-work"),
            photosDir = photos,
        )
        val maker = BackupMaker(writer, reader, documents, preferences)
        val restorer = Restorer(
            database = database,
            reader = reader,
            preferences = preferences,
            passphrases = passphrases,
            appLocales = locales,
            listeners = setOf(
                object : RestoreListener {
                    override suspend fun aroundSwap(swap: suspend () -> Unit) = this@Phone.aroundSwap(swap)

                    override suspend fun onRestored() = this@Phone.onRestored()
                },
            ),
            notices = notices,
            stagedDatabaseCheck = RoomStagedDatabaseCheck(),
            workDir = File(root, "restore"),
            photosDir = photos,
            log = { logged += it },
        )
        val status = RoomBackupStatusRepository(database, preferences)

        init {
            phones += this
        }

        suspend fun backUp(passphrase: String = PASSPHRASE, name: String = "orbit-backup.age"): File {
            val file = File(root, name)
            maker.backUp(file.path, passphrase, workFactor = FAST)
            return file
        }

        suspend fun restore(file: File, passphrase: String = PASSPHRASE): Int =
            restorer.apply(restorer.stage({ file.inputStream() }, passphrase))

        fun photo(name: String, bytes: ByteArray = name.toByteArray()) = File(photos, name).apply { writeBytes(bytes) }

        suspend fun names(): List<String> = database.personDao().getAll().map(Person::name).sorted()
    }

    /** Two People with every kind of detail, an Interaction, a Tag, a photo and changed preferences. */
    private suspend fun Phone.fillWithData() {
        val dao = database.personDao()
        val ringId = database.insertTestRing("Close")
        photo(SARA_PHOTO, ByteArray(3000) { it.toByte() })
        val sara = dao.insert(
            Person(
                name = "Sara",
                ringId = ringId,
                createdAt = backedUpAt,
                photoFileName = SARA_PHOTO,
                snoozedUntil = LocalDate.of(2026, 10, 20),
                lastNotifiedDueOn = LocalDate.of(2026, 10, 1),
            ),
        )
        val babak = dao.insert(Person(name = "بابک", ringId = ringId, createdAt = backedUpAt, notes = "Jazz"))
        dao.insertItems(listOf(PersonItem(personId = sara, kind = PersonItemKind.Like, text = "Jazz", position = 0)))
        dao.insertContactMethods(
            listOf(ContactMethod(personId = sara, type = ContactType.Phone, value = "+98912", isPrimary = true, position = 0)),
        )
        dao.insertImportantDate(
            ImportantDate(
                personId = babak,
                type = ImportantDateType.Birthday,
                date = RecurringDate(CalendarSystem.SolarHijri, 1, 14, 1370),
                position = 0,
            ),
        )
        dao.insertGiftIdea(GiftIdea(personId = sara, text = "Vinyl", createdAt = backedUpAt))
        val tag = database.tagDao().insert(Tag(name = "Gym"))
        dao.insertPersonTags(listOf(PersonTag(personId = sara, tagId = tag, position = 0)))
        val interaction = database.interactionDao().insert(
            Interaction(type = InteractionType.Call, date = LocalDate.of(2026, 10, 10), note = "Coffee"),
        )
        database.interactionDao().insertPeople(
            listOf(InteractionPerson(interaction, sara), InteractionPerson(interaction, babak)),
        )
        val preferencesDao = database.backedUpPreferencesDao()
        preferencesDao.update(
            preferencesDao.get().copy(
                theme = ThemeMode.Dark,
                primaryCalendar = CalendarSystem.SolarHijri,
                useLatinDigits = true,
                backupNudgeInterval = BackupNudgeInterval.Days60,
            ),
        )
    }

    @Test
    fun everythingComesBackOnAnotherPhone() = runTest {
        val old = Phone("old", locale = "fa")
        old.fillWithData()
        val backup = old.backUp()

        val new = Phone("new", locale = "en")
        val people = new.restore(backup)

        assertEquals(2, people)
        assertEquals(listOf("Sara", "بابک"), new.names())
        val sara = new.database.personDao().getAll().single { it.name == "Sara" }
        val details = new.database.personDao().getWithDetails(sara.id)!!
        assertEquals(listOf("Jazz"), details.items.map(PersonItem::text))
        assertEquals("+98912", details.contactMethods.single().value)
        assertEquals(listOf("Gym"), details.tags.map(Tag::name))
        assertEquals(LocalDate.of(2026, 10, 20), sara.snoozedUntil)
        assertEquals(LocalDate.of(2026, 10, 1), sara.lastNotifiedDueOn)
        assertEquals(listOf("Coffee"), new.database.interactionDao().observeForPerson(sara.id).first().map { it.interaction.note })
        val babak = new.database.personDao().getAll().single { it.name == "بابک" }
        assertEquals(RecurringDate(CalendarSystem.SolarHijri, 1, 14, 1370), new.database.personDao().getImportantDates(babak.id).single().date)
        // The photo, byte for byte.
        assertEquals(File(old.photos, SARA_PHOTO).readBytes().toList(), File(new.photos, SARA_PHOTO).readBytes().toList())
        // Backed-up preferences come with the database; the language from the manifest.
        val preferences = new.database.backedUpPreferencesDao().get()
        assertEquals(ThemeMode.Dark, preferences.theme)
        assertEquals(CalendarSystem.SolarHijri, preferences.primaryCalendar)
        assertTrue(preferences.useLatinDigits)
        assertEquals(BackupNudgeInterval.Days60, preferences.backupNudgeInterval)
        assertEquals(listOf<String?>("fa"), new.locales.applied)
        // Then: the reconcile hook ran, and the shell is told.
        assertEquals(1, new.listenerRuns)
        assertEquals(2, new.notices.restoredPeople.value)
    }

    @Test
    fun theManifestCountsWhatTheBackupHolds() = runTest {
        val old = Phone("old")
        old.fillWithData()
        old.photo("0c3b6f2e-1d7a-4f43-9a51-6f1b0f5f2a10.jpg") // Not referenced by anyone: left out.

        val manifest = old.reader.read(old.backUp().inputStream(), PASSPHRASE)

        assertEquals(2, manifest.people)
        assertEquals(1, manifest.interactions)
        assertEquals(1, manifest.photos)
        assertEquals(listOf(DATABASE_ENTRY, "photos/$SARA_PHOTO"), manifest.entries.map(BackupEntry::path))
    }

    @Test
    fun aRestoreReplacesRatherThanMerges() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp()
        val phone = Phone("phone")
        val ring = phone.database.insertTestRing("Mine")
        phone.photo(MINA_PHOTO)
        phone.database.personDao().insert(Person(name = "Mina", ringId = ring, createdAt = backedUpAt, photoFileName = MINA_PHOTO))

        phone.restore(backup)

        assertEquals(listOf("Sara", "بابک"), phone.names())
        assertEquals(listOf("Close"), phone.database.ringDao().observeAll().first().map { it.name })
        // The replaced data's photo is gone; the Backup's is there; nothing is left in staging.
        assertEquals(listOf(SARA_PHOTO), phone.photos.list()!!.sorted())
        assertTrue(File(phone.root, "restore").listFiles()!!.isEmpty())
    }

    @Test
    fun aBackupIsVerifiedAndMovesLastBackupAndTheCount() = runTest {
        val phone = Phone("phone")
        phone.fillWithData()
        assertTrue(phone.status.status.first().dataChanged)

        phone.backUp()

        val device = phone.preferences.device.value
        assertEquals(backedUpAt.toEpochMilli(), device.lastBackup!!.createdAtEpochMillis)
        assertEquals(1, device.backupCount)
        assertFalse(phone.status.status.first().dataChanged)
    }

    @Test
    fun aFailedBackupDeletesThePartialFileAndKeepsLastBackup() = runTest {
        val phone = Phone("phone")
        phone.fillWithData()
        phone.documents.failAfterBytes = 2_000
        val target = File(phone.root, "partial.age")

        val failure = runCatching { phone.maker.backUp(target.path, PASSPHRASE, workFactor = FAST) }

        assertTrue(failure.isFailure)
        assertFalse(target.exists())
        assertEquals(listOf(target.path), phone.documents.deleted)
        assertNull(phone.preferences.device.value.lastBackup)
        assertEquals(0, phone.preferences.device.value.backupCount)
        assertNull(phone.preferences.device.value.backupInProgressUri)
    }

    @Test
    fun aRestoreCountsAsABackup() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp()
        val new = Phone("new")
        new.preferences.device.value = new.preferences.device.value.copy(backupNudgeHiddenUntilEpochDay = 99_999)

        new.restore(backup)

        val status = new.status.status.first()
        assertEquals(backedUpAt, status.lastBackupAt)
        assertFalse(status.dataChanged)
        assertNull(status.nudgeHiddenUntil)
        // Restored before first run finished: Welcome's choices mustn't replace the restored ones.
        assertTrue(new.preferences.device.value.restoredDuringFirstRun)
        // The typed passphrase is now this phone's remembered one, sealed.
        assertEquals(PASSPHRASE, new.passphrases.current())
        assertFalse(new.preferences.device.value.wrappedBackupPassphrase!!.contains(PASSPHRASE))
        // A change after the Restore is a change.
        new.database.personDao().setSnoozedUntil(new.database.personDao().getAll().first().id, null)
        assertTrue(new.status.status.first().dataChanged)
    }

    @Test
    fun persianDigitsInARestorePassphraseAreTheAsciiOnes() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp(passphrase = "orbit passphrase 2026")

        val new = Phone("new")
        new.restore(backup, passphrase = "orbit passphrase ۲۰۲۶")

        assertEquals(2, new.names().size)
        assertEquals("orbit passphrase 2026", new.passphrases.current())
    }

    @Test
    fun theDeviceKeepsItsOwnDeviceBoundState() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp()
        val new = Phone("new")
        new.preferences.device.value = new.preferences.device.value.copy(firstRunFinished = true, backupCount = 7)

        new.restore(backup)

        val device = new.preferences.device.value
        assertTrue(device.firstRunFinished)
        assertEquals(7, device.backupCount)
        assertNotNull(device.lastBackup)
        assertFalse(device.restoredDuringFirstRun)
    }

    @Test
    fun aRestoreLeavesThisPhonesAppLockAsItWas() = runTest {
        val old = Phone("old")
        old.fillWithData()
        old.preferences.updateDeviceState {
            it.copy(appLock = AppLockSettings(enabled = true, relockAfter = RelockAfter.Immediately, showDetailsInNotifications = true))
        }
        val backup = old.backUp()
        val off = Phone("off")
        val on = Phone("on")
        val onSettings = AppLockSettings(enabled = true, relockAfter = RelockAfter.FiveMinutes)
        on.preferences.updateDeviceState { it.copy(appLock = onSettings) }

        off.restore(backup)
        on.restore(backup)

        assertEquals(AppLockSettings(), off.preferences.device.value.appLock)
        assertEquals(onSettings, on.preferences.device.value.appLock)
    }

    // --- Review fixes ---------------------------------------------------------------------------

    /** Finding 2: once the swap commits, the Restore is done, whatever fails after it. */
    @Test
    fun aFailureAfterTheCommitStillReportsRestored() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp()
        val new = Phone("new")
        new.cipher.failing = ProviderException("Keystore unavailable")
        new.onRestored = { error("a listener broke") }

        val people = new.restore(backup)

        assertEquals(2, people)
        assertEquals(listOf("Sara", "بابک"), new.names())
        assertEquals(backedUpAt.toEpochMilli(), new.preferences.device.value.lastBackup!!.createdAtEpochMillis)
        assertNull(new.preferences.device.value.wrappedBackupPassphrase)
        assertEquals(2, new.notices.restoredPeople.value)
        assertTrue(File(new.root, "restore").listFiles()!!.isEmpty())
        assertTrue(new.logged.toString(), new.logged.any { "IllegalStateException" in it })
    }

    /** Finding 8: every swap writes a new random token, inside its transaction. */
    @Test
    fun eachRestoreWritesANewRestoreToken() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp()
        val new = Phone("new")
        assertNull(new.database.backupStatusDao().restoreToken())

        new.restore(backup)
        val first = new.database.backupStatusDao().restoreToken()
        new.restore(backup)
        val second = new.database.backupStatusDao().restoreToken()

        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first != second)
        // The data version is untouched by it: the restored data counts as backed up.
        assertFalse(new.status.status.first().dataChanged)
    }

    /** Finding 6: the swap runs inside the listeners' aroundSwap (Reminders hold reconcile off there). */
    @Test
    fun theSwapRunsInsideTheListenersAroundSwap() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp()
        val new = Phone("new")
        val seen = mutableListOf<String>()
        new.aroundSwap = { swap ->
            seen += "before: " + new.names()
            swap()
            seen += "after: " + new.names()
        }

        new.restore(backup)

        assertEquals(listOf("before: []", "after: [Sara, بابک]"), seen)
    }

    /** Finding 5: no photo is copied in before a journal that can take it back out exists. */
    @Test
    fun photosCopiedInAreAlwaysCoveredByTheJournal() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp()
        val new = Phone("new")
        val journalFile = File(new.root, "restore/restore-journal.json")
        var journalAtTheCrash: String? = null
        // The process dies after the photos are copied, before the swap.
        new.aroundSwap = {
            assertTrue(File(new.photos, SARA_PHOTO).exists())
            journalAtTheCrash = journalFile.readText()
            throw IOException("process died")
        }

        val failure = runCatching { new.restore(backup) }

        assertTrue(failure.isFailure)
        assertTrue(journalAtTheCrash!!.contains(SARA_PHOTO))
        // The failed swap took its photo back out at once...
        assertFalse(File(new.photos, SARA_PHOTO).exists())
        // ...and after a real crash at that point, the next start does.
        new.photo(SARA_PHOTO)
        journalFile.writeText(journalAtTheCrash!!)
        new.restorer.recoverInterrupted()
        assertFalse(File(new.photos, SARA_PHOTO).exists())
        assertEquals(emptyList<String>(), new.names())
    }

    /** Finding 3: running out of memory while writing is a clear failure, and no file is left. */
    @Test
    fun runningOutOfMemoryWhileWritingIsAClearFailure() = runTest {
        val phone = Phone("phone")
        phone.fillWithData()
        val greedy = BackupWriter(
            snapshotter = { throw OutOfMemoryError("Failed to allocate 134217728 bytes") },
            clock = Clock.fixed(backedUpAt, ZoneOffset.UTC),
            appVersion = AppVersion("0.1.0", 1),
            appLocales = phone.locales,
            workDir = File(phone.root, "backup-work"),
            photosDir = phone.photos,
        )
        val target = File(phone.root, "oom.age")

        val failure = runCatching {
            BackupMaker(greedy, phone.reader, phone.documents, phone.preferences).backUp(target.path, PASSPHRASE)
        }

        assertTrue(failure.exceptionOrNull().toString(), failure.exceptionOrNull() is BackupException.NeedsMoreMemory)
        assertFalse(target.exists())
        assertEquals(listOf(target.path), phone.documents.deleted)
        assertNull(phone.preferences.device.value.lastBackup)
        assertNull(phone.preferences.device.value.backupInProgressUri)
    }

    /** Finding 4: a cancel that lands once the Backup is recorded never deletes the file. */
    @Test
    fun aCancelAfterTheBackupIsRecordedKeepsTheFile() = runTest {
        val phone = Phone("phone")
        phone.fillWithData()
        val target = File(phone.root, "late-cancel.age")
        var work: Job? = null
        val cancelsOnRecord = object : PreferencesRepository by phone.preferences {
            override suspend fun updateDeviceState(transform: (DeviceState) -> DeviceState) {
                phone.preferences.updateDeviceState(transform)
                if (phone.preferences.device.value.lastBackup != null) {
                    // The cancel lands right after the write, before backUp returns.
                    work?.cancel()
                    yield()
                }
            }
        }
        val maker = BackupMaker(phone.writer, phone.reader, phone.documents, cancelsOnRecord)

        work = launch { maker.backUp(target.path, PASSPHRASE, workFactor = FAST) }
        work.join()

        assertTrue(target.exists())
        assertTrue(phone.documents.deleted.isEmpty())
        assertEquals(1, phone.preferences.device.value.backupCount)
        assertNull(phone.preferences.device.value.backupInProgressUri)
    }

    /** Finding 5: a Backup whose process died is deleted at the next start, with any snapshot. */
    @Test
    fun anInterruptedBackupIsDeletedAtTheNextStart() = runTest {
        val phone = Phone("phone")
        val partial = File(phone.root, "partial.age").apply { writeText("half an age file") }
        phone.preferences.updateDeviceState { it.copy(backupInProgressUri = partial.path) }
        val snapshot = File(phone.root, "backup-work/snapshot-dead.db").apply {
            parentFile!!.mkdirs()
            writeText("plaintext")
            setLastModified(0)
        }

        phone.maker.cleanUpInterrupted(startedAtMillis = System.currentTimeMillis())

        assertFalse(partial.exists())
        assertFalse(snapshot.exists())
        assertNull(phone.preferences.device.value.backupInProgressUri)
        // A finished one is never touched.
        val finished = phone.backUp()
        phone.maker.cleanUpInterrupted(startedAtMillis = System.currentTimeMillis())
        assertTrue(finished.exists())
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery staple"
        const val SARA_PHOTO = "3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg"
        const val MINA_PHOTO = "9b2c6a1e-8d4f-4c3b-b5a7-2e1f0d9c8b7a.jpg"

        /** A low scrypt cost (log2 N = 10) so these tests stay fast. */
        const val FAST = 10
    }
}
