package dev.alializadeh.orbit.about

/**
 * The licence texts bundled in `assets/licences/`. Names and texts are legal documents, so they're
 * shown as they are, in English, in both UI languages.
 */
enum class Licence(val title: String, val asset: String) {
    Apache2("Apache License 2.0", "licences/apache-2.0.txt"),
    MitMaterialKolor("MIT License", "licences/mit-materialkolor.txt"),
    MitColormath("MIT License", "licences/mit-colormath.txt"),
    IscKotlinResult("ISC License", "licences/isc-kotlin-result.txt"),
    BouncyCastle("Bouncy Castle Licence", "licences/bouncy-castle.txt"),
    OflOutfit("SIL Open Font License 1.1", "licences/outfit-OFL.txt"),
    OflVazirmatn("SIL Open Font License 1.1", "licences/vazirmatn-OFL.txt"),
}

/**
 * Something Orbit ships that someone else made: a font, or the libraries of one project. [modules]
 * are the Maven coordinates it covers, `group:name` with `*` matching anything; `LicencesTest`
 * fails when the release build ships a module that no entry covers, or an entry covers nothing.
 */
data class OpenSourceItem(
    val name: String,
    val licences: List<Licence>,
    val modules: List<String> = emptyList(),
)

object OpenSourceLicences {

    val fonts: List<OpenSourceItem> = listOf(
        OpenSourceItem("Outfit", listOf(Licence.OflOutfit)),
        OpenSourceItem("Vazirmatn", listOf(Licence.OflVazirmatn)),
    )

    /** Every third-party library in the release APK, by project, alphabetically. */
    val libraries: List<OpenSourceItem> = listOf(
        OpenSourceItem("Accompanist", listOf(Licence.Apache2), listOf("com.google.accompanist:*")),
        OpenSourceItem("Android Jetpack (AndroidX)", listOf(Licence.Apache2), listOf("androidx.*:*")),
        OpenSourceItem("Bouncy Castle", listOf(Licence.BouncyCastle), listOf("org.bouncycastle:*")),
        OpenSourceItem("Coil", listOf(Licence.Apache2), listOf("io.coil-kt.coil3:*")),
        OpenSourceItem("Colormath", listOf(Licence.MitColormath), listOf("com.github.ajalt.colormath:*")),
        OpenSourceItem(
            "Compose Multiplatform",
            listOf(Licence.Apache2),
            listOf("org.jetbrains.compose.*:*", "org.jetbrains.androidx.*:*"),
        ),
        OpenSourceItem("Dagger and Hilt", listOf(Licence.Apache2), listOf("com.google.dagger:*")),
        OpenSourceItem("Guava: ListenableFuture", listOf(Licence.Apache2), listOf("com.google.guava:listenablefuture")),
        OpenSourceItem("HKDF", listOf(Licence.Apache2), listOf("at.favre.lib:hkdf")),
        OpenSourceItem("Jakarta Dependency Injection", listOf(Licence.Apache2), listOf("jakarta.inject:*")),
        OpenSourceItem("javax.inject", listOf(Licence.Apache2), listOf("javax.inject:*")),
        OpenSourceItem("JetBrains Java Annotations", listOf(Licence.Apache2), listOf("org.jetbrains:annotations")),
        OpenSourceItem("JSpecify", listOf(Licence.Apache2), listOf("org.jspecify:*")),
        OpenSourceItem("JSR 305 annotations", listOf(Licence.Apache2), listOf("com.google.code.findbugs:jsr305")),
        OpenSourceItem("kage", listOf(Licence.Apache2), listOf("com.github.android-password-store:kage")),
        OpenSourceItem("kotlin-result", listOf(Licence.IscKotlinResult), listOf("com.michael-bull.kotlin-result:*")),
        OpenSourceItem("Kotlin", listOf(Licence.Apache2), listOf("org.jetbrains.kotlin:*")),
        OpenSourceItem("kotlinx.coroutines", listOf(Licence.Apache2), listOf("org.jetbrains.kotlinx:kotlinx-coroutines-*")),
        OpenSourceItem("kotlinx.serialization", listOf(Licence.Apache2), listOf("org.jetbrains.kotlinx:kotlinx-serialization-*")),
        // MaterialKolor is MIT; its port of Google's material-color-utilities keeps Apache 2.0 too.
        OpenSourceItem("MaterialKolor", listOf(Licence.MitMaterialKolor, Licence.Apache2), listOf("com.materialkolor:*")),
        OpenSourceItem("Okio", listOf(Licence.Apache2), listOf("com.squareup.okio:*")),
        OpenSourceItem("Poko", listOf(Licence.Apache2), listOf("dev.drewhamilton.poko:*")),
    )

    /** Whether [item] covers the module [group]:[name]. */
    fun covers(item: OpenSourceItem, group: String, name: String): Boolean = item.modules.any { pattern ->
        val (patternGroup, patternName) = pattern.split(':', limit = 2)
        matches(patternGroup, group) && matches(patternName, name)
    }

    private fun matches(pattern: String, value: String): Boolean =
        Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) }).matches(value)
}
