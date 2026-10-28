package dev.alializadeh.orbit.reminders

import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alializadeh.orbit.ApplicationScope
import dev.alializadeh.orbit.backup.RestoreListener
import dev.alializadeh.orbit.data.OrbitDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * The in-process triggers of `reconcile()`: app start, every data edit and preference change (the
 * data version moves with each; the "last notified as Due" marker doesn't), a change of how
 * notifications render (App lock's settings), and [request] (the activity starting, a settings
 * screen coming back). Bursts are debounced into one run. The
 * receivers and Restore call the reconciler themselves.
 */
@Singleton
class ReminderTriggers @Inject constructor(
    private val reconciler: ReminderReconciler,
    private val database: OrbitDatabase,
    private val rendering: NotificationRenderingPolicy,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            val edits = database.backupStatusDao().observeDataVersion().distinctUntilChanged().map { }
            // App lock's settings changed how notifications render: re-render what's showing.
            val renderings = rendering.changes().distinctUntilChanged().drop(1).map { }
            merge(edits, renderings, requests).debounce(DEBOUNCE_MILLIS).collect {
                try {
                    reconciler.reconcile()
                } catch (e: Exception) {
                    Log.w("OrbitReminders", "Reconcile failed", e)
                }
            }
        }
    }

    fun request() {
        requests.tryEmit(Unit)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}

/** After a Restore: the restored data's Reminders and Due People (PRD: swap, reconcile, Upcoming). */
class ReminderRestoreListener @Inject constructor(private val reconciler: ReminderReconciler) : RestoreListener {
    override suspend fun onRestored() {
        reconciler.reconcile()
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface ReminderBindings {

    @Binds
    fun bindNotifier(notifier: AndroidReminderNotifier): ReminderNotifier

    @Binds
    fun bindAlarm(alarm: AndroidReminderAlarm): ReminderAlarm

    @Binds
    @IntoSet
    fun bindRestoreListener(listener: ReminderRestoreListener): RestoreListener

    @Binds
    fun bindHealthChecker(checker: AndroidReminderHealthChecker): ReminderHealthChecker

    @Binds
    fun bindPermissionAsks(asks: DefaultReminderPermissionAsks): ReminderPermissionAsks
}

