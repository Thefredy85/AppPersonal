package app.agenda.ui.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.agenda.data.TimetableBlock
import app.agenda.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

val DayShort = listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do")
val DayLong = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/**
 * Días en texto natural:
 *  - seguidos (3 o más): "De lunes a viernes"
 *  - sueltos: "Lu, Ma y Vi"  ·  uno solo: "Jueves"
 */
fun formatDays(days: Collection<Int>): String {
    val d = days.toSortedSet().toList()
    if (d.isEmpty()) return "Elige días"
    if (d.size == 1) return DayLong[d[0] - 1].replaceFirstChar { it.uppercase() }
    val consecutive = d.zipWithNext().all { (a, b) -> b == a + 1 }
    if (consecutive && d.size >= 3) return "De ${DayLong[d.first() - 1]} a ${DayLong[d.last() - 1]}"
    val names = d.map { DayShort[it - 1] }
    return names.dropLast(1).joinToString(", ") + " y " + names.last()
}

private fun minutesOf(hhmm: String): Int = toMinutes(hhmm)

/** Reparte en carriles las clases que se cruzan el mismo día, para mostrarlas lado a lado. */
private data class Placed(val block: TimetableBlock, val lane: Int, val lanes: Int)

private fun placeLanes(dayBlocks: List<TimetableBlock>): List<Placed> {
    val sorted = dayBlocks.sortedBy { minutesOf(it.startTime) }
    val result = mutableListOf<Placed>()
    var cluster = mutableListOf<Pair<TimetableBlock, Int>>()
    var clusterEnd = -1
    fun flush() {
        val lanes = (cluster.maxOfOrNull { it.second } ?: -1) + 1
        cluster.forEach { (b, l) -> result += Placed(b, l, lanes) }
        cluster = mutableListOf()
    }
    val laneEnds = mutableListOf<Int>()
    for (b in sorted) {
        val s = minutesOf(b.startTime); val e = minutesOf(b.endTime)
        if (s >= clusterEnd && cluster.isNotEmpty()) { flush(); laneEnds.clear() }
        var lane = laneEnds.indexOfFirst { it <= s }
        if (lane == -1) { lane = laneEnds.size; laneEnds += e } else laneEnds[lane] = e
        cluster += b to lane
        clusterEnd = maxOf(clusterEnd, e)
    }
    if (cluster.isNotEmpty()) flush()
    return result
}

/** Minutos desde medianoche, actualizados cada 30 s (para la línea de "ahora"). */
@Composable
private fun rememberNowMinutes(): Int {
    var now by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now().let { it.hour * 60 + it.minute } } }
    return now
}

/** S05 · Horario v3. */
@Composable
fun TimetableGrid(
    blocks: List<TimetableBlock>,
    justSavedLessonId: String?,
    onSavedAnimationDone: () -> Unit,
    onBlockClick: (TimetableBlock) -> Unit,
    onEmptySlot: (weekday: Int, start: String) -> Unit,
    onEmptyCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    if (blocks.isEmpty()) { EmptyTimetable(onEmptyCreate, modifier); return }

    val showWeekend = blocks.any { it.weekday >= 6 }
    val days = if (showWeekend) (1..7).toList() else (1..5).toList()
    val firstHour = ((blocks.minOf { minutesOf(it.startTime) } / 60) - 1).coerceIn(0, 22)
    val lastHour = ((blocks.maxOf { minutesOf(it.endTime) } + 59) / 60 + 1).coerceIn(firstHour + 2, 24)
    val hourH: Dp = Dimens.timetableHourRow
    val today = LocalDate.now().dayOfWeek.value
    val nowMin = rememberNowMinutes()
    val placed = remember(blocks) { days.flatMap { d -> placeLanes(blocks.filter { it.weekday == d }) } }

    // M05: entrada escalonada de los bloques la primera vez.
    val entered = remember { Animatable(if (level == MotionLevel.Full) 0f else 1f) }
    LaunchedEffect(Unit) { entered.animateTo(1f, tween(Motion.Slow + Motion.StaggerMs * Motion.StaggerMaxItems, easing = Motion.EaseEmphasized)) }

    LaunchedEffect(justSavedLessonId) { if (justSavedLessonId != null) { delay(1_400); onSavedAnimationDone() } }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = Dimens.timetableTimeCol)) {
            days.forEach { d ->
                Box(Modifier.weight(1f).padding(vertical = Space.sm), contentAlignment = Alignment.Center) {
                    val isToday = d == today
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(if (isToday) c.accent else c.accent.copy(alpha = 0f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(DayShort[d - 1], style = MaterialTheme.typography.labelMedium, color = if (isToday) c.onAccent else c.textMuted) }
                }
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Dimens.bottomBar + Space.x4)
        ) {
            val colW = (maxWidth - Dimens.timetableTimeCol) / days.size
            val totalH = hourH * (lastHour - firstHour)
            val density = LocalDensity.current
            Box(
                Modifier.fillMaxWidth().height(totalH)
                    // Tocar un hueco vacío = nueva clase en ese día y hora (redondeado a 30 min).
                    .pointerInput(days, firstHour, colW) {
                        detectTapGestures { p ->
                            val x = p.x - with(density) { Dimens.timetableTimeCol.toPx() }
                            if (x < 0) return@detectTapGestures
                            val col = (x / with(density) { colW.toPx() }).toInt().coerceIn(0, days.size - 1)
                            val minute = firstHour * 60 + (p.y / with(density) { hourH.toPx() } * 60).toInt()
                            onEmptySlot(days[col], toHhmm((minute / 30) * 30))
                        }
                    }
            ) {
                // Columna de hoy resaltada
                val todayIdx = days.indexOf(today)
                if (todayIdx >= 0) {
                    Box(
                        Modifier.offset(x = Dimens.timetableTimeCol + colW * todayIdx).width(colW).fillMaxHeight()
                            .background(Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.10f), c.accent.copy(alpha = 0.03f))))
                    )
                }
                // Líneas y horas
                for (h in firstHour until lastHour) {
                    val y = hourH * (h - firstHour)
                    Text(
                        "%d:00".format(h), style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                        modifier = Modifier.offset(y = y - 6.dp).width(Dimens.timetableTimeCol).padding(start = Space.xs),
                    )
                    Box(Modifier.offset(x = Dimens.timetableTimeCol, y = y).fillMaxWidth().height(1.dp).background(c.border))
                }
                // Bloques
                placed.forEachIndexed { index, pl ->
                    val b = pl.block
                    val col = days.indexOf(b.weekday)
                    val top = hourH * ((minutesOf(b.startTime) - firstHour * 60) / 60f)
                    val height = hourH * ((minutesOf(b.endTime) - minutesOf(b.startTime)) / 60f)
                    val laneW = colW / pl.lanes
                    val isPast = b.weekday == today && minutesOf(b.endTime) <= nowMin
                    val isNow = b.weekday == today && nowMin in minutesOf(b.startTime) until minutesOf(b.endTime)
                    BlockCell(
                        b = b, isPast = isPast, isNow = isNow,
                        justSaved = b.lessonId == justSavedLessonId,
                        enterProgress = { ((entered.value * (1f + 0.08f * Motion.StaggerMaxItems)) - 0.08f * minOf(index, Motion.StaggerMaxItems - 1)).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .offset(x = Dimens.timetableTimeCol + colW * col + laneW * pl.lane + 2.dp, y = top + 1.dp)
                            .width(laneW - 4.dp)
                            .height((height - 2.dp).coerceAtLeast(28.dp)),
                        onClick = { onBlockClick(b) },
                    )
                }
                // Línea de "ahora" (solo en la columna de hoy)
                if (todayIdx >= 0 && nowMin in firstHour * 60 until lastHour * 60) {
                    NowLine(
                        Modifier.offset(x = Dimens.timetableTimeCol + colW * todayIdx - 5.dp, y = hourH * ((nowMin - firstHour * 60) / 60f) - 5.dp)
                            .width(colW + 5.dp).height(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NowLine(modifier: Modifier) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val pulse = if (level == MotionLevel.Full) {
        rememberInfiniteTransition(label = "now").animateFloat(0.6f, 1f, infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Reverse), label = "nowPulse")
    } else remember { mutableFloatStateOf(1f) }
    Canvas(modifier.semantics { contentDescription = "Hora actual" }) {
        val y = size.height / 2
        drawLine(c.danger, Offset(5.dp.toPx(), y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        drawCircle(c.danger.copy(alpha = 0.35f), 5.dp.toPx() * pulse.value * 1.6f, Offset(5.dp.toPx(), y))
        drawCircle(c.danger, 5.dp.toPx(), Offset(5.dp.toPx(), y))
    }
}

@Composable
private fun BlockCell(
    b: TimetableBlock,
    isPast: Boolean,
    isNow: Boolean,
    justSaved: Boolean,
    enterProgress: () -> Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val level = LocalMotionLevel.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.light(level), label = "blockPress")
    val bg = subjectColor(b.colorArgb)
    val fg = onSubjectColor(bg)

    // Al guardar: el bloque se pinta de arriba abajo y suelta una onda de color.
    val paint = remember { Animatable(if (justSaved && level == MotionLevel.Full) 0f else 1f) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(justSaved) {
        if (justSaved && level == MotionLevel.Full) {
            paint.snapTo(0f); ripple.snapTo(0f)
            paint.animateTo(1f, tween(550, easing = Motion.EaseEmphasized))
            ripple.animateTo(1f, tween(700))
        }
    }
    val desc = "${b.subjectName}, ${DayLong[b.weekday - 1]} de ${b.startTime} a ${b.endTime}" + (b.room?.let { ", aula $it" } ?: "") +
        (if (isNow) ", en curso" else "")

    Box(
        modifier
            .graphicsLayer {
                val p = enterProgress()
                scaleX = scale; scaleY = scale
                alpha = p * (if (isPast) 0.45f else 1f)
                translationY = (1f - p) * 12.dp.toPx()
            }
            .drawWithContent {
                // Onda (debajo del bloque)
                val r = ripple.value
                if (r > 0f && r < 1f) {
                    drawRoundRect(
                        bg.copy(alpha = (1f - r) * 0.8f),
                        topLeft = Offset(-r * 14.dp.toPx(), -r * 14.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width + r * 28.dp.toPx(), size.height + r * 28.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                }
                clipRect(bottom = size.height * paint.value) { this@drawWithContent.drawContent() }
            }
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.88f))))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column {
            Text(b.subjectName, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
            b.room?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.75f), maxLines = 1) }
        }
        if (isNow) {
            Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(fg))
        }
    }
}

@Composable
private fun EmptyTimetable(onCreate: () -> Unit, modifier: Modifier) {
    val c = Agenda.colors
    Column(modifier.fillMaxSize().padding(Space.x3), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CalendarViewWeek, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Space.lg))
        Text("Arma tu semana", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(Space.sm))
        Text("Agrega tus clases una vez y la app te avisará antes de cada una.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(Space.xl))
        Button(onClick = onCreate) { Text("Crear mi primera clase") }
    }
}
