package app.agenda.ui.agenda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.agenda.data.Subject
import app.agenda.data.TimetableBlock
import app.agenda.ui.common.ConfirmAddDialog
import app.agenda.ui.common.MonthCalendar
import app.agenda.ui.common.SingleTimeDial
import app.agenda.ui.common.SummaryLine
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.GlassCard
import app.agenda.ui.timetable.ReminderPicker
import app.agenda.ui.timetable.toHhmm
import app.agenda.ui.timetable.toMinutes
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale.forLanguageTag("es-MX")

fun prettyDate(iso: String): String {
    val d = LocalDate.parse(iso)
    val today = LocalDate.now()
    val base = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} de ${d.month.getDisplayName(TextStyle.FULL, es)}"
    return when (d) { today -> "Hoy · $base"; today.plusDays(1) -> "Mañana · $base"; else -> base }
}

private fun typeIcon(type: String): ImageVector = when (type) { "exam" -> Icons.Outlined.Quiz; "reminder" -> Icons.Outlined.NotificationsNone; else -> Icons.Outlined.Assignment }

/**
 * S03 · Formulario según el tipo:
 *  - Tarea: asignatura (opcional), cuándo y hora, aviso, subtareas, nota.
 *  - Examen: asignatura del horario (obligatoria), cuándo y hora, aviso, nota.
 *  - Recordatorio: cuándo y hora, aviso, nota.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditor(
    state: EventEditorState,
    subjects: List<Subject>,
    blocks: List<TimetableBlock>,
    busyDays: Set<String>,
    onChange: ((EventEditorState) -> EventEditorState) -> Unit,
    onSave: () -> Unit,
    onDiscardDraft: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf<String?>(null) } // "date" | "time" | null
    var confirming by remember { mutableStateOf(false) }
    val isTask = state.type == "homework"; val isExam = state.type == "exam"; val isReminder = state.type == "reminder"
    val subject = subjects.firstOrNull { it.id == state.subjectId }
    val accent = subject?.let { subjectColor(it.colorArgb) } ?: c.accent
    // Examen: solo asignaturas que están en el horario.
    val timetableSubjectIds = blocks.map { it.subjectId }.toSet()
    val subjectChoices = if (isExam) subjects.filter { it.id in timetableSubjectIds } else subjects
    // Examen: si esa asignatura tiene clase ese día, se sugiere la hora de la clase.
    val classThatDay = if (state.subjectId != null) blocks.firstOrNull {
        it.subjectId == state.subjectId && it.weekday == LocalDate.parse(state.dueDate).dayOfWeek.value
    } else null
    fun suggestExamTime(subjectId: String?, date: String) = blocks.firstOrNull {
        it.subjectId == subjectId && it.weekday == LocalDate.parse(date).dayOfWeek.value
    }?.startTime
    val title = when { isExam -> "examen"; isReminder -> "recordatorio"; else -> "tarea" }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(level)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar (el borrador se guarda)", tint = c.text) }
                Icon(typeIcon(state.type), contentDescription = null, tint = accent)
                Spacer(Modifier.width(Space.sm))
                Text(if (state.id == null) "Nuevo ${title}".replace("Nuevo tarea", "Nueva tarea") else "Editar $title", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                Button(onClick = { confirming = true }, enabled = !state.saving) { Text("Guardar") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding)) {
                AnimatedVisibility(state.restoredDraft, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Space.md).clip(MaterialTheme.shapes.large).background(c.accent.copy(alpha = 0.14f)).padding(Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, tint = c.accent)
                        Spacer(Modifier.width(Space.sm))
                        Text("Recuperamos lo que estabas escribiendo", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDiscardDraft) { Text("Descartar") }
                    }
                }

                // Título
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isReminder) AnimatedCheck(state.done, accent, onToggle = { onChange { it.copy(done = !it.done) } })
                        else Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = accent, modifier = Modifier.padding(Space.md))
                        BasicTextField(
                            value = state.title, onValueChange = { v -> onChange { it.copy(title = v) } },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(color = c.text),
                            cursorBrush = SolidColor(c.accent), modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box {
                                    if (state.title.isEmpty()) Text(
                                        when { isExam -> "Examen de…"; isReminder -> "Qué te recuerdo"; else -> "Qué hay que hacer" },
                                        style = MaterialTheme.typography.headlineSmall, color = c.textMuted,
                                    )
                                    inner()
                                }
                            },
                        )
                    }
                }
                AnimatedVisibility(state.error != null) {
                    Text(state.error.orEmpty(), color = c.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm))
                }

                // Asignatura (tarea: opcional · examen: obligatoria y del horario)
                if (!isReminder) {
                    Label(if (isExam) "Asignatura del horario (obligatoria)" else "Asignatura (opcional)")
                    if (isExam && subjectChoices.isEmpty()) {
                        Text("Primero agrega tus clases en Horario.", style = MaterialTheme.typography.bodyMedium, color = c.warning)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        if (isTask) FilterChip(selected = state.subjectId == null, onClick = { onChange { it.copy(subjectId = null) } }, label = { Text("Ninguna") })
                        subjectChoices.forEach { s ->
                            FilterChip(
                                selected = state.subjectId == s.id,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onChange { st ->
                                        val t = if (isExam) suggestExamTime(s.id, st.dueDate) ?: st.dueTime else st.dueTime
                                        st.copy(subjectId = s.id, dueTime = t)
                                    }
                                },
                                label = { Text(s.name, maxLines = 1) },
                                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(subjectColor(s.colorArgb))) },
                            )
                        }
                    }
                }

                // Cuándo y a qué hora (calendario y reloj propios, se abren en la misma pantalla)
                Label("Cuándo y a qué hora")
                GlassCard {
                    PickerRow(Icons.Outlined.CalendarMonth, prettyDate(state.dueDate), open == "date") { open = if (open == "date") null else "date" }
                    AnimatedVisibility(open == "date", enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        MonthCalendar(
                            selected = LocalDate.parse(state.dueDate), color = accent, busyDays = busyDays,
                            onSelect = { d ->
                                onChange { st ->
                                    val t = if (st.type == "exam") suggestExamTime(st.subjectId, d.toString()) ?: st.dueTime else st.dueTime
                                    st.copy(dueDate = d.toString(), dueTime = t)
                                }
                                open = "time"
                            },
                            modifier = Modifier.padding(vertical = Space.sm),
                        )
                    }
                    PickerRow(Icons.Outlined.Schedule, state.dueTime ?: "08:00", open == "time") { open = if (open == "time") null else "time" }
                    AnimatedVisibility(open == "time", enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        SingleTimeDial(
                            minutes = toMinutes(state.dueTime ?: "08:00"), color = accent,
                            onChange = { m -> onChange { it.copy(dueTime = toHhmm(m)) } },
                            modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
                        )
                    }
                    if (isExam && classThatDay != null) {
                        Text(
                            "Ese día tienes ${classThatDay.subjectName} de ${classThatDay.startTime} a ${classThatDay.endTime}",
                            style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }

                // Aviso anclado a la hora (mismo diseño que en las clases)
                Label("Avisarme antes")
                ReminderPicker(
                    minutes = state.remindMinutes,
                    classStart = toMinutes(state.dueTime ?: "08:00"),
                    onChange = { m -> onChange { it.copy(remindMinutes = m) } },
                )

                // Subtareas (solo tareas)
                if (isTask) {
                    Label("Subtareas")
                    GlassCard {
                        state.subtasks.forEachIndexed { i, (t, d) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedCheck(d, accent, onToggle = { onChange { st -> st.copy(subtasks = st.subtasks.mapIndexed { j, p -> if (j == i) p.first to !p.second else p }) } })
                                BasicTextField(
                                    value = t, onValueChange = { v -> onChange { st -> st.copy(subtasks = st.subtasks.mapIndexed { j, p -> if (j == i) v to p.second else p }) } },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text), cursorBrush = SolidColor(c.accent), modifier = Modifier.weight(1f),
                                    decorationBox = { inner -> Box { if (t.isEmpty()) Text("Paso ${i + 1}", color = c.textMuted, style = MaterialTheme.typography.bodyLarge); inner() } },
                                )
                                IconButton(onClick = { onChange { st -> st.copy(subtasks = st.subtasks.filterIndexed { j, _ -> j != i }) } }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Quitar subtarea", tint = c.textMuted)
                                }
                            }
                        }
                        TextButton(onClick = { onChange { it.copy(subtasks = it.subtasks + ("" to false)) } }) {
                            Icon(Icons.Outlined.Add, contentDescription = null); Spacer(Modifier.width(Space.xs)); Text("Agregar subtarea")
                        }
                    }
                }

                Label("Nota")
                OutlinedTextField(
                    value = state.note, onValueChange = { v -> onChange { it.copy(note = v) } },
                    placeholder = { Text(if (isExam) "Temas, materiales, aula…" else "Lo que necesites recordar") },
                    minLines = 4, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.x4))
            }
        }
    }

    if (confirming) {
        val lines = buildList {
            add(SummaryLine(Icons.Outlined.CalendarMonth, prettyDate(state.dueDate) + " · " + (state.dueTime ?: "08:00")))
            if (!isReminder) subject?.let { add(SummaryLine(Icons.Outlined.School, it.name)) }
            add(SummaryLine(
                if (state.remindMinutes == null) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                state.remindMinutes?.let { "Aviso $it min antes · a las ${toHhmm(toMinutes(state.dueTime ?: "08:00") - it)}" } ?: "Sin aviso",
            ))
            if (isTask && state.subtasks.isNotEmpty()) add(SummaryLine(Icons.Outlined.Checklist, "${state.subtasks.size} subtareas"))
        }
        ConfirmAddDialog(
            title = if (state.id == null) "¿Agregar $title?" else "¿Guardar los cambios?",
            accent = accent,
            headline = state.title.ifBlank { "Sin título" },
            lines = lines,
            confirmLabel = if (state.id == null) "Agregar" else "Guardar",
            onConfirm = { confirming = false; haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSave() },
            onDismiss = { confirming = false },
        )
    }
}

/** Fila que abre/cierra el calendario o el reloj; la flecha gira al abrir. */
@Composable
private fun PickerRow(icon: ImageVector, text: String, expanded: Boolean, onClick: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val rot by animateFloatAsState(if (expanded) 180f else 0f, Motion.enter(level), label = "chevron")
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (expanded) c.accent else c.textMuted)
        Spacer(Modifier.width(Space.md))
        app.agenda.ui.timetable.RollingText(text, MaterialTheme.typography.bodyLarge, c.text, Modifier.weight(1f))
        Icon(Icons.Outlined.ExpandMore, contentDescription = if (expanded) "Cerrar" else "Abrir", tint = c.textMuted, modifier = Modifier.graphicsLayer { rotationZ = rot })
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Agenda.colors.textMuted, modifier = Modifier.padding(top = Space.xl, bottom = Space.sm))
}
