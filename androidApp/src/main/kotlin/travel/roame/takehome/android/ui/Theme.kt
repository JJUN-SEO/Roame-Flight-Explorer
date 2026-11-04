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

/**
 * Colours Material 3 has no role for (UI_SPEC §6). Each seat class has an icon colour, Roame's own, and a text colour
 * that's deep enough to read as small text (4.5:1).
 */
@Immutable
data class ExtraColors(
    /** Roame's pink, for graphics: the route, the plane, logos and gradients. Text uses `primary`. */
    val brand: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val saved: Color,
    val economy: Color,
    val premiumEconomy: Color,
    val businessIcon: Color,
    val businessText: Color,
    val firstIcon: Color,
    val firstText: Color,
)

private val LightExtras = ExtraColors(
    brand = Color(0xFFEA4167),
    gradientStart = Color(0xFFF2BD93),
    gradientEnd = Color(0xFFDA7083),
    saved = Color(0xFFEA4167),
    economy = Color(0xFF312D2A),
    premiumEconomy = Color(0xFF007986),
    businessIcon = Color(0xFFEC9D26),
    businessText = Color(0xFFA65F00),
    firstIcon = Color(0xFFFF2765),
    firstText = Color(0xFFD81B50),
)

private val DarkExtras = ExtraColors(
    brand = Color(0xFFFF7A98),
    gradientStart = Color(0xFFC98E68),
    gradientEnd = Color(0xFFB4506A),
    saved = Color(0xFFFF7A98),
    economy = Color(0xFFE8E2DC),
    premiumEconomy = Color(0xFF4CC3CF),
    businessIcon = Color(0xFFF2B24A),
    businessText = Color(0xFFF2B24A),
    firstIcon = Color(0xFFFF6F93),
    firstText = Color(0xFFFF6F93),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtras }

/**
 * Roame's look (Step 6.3, owner's design): a pink accent on warm white, sampled from Roame's app. The light theme's
 * `primary` is a little deeper than Roame's #EA4167, so pink text such as Reset reads at 4.5:1.
 */
@Composable
fun RoameTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFFF7A98),
            onPrimary = Color(0xFF4A0016),
            primaryContainer = Color(0xFF5C1A2C),
            onPrimaryContainer = Color(0xFFFFD9E1),
            secondaryContainer = Color(0xFF4A2430),
            onSecondaryContainer = Color(0xFFFFD9E1),
            tertiaryContainer = Color(0xFF4D2E1F),
            onTertiaryContainer = Color(0xFFFFDCC7),
            background = Color(0xFF151312),
            surface = Color(0xFF151312),
            surfaceContainerLowest = Color(0xFF100E0D),
            surfaceContainerLow = Color(0xFF1A1817),
            surfaceContainer = Color(0xFF211E1C),
            surfaceContainerHigh = Color(0xFF2A2725),
            surfaceContainerHighest = Color(0xFF34302E),
            onSurface = Color(0xFFF2EEE9),
            onSurfaceVariant = Color(0xFFBDB6AF),
            outline = Color(0xFF5A5450),
            outlineVariant = Color(0xFF3A3532),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFD63A60),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFDE3E9),
            onPrimaryContainer = Color(0xFF5C0A22),
            secondaryContainer = Color(0xFFFCE4EA),
            onSecondaryContainer = Color(0xFF7A1233),
            tertiaryContainer = Color(0xFFFCE7D9),
            onTertiaryContainer = Color(0xFF5A2A12),
            background = Color(0xFFFEFCF6),
            surface = Color(0xFFFEFCF6),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFEFCF6),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color(0xFFF2F0EA),
            onSurface = Color(0xFF302D2A),
            onSurfaceVariant = Color(0xFF6B6661),
            outline = Color(0xFFD6D3CE),
            outlineVariant = Color(0xFFE8E5E0),
        )
    }
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
