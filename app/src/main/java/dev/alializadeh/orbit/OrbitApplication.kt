package dev.alializadeh.orbit

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import dev.alializadeh.orbit.backup.Restorer
import dev.alializadeh.orbit.lock.AppLock
import dev.alializadeh.orbit.reminders.ReminderTriggers
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OrbitApplication : Application() {

    @Inject lateinit var restorer: Provider<Restorer>

    @Inject lateinit var reminderTriggers: Provider<ReminderTriggers>

    @Inject lateinit var appLock: Provider<AppLock>

    override fun onCreate() {
        super.onCreate()
        val startedAt = System.currentTimeMillis()
        // A Restore that a crash interrupted is finished (or its copied photos taken back) first.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            restorer.get().recoverInterrupted(startedAt)
        }
        // Reminders reconcile at app start and after every data edit. Robolectric tests start this
        // Application too; they test the reconciler directly instead.
        if (Build.FINGERPRINT != ROBOLECTRIC) {
            reminderTriggers.get().start()
            // App lock starts locked in a new process: a cold start always asks.
            appLock.get().start()
        }
    }

    private companion object {
        const val ROBOLECTRIC = "robolectric"
    }
}
