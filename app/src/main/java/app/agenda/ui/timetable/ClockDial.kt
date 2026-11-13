package app.agenda.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.agenda.ui.theme.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Paso del arrastre en el reloj: media hora. */
const val SNAP = 30

fun toMinutes(hhmm: String): Int = hhmm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
fun toHhmm(m: Int): String = "%02d:%02d".format(m.coerceIn(0, 1439) / 60, m.coerceIn(0, 1439) % 60)

fun durationLabel(min: Int): String {
    val h = min / 60; val m = min % 60
    return listOfNotNull(if (h > 0) "$h h" else null, if (m > 0) "$m min" else null).joinToString(" ").ifEmpty { "0 min" }
}

/**
 * Reloj propio de 24 horas: dos puntos (inicio y fin) sobre un anillo.
 * El arco entre ambos se pinta del color de la clase. Al arrastrar, salta de media en media hora (vibración ligera).
 * Los botones ± afinan de 5 en 5 min para horas exactas (p. ej. 10:15).
 * Mover el inicio arrastra la clase entera (misma duración); mover el fin cambia la duración (mín. 30 min).
 */
@Composable
fun ClockDial(
    start: Int,
    end: Int,
    color: Color,
    onChange: (start: Int, end: Int) -> Unit,
    modifier: Modifier = Modifier,
    busy: List<Pair<Int, Int>> = emptyList(),
) {
    val c = Agenda.colors
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val level = LocalMotionLevel.current
    var dragging by remember { mutableStateOf<Int?>(null) } // 0 = inicio, 1 = fin
    val startScale by animateFloatAsState(if (dragging == 0) 1.35f else 1f, Motion.light(level), label = "h1")
    val endScale by animateFloatAsState(if (dragging == 1) 1.35f else 1f, Motion.light(level), label = "h2")
    val latest by rememberUpdatedState(start to end)
    val onChangeLatest by rememberUpdatedState(onChange)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize()
                    .semantics { contentDescription = "Reloj: de ${toHhmm(start)} a ${toHhmm(end)}" }
                    .pointerInput(Unit) {
                        fun minuteAt(p: Offset): Int {
                            val x = p.x - size.width / 2f; val y = p.y - size.height / 2f
                            var a = atan2(x, -y); if (a < 0) a += (2 * PI).toFloat()
                            return ((a / (2 * PI) * 1440 / SNAP).roundToInt() * SNAP) % 1440
                        }
                        fun handlePos(m: Int): Offset {
                            val r = size.width * 0.40f; val a = m / 1440f * 2 * PI
                            return Offset(size.width / 2f + (r * sin(a)).toFloat(), size.height / 2f - (r * cos(a)).toFloat())
                        }
                        detectDragGestures(
                            onDragStart = { p ->
                                val (s, e) = latest
                                val ds = (p - handlePos(s)).getDistance(); val de = (p - handlePos(e)).getDistance()
                                dragging = if (ds <= de) 0 else 1
                            },
                            onDragEnd = { dragging = null },
                            onDragCancel = { dragging = null },
                            onDrag = { change, _ ->
                                val (s, e) = latest
                                val m = minuteAt(change.position)
                                val (ns, ne) = if (dragging == 0) {
                                    val dur = e - s
                                    val st = m.coerceAtMost(1440 - SNAP - dur.coerceAtLeast(0)); st to st + dur
                                } else {
                                    s to (if (m <= s) s + SNAP else m).coerceAtMost(1435)
                                }
                                if (ns != s || ne != e) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onChangeLatest(ns, ne)
                                }
                            },
                        )
                    }
            ) {
                val ring = size.width * 0.40f
                val stroke = 22.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                val topLeft = Offset(center.x - ring, center.y - ring)
                val arcSize = Size(ring * 2, ring * 2)
                // Pista
                drawCircle(c.surfaceHigh, ring, center, style = Stroke(stroke))
                // Marcas de hora (0, 6, 12, 18 con número)
                for (h in 0 until 24) {
                    val a = h / 24.0 * 2 * PI
                    val inner = ring - stroke / 2 - 10.dp.toPx()
                    val p = Offset(center.x + (inner * sin(a)).toFloat(), center.y - (inner * cos(a)).toFloat())
                    if (h % 6 == 0) {
                        val t = measurer.measure("$h", TextStyle(color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        drawText(t, topLeft = Offset(p.x - t.size.width / 2f, p.y - t.size.height / 2f))
                    } else {
                        drawCircle(c.textMuted.copy(alpha = 0.6f), 1.5.dp.toPx(), p)
                    }
                }
                // Horas ya ocupadas esos días (gris rayado de huecos)
                busy.forEach { (bs, be) ->
                    drawArc(
                        color = c.textMuted.copy(alpha = 0.45f), startAngle = bs / 1440f * 360f - 90f, sweepAngle = (be - bs) / 1440f * 360f,
                        useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke * 0.55f, cap = StrokeCap.Butt),
                    )
                }
                // Arco de la clase
                drawArc(
                    color = color, startAngle = start / 1440f * 360f - 90f, sweepAngle = (end - start) / 1440f * 360f,
                    useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
                // Puntos
                fun dot(m: Int, scale: Float) {
                    val a = m / 1440.0 * 2 * PI
                    val p = Offset(center.x + (ring * sin(a)).toFloat(), center.y - (ring * cos(a)).toFloat())
                    drawCircle(c.bg, 15.dp.toPx() * scale, p)
                    drawCircle(c.text, 12.dp.toPx() * scale, p)
                }
                dot(start, startScale); dot(end, endScale)
            }
            // Centro: horas que ruedan al cambiar (M04/M13)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(toHhmm(start), MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), c.text)
                Text("hasta ${toHhmm(end)}", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                Text(durationLabel(end - start), style = MaterialTheme.typography.labelMedium, color = c.accent)
            }
        }
        // Ajuste fino accesible (sin arrastrar)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FineTune("Inicio", { onChange((start - 5).coerceAtLeast(0), (end - 5).coerceAtLeast(15)) }, { if (end + 5 <= 1435) onChange(start + 5, end + 5) })
            Spacer(Modifier.width(Space.xl))
            FineTune("Fin", { if (end - 5 >= start + 15) onChange(start, end - 5) }, { if (end + 5 <= 1435) onChange(start, end + 5) })
        }
    }
}

@Composable
private fun FineTune(label: String, minus: () -> Unit, plus: () -> Unit) {
    val c = Agenda.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = minus) { Icon(Icons.Outlined.Remove, contentDescription = "$label 5 minutos antes", tint = c.textMuted) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        IconButton(onClick = plus) { Icon(Icons.Outlined.Add, contentDescription = "$label 5 minutos después", tint = c.textMuted) }
    }
}

/** Texto que entra deslizándose hacia arriba al cambiar (números que "ruedan"). */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val level = LocalMotionLevel.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(Motion.enter(level, 180)) { it / 2 } + fadeIn(Motion.enter(level, 180)))
                .togetherWith(slideOutVertically(Motion.exit(level, 120)) { -it / 2 } + fadeOut(Motion.exit(level, 120)))
        },
        label = "rolling",
        modifier = modifier,
    ) { t -> Text(t, style = style, color = color) }
}

@Suppress("unused")
private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
