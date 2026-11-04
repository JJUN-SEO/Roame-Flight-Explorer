package travel.roame.takehome.android.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** D39: the Account tab's Settings, the same three choices as on iOS. */
enum class Appearance { System, Light, Dark }

private const val PREFS = "settings"
private const val KEY = "appearance"

private fun Context.settings(): SharedPreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

/**
 * Loads the settings file on a background thread when the process starts. The first screen then reads it from memory:
 * reading it on the main thread is disk access, which StrictMode reports (PERF-2).
 */
fun preloadAppearance(context: Context) {
    Thread({ context.settings().getString(KEY, null) }, "preload-settings").start()
}

/** The saved choice. Setting it saves it, without blocking (`apply`). */
@Composable
fun rememberAppearance(): MutableState<Appearance> {
    val context = LocalContext.current.applicationContext
    val prefs = remember { context.settings() }
    val state = remember {
        val saved = prefs.getString(KEY, null)
        object : MutableState<Appearance> {
            private val inner = mutableStateOf(Appearance.entries.firstOrNull { it.name == saved } ?: Appearance.System)
            override var value: Appearance
                get() = inner.value
                set(choice) {
                    inner.value = choice
                    prefs.edit().putString(KEY, choice.name).apply()
                }
            override fun component1() = value
            override fun component2(): (Appearance) -> Unit = { value = it }
        }
    }
    return state
}

@Composable
fun Appearance.isDark(): Boolean = when (this) {
    Appearance.System -> isSystemInDarkTheme()
    Appearance.Light -> false
    Appearance.Dark -> true
}
