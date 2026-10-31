package app.smartdisplay

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// ---------------------------------------------------------------------------
// Farben: Jahreszeit, Anlass, Wetter und Tageszeit bestimmen die Palette.
// Jede Palette hat genau drei Verlaeufe, zwischen denen der Hintergrund wechselt.
// ---------------------------------------------------------------------------

data class Palette(
    val gradients: List<Pair<Color, Color>>,
    val accent: Color,
    val accent2: Color,
    val onBg: Color,
)

private fun pal(grads: List<Pair<Long, Long>>, accent: Long, accent2: Long, onBg: Long = 0xFFF5F0EA): Palette =
    Palette(
        gradients = grads.map { Color(it.first) to Color(it.second) },
        accent = Color(accent),
        accent2 = Color(accent2),
        onBg = Color(onBg),
    )

private fun basePalette(season: Season, occasion: Occasion): Palette = when (occasion) {
    Occasion.HALLOWEEN -> pal(
        listOf(0xFF150A1F to 0xFF3A1B5C, 0xFF1F0F0A to 0xFF7A2E0E, 0xFF0E0A14 to 0xFF2B1A3D),
        0xFFFF8A2B, 0xFF9B6BFF,
    )
    Occasion.CHRISTMAS -> pal(
        listOf(0xFF0F2A1E to 0xFF1F5A3D, 0xFF3A0F14 to 0xFF7A1F2B, 0xFF10243A to 0xFF244B6B),
        0xFFFFD27A, 0xFFFF6B6B,
    )
    Occasion.NEWYEAR -> pal(
        listOf(0xFF0A0F2A to 0xFF1C2A66, 0xFF1A1030 to 0xFF4B2A7A, 0xFF0A1A2A to 0xFF16486B),
        0xFFFFD84D, 0xFFFF7AD1,
    )
    Occasion.CARNIVAL -> pal(
        listOf(0xFF3A0F3A to 0xFFB0307A, 0xFF0F2A4A to 0xFF2D7DD2, 0xFF4A3A0F to 0xFFE0B020),
        0xFFFFE066, 0xFF5CE0D8,
    )
    Occasion.EASTER -> pal(
        listOf(0xFF2A3A2A to 0xFF7FA878, 0xFF3A2F4A to 0xFFA58FC9, 0xFF4A3A2A to 0xFFE0B97A),
        0xFFFFF2A8, 0xFFF8BBD0,
    )
    Occasion.VALENTINE -> pal(
        listOf(0xFF3A0F24 to 0xFF8C2A52, 0xFF2A0F2A to 0xFF6A2A7A, 0xFF3A1414 to 0xFF9C3A3A),
        0xFFFFB3C7, 0xFFFF6B8B,
    )
    Occasion.NONE -> when (season) {
        Season.WINTER -> pal(
            listOf(0xFF1B2A41 to 0xFF324A5F, 0xFF22344F to 0xFF3E5C76, 0xFF233042 to 0xFF44546A),
            0xFFB8D8F0, 0xFFE3F2FD,
        )
        Season.SPRING -> pal(
            listOf(0xFF1F4D3A to 0xFF3C7A57, 0xFF2E5A4C to 0xFF6FA287, 0xFF3A4F2A to 0xFF86A55B),
            0xFFC8E6A0, 0xFFF8BBD0,
        )
        Season.SUMMER -> pal(
            listOf(0xFF0D3B66 to 0xFF2D7DD2, 0xFF14507A to 0xFF4FB0C6, 0xFF7A3B1D to 0xFFF4A259),
            0xFFFFE08A, 0xFFFFB085,
        )
        Season.AUTUMN -> pal(
            listOf(0xFF3B2314 to 0xFF8C4A1E, 0xFF4A2A1A to 0xFFB5651D, 0xFF2F2A25 to 0xFF7A5230),
            0xFFFFC27A, 0xFFE07A3F,
        )
    }
}

/** Nachtpalette: sehr dunkel, warm, ohne Blauanteil, damit sie beim Schlafen nicht stoert. */
private val nightPalette = pal(
    listOf(0xFF050403 to 0xFF120C08, 0xFF070403 to 0xFF150C07, 0xFF040304 to 0xFF100A0B),
    0xFFD09860, 0xFFA8734A, 0xFFB89C82,
)

private fun lerpPalette(a: Palette, b: Palette, f: Float): Palette = Palette(
    gradients = a.gradients.zip(b.gradients) { x, y ->
        lerp(x.first, y.first, f) to lerp(x.second, y.second, f)
    },
    accent = lerp(a.accent, b.accent, f),
    accent2 = lerp(a.accent2, b.accent2, f),
    onBg = lerp(a.onBg, b.onBg, f),
)

private fun withSky(p: Palette, sky: Sky): Palette {
    val grey = Color(0xFF2B3138)
    val f = when (sky) {
        Sky.CLEAR -> 0f
        Sky.SNOW -> 0.15f
        Sky.CLOUDY -> 0.35f
        Sky.FOG -> 0.45f
        Sky.RAIN -> 0.5f
        Sky.STORM -> 0.65f
    }
    if (f == 0f) return p
    return p.copy(gradients = p.gradients.map { lerp(it.first, grey, f) to lerp(it.second, grey, f) })
}

fun buildPalette(season: Season, occasion: Occasion, sky: Sky, dim: Float): Palette =
    lerpPalette(withSky(basePalette(season, occasion), sky), nightPalette, dim.coerceIn(0f, 1f))

fun colorSchemeOf(p: Palette): ColorScheme = darkColorScheme(
    primary = p.accent,
    onPrimary = Color(0xFF1A1208),
    secondary = p.accent2,
    onSecondary = Color(0xFF1A1208),
    tertiary = lerp(p.accent, p.accent2, 0.5f),
    onTertiary = Color(0xFF1A1208),
    background = p.gradients[0].second,
    onBackground = p.onBg,
    surface = p.gradients[0].second,
    onSurface = p.onBg,
    surfaceVariant = Color.White.copy(alpha = 0.10f),
    onSurfaceVariant = p.onBg.copy(alpha = 0.75f),
)

// ---------------------------------------------------------------------------
// Schrift: Google Sans Flex (variable Schrift), wird beim Bauen in assets/fonts/ gelegt.
// Fehlt die Datei, wird die Systemschrift verwendet.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTextApi::class)
fun loadFontFamily(context: Context): FontFamily {
    return try {
        val name = context.assets.list("fonts")?.firstOrNull { it.endsWith(".ttf", ignoreCase = true) }
            ?: return FontFamily.Default
        val path = "fonts/$name"
        Typeface.createFromAsset(context.assets, path) // Test: laesst sich die Datei lesen?
        val weights = listOf(200, 300, 400, 500, 600, 700)
        FontFamily(
            weights.map { w ->
                Font(
                    path,
                    context.assets,
                    FontWeight(w),
                    FontStyle.Normal,
                    FontVariation.Settings(FontWeight(w), FontStyle.Normal),
                )
            }
        )
    } catch (e: Throwable) {
        FontFamily.Default
    }
}

fun typographyOf(f: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = f),
        displayMedium = d.displayMedium.copy(fontFamily = f),
        displaySmall = d.displaySmall.copy(fontFamily = f),
        headlineLarge = d.headlineLarge.copy(fontFamily = f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f),
        headlineSmall = d.headlineSmall.copy(fontFamily = f),
        titleLarge = d.titleLarge.copy(fontFamily = f),
        titleMedium = d.titleMedium.copy(fontFamily = f),
        titleSmall = d.titleSmall.copy(fontFamily = f),
        bodyLarge = d.bodyLarge.copy(fontFamily = f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f),
        bodySmall = d.bodySmall.copy(fontFamily = f),
        labelLarge = d.labelLarge.copy(fontFamily = f),
        labelMedium = d.labelMedium.copy(fontFamily = f),
        labelSmall = d.labelSmall.copy(fontFamily = f),
    )
}
