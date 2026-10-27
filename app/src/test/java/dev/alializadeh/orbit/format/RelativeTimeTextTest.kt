package dev.alializadeh.orbit.format

import android.content.res.Resources
import dev.alializadeh.orbit.language.OrbitLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The relative words through the real string resources, with formatted numbers. */
@RunWith(RobolectricTestRunner::class)
class RelativeTimeTextTest {

    @Test
    fun english() {
        val resources = resources("en")
        val numbers = OrbitNumberFormatter(OrbitLanguage.English, useLatinDigits = false)
        assertEquals("Today", RelativeAhead.Today.text(resources, numbers))
        assertEquals("Tomorrow", RelativeAhead.Tomorrow.text(resources, numbers))
        assertEquals("In 5 days", RelativeAhead.InDays(5).text(resources, numbers))
        assertEquals("today", RelativeAgo.Today.text(resources, numbers))
        assertEquals("yesterday", RelativeAgo.Yesterday.text(resources, numbers))
        assertEquals("34 days ago", RelativeAgo.DaysAgo(34).text(resources, numbers))
        assertEquals("2 months ago", RelativeAgo.MonthsAgo(2).text(resources, numbers))
        assertEquals("3 years ago", RelativeAgo.YearsAgo(3).text(resources, numbers))
    }

    @Test
    fun persianWithPersianDigits() {
        val resources = resources("fa")
        val numbers = OrbitNumberFormatter(OrbitLanguage.Persian, useLatinDigits = false)
        assertEquals("امروز", RelativeAhead.Today.text(resources, numbers))
        assertEquals("فردا", RelativeAhead.Tomorrow.text(resources, numbers))
        assertEquals("۵ روز دیگر", RelativeAhead.InDays(5).text(resources, numbers))
        assertEquals("دیروز", RelativeAgo.Yesterday.text(resources, numbers))
        assertEquals("۳۴ روز پیش", RelativeAgo.DaysAgo(34).text(resources, numbers))
        assertEquals("۲ ماه پیش", RelativeAgo.MonthsAgo(2).text(resources, numbers))
        assertEquals("۳ سال پیش", RelativeAgo.YearsAgo(3).text(resources, numbers))
    }

    @Test
    fun persianWithLatinDigits() {
        val numbers = OrbitNumberFormatter(OrbitLanguage.Persian, useLatinDigits = true)
        assertEquals("34 روز پیش", RelativeAgo.DaysAgo(34).text(resources("fa"), numbers))
    }

    private fun resources(qualifiers: String): Resources {
        RuntimeEnvironment.setQualifiers(qualifiers)
        return RuntimeEnvironment.getApplication().resources
    }
}
