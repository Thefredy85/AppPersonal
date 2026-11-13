package app.agenda.ui.timetable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.agenda.data.TimetableBlock
import app.agenda.ui.theme.*
import java.time.LocalDate

val DayShort = listOf("L", "M", "X", "J", "V", "S", "D")
val DayLong = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

private fun minutesOf(hhmm: String): Int = hhmm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

/** S05 · Horario: semana × horas con bloques de color. */
@Composable
fun TimetableGrid(
    blocks: List<TimetableBlock>,
    onBlockClick: (TimetableBlock) -> Unit,
    onEmptyCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Agenda.colors
    if (blocks.isEmpty()) {
        EmptyTimetable(onEmptyCreate, modifier)
        return
    }
    val showWeekend = blocks.any { it.weekday >= 6 }
    val days = if (showWeekend) (1..7).toList() else (1..5).toList()
    val firstHour = ((blocks.minOf { minutesOf(it.startTime) } / 60) - 1).coerceIn(0, 22)
    val lastHour = ((blocks.maxOf { minutesOf(it.endTime) } + 59) / 60).coerceIn(firstHour + 2, 24)
    val hourH: Dp = Dimens.timetableHourRow
    val today = LocalDate.now().dayOfWeek.value

    Column(modifier) {
        // Cabecera de días
        Row(Modifier.fillMaxWidth().padding(start = Dimens.timetableTimeCol)) {
            days.forEach { d ->
                Box(Modifier.weight(1f).padding(vertical = Space.sm), contentAlignment = Alignment.Center) {
                    val isToday = d == today
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(if (isToday) c.accent else c.surface.copy(alpha = 0f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(DayShort[d - 1], style = MaterialTheme.typography.labelMedium, color = if (isToday) c.onAccent else c.textMuted)
                    }
                }
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(bottom = Dimens.bottomBar + Space.x4)
        ) {
            val colW = (maxWidth - Dimens.timetableTimeCol) / days.size
            val totalH = hourH * (lastHour - firstHour)
            Box(Modifier.fillMaxWidth().height(totalH)) {
                // Líneas y etiquetas de hora
                for (h in firstHour until lastHour) {
                    val y = hourH * (h - firstHour)
                    Text(
                        "%d:00".format(h), style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                        modifier = Modifier.offset(y = y - 6.dp).width(Dimens.timetableTimeCol).padding(start = Space.xs),
                    )
                    Box(Modifier.offset(x = Dimens.timetableTimeCol, y = y).fillMaxWidth().height(1.dp).background(c.border))
                }
                // Bloques
                blocks.filter { it.weekday in days }.forEach { b ->
                    val col = days.indexOf(b.weekday)
                    val top = hourH * ((minutesOf(b.startTime) - firstHour * 60) / 60f)
                    val height = hourH * ((minutesOf(b.endTime) - minutesOf(b.startTime)) / 60f)
                    BlockCell(
                        b,
                        Modifier
                            .offset(x = Dimens.timetableTimeCol + colW * col + 2.dp, y = top + 1.dp)
                            .width(colW - 4.dp)
                            .height((height - 2.dp).coerceAtLeast(28.dp)),
                        onClick = { onBlockClick(b) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockCell(b: TimetableBlock, modifier: Modifier, onClick: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Presión: 0,96 con muelle ligero (motion.md). Se lee en graphicsLayer, sin recomponer.
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.light(level), label = "blockPress")
    val desc = "${b.subjectName}, ${DayLong[b.weekday - 1]} de ${b.startTime} a ${b.endTime}" + (b.room?.let { ", aula $it" } ?: "")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.medium)
            .background(c.subjects[b.colorIndex.coerceIn(0, 9)])
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column {
            Text(b.subjectName, style = MaterialTheme.typography.labelMedium, color = c.onSubject, maxLines = 3, overflow = TextOverflow.Ellipsis)
            b.room?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.onSubject.copy(alpha = 0.75f), maxLines = 1) }
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
