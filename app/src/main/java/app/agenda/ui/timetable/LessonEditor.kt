package app.agenda.ui.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.agenda.data.Subject
import app.agenda.ui.theme.*

private val reminderOptions: List<Pair<Int?, String>> =
    listOf(null to "Sin aviso", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min")

/** S06 · Crear o editar una clase (con S06b, sugerencias de asignatura sin duplicados). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonEditor(
    state: EditorState,
    subjects: List<Subject>,
    existingMatch: (String) -> Subject?,
    onChange: ((EditorState) -> EditorState) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Agenda.colors
    var timePick by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // (grupo, ¿inicio?)

    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        // Barra superior
        Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar sin guardar", tint = c.text) }
            Text(
                if (state.lessonId == null) "Nueva clase" else "Editar clase",
                style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f),
            )
            Button(onClick = onSave, enabled = !state.saving) { Text("Guardar") }
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding)) {
            // Asignatura
            Label("Asignatura")
            OutlinedTextField(
                value = state.subjectName,
                onValueChange = { v -> onChange { it.copy(subjectName = v) } },
                placeholder = { Text("Por ejemplo: Química aplicada") },
                singleLine = true,
                isError = state.error != null && state.subjectName.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            val match = existingMatch(state.subjectName)
            AnimatedVisibility(visible = match != null, enter = fadeIn(), exit = fadeOut()) {
                if (match != null) {
                    AssistChip(
                        onClick = { onChange { it.copy(subjectName = match.name, colorIndex = match.colorIndex) } },
                        label = { Text("Ya tienes «${match.name}»: se usará esa") },
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }
            if (state.subjectName.isBlank() && subjects.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                    subjects.take(8).forEach { s ->
                        SuggestionChip(
                            onClick = { onChange { it.copy(subjectName = s.name, colorIndex = s.colorIndex) } },
                            label = { Text(s.name, maxLines = 1) },
                            icon = { Box(Modifier.size(10.dp).clip(CircleShape).background(c.subjects[s.colorIndex.coerceIn(0, 9)])) },
                        )
                    }
                }
            }

            // Color
            Label("Color")
            ColorRow(state.colorIndex) { i -> onChange { it.copy(colorIndex = i) } }

            // Días y horas
            Label("Días y horas")
            state.groups.forEachIndexed { gi, g ->
                Column(
                    Modifier.fillMaxWidth().padding(bottom = Space.md)
                        .clip(MaterialTheme.shapes.medium).background(c.surface).padding(Space.md)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        (1..7).forEach { d ->
                            DayChip(DayShort[d - 1], DayLong[d - 1], d in g.days) {
                                onChange { st ->
                                    st.copy(groups = st.groups.mapIndexed { i, x ->
                                        if (i != gi) x else x.copy(days = if (d in x.days) x.days - d else x.days + d)
                                    })
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { timePick = gi to true }) { Text("Inicio ${g.start}") }
                        Text("  →  ", color = c.textMuted)
                        OutlinedButton(onClick = { timePick = gi to false }) { Text("Fin ${g.end}") }
                        Spacer(Modifier.weight(1f))
                        if (state.groups.size > 1) {
                            IconButton(onClick = { onChange { st -> st.copy(groups = st.groups.filterIndexed { i, _ -> i != gi }) } }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Quitar este horario", tint = c.textMuted)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { onChange { it.copy(groups = it.groups + SlotGroup()) } }) {
                Icon(Icons.Outlined.Add, contentDescription = null); Spacer(Modifier.width(Space.xs))
                Text("Otro horario (otros días u horas)")
            }

            // Aula
            Label("Aula")
            OutlinedTextField(
                value = state.room, onValueChange = { v -> onChange { it.copy(room = v) } },
                placeholder = { Text("Por ejemplo: B12") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            // Aviso
            Label("Avisarme antes de la clase")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                reminderOptions.forEach { (value, text) ->
                    FilterChip(selected = state.remindMinutes == value, onClick = { onChange { it.copy(remindMinutes = value) } }, label = { Text(text) })
                }
            }

            state.error?.let {
                Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.lg))
            }
            Spacer(Modifier.height(Space.x4))
        }
    }

    timePick?.let { (gi, isStart) ->
        val current = state.groups.getOrNull(gi)?.let { if (isStart) it.start else it.end } ?: "08:00"
        TimeDialog(
            initial = current,
            title = if (isStart) "Hora de inicio" else "Hora de fin",
            onDismiss = { timePick = null },
            onConfirm = { hhmm ->
                onChange { st ->
                    st.copy(groups = st.groups.mapIndexed { i, x ->
                        when {
                            i != gi -> x
                            isStart -> {
                                // Al mover el inicio, el fin se mueve con él (misma duración).
                                val dur = minutesBetween(x.start, x.end).coerceAtLeast(30)
                                x.copy(start = hhmm, end = TimetableViewModel.plusMinutes(hhmm, dur))
                            }
                            else -> x.copy(end = hhmm)
                        }
                    })
                }
                timePick = null
            },
        )
    }
}

private fun minutesBetween(a: String, b: String): Int {
    fun m(s: String) = s.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
    return m(b) - m(a)
}

@Composable
private fun Label(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = Agenda.colors.textMuted,
        modifier = Modifier.padding(top = Space.xl, bottom = Space.sm),
    )
}

@Composable
private fun ColorRow(selected: Int, onSelect: (Int) -> Unit) {
    val c = Agenda.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        c.subjects.forEachIndexed { i, color ->
            val isSel = i == selected
            val scale by animateFloatAsState(if (isSel) 1.12f else 1f, Motion.light(LocalMotionLevel.current), label = "colorPick")
            Box(
                Modifier.size(28.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape).background(color)
                    .then(if (isSel) Modifier.border(3.dp, c.text, CircleShape) else Modifier)
                    .clickable { onSelect(i) }
                    .semantics { contentDescription = "Color ${i + 1}"; this.selected = isSel },
                contentAlignment = Alignment.Center,
            ) {
                // La marca hace que la selección no dependa solo del color.
                if (isSel) Icon(Icons.Outlined.Check, contentDescription = null, tint = c.onSubject, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DayChip(short: String, long: String, selected: Boolean, onClick: () -> Unit) {
    val c = Agenda.colors
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, Motion.light(LocalMotionLevel.current), label = "dayChip")
    Box(
        Modifier.size(36.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (selected) c.accent else c.surfaceHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = long; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(short, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onAccent else c.text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: String, title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val (h, m) = initial.split(":").map { it.toInt() }
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
