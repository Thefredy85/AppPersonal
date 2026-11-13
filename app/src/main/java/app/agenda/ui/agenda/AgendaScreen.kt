package app.agenda.ui.agenda

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.agenda.data.EventRow
import app.agenda.ui.theme.*
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

/** S02 · Agenda: tira semanal, filtros y lista por día. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AgendaScreen(vm: AgendaViewModel, rows: List<EventRow>, onEdit: (String) -> Unit) {
    val c = Agenda.colors
    val filter by vm.filter.collectAsState()
    val typeFilter by vm.typeFilter.collectAsState()
    val today = LocalDate.now()
    var selected by remember { mutableStateOf(today) }

    val visible = rows.filter {
        when (filter) { AgendaFilter.Pending -> it.doneAt == null; AgendaFilter.Done -> it.doneAt != null; AgendaFilter.All -> true }
    }.filter { typeFilter == null || it.type == typeFilter }
    // "Vencidas" agrupa todo lo pendiente anterior a hoy.
    val groups = visible.groupBy { if (it.dueDate < today.toString() && it.doneAt == null) "0000-00-00" else it.dueDate }.toSortedMap()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        WeekStrip(
            selected = selected,
            busyDays = rows.filter { it.doneAt == null }.map { it.dueDate }.toSet(),
            onSelect = { d ->
                selected = d
                // Lleva la lista al día elegido.
                var index = 0
                for ((k, list) in groups) {
                    if (k >= d.toString()) break
                    index += 1 + list.size
                }
                scope.launch { listState.animateScrollToItem(index.coerceAtMost((groups.size + visible.size - 1).coerceAtLeast(0))) }
            },
        )
        FlowRow(Modifier.padding(horizontal = Dimens.screenPadding), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            AgendaFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { vm.filter.value = f }, label = { Text(f.label) }) }
            listOf("homework" to "Tareas", "exam" to "Exámenes", "reminder" to "Recordatorios").forEach { (t, l) ->
                FilterChip(selected = typeFilter == t, onClick = { vm.typeFilter.value = if (typeFilter == t) null else t }, label = { Text(l) })
            }
        }
        if (visible.isEmpty()) {
            EmptyAgenda(filter); return@Column
        }
        LazyColumn(
            state = listState,
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
                        EventItem(row, onToggle = { vm.toggleDone(row) }, onClick = { onEdit(row.id) })
                    }
                }
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
private fun EmptyAgenda(filter: AgendaFilter) {
    val c = Agenda.colors
    Column(Modifier.fillMaxSize().padding(Space.x3), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Space.lg))
        Text(if (filter == AgendaFilter.Done) "Aún no completas nada" else "Nada pendiente", style = MaterialTheme.typography.titleMedium, color = c.text)
        Text("Toca + para anotar una tarea, un examen o un recordatorio.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}
