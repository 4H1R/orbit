package dev.alializadeh.orbit

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.alializadeh.orbit.backup.Restorer
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OrbitApplication : Application() {

    @Inject lateinit var restorer: Provider<Restorer>

    override fun onCreate() {
        super.onCreate()
        val startedAt = System.currentTimeMillis()
        // A Restore that a crash interrupted is finished (or its copied photos taken back) first.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            restorer.get().recoverInterrupted(startedAt)
        }
    }
}
