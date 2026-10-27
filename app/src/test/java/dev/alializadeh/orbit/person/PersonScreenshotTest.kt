package dev.alializadeh.orbit.person

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.alializadeh.orbit.person.detail.PersonDetailScreen
import dev.alializadeh.orbit.person.detail.PersonDetailUiState
import dev.alializadeh.orbit.person.list.PeopleScreen
import dev.alializadeh.orbit.person.list.PeopleUiState
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of the People screens in en/LTR and fa/RTL, light and dark.
 *
 * Record: `./gradlew recordRoborazziDebug`. Verify: `./gradlew verifyRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PersonScreenshotTest(private val variant: Variant) {

    @Test
    fun peopleList() = capture("people_list") {
        PeopleScreen(
            uiState = PeopleUiState.Loaded(variant.names.mapIndexed { index, name -> person(index + 1L, name) }),
            onPersonClick = {},
            onAddPerson = {},
        )
    }

    @Test
    fun peopleListEmpty() = capture("people_list_empty") {
        PeopleScreen(uiState = PeopleUiState.Loaded(emptyList()), onPersonClick = {}, onAddPerson = {})
    }

    @Test
    fun personDetail() = capture("person_detail") {
        PersonDetailScreen(uiState = PersonDetailUiState.Loaded(person(1, variant.names.first())), onBack = {})
    }

    private fun capture(screen: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(variant.qualifiers)
        captureRoboImage("src/test/screenshots/${screen}_${variant.id}.png") {
            OrbitTheme { content() }
        }
    }

    private fun person(id: Long, name: String) = Person(id = id, name = name, createdAt = Instant.EPOCH)

    enum class Variant(val id: String, val qualifiers: String, val names: List<String>) {
        EnLight("en_light", "+en-ldltr-notnight", ENGLISH_NAMES),
        EnDark("en_dark", "+en-ldltr-night", ENGLISH_NAMES),
        FaLight("fa_light", "+fa-ldrtl-notnight", PERSIAN_NAMES),
        FaDark("fa_dark", "+fa-ldrtl-night", PERSIAN_NAMES),
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Variant> = Variant.entries
    }
}

// Sorted as the DAO would return them. The Persian list mixes in a Latin name, as real lists do.
private val ENGLISH_NAMES = listOf("Ali Rezaei", "Babak", "Mina Karimi", "Sara Ahmadi", "Tom O'Neill")
private val PERSIAN_NAMES = listOf("Tom O'Neill", "بابک", "سارا احمدی", "علی رضایی", "مینا کریمی")
