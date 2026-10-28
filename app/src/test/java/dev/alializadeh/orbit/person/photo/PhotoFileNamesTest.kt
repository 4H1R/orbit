package dev.alializadeh.orbit.person.photo

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Review finding 10: a photo name from the data never reaches outside the photos folder. */
@RunWith(RobolectricTestRunner::class)
class PhotoFileNamesTest {

    private val unsafe = listOf("../../shared_prefs/device_state.json", "../x.jpg", "a/b.jpg", "..", "", "x.jpg", "${UUID.randomUUID()}.jpg/..")

    @Test
    fun onlyOrbitsOwnNamesAreValid() {
        assertTrue(FilesPhotoStore.isValidFileName("${UUID.randomUUID()}.jpg"))
        unsafe.forEach { assertFalse(it, FilesPhotoStore.isValidFileName(it)) }
    }

    @Test
    fun anUnsafeNameNamesNoFileAndDeletesNothing() {
        val context = RuntimeEnvironment.getApplication()
        val store = FilesPhotoStore(context)
        val photos = File(context.filesDir, FilesPhotoStore.DIRECTORY)
        val outside = File(context.filesDir, "keep.txt").apply { writeText("keep") }

        for (name in unsafe + "../keep.txt") {
            assertEquals(name, photos.canonicalFile, store.file(name).canonicalFile.parentFile)
            store.delete(name)
        }

        assertTrue(outside.exists())
    }
}
