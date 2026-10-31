package app.smartdisplay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TWO_PI_F: Float = (2.0 * PI).toFloat()

/** Selbst gezeichnete, animierte Wetter-Symbole (ohne Emojis und ohne Bilddateien). */
@Composable
fun WeatherIcon(sky: Sky, isDay: Boolean, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "weatherIcon")
    val time = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
        label = "iconTime",
    )
    Canvas(modifier) {
        val t = time.value
        val s = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val bob = sin(TWO_PI_F * t * 2f) * s * 0.015f
        when (sky) {
            Sky.CLEAR -> if (isDay) sunIcon(c, s * 0.26f, t) else moonIcon(c, s * 0.32f)
            Sky.CLOUDY -> {
                if (isDay) sunIcon(Offset(c.x - s * 0.14f, c.y - s * 0.16f), s * 0.17f, t)
                else moonIcon(Offset(c.x - s * 0.14f, c.y - s * 0.16f), s * 0.2f)
                cloudIcon(Offset(c.x + s * 0.04f, c.y + s * 0.08f + bob), s * 0.66f, Color(0xFFE8EEF4))
            }
            Sky.FOG -> fogIcon(c, s, t)
            Sky.RAIN -> {
                val cc = Offset(c.x, c.y - s * 0.1f + bob)
                cloudIcon(cc, s * 0.66f, Color(0xFFB8C6D4))
                for (i in 0 until 3) {
                    val f = (t * 4f + i / 3f) % 1f
                    val x = c.x + (i - 1) * s * 0.17f
                    val y0 = c.y + s * 0.14f
                    drawLine(
                        Color(0xFF7FC4FF).copy(alpha = 1f - f * 0.7f),
                        Offset(x, y0 + f * s * 0.3f),
                        Offset(x - s * 0.03f, y0 + f * s * 0.3f + s * 0.09f),
                        strokeWidth = s * 0.03f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Sky.SNOW -> {
                val cc = Offset(c.x, c.y - s * 0.1f + bob)
                cloudIcon(cc, s * 0.66f, Color(0xFFDDE6EE))
                for (i in 0 until 4) {
                    val f = (t * 2f + i / 4f) % 1f
                    val x = c.x + (i - 1.5f) * s * 0.14f + sin(TWO_PI_F * (t * 2f + i)) * s * 0.02f
                    drawCircle(Color.White.copy(alpha = 1f - f * 0.6f), s * 0.025f, Offset(x, c.y + s * 0.14f + f * s * 0.3f))
                }
            }
            Sky.STORM -> {
                val cc = Offset(c.x, c.y - s * 0.12f + bob)
                cloudIcon(cc, s * 0.66f, Color(0xFF8E9BAA))
                val flick = if ((t * 4f) % 1f < 0.25f) 1f else 0.55f
                val bolt = Path().apply {
                    moveTo(c.x + s * 0.03f, c.y + s * 0.08f)
                    lineTo(c.x - s * 0.08f, c.y + s * 0.26f)
                    lineTo(c.x - s * 0.01f, c.y + s * 0.26f)
                    lineTo(c.x - s * 0.06f, c.y + s * 0.42f)
                    lineTo(c.x + s * 0.1f, c.y + s * 0.2f)
                    lineTo(c.x + s * 0.02f, c.y + s * 0.2f)
                    close()
                }
                drawPath(bolt, Color(0xFFFFD54F).copy(alpha = flick))
            }
        }
    }
}

private fun DrawScope.sunIcon(c: Offset, r: Float, t: Float) {
    val sun = Color(0xFFFFD54F)
    rotate(degrees = t * 360f, pivot = c) {
        for (i in 0 until 8) {
            val a = i * (TWO_PI_F / 8f)
            drawLine(
                sun,
                Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 1.35f),
                Offset(c.x + cos(a) * r * 1.75f, c.y + sin(a) * r * 1.75f),
                strokeWidth = r * 0.2f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(sun, r, c)
}

private fun DrawScope.moonIcon(c: Offset, r: Float) {
    val a = Path().apply { addOval(Rect(center = c, radius = r)) }
    val b = Path().apply { addOval(Rect(center = Offset(c.x + r * 0.5f, c.y - r * 0.3f), radius = r * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, a, b), Color(0xFFFFF3C4))
}

private fun DrawScope.cloudIcon(c: Offset, w: Float, color: Color) {
    val h = w * 0.55f
    drawCircle(color, w * 0.22f, Offset(c.x - w * 0.18f, c.y))
    drawCircle(color, w * 0.28f, Offset(c.x + w * 0.04f, c.y - h * 0.2f))
    drawCircle(color, w * 0.2f, Offset(c.x + w * 0.27f, c.y + h * 0.04f))
    drawRoundRect(
        color,
        topLeft = Offset(c.x - w * 0.4f, c.y),
        size = Size(w * 0.8f, h * 0.34f),
        cornerRadius = CornerRadius(h * 0.17f),
    )
}

private fun DrawScope.fogIcon(c: Offset, s: Float, t: Float) {
    for (i in 0 until 4) {
        val shift = sin(TWO_PI_F * (t + i * 0.25f)) * s * 0.05f
        val w = s * (0.55f - 0.06f * (i % 2))
        drawRoundRect(
            Color(0xFFD5DEE6).copy(alpha = 0.9f),
            topLeft = Offset(c.x - w / 2f + shift, c.y - s * 0.22f + i * s * 0.14f),
            size = Size(w, s * 0.06f),
            cornerRadius = CornerRadius(s * 0.03f),
        )
    }
}
