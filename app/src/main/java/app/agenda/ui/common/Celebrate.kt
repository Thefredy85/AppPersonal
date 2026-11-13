package app.agenda.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.agenda.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Confetti(val angle: Float, val speed: Float, val spin: Float, val size: Float, val color: Color, val round: Boolean)

/**
 * "¡Felicidades!" al marcar algo como hecho.
 * Primario: tarjeta que hace pop y la palomita se dibuja. Secundario: anillo que se expande.
 * Ambiente: confeti con gravedad y giro. No bloquea los toques; se va solo en ~1,8 s.
 */
@Composable
fun DoneCelebration(type: String, title: String, stamp: Long, onDone: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val color = typeColor(type)
    val full = level == MotionLevel.Full

    val pop = remember(stamp) { Animatable(if (level == MotionLevel.Off) 1f else 0.6f) }
    val draw = remember(stamp) { Animatable(if (level == MotionLevel.Off) 1f else 0f) }
    val burst = remember(stamp) { Animatable(0f) }
    val fade = remember(stamp) { Animatable(1f) }
    val confetti = remember(stamp) {
        val palette = listOf(color, Color(0xFFFFE066), Color(0xFF34E5B4), Color(0xFFA78BFA), Color(0xFFFF8FB1), Color(0xFF6EC8FF))
        List(48) {
            Confetti(
                angle = (-160f + Random.nextFloat() * 140f) * (Math.PI / 180f).toFloat(),
                speed = 260f + Random.nextFloat() * 420f,
                spin = (Random.nextFloat() - 0.5f) * 900f,
                size = 5f + Random.nextFloat() * 6f,
                color = palette[it % palette.size],
                round = it % 3 == 0,
            )
        }
    }
    LaunchedEffect(stamp) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (level != MotionLevel.Off) {
            launch { pop.animateTo(1f, Motion.springLight()) }
            launch { delay(120); draw.animateTo(1f, tween(320, easing = Motion.EaseEmphasized)) }
            if (full) launch { burst.animateTo(1f, tween(1500, easing = LinearEasing)) }
            delay(260); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        delay(1250)
        fade.animateTo(0f, tween(300))
        onDone()
    }

    val headline = when (type) { "exam" -> "¡Examen hecho!"; "reminder" -> "¡Listo!"; else -> "¡Felicidades!" }
    val sub = when (type) { "exam" -> "Uno menos, ¡bien hecho!"; "reminder" -> "Recordatorio completado"; else -> "Tarea completada" }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.35f)))
        if (full) Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            val origin = Offset(size.width / 2f, size.height / 2f - 40.dp.toPx())
            confetti.forEach { p ->
                val d = p.speed * density * 0.6f * t
                val x = origin.x + cos(p.angle) * d
                val y = origin.y + sin(p.angle) * d + 520f * density * 0.5f * t * t
                val a = (1f - t).coerceIn(0f, 1f)
                val s = p.size * density
                if (p.round) drawCircle(p.color.copy(alpha = a), s / 2f, Offset(x, y))
                else rotate(p.spin * t, Offset(x, y)) {
                    drawRect(p.color.copy(alpha = a), Offset(x - s / 2f, y - s), Size(s, s * 2f))
                }
            }
        }
        Column(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .widthIn(max = 300.dp).clip(RoundedCornerShape(32.dp)).background(c.surfaceHigh)
                .padding(horizontal = Space.xxl, vertical = Space.xl)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    // Anillo que se expande
                    if (full && burst.value < 0.5f) {
                        val k = burst.value / 0.5f
                        drawCircle(color.copy(alpha = 1f - k), size.minDimension / 2f * (0.7f + 0.5f * k), style = Stroke(3.dp.toPx()))
                    }
                    drawCircle(color, size.minDimension / 2f * 0.72f)
                    val p = Path().apply {
                        moveTo(size.width * 0.33f, size.height * 0.52f)
                        lineTo(size.width * 0.45f, size.height * 0.64f)
                        lineTo(size.width * 0.68f, size.height * 0.38f)
                    }
                    val pm = PathMeasure().apply { setPath(p, false) }
                    val seg = Path(); pm.getSegment(0f, pm.length * draw.value, seg, true)
                    drawPath(seg, onSubjectColor(color), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.height(Space.md))
            Text(headline, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = c.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = color)
            Spacer(Modifier.height(Space.xs))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

