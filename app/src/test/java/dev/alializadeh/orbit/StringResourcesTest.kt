package dev.alializadeh.orbit

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * English and Persian stay in step (docs/copy-deck-v1.md): the same keys, the same placeholders in
 * the same order, the plural quantities each language needs, and no `%d` (numbers are formatted
 * by OrbitNumberFormatter and passed as `%s`).
 */
class StringResourcesTest {

    private val english = parse(File("src/main/res/values/strings.xml"))
    private val persian = parse(File("src/main/res/values-fa/strings.xml"))

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        assertEquals(english.keys.sorted(), persian.keys.sorted())
    }

    @Test
    fun placeholdersMatchInCountAndOrder() {
        val mismatches = english.keys.intersect(persian.keys).mapNotNull { key ->
            val en = english.getValue(key).placeholders()
            val fa = persian.getValue(key).placeholders()
            if (en == fa) null else "$key: en $en, fa $fa"
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun placeholdersArePositionalStrings() {
        val bad = (english.values + persian.values).flatMap { resource ->
            resource.placeholders().filterNot { it.matches(POSITIONAL_STRING) }.map { "${resource.key}: $it" }
        }
        assertTrue("Use %1\$s-style placeholders only, never %d:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun pluralsHaveEveryQuantityTheLanguageNeeds() {
        // CLDR: English needs one + other; Persian needs one (which includes 0) + other.
        val required = setOf("one", "other")
        val missing = listOf(english, persian).flatMap { resources ->
            resources.values.filter { it.plural != null }.mapNotNull { resource ->
                val absent = required - resource.plural!!.keys
                if (absent.isEmpty()) null else "${resource.key}: missing $absent"
            }
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun pluralsHaveTheSamePlaceholdersInEveryQuantity() {
        val bad = (english.values + persian.values).filter { it.plural != null }.mapNotNull { resource ->
            val perQuantity = resource.plural!!.mapValues { (_, text) -> placeholdersIn(text) }
            if (perQuantity.values.toSet().size <= 1) null else "${resource.key}: $perQuantity"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    private data class StringResource(
        val key: String,
        val text: String? = null,
        val plural: Map<String, String>? = null,
    ) {
        /** Placeholders of a string, or of a plural's "other" quantity. */
        fun placeholders(): List<String> = placeholdersIn(text ?: plural?.get("other").orEmpty())
    }

    private companion object {
        val PLACEHOLDER = Regex("""%(\d+[$])?[-#+ 0,(<]*\d*(\.\d+)?[a-zA-Z]""")
        val POSITIONAL_STRING = Regex("""%\d+[$]s""")

        fun placeholdersIn(text: String): List<String> =
            PLACEHOLDER.findAll(text.replace("%%", "")).map { it.value }.toList()

        /** Translatable strings and plurals, by key. */
        fun parse(file: File): Map<String, StringResource> {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val resources = mutableMapOf<String, StringResource>()
            val children = root.childNodes
            for (i in 0 until children.length) {
                val element = children.item(i) as? Element ?: continue
                if (element.getAttribute("translatable") == "false") continue
                val key = element.getAttribute("name")
                when (element.tagName) {
                    "string" -> resources[key] = StringResource(key, text = element.textContent)
                    "plurals" -> {
                        val items = element.getElementsByTagName("item")
                        val quantities = (0 until items.length).associate { index ->
                            val item = items.item(index) as Element
                            item.getAttribute("quantity") to item.textContent
                        }
                        resources[key] = StringResource(key, plural = quantities)
                    }
                    else -> error("Unexpected <${element.tagName}> in $file; extend this test for it")
                }
            }
            return resources
        }
    }
}
