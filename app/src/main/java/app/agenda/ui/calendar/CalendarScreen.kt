package app.agenda.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.agenda.data.EventRow
import app.agenda.data.TimetableBlock
import app.agenda.ui.agenda.EventItem
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.DayShort
import app.agenda.ui.timetable.toMinutes
import app.agenda.ui.today.HeatLegend
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale.forLanguageTag("es-MX")

/**
 * S04 · Calendario: el mes como mapa de calor. Cada día se tiñe según su carga (pendientes + horas de clase),
 * con puntitos del color de cada tipo. Se cambia de mes con flechas o deslizando; al entrar, los días se
 * encienden en diagonal. Tocar un día muestra sus clases y pendientes debajo y permite anotar ahí.
 */
@Composable
fun CalendarScreen(
    rows: List<EventRow>,
    blocks: List<TimetableBlock>,
    onToggle: (EventRow) -> Unit,
    onEdit: (String) -> Unit,
    onNew: (type: String, date: LocalDate) -> Unit,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val ym = YearMonth.parse(month)
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val sel = LocalDate.parse(selected)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.bottomBar + Space.x4)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Mes anterior", tint = c.text) }
            AnimatedContent(
                targetState = ym,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(Motion.enter(level)) { it / 3 * dir } + fadeIn()).togetherWith(slideOutHorizontally(Motion.exit(level)) { -it / 3 * dir } + fadeOut())
                },
                label = "calTitle", modifier = Modifier.weight(1f),
            ) { m ->
                Text(
                    m.month.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() } + " " + m.year,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = c.text,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = { month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Mes siguiente", tint = c.text) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = Space.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (ym != YearMonth.from(today)) TextButton(onClick = { month = YearMonth.from(today).toString(); selected = today.toString() }) { Text("Ir a hoy") }
            else Spacer(Modifier.height(1.dp))
            HeatLegend()
        }
        Row(Modifier.fillMaxWidth()) {
            DayShort.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
        AnimatedContent(
            targetState = ym,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(Motion.enter(level)) { it / 2 * dir } + fadeIn()).togetherWith(slideOutHorizontally(Motion.exit(level)) { -it / 2 * dir } + fadeOut())
            },
            label = "calGrid",
            modifier = Modifier.pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (total > 80) month = YearMonth.parse(month).minusMonths(1).toString()
                        else if (total < -80) month = YearMonth.parse(month).plusMonths(1).toString()
                        total = 0f
                    },
                    onHorizontalDrag = { _, d -> total += d },
                )
            },
        ) { m ->
            val ignite = remember(m) { Animatable(if (level == MotionLevel.Full) 0f else 1f) }
            LaunchedEffect(m) { ignite.animateTo(1f, tween(900, easing = Motion.EaseEmphasized)) }
            val offset = m.atDay(1).dayOfWeek.value - 1
            val cells = offset + m.lengthOfMonth()
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (week in 0 until (cells + 6) / 7) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (dow in 0 until 7) {
                            val n = week * 7 + dow - offset + 1
                            Box(Modifier.weight(1f).aspectRatio(0.82f)) {
                                if (n in 1..m.lengthOfMonth()) {
                                    val d = m.atDay(n)
                                    val k = ((ignite.value * 2f) - (week + dow) * 0.08f).coerceIn(0f, 1f)
                                    DayCell(d, today, d == sel, dayLoad(d, rows, blocks), rows.filter { it.doneAt == null && it.dueDate == d.toString() }, k) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); selected = d.toString()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Detalle del día
        AnimatedContent(
            targetState = sel,
            transitionSpec = { (fadeIn(Motion.enter(level)) + slideInVertically(Motion.enter(level)) { it / 6 }).togetherWith(fadeOut(Motion.exit(level))) },
            label = "calDay", modifier = Modifier.padding(top = Space.xl),
        ) { d -> DayDetail(d, today, rows, blocks, onToggle, onEdit, onNew) }
    }
}

@Composable
private fun DayCell(d: LocalDate, today: LocalDate, selected: Boolean, load: Float, items: List<EventRow>, ignite: Float, onClick: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val heat = heatColor(load)
    val bg = lerp(c.surface, heat, (if (load > 0f) 0.85f else 0.35f) * ignite)
    val fg = if (bg.luminance() > 0.4f) Color(0xFF14121C) else Color(0xFFF4F6FA)
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, Motion.light(level), label = "cellSel")
    val ring by animateColorAsState(if (selected) c.text else Color.Transparent, label = "cellRing")
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer { scaleX = scale * (0.85f + 0.15f * ignite); scaleY = scaleX; alpha = 0.4f + 0.6f * ignite }
            .clip(shape).background(bg).border(2.dp, ring, shape)
            .then(if (d == today && !selected) Modifier.border(1.5.dp, c.accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${d.dayOfMonth}, ${heatLabel(load)}, ${items.size} pendientes" }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${d.dayOfMonth}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (d == today) FontWeight.ExtraBold else FontWeight.Medium),
            color = if (d.isBefore(today)) fg.copy(alpha = 0.55f) else fg,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.take(3).forEach { r -> Box(Modifier.size(6.dp).clip(CircleShape).background(typeColor(r.type)).border(0.5.dp, fg.copy(alpha = 0.4f), CircleShape)) }
            if (items.size > 3) Text("+", style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

@Composable
private fun DayDetail(
    d: LocalDate, today: LocalDate, rows: List<EventRow>, blocks: List<TimetableBlock>,
    onToggle: (EventRow) -> Unit, onEdit: (String) -> Unit, onNew: (String, LocalDate) -> Unit,
) {
    val c = Agenda.colors
    val classes = blocks.filter { it.weekday == d.dayOfWeek.value }.sortedBy { toMinutes(it.startTime) }
    val items = rows.filter { it.dueDate == d.toString() }.sortedWith(compareBy({ it.doneAt != null }, { it.dueTime ?: "" }))
    val load = dayLoad(d, rows, blocks)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (d) { today -> "Hoy"; today.plusDays(1) -> "Mañana"; else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.labelMedium, color = c.accent,
                )
                Text("${d.dayOfMonth} de ${d.month.getDisplayName(TextStyle.FULL, es)}", style = MaterialTheme.typography.titleLarge, color = c.text)
            }
            Box(Modifier.clip(CircleShape).background(heatColor(load).copy(alpha = 0.3f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(heatLabel(load), style = MaterialTheme.typography.labelMedium, color = c.text)
            }
        }
        if (classes.isNotEmpty()) {
            Row(Modifier.padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                classes.take(4).forEach { b ->
                    val col = subjectColor(b.colorArgb)
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.18f)).padding(Space.sm)) {
                        Box(Modifier.width(18.dp).height(3.dp).clip(CircleShape).background(col))
                        Text(b.subjectName, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1)
                        Text(b.startTime, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.md))
        if (items.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text("Nada anotado este día", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            items.forEach { r -> key(r.id) { EventItem(r, onToggle = { onToggle(r) }, onClick = { onEdit(r.id) }) } }
        }
        if (!d.isBefore(today)) Row(Modifier.padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            listOf("homework" to "Tarea", "exam" to "Examen", "reminder" to "Recordatorio").forEach { (t, l) ->
                val col = typeColor(t)
                Row(
                    Modifier.weight(1f).clip(CircleShape).background(col.copy(alpha = 0.16f)).border(1.dp, col.copy(alpha = 0.5f), CircleShape)
                        .clickable { onNew(t, d) }.padding(vertical = Space.sm),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = col, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(l, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1)
                }
            }
        }
    }
}
