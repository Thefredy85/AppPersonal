package app.agenda.ui.agenda

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.agenda.data.EventRow
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.RollingText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale.forLanguageTag("es-MX")

fun dayHeader(date: String, today: LocalDate = LocalDate.now()): String {
    val d = LocalDate.parse(date)
    val name = d.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }
    val month = d.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.')
    return when {
        d.isBefore(today) -> "Vencidas"
        d == today -> "Hoy · $name ${d.dayOfMonth} $month"
        d == today.plusDays(1) -> "Mañana · $name ${d.dayOfMonth} $month"
        else -> "$name ${d.dayOfMonth} $month"
    }
}

/** S02 · Agenda: tira semanal, filtro animado y lista por día. Lo recién agregado se "plasma" aquí. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(vm: AgendaViewModel, rows: List<EventRow>, onEdit: (String) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val filter by vm.filter.collectAsState()
    val typeFilter by vm.typeFilter.collectAsState()
    val justAdded by vm.justAdded.collectAsState()
    val today = LocalDate.now()
    var selected by remember { mutableStateOf(today) }

    val byStatus = rows.filter {
        when (filter) { AgendaFilter.Pending -> it.doneAt == null; AgendaFilter.Done -> it.doneAt != null; AgendaFilter.All -> true }
    }
    val visible = byStatus.filter { typeFilter == null || it.type == typeFilter }
    // "Vencidas" agrupa todo lo pendiente anterior a hoy.
    val groups = visible.groupBy { if (it.dueDate < today.toString() && it.doneAt == null) "0000-00-00" else it.dueDate }.toSortedMap()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Examen: sacudida del tablero + destello ámbar. Tarea/recordatorio: solo su tarjeta.
    val shake = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(justAdded) {
        val ja = justAdded ?: return@LaunchedEffect
        // Que se vea lo que se acaba de agregar.
        if (filter == AgendaFilter.Done) vm.filter.value = AgendaFilter.Pending
        if (typeFilter != null && typeFilter != ja.type) vm.typeFilter.value = null
        delay(60)
        val firstDate = rows.filter { it.id in ja.ids }.minOfOrNull { it.dueDate }
        if (firstDate != null) {
            var index = 0
            for ((k, list) in groups) {
                val hit = list.indexOfFirst { it.id in ja.ids }
                if (hit >= 0) { index += 1 + hit; break }
                index += 1 + list.size
            }
            listState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
        if (ja.type == "exam" && level != MotionLevel.Off) {
            delay(260) // cuando el examen "aterriza"
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            launch { flash.snapTo(0.32f); flash.animateTo(0f, tween(700, easing = Motion.EaseEmphasized)) }
            if (level == MotionLevel.Full) {
                for (x in listOf(14f, -11f, 8f, -5f, 2f, 0f)) shake.animateTo(x, tween(55))
            }
        }
        delay(1600)
        vm.clearJustAdded()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            WeekStrip(
                selected = selected,
                busyDays = rows.filter { it.doneAt == null }.map { it.dueDate }.toSet(),
                onSelect = { d ->
                    selected = d
                    var index = 0
                    for ((k, list) in groups) {
                        if (k >= d.toString()) break
                        index += 1 + list.size
                    }
                    scope.launch { listState.animateScrollToItem(index.coerceAtMost((groups.size + visible.size - 1).coerceAtLeast(0))) }
                },
            )
            StatusFilter(
                filter = filter,
                counts = mapOf(
                    AgendaFilter.Pending to rows.count { it.doneAt == null },
                    AgendaFilter.Done to rows.count { it.doneAt != null },
                    AgendaFilter.All to rows.size,
                ),
                onPick = { vm.filter.value = it },
            )
            TypeFilter(
                selected = typeFilter,
                counts = byStatus.groupingBy { it.type }.eachCount(),
                onPick = { t -> vm.typeFilter.value = if (typeFilter == t) null else t },
            )
            AnimatedContent(
                targetState = visible.isEmpty(),
                transitionSpec = { fadeIn(Motion.enter(level)).togetherWith(fadeOut(Motion.exit(level))) },
                label = "agendaEmpty",
            ) { empty ->
                if (empty) EmptyAgenda(filter, typeFilter) else LazyColumn(
                    state = listState,
                    modifier = Modifier.graphicsLayer { translationX = shake.value.dp.toPx() },
                    contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = Dimens.bottomBar + Space.x4 + 56.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    groups.forEach { (key, list) ->
                        stickyHeader(key = "h$key") {
                            Text(
                                if (key == "0000-00-00") "Vencidas" else dayHeader(key, today),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (key == "0000-00-00") c.danger else c.textMuted,
                                modifier = Modifier.fillMaxWidth().background(c.bg.copy(alpha = 0.92f)).padding(vertical = Space.sm),
                            )
                        }
                        items(list, key = { it.id }) { row ->
                            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                                when (v) {
                                    SwipeToDismissBoxValue.EndToStart -> { vm.delete(row.id); true }
                                    SwipeToDismissBoxValue.StartToEnd -> { vm.toggleDone(row); false }
                                    else -> false
                                }
                            })
                            SwipeToDismissBox(
                                state = dismiss,
                                modifier = Modifier.animateItem(),
                                backgroundContent = { SwipeBackground(dismiss.dismissDirection) },
                            ) {
                                val stamp = justAdded?.takeIf { row.id in it.ids }
                                EventItem(row, onToggle = { vm.toggleDone(row) }, onClick = { onEdit(row.id) }, stamp = stamp?.stamp)
                            }
                        }
                    }
                }
            }
        }
        // Destello ámbar del examen (no intercepta toques).
        if (flash.value > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(typeColor("exam")))
    }
}

/** Pendientes · Hechas · Todo, con la píldora que se desliza y contadores que ruedan. */
@Composable
private fun StatusFilter(filter: AgendaFilter, counts: Map<AgendaFilter, Int>, onPick: (AgendaFilter) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val options = AgendaFilter.entries
    val index = options.indexOf(filter)
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = Space.xs)
            .clip(RoundedCornerShape(999.dp)).background(c.surface).padding(3.dp)
    ) {
        val cell = maxWidth / options.size
        val x by animateDpAsState(cell * index, Motion.light(level), label = "statusX")
        Box(Modifier.offset(x = x).width(cell).height(38.dp).clip(RoundedCornerShape(999.dp)).background(c.accent))
        Row(Modifier.fillMaxWidth()) {
            options.forEach { f ->
                val sel = f == filter
                val fg by animateColorAsState(if (sel) c.onAccent else c.textMuted, label = "st$f")
                Row(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(999.dp)).clickable { onPick(f) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(f.label, style = MaterialTheme.typography.labelMedium, color = fg)
                    Spacer(Modifier.width(6.dp))
                    RollingText("${counts[f] ?: 0}", MaterialTheme.typography.labelMedium, fg.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/** Fichas por tipo con su color: al elegir una, se rellena y hace pop; las demás se atenúan. */
@Composable
private fun TypeFilter(selected: String?, counts: Map<String, Int>, onPick: (String) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        listOf("homework" to "Tareas", "exam" to "Exámenes", "reminder" to "Recordatorios").forEach { (t, l) ->
            val col = typeColor(t)
            val sel = selected == t
            val dim = selected != null && !sel
            val bg by animateColorAsState(if (sel) col else col.copy(alpha = 0.14f), Motion.enter(level), label = "tbg$t")
            val fg by animateColorAsState(if (sel) onSubjectColor(col) else c.text, label = "tfg$t")
            val scale by animateFloatAsState(if (sel) 1.04f else if (dim) 0.96f else 1f, Motion.light(level), label = "tsc$t")
            val alpha by animateFloatAsState(if (dim) 0.55f else 1f, label = "tal$t")
            Row(
                Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .clip(RoundedCornerShape(14.dp)).background(bg).clickable { onPick(t) }
                    .padding(horizontal = Space.sm, vertical = Space.sm),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(typeIcon(t), contentDescription = null, tint = if (sel) fg else col, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(l, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                RollingText("${counts[t] ?: 0}", MaterialTheme.typography.labelMedium, fg)
            }
        }
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val c = Agenda.colors
    val (color, icon, align) = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> Triple(c.danger, Icons.Outlined.DeleteOutline, Alignment.CenterEnd)
        SwipeToDismissBoxValue.StartToEnd -> Triple(c.success, Icons.Outlined.TaskAlt, Alignment.CenterStart)
        else -> Triple(c.surface, Icons.Outlined.TaskAlt, Alignment.Center)
    }
    Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(color.copy(alpha = 0.25f)).padding(horizontal = Space.xl), contentAlignment = align) {
        if (direction != SwipeToDismissBoxValue.Settled) Icon(icon, contentDescription = null, tint = color)
    }
}

/** M06: tira de la semana; el círculo del día elegido se desliza con un muelle ligero. */
@Composable
fun WeekStrip(selected: LocalDate, busyDays: Set<String>, onSelect: (LocalDate) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val today = LocalDate.now()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = Space.sm)) {
        val cell = maxWidth / 7
        val x by animateDpAsState(cell * (selected.dayOfWeek.value - 1), Motion.light(level), label = "weekPill")
        Box(Modifier.offset(x = x + (cell - 44.dp) / 2).size(44.dp, 60.dp).clip(MaterialTheme.shapes.large).background(c.accent))
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong())
                val sel = d == selected
                val fg by animateColorAsState(if (sel) c.onAccent else if (d == today) c.accent else c.text, label = "wd$i")
                Column(
                    Modifier.weight(1f).height(60.dp).clip(MaterialTheme.shapes.large).clickable { onSelect(d) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(app.agenda.ui.timetable.DayShort[i], style = MaterialTheme.typography.labelMedium, color = fg)
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = fg)
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (d.toString() in busyDays) fg else fg.copy(alpha = 0f)))
                }
            }
        }
    }
}

@Composable
private fun EmptyAgenda(filter: AgendaFilter, typeFilter: String?) {
    val c = Agenda.colors
    Column(Modifier.fillMaxSize().padding(Space.x3), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Space.lg))
        Text(
            when {
                filter == AgendaFilter.Done -> "Aún no completas nada"
                typeFilter == "exam" -> "Sin exámenes a la vista"
                typeFilter == "reminder" -> "Sin recordatorios"
                else -> "Nada pendiente"
            }, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text("Toca + para anotar una tarea, un examen o un recordatorio.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}
