package dev.alializadeh.orbit.lock

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Whether the phone has a secure screen lock (PIN, pattern or password; biometrics need one). */
fun Context.hasSecureScreenLock(): Boolean =
    getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/** How asking for the phone's screen lock ended. */
enum class ScreenLockResult {
    /** The user unlocked: fingerprint, face (strong) or the PIN, pattern or password. */
    Unlocked,

    /** The phone has no secure screen lock, so there was nothing to ask. */
    NoScreenLock,

    /** The user backed out of the prompt. */
    Cancelled,

    /** The prompt couldn't be shown or locked the user out. */
    Failed,
}

/**
 * The phone's own screen lock, as the system prompt: `BiometricPrompt` allowing
 * `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` (biometric 1.1.0), so the PIN, pattern or password always
 * works too. Orbit has no PIN of its own.
 *
 * Used to reveal or replace the Backup passphrase, and by App lock (the locked screen, turning it
 * on or off).
 */
class ScreenLockPrompt(private val activity: FragmentActivity) {

    val hasSecureScreenLock: Boolean get() = activity.hasSecureScreenLock()

    /** Shows the prompt with [title]; [onResult] runs on the main thread when it's over. */
    fun authenticate(title: String, subtitle: String? = null, onResult: (ScreenLockResult) -> Unit) {
        if (!hasSecureScreenLock) {
            onResult(ScreenLockResult.NoScreenLock)
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(ScreenLockResult.Unlocked)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED,
                        -> ScreenLockResult.Cancelled

                        else -> ScreenLockResult.Failed
                    },
                )
            }
            // onAuthenticationFailed is one rejected attempt; the prompt stays up for another.
        }
        // A system window on Android 12+: Orbit only pauses under it, so App lock needs no exemption.
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}

/**
 * Runs an action only after the phone's screen lock, when it has one; on a phone without one, the
 * action runs straight away.
 */
@Stable
class ScreenLockGate internal constructor(private val prompt: ScreenLockPrompt?) {

    fun require(title: String, onPassed: () -> Unit) {
        if (prompt == null) {
            onPassed()
            return
        }
        prompt.authenticate(title) { result ->
            if (result == ScreenLockResult.Unlocked || result == ScreenLockResult.NoScreenLock) onPassed()
        }
    }
}

/** A [ScreenLockGate] for the hosting activity (none in previews and screenshot tests). */
@Composable
fun rememberScreenLockGate(): ScreenLockGate {
    val context = LocalContext.current
    return remember(context) { ScreenLockGate(context.findFragmentActivity()?.let(::ScreenLockPrompt)) }
}

/** The system prompt for the hosting activity (none in previews and screenshot tests). */
@Composable
fun rememberScreenLockPrompt(): ScreenLockPrompt? {
    val context = LocalContext.current
    return remember(context) { context.findFragmentActivity()?.let(::ScreenLockPrompt) }
}
