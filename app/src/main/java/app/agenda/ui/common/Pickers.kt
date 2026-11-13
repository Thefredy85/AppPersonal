package app.agenda.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.DayShort
import app.agenda.ui.timetable.RollingText
import app.agenda.ui.timetable.SNAP
import app.agenda.ui.timetable.toHhmm
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val es = Locale.forLanguageTag("es-MX")

/**
 * Reloj de una sola manecilla (misma familia que el reloj del horario): anillo de 24 h,
 * arrastre por medias horas con vibración, ± de 5 en 5 min y la hora que "rueda" en el centro.
 * Un arco tenue marca desde medianoche hasta la hora elegida, para leerla de un vistazo.
 */
@Composable
fun SingleTimeDial(minutes: Int, color: Color, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Agenda.colors
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val level = LocalMotionLevel.current
    var dragging by remember { mutableStateOf(false) }
    val handleScale by animateFloatAsState(if (dragging) 1.35f else 1f, Motion.light(level), label = "handle")
    val latest by rememberUpdatedState(minutes)
    val onChangeLatest by rememberUpdatedState(onChange)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize()
                    .semantics { contentDescription = "Reloj: ${toHhmm(minutes)}" }
                    .pointerInput(Unit) {
                        fun minuteAt(p: Offset): Int {
                            val x = p.x - size.width / 2f; val y = p.y - size.height / 2f
                            var a = atan2(x, -y); if (a < 0) a += (2 * PI).toFloat()
                            return ((a / (2 * PI) * 1440 / SNAP).roundToInt() * SNAP) % 1440
                        }
                        fun set(p: Offset) {
                            val m = minuteAt(p)
                            if (m != latest) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChangeLatest(m) }
                        }
                        detectDragGestures(
                            onDragStart = { dragging = true; set(it) },
                            onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
                            onDrag = { ch, _ -> set(ch.position) },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            val x = p.x - size.width / 2f; val y = p.y - size.height / 2f
                            var a = atan2(x, -y); if (a < 0) a += (2 * PI).toFloat()
                            val m = ((a / (2 * PI) * 1440 / SNAP).roundToInt() * SNAP) % 1440
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChangeLatest(m)
                        }
                    }
            ) {
                val ring = size.width * 0.40f
                val stroke = 20.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(c.surfaceHigh, ring, center, style = Stroke(stroke))
                drawArc(
                    color.copy(alpha = 0.35f), -90f, minutes / 1440f * 360f, false,
                    topLeft = Offset(center.x - ring, center.y - ring), size = androidx.compose.ui.geometry.Size(ring * 2, ring * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                for (h in 0 until 24) {
                    val a = h / 24.0 * 2 * PI
                    val inner = ring - stroke / 2 - 10.dp.toPx()
                    val p = Offset(center.x + (inner * sin(a)).toFloat(), center.y - (inner * cos(a)).toFloat())
                    if (h % 3 == 0) {
                        val t = measurer.measure("$h", TextStyle(color = if (h % 6 == 0) c.text else c.textMuted, fontSize = if (h % 6 == 0) 12.sp else 10.sp, fontWeight = FontWeight.Bold))
                        drawText(t, topLeft = Offset(p.x - t.size.width / 2f, p.y - t.size.height / 2f))
                    } else drawCircle(c.textMuted.copy(alpha = 0.5f), 1.5.dp.toPx(), p)
                }
                val a = minutes / 1440.0 * 2 * PI
                val hp = Offset(center.x + (ring * sin(a)).toFloat(), center.y - (ring * cos(a)).toFloat())
                drawCircle(c.bg, 16.dp.toPx() * handleScale, hp)
                drawCircle(color, 13.dp.toPx() * handleScale, hp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(toHhmm(minutes), MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), c.text)
                Text(dayPart(minutes), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((minutes - 5).coerceAtLeast(0)) }) { Icon(Icons.Outlined.Remove, contentDescription = "5 minutos antes", tint = c.textMuted) }
            Text("Afinar 5 min", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            IconButton(onClick = { onChange((minutes + 5).coerceAtMost(1435)) }) { Icon(Icons.Outlined.Add, contentDescription = "5 minutos después", tint = c.textMuted) }
        }
    }
}

private fun dayPart(m: Int) = when (m / 60) { in 0..5 -> "de madrugada"; in 6..11 -> "de la mañana"; in 12..18 -> "de la tarde"; else -> "de la noche" }

/**
 * Calendario propio: mes con flechas (el mes nuevo entra deslizándose desde su lado),
 * hoy con anillo, día elegido con círculo que hace pop, puntos en días con pendientes y atajos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthCalendar(
    selected: LocalDate,
    color: Color,
    busyDays: Set<String>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** v7: varias fechas (exámenes 1–5, recordatorios en varias fechas). Vacío = modo de una fecha. */
    multiSelected: List<LocalDate> = emptyList(),
    showShortcuts: Boolean = true,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val multi = multiSelected.isNotEmpty()
    val chosen = if (multi) multiSelected.sorted() else listOf(selected)
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val today = LocalDate.now()

    Column(modifier) {
        // Atajos
        if (showShortcuts) FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            val nextMonday = today.plusDays((8 - today.dayOfWeek.value).toLong())
            listOf("Hoy" to today, "Mañana" to today.plusDays(1), "Próximo lunes" to nextMonday, "En una semana" to today.plusWeeks(1)).forEach { (l, d) ->
                FilterChip(selected = selected == d, onClick = { month = YearMonth.from(d); onSelect(d) }, label = { Text(l) })
            }
        }
        Spacer(Modifier.height(Space.sm))
        // Cabecera del mes
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Mes anterior", tint = c.text) }
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(Motion.enter(level)) { it / 3 * dir } + fadeIn(Motion.enter(level)))
                        .togetherWith(slideOutHorizontally(Motion.exit(level)) { -it / 3 * dir } + fadeOut(Motion.exit(level)))
                },
                label = "monthTitle", modifier = Modifier.weight(1f),
            ) { m ->
                Text(
                    "${m.month.getDisplayName(JTextStyle.FULL, es).replaceFirstChar { it.uppercase() }} ${m.year}",
                    style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Mes siguiente", tint = c.text) }
        }
        Row(Modifier.fillMaxWidth()) {
            DayShort.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(Motion.enter(level)) { it / 2 * dir } + fadeIn(Motion.enter(level)))
                    .togetherWith(slideOutHorizontally(Motion.exit(level)) { -it / 2 * dir } + fadeOut(Motion.exit(level)))
            },
            label = "monthGrid",
            // Deslizar a los lados también cambia de mes.
            modifier = Modifier.pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = { if (total > 80) month = month.minusMonths(1) else if (total < -80) month = month.plusMonths(1); total = 0f },
                    onHorizontalDrag = { _, d -> total += d },
                )
            },
        ) { m ->
            val first = m.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val cells = offset + m.lengthOfMonth()
            Column {
                for (week in 0 until (cells + 6) / 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (dow in 0 until 7) {
                            val dayNum = week * 7 + dow - offset + 1
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (dayNum in 1..m.lengthOfMonth()) {
                                    val d = m.atDay(dayNum)
                                    val order = chosen.indexOf(d)
                                    val sel = order >= 0
                                    val scale by animateFloatAsState(if (sel) 1f else 0f, Motion.light(level), label = "sel$dayNum")
                                    val fg by animateColorAsState(if (sel) onSubjectColor(color) else if (d.isBefore(today)) c.textMuted else c.text, label = "fg$dayNum")
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape)
                                            .then(if (d == today && !sel) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier)
                                            .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(d) }
                                            .semantics { contentDescription = "$dayNum"; this.selected = sel },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(Modifier.size(40.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(color))
                                        Text("$dayNum", style = MaterialTheme.typography.bodyLarge, color = fg)
                                        if (multi && sel && chosen.size > 1) {
                                            Box(
                                                Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(c.bg),
                                                contentAlignment = Alignment.Center,
                                            ) { Text("${order + 1}", style = MaterialTheme.typography.labelMedium, color = c.text) }
                                        }
                                        if (d.toString() in busyDays) {
                                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(CircleShape).background(fg))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
