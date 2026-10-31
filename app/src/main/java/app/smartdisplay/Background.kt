package app.smartdisplay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private val TWO_PI: Float = (2.0 * PI).toFloat()

/** Deterministischer Pseudo-Zufall pro Partikel: gleiche Eingabe, gleicher Wert. */
private fun hash(i: Int, k: Int): Float {
    val v = sin(i * 12.9898f + k * 78.233f) * 43758.5453f
    return v - floor(v)
}

/**
 * Animierter Hintergrund. Alle Bewegungen laufen ueber eine einzige Zeitgroesse t (0..1 alle 60 Sekunden)
 * und sind so gebaut, dass sie nahtlos wiederholen. Gelesen wird t nur beim Zeichnen,
 * damit die Oberflaeche nicht bei jedem Bild neu aufgebaut werden muss.
 */
@Composable
fun AmbientBackground(
    palette: Palette,
    season: Season,
    occasion: Occasion,
    sky: Sky,
    isDay: Boolean,
    dim: Float,
    modifier: Modifier = Modifier,
) {
    var prev by remember { mutableIntStateOf(0) }
    var cur by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(45_000)
            prev = cur
            cur = (cur + 1) % 3
            progress.snapTo(0f)
            progress.animateTo(1f, tween(7_000))
        }
    }

    val infinite = rememberInfiniteTransition(label = "background")
    val time = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )

    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        val p = progress.value
        val from = palette.gradients[prev % palette.gradients.size]
        val to = palette.gradients[cur % palette.gradients.size]
        drawRect(
            Brush.verticalGradient(
                listOf(lerp(from.first, to.first, p), lerp(from.second, to.second, p))
            )
        )

        val fx = 1f - 0.7f * dim // Effekte werden nachts deutlich leiser
        blobs(t, palette, fx)

        if (sky == Sky.CLEAR && isDay && dim < 0.5f) sunGlow(t, palette.accent, fx)

        val starAlpha = max(dim, if (!isDay) 0.8f else 0f) *
            (if (sky == Sky.CLEAR) 1f else if (sky == Sky.CLOUDY) 0.4f else 0f)
        if (starAlpha > 0.02f) stars(t, starAlpha)

        when (sky) {
            Sky.RAIN -> rain(t, 70, Color(0xFFBFD9F2), 0.9f * fx)
            Sky.STORM -> {
                rain(t, 110, Color(0xFFBFD9F2), 0.95f * fx)
                lightning(t, fx)
            }
            Sky.SNOW -> falling(t, 70, listOf(Color.White), 2.5f, 6f, 6, 28f, 0.9f * fx)
            Sky.FOG -> fog(t, fx)
            else -> Unit
        }

        // Anlass hat Vorrang vor der Jahreszeit
        when (occasion) {
            Occasion.HALLOWEEN -> {
                falling(t, 26, listOf(palette.accent, palette.accent2), 2f, 5f, 4, 18f, 0.75f * fx, up = true)
            }
            Occasion.CHRISTMAS -> {
                if (sky != Sky.SNOW) falling(t, 55, listOf(Color.White), 2f, 5.5f, 6, 24f, 0.85f * fx)
                falling(t, 12, listOf(palette.accent), 3f, 8f, 3, 10f, 0.4f * fx, up = true, seed = 99)
            }
            Occasion.NEWYEAR -> sparkles(t, 46, listOf(palette.accent, palette.accent2, Color.White), fx)
            Occasion.CARNIVAL -> {
                falling(
                    t, 44,
                    listOf(Color(0xFFFFE066), Color(0xFF5CE0D8), Color(0xFFFF6BB5), Color(0xFF8E7BFF)),
                    3f, 6f, 5, 30f, 0.9f * fx, oval = true,
                )
            }
            Occasion.EASTER -> {
                falling(t, 20, listOf(Color(0xFFFFF2A8), Color(0xFFF8BBD0), Color(0xFFB9E4C9)), 4f, 12f, 3, 14f, 0.35f * fx, up = true)
            }
            Occasion.VALENTINE -> {
                falling(t, 22, listOf(Color(0xFFFFB3C7), Color(0xFFFF6B8B)), 5f, 14f, 3, 14f, 0.3f * fx, up = true)
            }
            Occasion.NONE -> when (season) {
                Season.WINTER -> if (sky != Sky.SNOW) sparkles(t, 26, listOf(Color.White, palette.accent), fx * 0.8f)
                Season.SPRING -> if (sky != Sky.RAIN && sky != Sky.STORM) {
                    falling(t, 18, listOf(Color(0xFFF8BBD0), Color(0xFFFFFFFF)), 4f, 8f, 4, 40f, 0.7f * fx, oval = true)
                }
                Season.SUMMER -> if (sky == Sky.CLEAR) {
                    falling(t, 20, listOf(Color(0xFFFFF3B0)), 2f, 5f, 3, 12f, 0.35f * fx, up = true)
                }
                Season.AUTUMN -> if (sky != Sky.RAIN && sky != Sky.STORM) {
                    falling(
                        t, 16,
                        listOf(Color(0xFFE07A3F), Color(0xFFC8501E), Color(0xFFF2B33D), Color(0xFF9C4A1A)),
                        6f, 12f, 4, 60f, 0.9f * fx, oval = true,
                    )
                }
            }
        }
        Unit
    }
}

private fun DrawScope.blobs(t: Float, p: Palette, fx: Float) {
    for (b in 0 until 3) {
        val ph = b / 3f
        val cx = size.width * (0.5f + 0.38f * sin(TWO_PI * (t + ph)))
        val cy = size.height * (0.5f + 0.32f * sin(TWO_PI * (t * 2f + ph * 2f)))
        val rad = size.maxDimension * (0.45f + 0.08f * b)
        val col = if (b % 2 == 0) p.accent else p.accent2
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(col.copy(alpha = 0.16f * fx), Color.Transparent),
                center = Offset(cx, cy),
                radius = rad,
            ),
            radius = rad,
            center = Offset(cx, cy),
        )
    }
}

private fun DrawScope.sunGlow(t: Float, color: Color, fx: Float) {
    val c = Offset(size.width * 0.85f, size.height * 0.12f)
    val r = size.maxDimension * 0.7f * (1f + 0.03f * sin(TWO_PI * t * 3f))
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = 0.26f * fx), Color.Transparent), c, r),
        radius = r,
        center = c,
    )
}

private fun DrawScope.stars(t: Float, alpha: Float) {
    val u = 1.dp.toPx()
    for (i in 0 until 70) {
        val x = hash(i, 1) * size.width
        val y = hash(i, 2) * size.height * 0.75f
        val r = (0.6f + 1.6f * hash(i, 3)) * u
        val tw = 0.35f + 0.65f * abs(sin(TWO_PI * (t * (2 + i % 5) + hash(i, 4))))
        drawCircle(Color.White.copy(alpha = alpha * tw), r, Offset(x, y))
    }
}

private fun DrawScope.rain(t: Float, n: Int, color: Color, alpha: Float) {
    val u = 1.dp.toPx()
    for (i in 0 until n) {
        val x = hash(i, 1) * (size.width + 100f * u)
        val cyc = 38 + (i % 4) * 6
        val y = ((hash(i, 2) + t * cyc) % 1f) * (size.height + 120f * u) - 60f * u
        val len = (24f + hash(i, 3) * 28f) * u
        drawLine(
            color = color.copy(alpha = alpha * (0.25f + 0.45f * hash(i, 4))),
            start = Offset(x, y),
            end = Offset(x - len * 0.25f, y + len),
            strokeWidth = 1.2f * u,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.lightning(t: Float, fx: Float) {
    val a = max(0f, sin(TWO_PI * t * 7f)).pow(60) * 0.30f * fx
    val b = max(0f, sin(TWO_PI * (t * 5f + 0.3f))).pow(80) * 0.22f * fx
    val flash = max(a, b)
    if (flash > 0.01f) drawRect(Color.White.copy(alpha = flash))
}

private fun DrawScope.fog(t: Float, fx: Float) {
    for (i in 0 until 5) {
        val rw = size.width * (0.5f + 0.15f * hash(i, 1))
        val rh = size.height * 0.12f
        val x = ((hash(i, 2) + t) % 1f) * (size.width + 2 * rw) - rw
        val y = size.height * (0.15f + 0.7f * hash(i, 3))
        drawOval(
            Color.White.copy(alpha = 0.07f * fx),
            topLeft = Offset(x - rw / 2, y - rh / 2),
            size = Size(rw, rh),
        )
    }
}

private fun DrawScope.sparkles(t: Float, n: Int, colors: List<Color>, fx: Float) {
    val u = 1.dp.toPx()
    for (i in 0 until n) {
        val x = hash(i, 1) * size.width
        val y = hash(i, 2) * size.height
        val tw = max(0f, sin(TWO_PI * (t * (2 + i % 4) + hash(i, 3)))).pow(3)
        val r = (1.5f + 2.5f * hash(i, 4)) * u
        val c = colors[i % colors.size].copy(alpha = 0.85f * tw * fx)
        drawCircle(c, r, Offset(x, y))
        drawLine(c, Offset(x - r * 2.2f, y), Offset(x + r * 2.2f, y), strokeWidth = 0.8f * u, cap = StrokeCap.Round)
        drawLine(c, Offset(x, y - r * 2.2f), Offset(x, y + r * 2.2f), strokeWidth = 0.8f * u, cap = StrokeCap.Round)
    }
}

/**
 * Fallende (oder aufsteigende) Teilchen: Schnee, Blaetter, Konfetti, Funken.
 * [cycles] = ganze Durchlaeufe pro 60 Sekunden, damit die Schleife unsichtbar bleibt.
 */
private fun DrawScope.falling(
    t: Float,
    n: Int,
    colors: List<Color>,
    minR: Float,
    maxR: Float,
    cycles: Int,
    sway: Float,
    alpha: Float,
    oval: Boolean = false,
    up: Boolean = false,
    seed: Int = 0,
) {
    val u = 1.dp.toPx()
    for (i in 0 until n) {
        val k = i + seed
        val cyc = cycles + (k % 3)
        val frac = (hash(k, 2) + t * cyc) % 1f
        val pad = maxR * u
        val y = (if (up) 1f - frac else frac) * (size.height + 2 * pad) - pad
        val x = hash(k, 1) * size.width + sin(TWO_PI * (t * (1 + k % 2) + hash(k, 3))) * sway * u
        val r = (minR + hash(k, 4) * (maxR - minR)) * u
        val col = colors[k % colors.size].copy(alpha = alpha * (0.5f + 0.5f * hash(k, 5)))
        if (oval) {
            val angle = t * 360f * (1 + k % 3) + hash(k, 6) * 360f
            rotate(degrees = angle, pivot = Offset(x, y)) {
                drawOval(col, topLeft = Offset(x - r, y - r * 0.55f), size = Size(2f * r, 1.1f * r))
            }
        } else {
            drawCircle(col, r, Offset(x, y))
        }
    }
}
