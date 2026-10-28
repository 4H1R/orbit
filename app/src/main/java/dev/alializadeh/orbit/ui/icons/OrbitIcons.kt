package dev.alializadeh.orbit.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons Material doesn't have. */
object OrbitIcons {

    /** The Orbit mark (a centre, one ring and a body on it), for the Orbit tab. */
    val Orbit: ImageVector by lazy {
        ImageVector.Builder(
            name = "Orbit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Ring: radius 8 around the centre.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(12f, 4f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 16f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -16f)
            }
            // Centre.
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 9f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 6f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -6f)
            }
            // Body on the ring, top right.
            path(fill = SolidColor(Color.Black)) {
                moveTo(17.66f, 3.84f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 5f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -5f)
            }
        }.build()
    }

    // Material Symbols paths for the few icons material-icons-core lacks (Apache 2.0, like the
    // core set). Each is filled, 24dp.

    /** A message bubble: SMS and the messaging apps. */
    val Chat: ImageVector by lazy {
        filled(
            "Chat",
            "M20,2H4c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z" +
                "M6,9h12v2H6V9zM14,14H6v-2h8v2zM18,8H6V6h12v2z",
        )
    }

    val Link: ImageVector by lazy {
        filled(
            "Link",
            "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7" +
                "c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1" +
                "s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
        )
    }

    /** An @: usernames on Instagram and X. */
    val Handle: ImageVector by lazy {
        filled(
            "Handle",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10h5v-2h-5c-4.34,0 -8,-3.66 -8,-8s3.66,-8 8,-8 8,3.66 8,8" +
                "v1.43c0,0.79 -0.71,1.57 -1.5,1.57s-1.5,-0.78 -1.5,-1.57V12c0,-2.76 -2.24,-5 -5,-5" +
                "s-5,2.24 -5,5 2.24,5 5,5c1.38,0 2.64,-0.56 3.54,-1.47 0.65,0.89 1.77,1.47 2.96,1.47" +
                " 1.97,0 3.5,-1.6 3.5,-3.57V12c0,-5.52 -4.48,-10 -10,-10zM12,15c-1.66,0 -3,-1.34 -3,-3" +
                "s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3z",
        )
    }

    val Photo: ImageVector by lazy {
        filled(
            "Photo",
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2z" +
                "M8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
        )
    }

    val Map: ImageVector by lazy {
        filled(
            "Map",
            "M20.5,3l-0.16,0.03L15,5.1 9,3 3.36,4.9c-0.21,0.07 -0.36,0.25 -0.36,0.48V20.5" +
                "c0,0.28 0.22,0.5 0.5,0.5l0.16,-0.03L9,18.9l6,2.1 5.64,-1.9c0.21,-0.07 0.36,-0.25 0.36,-0.48" +
                "V3.5c0,-0.28 -0.22,-0.5 -0.5,-0.5zM15,19l-6,-2.11V5l6,2.11V19z",
        )
    }

    val OpenInNew: ImageVector by lazy {
        filled(
            "OpenInNew",
            "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7z" +
                "M14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z",
        )
    }

    /** The unselected primary star. */
    val StarOutline: ImageVector by lazy {
        filled(
            "StarOutline",
            "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21" +
                "l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1" +
                "l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
        )
    }

    /** A Birthday. */
    val Cake: ImageVector by lazy {
        filled(
            "Cake",
            "M12,6c1.11,0 2,-0.9 2,-2 0,-0.38 -0.1,-0.73 -0.29,-1.03L12,0l-1.71,2.97c-0.19,0.3 -0.29,0.65 -0.29,1.03" +
                " 0,1.1 0.9,2 2,2zM16.6,15.99l-1.07,-1.07 -1.08,1.07c-1.3,1.3 -3.58,1.31 -4.89,0l-1.07,-1.07" +
                " -1.09,1.07C6.75,16.64 5.88,17 4.96,17c-0.73,0 -1.4,-0.23 -1.96,-0.61L3,21c0,0.55 0.45,1 1,1h16" +
                "c0.55,0 1,-0.45 1,-1v-4.61c-0.56,0.38 -1.23,0.61 -1.96,0.61 -0.92,0 -1.79,-0.36 -2.44,-1.01z" +
                "M18,9h-5L13,7h-2v2L6,9c-1.66,0 -3,1.34 -3,3v1.54c0,1.08 0.88,1.96 1.96,1.96 0.52,0 1.02,-0.2 1.38,-0.57" +
                "l2.14,-2.13 2.13,2.13c0.74,0.74 2.03,0.74 2.77,0l2.14,-2.13 2.13,2.13c0.37,0.37 0.86,0.57 1.38,0.57" +
                " 1.08,0 1.96,-0.88 1.96,-1.96L20.99,12C21,10.34 19.66,9 18,9z",
        )
    }

    /** An open Gift idea. */
    val Lightbulb: ImageVector by lazy {
        filled(
            "Lightbulb",
            "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1L9,20v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74" +
                "L8,17c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z",
        )
    }

    /** A given Gift idea, and Mark as given. */
    val Gift: ImageVector by lazy {
        filled(
            "Gift",
            "M20,6h-2.18c0.11,-0.31 0.18,-0.65 0.18,-1 0,-1.66 -1.34,-3 -3,-3 -1.05,0 -1.96,0.54 -2.5,1.35" +
                "l-0.5,0.67 -0.5,-0.68C10.96,2.54 10.05,2 9,2 7.34,2 6,3.34 6,5c0,0.35 0.07,0.69 0.18,1L4,6" +
                "c-1.11,0 -1.99,0.89 -1.99,2L2,19c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2L22,8c0,-1.11 -0.89,-2 -2,-2z" +
                "M15,4c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM9,4c0.55,0 1,0.45 1,1s-0.45,1 -1,1" +
                " -1,-0.45 -1,-1 0.45,-1 1,-1zM20,19L4,19v-2h16v2zM20,14L4,14L4,8h5.08L7,10.83 8.62,12 11,8.76" +
                "l1,-1.36 1,1.36L15.38,12 17,10.83 14.92,8L20,8v6z",
        )
    }

    /** Log interaction: a speech bubble with a plus. */
    val LogInteraction: ImageVector by lazy {
        filled(
            "LogInteraction",
            "M21.99,4c0,-1.1 -0.89,-2 -1.99,-2H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h14l4,4 -0.01,-18z" +
                "M17,11h-4v4h-2v-4H7V9h4V5h2v4h4v2z",
        )
    }

    /** An In person Interaction. */
    val People: ImageVector by lazy {
        filled(
            "People",
            "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11" +
                "c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5" +
                "L1,19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97" +
                " 1.97,3.45L17,19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z",
        )
    }

    /** An Interaction of type Other. */
    val MoreHoriz: ImageVector by lazy {
        filled(
            "MoreHoriz",
            "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2" +
                " 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
        )
    }

    /** Snooze: an alarm clock with a z. */
    val Snooze: ImageVector by lazy {
        filled(
            "Snooze",
            "M9,11h3.63L9,15.2V17h6v-2h-3.63L15,10.8V9H9v2zM16.56,2.44l4.6,3.86 -1.29,1.53 -4.6,-3.86 1.29,-1.53z" +
                "M7.44,2.44l1.29,1.53 -4.6,3.86 -1.29,-1.53 4.6,-3.86zM12,6c3.86,0 7,3.14 7,7s-3.14,7 -7,7" +
                " -7,-3.14 -7,-7 3.14,-7 7,-7m0,-2c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9 -4.03,-9 -9,-9z",
        )
    }

    /** A drag handle (Settings → Rings). */
    val DragHandle: ImageVector by lazy {
        filled("DragHandle", "M20,9H4v2h16V9zM4,15h16v-2H4v2z")
    }

    /** Show a passphrase. */
    val Visibility: ImageVector by lazy {
        filled(
            "Visibility",
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
                "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3" +
                "s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }

    /** Hide a passphrase. */
    val VisibilityOff: ImageVector by lazy {
        filled(
            "VisibilityOff",
            "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75" +
                " -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
                "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84" +
                "l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65" +
                " 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53" +
                " -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16" +
                "c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
        )
    }

    /** Backup: a cloud with an upward arrow. */
    val Backup: ImageVector by lazy {
        filled(
            "Backup",
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14" +
                "c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z" +
                "M14,13v4h-4v-4H7l5,-5 5,5h-3z",
        )
    }

    /** Sort (the People list's Sort chip); its bars line up at the start, so it mirrors in RTL. */
    val Sort: ImageVector by lazy {
        filled("Sort", "M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z", autoMirror = true)
    }

    private fun filled(name: String, pathData: String, autoMirror: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
}
