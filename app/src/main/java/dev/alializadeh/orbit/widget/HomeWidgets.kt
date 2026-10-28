package dev.alializadeh.orbit.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * The home-screen widgets, as `reconcile()` sees them: it redraws them after every run (data,
 * Restore, App lock, the alarm), and wakes at midnight while one is placed so its day turns over.
 * Faked in tests.
 */
interface HomeWidgets {
    suspend fun isPlaced(): Boolean

    suspend fun refresh()
}

class GlanceHomeWidgets @Inject constructor(@ApplicationContext private val context: Context) : HomeWidgets {

    override suspend fun isPlaced(): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(UpcomingWidget::class.java).isNotEmpty()

    override suspend fun refresh() {
        UpcomingWidget().updateAll(context)
    }
}

/** No widgets (tests, and anything that doesn't draw them). */
object NoHomeWidgets : HomeWidgets {
    override suspend fun isPlaced(): Boolean = false

    override suspend fun refresh() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
interface HomeWidgetsModule {

    @Binds
    fun bindHomeWidgets(widgets: GlanceHomeWidgets): HomeWidgets
}
