package dev.alializadeh.orbit.about

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings → About → Open-source licences names everything the release APK ships. The build writes
 * the release runtime classpath's modules to a file (`writeReleaseRuntimeModules`); a new dependency
 * fails here until [OpenSourceLicences] lists it with its licence.
 */
class LicencesTest {

    private val modules: List<Pair<String, String>> by lazy {
        val path = System.getProperty("orbit.releaseRuntimeModules") ?: error("Run through Gradle: it writes the module list")
        File(path).readLines().filter(String::isNotBlank).map { line ->
            val (group, name) = line.split(':', limit = 2)
            group to name
        }
    }

    /** Platforms (BOMs) only align versions: they put no code in the APK. */
    private val shipped get() = modules.filterNot { (_, name) -> name.endsWith("-bom") }

    @Test
    fun theReleaseBuildHasDependencies() {
        assertTrue(shipped.size > 50)
    }

    @Test
    fun everyShippedModuleIsListed() {
        val unlisted = shipped.filter { (group, name) ->
            OpenSourceLicences.libraries.none { OpenSourceLicences.covers(it, group, name) }
        }
        assertTrue(
            "Add these to OpenSourceLicences.libraries (with their licence text in assets/licences):\n" +
                unlisted.joinToString("\n") { (group, name) -> "$group:$name" },
            unlisted.isEmpty(),
        )
    }

    @Test
    fun everyListedLibraryIsStillShipped() {
        val stale = OpenSourceLicences.libraries.filter { item ->
            shipped.none { (group, name) -> OpenSourceLicences.covers(item, group, name) }
        }
        assertTrue("No longer in the release build: ${stale.map(OpenSourceItem::name)}", stale.isEmpty())
    }

    @Test
    fun everyLicenceTextIsBundled() {
        val missing = Licence.entries.filterNot { File("src/main/assets/${it.asset}").isFile }
        assertTrue("Missing licence texts: $missing", missing.isEmpty())
        val unused = Licence.entries.filter { licence ->
            (OpenSourceLicences.fonts + OpenSourceLicences.libraries).none { licence in it.licences }
        }
        assertTrue("Licences nothing uses: $unused", unused.isEmpty())
    }

    @Test
    fun patternsMatchWholeGroupsAndNames() {
        val androidx = OpenSourceLicences.libraries.single { it.name.startsWith("Android Jetpack") }
        assertTrue(OpenSourceLicences.covers(androidx, "androidx.compose.ui", "ui-graphics"))
        assertTrue(!OpenSourceLicences.covers(androidx, "com.example.androidx", "core"))
    }

    @Test
    fun licenceTextsAreReflowedIntoParagraphs() {
        assertTrue(reflowed("Intro\n-----\nTITLE\n=====\n\nBody") == listOf("Intro", "TITLE", "Body"))
        val text = "Line one\nline two.\n\n   Indented\n   paragraph.\n\n\n"
        assertTrue(reflowed(text) == listOf("Line one line two.", "Indented paragraph."))
    }
}
