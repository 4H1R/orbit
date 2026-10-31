package dev.alializadeh.orbit.person.contact

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.result.contract.ActivityResultContract

/** The name and phone number the user picked from the phone's contacts. */
data class PickedContact(val name: String?, val phone: String?)

/**
 * Android's contact picker, on phone numbers: the user picks one, and Orbit gets read access to that
 * one row. Orbit has no contacts permission and sees nothing else in the address book, so only what
 * that row holds (name and number) can come across.
 */
class PickContactPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

/** The row at [uri] from [PickContactPhone], or null when it can't be read. Blocks: call off the main thread. */
fun readPickedContact(resolver: ContentResolver, uri: Uri): PickedContact? = try {
    resolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) PickedContact(cursor.getString(0), cursor.getString(1)) else null
    }
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
