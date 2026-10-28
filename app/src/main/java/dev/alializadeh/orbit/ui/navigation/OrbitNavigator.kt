package dev.alializadeh.orbit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Orbit's navigation state: one back stack per [TopLevelTab] plus the selected tab.
 *
 * - Re-selecting the current tab returns it to its root.
 * - Back pops the current tab's stack. At the root of any tab but Upcoming it goes to Upcoming;
 *   at Upcoming's root it leaves the app (the system handles that, as there's nothing to pop).
 * - Detail screens and forms push onto the current tab, and the navigation bar hides while
 *   anything is pushed.
 */
@Stable
class OrbitNavigator(
    private val backStacks: Map<TopLevelTab, NavBackStack<NavKey>>,
    currentTab: MutableState<TopLevelTab> = mutableStateOf(TopLevelTab.Upcoming),
) {
    var currentTab: TopLevelTab by currentTab
        private set

    /** The tabs whose entries are on screen, bottom first. Upcoming always sits underneath. */
    val tabsInUse: List<TopLevelTab>
        get() = if (currentTab == TopLevelTab.Upcoming) {
            listOf(TopLevelTab.Upcoming)
        } else {
            listOf(TopLevelTab.Upcoming, currentTab)
        }

    val showsNavigationBar: Boolean
        get() = backStack(currentTab).size == 1

    fun backStack(tab: TopLevelTab): NavBackStack<NavKey> = backStacks.getValue(tab)

    fun selectTab(tab: TopLevelTab) {
        if (tab == currentTab) {
            backStack(tab).popToRoot()
        } else {
            currentTab = tab
        }
    }

    fun navigate(key: NavKey) {
        backStack(currentTab).add(key)
    }

    /** Replaces the top of the current tab's stack: Add person → the new Person's detail. */
    fun replaceTop(key: NavKey) {
        val stack = backStack(currentTab)
        if (stack.size > 1) stack[stack.lastIndex] = key else stack.add(key)
    }

    fun goBack() {
        val stack = backStack(currentTab)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            currentTab != TopLevelTab.Upcoming -> currentTab = TopLevelTab.Upcoming
        }
    }

    /**
     * A notification tap: Upcoming at its root, with [key] (Person detail) pushed over it, so Back
     * lands on Upcoming. Other tabs keep their stacks.
     */
    fun openOnUpcoming(key: NavKey?) {
        val stack = backStack(TopLevelTab.Upcoming)
        stack.popToRoot()
        if (key != null) stack.add(key)
        currentTab = TopLevelTab.Upcoming
    }

    /** Every tab back at its root, on Upcoming: after a Restore replaced the data under them. */
    fun resetToRoots() {
        TopLevelTab.entries.forEach { backStack(it).popToRoot() }
        currentTab = TopLevelTab.Upcoming
    }

    private fun NavBackStack<NavKey>.popToRoot() {
        while (size > 1) removeAt(lastIndex)
    }

    companion object {
        /** A navigator with every tab at its root and Upcoming selected. */
        fun atRoots(): OrbitNavigator =
            OrbitNavigator(TopLevelTab.entries.associateWith { NavBackStack(it.rootKey) })
    }
}

/** Remembers the navigator; its back stacks and selected tab survive process death. */
@Composable
fun rememberOrbitNavigator(): OrbitNavigator {
    val backStacks = TopLevelTab.entries.associateWith { tab ->
        key(tab) { rememberNavBackStack(tab.rootKey) }
    }
    val currentTab = rememberSaveable { mutableStateOf(TopLevelTab.Upcoming) }
    return remember(backStacks, currentTab) { OrbitNavigator(backStacks, currentTab) }
}

/**
 * The entries to draw, decorated per tab so each tab keeps its saved UI state and ViewModels while
 * another tab is shown. Upcoming's entries sit under the current tab's, so back from a tab root
 * reveals Upcoming (with predictive back).
 */
@Composable
fun OrbitNavigator.rememberEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> {
    val entriesByTab = TopLevelTab.entries.associateWith { tab ->
        key(tab) {
            rememberDecoratedNavEntries(
                backStack = backStack(tab),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )
        }
    }
    return tabsInUse.flatMap { entriesByTab.getValue(it) }
}
