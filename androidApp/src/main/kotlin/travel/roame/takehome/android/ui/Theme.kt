package travel.roame.takehome.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours Material 3 has no role for (UI_SPEC §6). */
@Immutable
data class ExtraColors(
    val saved: Color,
    val economy: Color,
    val premiumEconomy: Color,
    val business: Color,
)

private val LightExtras = ExtraColors(
    saved = Color(0xFFD81B60),
    economy = Color(0xFF616161),
    premiumEconomy = Color(0xFF00796B),
    business = Color(0xFF3949AB),
)

private val DarkExtras = ExtraColors(
    saved = Color(0xFFF48FB1),
    economy = Color(0xFFBDBDBD),
    premiumEconomy = Color(0xFF4DB6AC),
    business = Color(0xFF9FA8DA),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtras }

/** The accent is a deep blue on both platforms (UI_SPEC §6). */
@Composable
fun RoameTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(primary = Color(0xFFAFC6FF), onPrimary = Color(0xFF002D6C), primaryContainer = Color(0xFF16448F))
    } else {
        lightColorScheme(primary = Color(0xFF1E4FA3), onPrimary = Color.White, primaryContainer = Color(0xFFD9E2FF))
    }
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
