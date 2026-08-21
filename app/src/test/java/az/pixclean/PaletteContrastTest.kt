package az.pixclean

import androidx.compose.ui.graphics.Color
import az.pixclean.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Contrast is a number, not a matter of taste. Every foreground/background pair the UI
 * actually renders is checked against WCAG AA here, so a palette tweak that looks fine on
 * the author's screen cannot quietly ship something unreadable.
 */
class PaletteContrastTest {

    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color) =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(fg: Color, bg: Color): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    private class Pair(val name: String, val fg: Color, val bg: Color)

    private fun pairsFor(
        prefix: String,
        surface: Color, background: Color, container: Color, variant: Color,
        onSurface: Color, onSurfaceVariant: Color,
        primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
        error: Color, onError: Color, errorContainer: Color, onErrorContainer: Color,
        keep: Color, keepContainer: Color, onKeepContainer: Color,
        similar: Color, similarContainer: Color, onSimilarContainer: Color,
        people: Color, peopleContainer: Color, onPeopleContainer: Color,
    ): List<Pair> {
        val backgrounds = listOf(
            "surface" to surface, "background" to background,
            "surfaceContainer" to container, "surfaceVariant" to variant,
        )
        val foregrounds = listOf(
            "onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant,
            "primary" to primary, "error" to error,
            "keep" to keep, "similar" to similar, "people" to people,
        )
        val out = ArrayList<Pair>()
        for ((fn, fc) in foregrounds) for ((bn, bc) in backgrounds) {
            out.add(Pair("$prefix $fn on $bn", fc, bc))
        }
        out.add(Pair("$prefix onPrimary on primary", onPrimary, primary))
        out.add(Pair("$prefix onError on error", onError, error))
        out.add(Pair("$prefix onPrimaryContainer on primaryContainer", onPrimaryContainer, primaryContainer))
        out.add(Pair("$prefix onErrorContainer on errorContainer", onErrorContainer, errorContainer))
        out.add(Pair("$prefix onKeepContainer on keepContainer", onKeepContainer, keepContainer))
        out.add(Pair("$prefix onSimilarContainer on similarContainer", onSimilarContainer, similarContainer))
        out.add(Pair("$prefix onPeopleContainer on peopleContainer", onPeopleContainer, peopleContainer))
        return out
    }

    private val allPairs: List<Pair> =
        pairsFor(
            "light",
            LightSurface, LightBackground, LightSurfaceContainer, LightSurfaceVariant,
            LightOnSurface, LightOnSurfaceVariant,
            LightPrimary, LightOnPrimary, LightPrimaryContainer, LightOnPrimaryContainer,
            LightError, LightOnError, LightErrorContainer, LightOnErrorContainer,
            LightKeep, LightKeepContainer, LightOnKeepContainer,
            LightSimilar, LightSimilarContainer, LightOnSimilarContainer,
            LightPeople, LightPeopleContainer, LightOnPeopleContainer,
        ) + pairsFor(
            "dark",
            DarkSurface, DarkBackground, DarkSurfaceContainer, DarkSurfaceVariant,
            DarkOnSurface, DarkOnSurfaceVariant,
            DarkPrimary, DarkOnPrimary, DarkPrimaryContainer, DarkOnPrimaryContainer,
            DarkError, DarkOnError, DarkErrorContainer, DarkOnErrorContainer,
            DarkKeep, DarkKeepContainer, DarkOnKeepContainer,
            DarkSimilar, DarkSimilarContainer, DarkOnSimilarContainer,
            DarkPeople, DarkPeopleContainer, DarkOnPeopleContainer,
        )

    @Test
    fun `every rendered pair meets WCAG AA for body text`() {
        val failures = allPairs
            .map { it to ratio(it.fg, it.bg) }
            .filter { it.second < 4.5 }
            .map { "%s = %.2f".format(it.first.name, it.second) }
        assertTrue("below 4.5:1 —\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `white on the photo scrim stays readable over a white photo`() {
        // Worst case for the overlay controls: a fully white picture behind a 0xCC scrim.
        val alpha = 0xCC / 255f
        val scrim = Color(0xFF101014)
        val composited = Color(
            red = scrim.red * alpha + 1f * (1 - alpha),
            green = scrim.green * alpha + 1f * (1 - alpha),
            blue = scrim.blue * alpha + 1f * (1 - alpha),
        )
        val r = ratio(Color.White, composited)
        assertTrue("scrim contrast %.2f".format(r), r >= 4.5)
    }
}
