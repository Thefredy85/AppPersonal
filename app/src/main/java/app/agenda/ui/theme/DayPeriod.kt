package app.agenda.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Momento del día de una hora: da color al cielo de los relojes, al saludo de Hoy y al icono de las tarjetas.
 * Paleta propia (no viene de ninguna app): amanecer coral, mediodía azul y miel, noche índigo.
 */
enum class DayPeriod(
    val label: String,
    val greeting: String,
    val skyTop: Color,
    val skyBottom: Color,
    val accent: Color,
    /** Texto que se lee bien sobre ese cielo. */
    val onSky: Color,
    val icon: ImageVector,
    val night: Boolean,
) {
    Madrugada("de madrugada", "Buenas noches", Color(0xFF080C26), Color(0xFF2B1D5E), Color(0xFF8FA8FF), Color(0xFFF2F0FF), Icons.Outlined.Bedtime, true),
    Manana("de la mañana", "Buenos días", Color(0xFFFF8A65), Color(0xFFFFD86B), Color(0xFFFFA94D), Color(0xFF2A1630), Icons.Outlined.WbTwilight, false),
    Tarde("de la tarde", "Buenas tardes", Color(0xFF3DA5F4), Color(0xFFFFCF70), Color(0xFFFFC23D), Color(0xFF0E1E3A), Icons.Outlined.WbSunny, false),
    Noche("de la noche", "Buenas noches", Color(0xFF111947), Color(0xFF5A2A8C), Color(0xFFB9A7FF), Color(0xFFF2F0FF), Icons.Outlined.NightsStay, true),
}

fun periodOf(minutes: Int): DayPeriod = when ((minutes.coerceIn(0, 1439)) / 60) {
    in 0..5 -> DayPeriod.Madrugada
    in 6..11 -> DayPeriod.Manana
    in 12..18 -> DayPeriod.Tarde
    else -> DayPeriod.Noche
}

fun periodOf(hhmm: String?): DayPeriod =
    periodOf(hhmm?.split(":")?.let { (it[0].toIntOrNull() ?: 8) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: 480)

/** Avance del sol (6:00–19:00) o de la luna (19:00–6:00) en su arco, de 0 a 1. */
private fun arcProgress(m: Int): Float = when {
    m in 360 until 1140 -> (m - 360) / 780f
    m >= 1140 -> (m - 1140) / 660f
    else -> (m + 300) / 660f
}

/**
 * Cielo dinámico: degradado del momento del día; el sol (con halo y rayos que giran despacio) o la luna
 * (creciente con estrellas que titilan) recorren su arco según la hora. Se usa dentro de los relojes y en los mosaicos.
 */
@Composable
fun SkyCanvas(minutes: Int, modifier: Modifier = Modifier, showBody: Boolean = true) {
    val level = LocalMotionLevel.current
    val period = periodOf(minutes)
    val top by animateColorAsState(period.skyTop, Motion.enter(level, Motion.Slow), label = "skyTop")
    val bottom by animateColorAsState(period.skyBottom, Motion.enter(level, Motion.Slow), label = "skyBottom")
    val progress by animateFloatAsState(arcProgress(minutes), Motion.light(level), label = "sunArc")
    val nightness by animateFloatAsState(if (period.night) 1f else 0f, Motion.enter(level, Motion.Slow), label = "night")
    val infinite = rememberInfiniteTransition(label = "sky")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rays")
    val twinkle by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse), label = "twinkle")
    val moving = level == MotionLevel.Full
    val stars = remember { List(16) { Triple(Random.nextFloat(), Random.nextFloat() * 0.7f, Random.nextFloat()) } }

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        if (nightness > 0f) stars.forEach { (x, y, phase) ->
            val tw = if (moving) (0.35f + 0.65f * ((twinkle + phase) % 1f)) else 0.8f
            drawCircle(Color.White.copy(alpha = nightness * tw * 0.9f), (1f + phase * 1.6f) * density, Offset(x * size.width, y * size.height))
        }
        if (!showBody) return@Canvas
        val p = progress.coerceIn(0f, 1f)
        val body = Offset(
            size.width * (0.18f + 0.64f * p),
            size.height * 0.70f - sin(PI * p).toFloat() * size.height * 0.42f,
        )
        val r = size.minDimension * 0.11f
        if (nightness < 1f) drawSun(body, r, if (moving) spin else 20f, 1f - nightness)
        if (nightness > 0f) drawMoon(body, r, lerp(top, bottom, 0.3f), nightness)
        // Horizonte suave
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, bottom.copy(alpha = 0.55f)), startY = size.height * 0.7f),
            topLeft = Offset(0f, size.height * 0.7f),
        )
    }
}

private fun DrawScope.drawSun(c: Offset, r: Float, spin: Float, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3B0).copy(alpha = 0.7f * alpha), Color.Transparent), c, r * 3.2f), r * 3.2f, c)
    for (i in 0 until 8) {
        val a = Math.toRadians((spin + i * 45f).toDouble())
        val s = Offset(c.x + (cos(a) * r * 1.45f).toFloat(), c.y + (sin(a) * r * 1.45f).toFloat())
        val e = Offset(c.x + (cos(a) * r * 1.95f).toFloat(), c.y + (sin(a) * r * 1.95f).toFloat())
        drawLine(Color(0xFFFFF1A8).copy(alpha = alpha), s, e, strokeWidth = r * 0.28f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
    drawCircle(Color(0xFFFFE066).copy(alpha = alpha), r, c)
}

private fun DrawScope.drawMoon(c: Offset, r: Float, sky: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFDCE3FF).copy(alpha = 0.35f * alpha), Color.Transparent), c, r * 3f), r * 3f, c)
    drawCircle(Color(0xFFF3F1E6).copy(alpha = alpha), r, c)
    drawCircle(sky.copy(alpha = alpha), r * 0.88f, Offset(c.x + r * 0.45f, c.y - r * 0.25f))
}
