package dev.alializadeh.orbit.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.alializadeh.orbit.orbitview.OrbitViewKey
import dev.alializadeh.orbit.person.PeopleKey
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.settings.SettingsKey
import dev.alializadeh.orbit.upcoming.UpcomingKey
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A tab root's ViewModel (the People tab's query and filters live in one) survives switching tabs,
 * opening a Person and coming back, and re-selecting the tab; it goes only with the entry.
 */
@RunWith(RobolectricTestRunner::class)
class TabStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    class Probe : ViewModel()

    @Test
    fun aTabRootsViewModelOutlivesTabSwitchesAndPushedScreens() {
        val navigator = OrbitNavigator.atRoots()
        var current: Probe? = null
        composeRule.setContent {
            val entries = navigator.rememberEntries(
                rememberNavEntryStates(),
                entryProvider {
                    entry<UpcomingKey> {}
                    entry<PeopleKey> { current = viewModel<Probe>() }
                    entry<PersonDetailKey> {}
                    entry<OrbitViewKey> {}
                    entry<SettingsKey> {}
                },
            )
            NavDisplay(entries = entries, onBack = navigator::goBack)
        }

        composeRule.runOnIdle { navigator.selectTab(TopLevelTab.People) }
        composeRule.waitForIdle()
        val first = current!!

        composeRule.runOnIdle { navigator.selectTab(TopLevelTab.Settings) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navigator.selectTab(TopLevelTab.People) }
        composeRule.waitForIdle()
        assertSame(first, current)

        composeRule.runOnIdle { navigator.navigate(PersonDetailKey(1)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navigator.goBack() }
        composeRule.waitForIdle()
        assertSame(first, current)

        composeRule.runOnIdle { navigator.selectTab(TopLevelTab.People) }
        composeRule.waitForIdle()
        assertSame(first, current)

        // A notification tap or "Who else likes jazz?" from Upcoming's Person detail.
        composeRule.runOnIdle {
            navigator.openOnUpcoming(PersonDetailKey(2))
            navigator.openPeople()
        }
        composeRule.waitForIdle()
        assertSame(first, current)
    }
}
