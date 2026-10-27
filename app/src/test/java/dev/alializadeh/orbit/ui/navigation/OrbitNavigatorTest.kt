package dev.alializadeh.orbit.ui.navigation

import dev.alializadeh.orbit.person.AddPersonKey
import dev.alializadeh.orbit.person.PeopleKey
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.settings.SettingsKey
import dev.alializadeh.orbit.upcoming.UpcomingKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitNavigatorTest {

    private val navigator = OrbitNavigator.atRoots()

    @Test
    fun startsOnUpcomingWithTheBarShowing() {
        assertEquals(TopLevelTab.Upcoming, navigator.currentTab)
        assertEquals(listOf(TopLevelTab.Upcoming), navigator.tabsInUse)
        assertTrue(navigator.showsNavigationBar)
    }

    @Test
    fun eachTabKeepsItsOwnBackStack() {
        navigator.selectTab(TopLevelTab.People)
        navigator.navigate(PersonDetailKey(7))
        navigator.selectTab(TopLevelTab.Settings)
        navigator.selectTab(TopLevelTab.People)

        assertEquals(listOf(PeopleKey, PersonDetailKey(7)), navigator.backStack(TopLevelTab.People).toList())
        assertEquals(listOf(SettingsKey), navigator.backStack(TopLevelTab.Settings).toList())
    }

    @Test
    fun pushingHidesTheBarAndBackShowsItAgain() {
        navigator.selectTab(TopLevelTab.People)
        navigator.navigate(AddPersonKey)
        assertFalse(navigator.showsNavigationBar)

        navigator.goBack()

        assertTrue(navigator.showsNavigationBar)
        assertEquals(listOf(PeopleKey), navigator.backStack(TopLevelTab.People).toList())
    }

    @Test
    fun reselectingATabReturnsItToItsRoot() {
        navigator.selectTab(TopLevelTab.People)
        navigator.navigate(PersonDetailKey(1))
        navigator.navigate(AddPersonKey)

        navigator.selectTab(TopLevelTab.People)

        assertEquals(listOf(PeopleKey), navigator.backStack(TopLevelTab.People).toList())
    }

    @Test
    fun backAtTheRootOfAnotherTabGoesToUpcoming() {
        navigator.selectTab(TopLevelTab.Settings)

        navigator.goBack()

        assertEquals(TopLevelTab.Upcoming, navigator.currentTab)
    }

    @Test
    fun backAtAnotherTabsRootLandsOnUpcomingsOwnStack() {
        navigator.navigate(AddPersonKey)
        navigator.selectTab(TopLevelTab.People)

        navigator.goBack()

        assertEquals(TopLevelTab.Upcoming, navigator.currentTab)
        assertEquals(listOf(UpcomingKey, AddPersonKey), navigator.backStack(TopLevelTab.Upcoming).toList())
    }

    @Test
    fun upcomingSitsUnderEveryOtherTab() {
        navigator.selectTab(TopLevelTab.Orbit)

        assertEquals(listOf(TopLevelTab.Upcoming, TopLevelTab.Orbit), navigator.tabsInUse)
    }

    @Test
    fun backAtUpcomingsRootLeavesTheStackAsItIs() {
        navigator.goBack()

        assertEquals(TopLevelTab.Upcoming, navigator.currentTab)
        assertEquals(listOf(UpcomingKey), navigator.backStack(TopLevelTab.Upcoming).toList())
    }
}
