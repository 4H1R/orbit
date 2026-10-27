package dev.alializadeh.orbit

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.alializadeh.orbit.ui.navigation.OrbitApp
import dev.alializadeh.orbit.ui.theme.OrbitTheme

/**
 * The single host activity. It's an [AppCompatActivity] because AppCompat per-app locales and
 * biometric 1.1.0 (App lock) both need one.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OrbitTheme {
                OrbitApp()
            }
        }
    }
}
