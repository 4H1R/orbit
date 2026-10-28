package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.interaction.Interaction
import dev.alializadeh.orbit.interaction.InteractionPerson
import dev.alializadeh.orbit.interaction.InteractionType
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
            listeners = setOf(RestoreListener { listenerRuns++ }),
            notices = notices,
            stagedDatabaseCheck = RoomStagedDatabaseCheck(),
            workDir = File(root, "restore"),
            photosDir = photos,
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
        photo("sara.jpg", ByteArray(3000) { it.toByte() })
        val sara = dao.insert(
            Person(
                name = "Sara",
                ringId = ringId,
                createdAt = backedUpAt,
                photoFileName = "sara.jpg",
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
        assertEquals(File(old.photos, "sara.jpg").readBytes().toList(), File(new.photos, "sara.jpg").readBytes().toList())
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
        old.photo("orphan.jpg") // Not referenced by anyone: left out.

        val manifest = old.reader.read(old.backUp().inputStream(), PASSPHRASE)

        assertEquals(2, manifest.people)
        assertEquals(1, manifest.interactions)
        assertEquals(1, manifest.photos)
        assertEquals(listOf(DATABASE_ENTRY, "photos/sara.jpg"), manifest.entries.map(BackupEntry::path))
    }

    @Test
    fun aRestoreReplacesRatherThanMerges() = runTest {
        val old = Phone("old")
        old.fillWithData()
        val backup = old.backUp()
        val phone = Phone("phone")
        val ring = phone.database.insertTestRing("Mine")
        phone.photo("mina.jpg")
        phone.database.personDao().insert(Person(name = "Mina", ringId = ring, createdAt = backedUpAt, photoFileName = "mina.jpg"))

        phone.restore(backup)

        assertEquals(listOf("Sara", "بابک"), phone.names())
        assertEquals(listOf("Close"), phone.database.ringDao().observeAll().first().map { it.name })
        // The replaced data's photo is gone; the Backup's is there; nothing is left in staging.
        assertEquals(listOf("sara.jpg"), phone.photos.list()!!.sorted())
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
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery staple"

        /** A low scrypt cost (log2 N = 10) so these tests stay fast. */
        const val FAST = 10
    }
}
