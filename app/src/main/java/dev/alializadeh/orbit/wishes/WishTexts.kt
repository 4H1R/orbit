package dev.alializadeh.orbit.wishes

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.ImportantDateType
import java.util.Locale
import javax.inject.Inject

/** The default Wishes, in either language whatever the UI's: a Person may be written to in the other one. */
fun interface WishTexts {
    /** The default message for [kind] in [language], greeting [name]. */
    fun defaultText(kind: WishKind, language: OrbitLanguage, name: String): String
}

class AndroidWishTexts @Inject constructor(@ApplicationContext private val context: Context) : WishTexts {

    override fun defaultText(kind: WishKind, language: OrbitLanguage, name: String): String =
        resources(language).getString(kind.textRes, name)

    private fun resources(language: OrbitLanguage): Resources {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
        return context.createConfigurationContext(configuration).resources
    }
}

private val WishKind.textRes: Int
    get() = when (this) {
        is WishKind.Date -> when (type) {
            ImportantDateType.Birthday -> R.string.wishes_default_birthday
            ImportantDateType.Anniversary -> R.string.wishes_default_anniversary
            ImportantDateType.Custom -> R.string.wishes_default_custom
        }
    }

@Module
@InstallIn(SingletonComponent::class)
interface WishesModule {

    @Binds
    fun bindWishTexts(texts: AndroidWishTexts): WishTexts
}
