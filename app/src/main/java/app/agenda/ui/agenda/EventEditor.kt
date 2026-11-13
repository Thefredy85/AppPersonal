package app.agenda.ui.agenda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.agenda.data.Subject
import app.agenda.ui.common.ConfirmAddDialog
import app.agenda.ui.common.SummaryLine
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.GlassCard
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale.forLanguageTag("es-MX")

fun prettyDate(iso: String): String {
    val d = LocalDate.parse(iso)
    return "${d.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} de ${d.month.getDisplayName(TextStyle.FULL, es)}"
}

fun prettyReminder(local: String, due: String): String {
    val t = LocalDateTime.parse(local)
    val d = LocalDate.parse(due)
    val day = when (t.toLocalDate()) {
        d -> "El mismo día"
        d.minusDays(1) -> "El día anterior"
        else -> "${t.dayOfMonth} ${t.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.')}"
    }
    return "$day · %02d:%02d".format(t.hour, t.minute)
}

/** S03 · Crear/editar tarea, examen o recordatorio. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditor(
    state: EventEditorState,
    subjects: List<Subject>,
    onChange: ((EventEditorState) -> EventEditorState) -> Unit,
    onSave: () -> Unit,
    onDiscardDraft: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickReminder by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val subject = subjects.firstOrNull { it.id == state.subjectId }
    val accent = subject?.let { subjectColor(it.colorArgb) } ?: c.accent

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(level)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar (el borrador se guarda)", tint = c.text) }
                Text(if (state.id == null) "Nueva" else "Editar", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
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

                // Tipo
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val types = listOf("homework" to "Tarea", "exam" to "Examen", "reminder" to "Recordatorio")
                    types.forEachIndexed { i, (t, l) ->
                        SegmentedButton(selected = state.type == t, onClick = { onChange { it.copy(type = t) } }, shape = SegmentedButtonDefaults.itemShape(i, types.size)) { Text(l) }
                    }
                }
                Spacer(Modifier.height(Space.lg))

                // Título con casilla
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedCheck(state.done, accent, onToggle = { onChange { it.copy(done = !it.done) } })
                        BasicTextField(
                            value = state.title, onValueChange = { v -> onChange { it.copy(title = v) } },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (state.title.isEmpty()) Text("Título", style = MaterialTheme.typography.headlineSmall, color = c.textMuted)
                                inner()
                            },
                        )
                    }
                    AnimatedVisibility(state.error != null) {
                        Text(state.error.orEmpty(), color = c.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 48.dp))
                    }
                }

                Label("Asignatura")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FilterChip(selected = state.subjectId == null, onClick = { onChange { it.copy(subjectId = null) } }, label = { Text("Ninguna") })
                    subjects.forEach { s ->
                        FilterChip(
                            selected = state.subjectId == s.id, onClick = { onChange { it.copy(subjectId = s.id) } },
                            label = { Text(s.name, maxLines = 1) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(subjectColor(s.colorArgb))) },
                        )
                    }
                }

                Label("Cuándo")
                GlassCard {
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { pickDate = true }.padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = c.textMuted)
                        Spacer(Modifier.width(Space.md))
                        Text(prettyDate(state.dueDate), style = MaterialTheme.typography.bodyLarge, color = c.text)
                    }
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { pickTime = true }.padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = c.textMuted)
                        Spacer(Modifier.width(Space.md))
                        Text(state.dueTime ?: "Sin hora", style = MaterialTheme.typography.bodyLarge, color = if (state.dueTime == null) c.textMuted else c.text, modifier = Modifier.weight(1f))
                        if (state.dueTime != null) TextButton(onClick = { onChange { it.copy(dueTime = null) } }) { Text("Quitar") }
                    }
                }

                Label("Avisos")
                RemindersCard(state, onChange, onCustom = { pickReminder = true })

                Label("Subtareas")
                GlassCard {
                    state.subtasks.forEachIndexed { i, (t, d) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedCheck(d, accent, onToggle = { onChange { st -> st.copy(subtasks = st.subtasks.mapIndexed { j, p -> if (j == i) p.first to !p.second else p }) } })
                            BasicTextField(
                                value = t, onValueChange = { v -> onChange { st -> st.copy(subtasks = st.subtasks.mapIndexed { j, p -> if (j == i) v to p.second else p }) } },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text), cursorBrush = SolidColor(c.accent), modifier = Modifier.weight(1f),
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

                Label("Nota")
                OutlinedTextField(
                    value = state.note, onValueChange = { v -> onChange { it.copy(note = v) } },
                    placeholder = { Text("Lo que necesites recordar") }, minLines = 4, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.x4))
            }
        }
    }

    if (pickDate) {
        val init = LocalDate.parse(state.dueDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val ds = rememberDatePickerState(initialSelectedDateMillis = init)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        onChange { it.copy(dueDate = d) }
                    }
                    pickDate = false
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state = ds) }
    }
    if (pickTime) {
        TimeDialog(state.dueTime ?: "08:00", "Hora", onDismiss = { pickTime = false }) { hhmm -> onChange { it.copy(dueTime = hhmm) }; pickTime = false }
    }
    if (pickReminder) {
        // Aviso personalizado: misma fecha de entrega y hora elegida.
        TimeDialog("07:00", "Hora del aviso (${prettyDate(state.dueDate)})", onDismiss = { pickReminder = false }) { hhmm ->
            onChange { it.copy(reminders = (it.reminders + "${it.dueDate}T$hhmm").distinct().sorted()) }
            pickReminder = false
        }
    }
    if (confirming) {
        val lines = buildList {
            add(SummaryLine(Icons.Outlined.CalendarMonth, prettyDate(state.dueDate) + (state.dueTime?.let { " · $it" } ?: "")))
            subject?.let { add(SummaryLine(Icons.Outlined.School, it.name)) }
            add(SummaryLine(
                if (state.reminders.isEmpty()) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                if (state.reminders.isEmpty()) "Sin aviso" else state.reminders.joinToString(", ") { prettyReminder(it, state.dueDate) },
            ))
            if (state.subtasks.isNotEmpty()) add(SummaryLine(Icons.Outlined.Checklist, "${state.subtasks.size} subtareas"))
        }
        ConfirmAddDialog(
            title = if (state.id == null) "¿Agregar ${typeLabel(state.type).lowercase()}?" else "¿Guardar los cambios?",
            accent = accent,
            headline = state.title.ifBlank { "Sin título" },
            lines = lines,
            confirmLabel = if (state.id == null) "Agregar" else "Guardar",
            onConfirm = { confirming = false; haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSave() },
            onDismiss = { confirming = false },
        )
    }
}

/** Avisos a fecha y hora exactas (gratis): atajos + personalizado. La campana se agita al agregar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersCard(state: EventEditorState, onChange: ((EventEditorState) -> EventEditorState) -> Unit, onCustom: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val swing = remember { Animatable(0f) }
    LaunchedEffect(state.reminders.size) {
        if (state.reminders.isNotEmpty() && level == MotionLevel.Full) {
            swing.animateTo(0f, keyframes { durationMillis = 520; 18f at 90; -14f at 200; 8f at 310; -4f at 420; 0f at 520 })
        }
    }
    val d = LocalDate.parse(state.dueDate)
    val presets = buildList {
        add("El día anterior · 18:00" to "${d.minusDays(1)}T18:00")
        add("El mismo día · 07:00" to "${d}T07:00")
        state.dueTime?.let { t ->
            val due = LocalDateTime.parse("${d}T$t")
            add("1 h antes" to due.minusHours(1).toString().take(16))
            add("30 min antes" to due.minusMinutes(30).toString().take(16))
        }
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.reminders.isEmpty()) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                contentDescription = null, tint = if (state.reminders.isEmpty()) c.textMuted else c.accent,
                modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = swing.value; transformOrigin = TransformOrigin(0.5f, 0.1f) },
            )
            Spacer(Modifier.width(Space.md))
            Text(
                if (state.reminders.isEmpty()) "Sin aviso" else "${state.reminders.size} aviso${if (state.reminders.size > 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = c.text,
            )
        }
        Spacer(Modifier.height(Space.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            presets.forEach { (label, value) ->
                val on = value in state.reminders
                FilterChip(
                    selected = on,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange { it.copy(reminders = if (on) it.reminders - value else (it.reminders + value).sorted()) }
                    },
                    label = { Text(label) },
                )
            }
            AssistChip(onClick = onCustom, label = { Text("Otra hora") }, leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) })
        }
        // Avisos personalizados (los que no son atajos)
        state.reminders.filter { r -> presets.none { it.second == r } }.forEach { r ->
            InputChip(
                selected = true, onClick = { onChange { it.copy(reminders = it.reminders - r) } },
                label = { Text(prettyReminder(r, state.dueDate)) },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Quitar aviso", modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: String, title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val (h, m) = initial.split(":").map { it.toInt() }
    val st = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = st) },
        confirmButton = { TextButton(onClick = { onConfirm("%02d:%02d".format(st.hour, st.minute)) }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Agenda.colors.textMuted, modifier = Modifier.padding(top = Space.xl, bottom = Space.sm))
}
