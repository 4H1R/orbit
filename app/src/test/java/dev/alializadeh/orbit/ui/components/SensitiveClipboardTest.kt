package dev.alializadeh.orbit.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Review findings 3 and 5: copied passphrases and contact values are marked sensitive. */
@RunWith(RobolectricTestRunner::class)
class SensitiveClipboardTest {

    @Test
    fun aCopiedValueIsSensitiveAndHasNoLabel() {
        val clip = sensitiveClipData("+98 912 345 6789")

        assertEquals("+98 912 345 6789", clip.getItemAt(0).text)
        assertNull(clip.description.label)
        assertTrue(clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        // The key apps read below Android 13 too.
        assertTrue(clip.description.extras.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test
    fun markingKeepsTheClipsOtherExtras() {
        val clip = ClipData.newPlainText(null, "correct horse battery staple")
        clip.description.extras = PersistableBundle().apply { putString("other", "kept") }

        clip.markedSensitive()

        assertEquals("kept", clip.description.extras.getString("other"))
        assertTrue(clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }
}
