package dev.alializadeh.orbit.person.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.contact.ContactActionKind
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.CadenceUnit
import dev.alializadeh.orbit.ui.icons.OrbitIcons

val ContactType.icon: ImageVector
    get() = when (this) {
        ContactType.Phone -> Icons.Filled.Call
        ContactType.WhatsApp, ContactType.Telegram, ContactType.Signal -> OrbitIcons.Chat
        ContactType.Email -> Icons.Filled.Email
        ContactType.Instagram, ContactType.X -> OrbitIcons.Handle
        ContactType.LinkedIn -> Icons.Filled.AccountCircle
        ContactType.Address -> Icons.Filled.Place
        ContactType.Link -> OrbitIcons.Link
    }

val ContactActionKind.icon: ImageVector
    get() = when (this) {
        ContactActionKind.Call -> Icons.Filled.Call
        ContactActionKind.Sms, ContactActionKind.WhatsApp, ContactActionKind.Telegram, ContactActionKind.Signal ->
            OrbitIcons.Chat
        ContactActionKind.Email -> Icons.Filled.Email
        ContactActionKind.Instagram, ContactActionKind.X -> OrbitIcons.Handle
        ContactActionKind.LinkedIn -> Icons.Filled.AccountCircle
        ContactActionKind.Map -> OrbitIcons.Map
        ContactActionKind.Open -> OrbitIcons.OpenInNew
    }

@Composable
@ReadOnlyComposable
fun ContactType.displayName(): String = stringResource(nameRes)

@Composable
@ReadOnlyComposable
fun ContactActionKind.label(): String = stringResource(labelRes)

/**
 * "every 30 days", "every week", "No cadence" (copy deck); "no cadence" when [inline], inside a
 * sentence. At the start of a line, pass the result through `sentenceStart`.
 */
@Composable
@ReadOnlyComposable
fun cadenceText(cadence: Cadence?, inline: Boolean = false): String {
    if (cadence == null) return stringResource(if (inline) R.string.cadence_none_inline else R.string.cadence_none)
    val resources = LocalResources.current
    val count = LocalOrbitFormats.current.numbers.format(cadence.count)
    return when (cadence.unit) {
        CadenceUnit.Days -> if (cadence.count == 1) {
            resources.getString(R.string.cadence_every_day)
        } else {
            resources.getQuantityString(R.plurals.cadence_every_days, cadence.count, count)
        }
        CadenceUnit.Weeks -> if (cadence.count == 1) {
            resources.getString(R.string.cadence_every_week)
        } else {
            resources.getQuantityString(R.plurals.cadence_every_weeks, cadence.count, count)
        }
        CadenceUnit.Months -> if (cadence.count == 1) {
            resources.getString(R.string.cadence_every_month)
        } else {
            resources.getQuantityString(R.plurals.cadence_every_months, cadence.count, count)
        }
    }
}
