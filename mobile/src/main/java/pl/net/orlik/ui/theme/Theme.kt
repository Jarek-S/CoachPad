package pl.net.orlik.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Green,
    onPrimary = Ink,
    primaryContainer = GreenLight,
    onPrimaryContainer = GreenDark,
    secondaryContainer = GreenPale,
    onSecondaryContainer = GreenDark,
    background = Ground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Ground,
    onSurfaceVariant = InkMuted,
    outline = Outline,
)

/**
 * Light only for now (the review colors are designed on a light ground), and fixed colors
 * instead of wallpaper-based dynamic color, so the green stays recognisable.
 */
@Composable
fun MatchNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content,
    )
}
