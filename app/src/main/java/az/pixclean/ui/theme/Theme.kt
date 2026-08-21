package az.pixclean.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The three states this app talks about — kept, similar, person — are not in Material's
 * scheme, so they live here as an explicit extra set rather than as literals at call sites.
 */
class PixColors(
    val keep: Color,
    val keepContainer: Color,
    val onKeepContainer: Color,
    val similar: Color,
    val similarContainer: Color,
    val onSimilarContainer: Color,
    val people: Color,
    val peopleContainer: Color,
    val onPeopleContainer: Color,
)

private val LightExtras = PixColors(
    keep = LightKeep, keepContainer = LightKeepContainer, onKeepContainer = LightOnKeepContainer,
    similar = LightSimilar, similarContainer = LightSimilarContainer, onSimilarContainer = LightOnSimilarContainer,
    people = LightPeople, peopleContainer = LightPeopleContainer, onPeopleContainer = LightOnPeopleContainer,
)

private val DarkExtras = PixColors(
    keep = DarkKeep, keepContainer = DarkKeepContainer, onKeepContainer = DarkOnKeepContainer,
    similar = DarkSimilar, similarContainer = DarkSimilarContainer, onSimilarContainer = DarkOnSimilarContainer,
    people = DarkPeople, peopleContainer = DarkPeopleContainer, onPeopleContainer = DarkOnPeopleContainer,
)

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightPrimaryContainer,
    onSecondaryContainer = LightOnPrimaryContainer,
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightPrimaryContainer,
    onTertiaryContainer = LightOnPrimaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerLow = LightSurface,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkPrimaryContainer,
    onSecondaryContainer = DarkOnPrimaryContainer,
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkPrimaryContainer,
    onTertiaryContainer = DarkOnPrimaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

private val LocalPixColors = staticCompositionLocalOf { LightExtras }

object PixTheme {
    val colors: PixColors
        @Composable @ReadOnlyComposable get() = LocalPixColors.current
}

/**
 * Dynamic colour is deliberately off: keep/similar/remove are meanings, and letting the
 * wallpaper repaint them would make green stop meaning "this is the copy you keep".
 */
@Composable
fun PixCleanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPixColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = PixTypography,
            content = content,
        )
    }
}
