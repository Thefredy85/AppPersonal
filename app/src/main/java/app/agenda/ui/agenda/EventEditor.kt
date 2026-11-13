package app.agenda.ui.agenda

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.agenda.data.Subject
import app.agenda.data.TimetableBlock
import app.agenda.ui.common.ConfirmAddDialog
import app.agenda.ui.common.MonthCalendar
import app.agenda.ui.common.SingleTimeDial
import app.agenda.ui.common.SummaryLine
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.DayShort
import app.agenda.ui.timetable.GlassCard
import app.agenda.ui.timetable.ReminderPicker
import app.agenda.ui.timetable.RollingText
import app.agenda.ui.timetable.toHhmm
import app.agenda.ui.timetable.toMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

private fun shortDate(iso: String): String {
    val d = LocalDate.parse(iso)
    return "${DayShort[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.')}"
}

fun typeIcon(type: String): ImageVector = when (type) { "exam" -> Icons.Outlined.Quiz; "reminder" -> Icons.Outlined.NotificationsNone; else -> Icons.Outlined.Assignment }

private val TYPES = listOf("homework" to "Tarea", "exam" to "Examen", "reminder" to "Recordatorio")
private const val MAX_EXAM_DATES = 5

/**
 * S03 · Un formulario distinto por tipo:
 *  - Tarea: título, asignatura (opcional), cuándo/hora, aviso, subtareas, nota · se puede repetir cada semana.
 *  - Examen: "Examen de <asignatura>" (se completa solo), asignatura del horario (obligatoria), cuándo/hora,
 *    aviso, notas · una fecha o de 2 a 5.
 *  - Recordatorio: nombre, cuándo/hora, aviso, "Recordar que…", imágenes, notas · varias fechas o rutina.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditor(
    state: EventEditorState,
    subjects: List<Subject>,
    blocks: List<TimetableBlock>,
    busyDays: Set<String>,
    onChange: ((EventEditorState) -> EventEditorState) -> Unit,
    onSwitchType: (String) -> Unit,
    onSave: () -> Unit,
    onDiscardDraft: () -> Unit,
    onDeleteSeries: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf<String?>(null) } // "date" | "time" | "until" | null
    var confirming by remember { mutableStateOf(false) }
    var confirmSeriesDelete by remember { mutableStateOf(false) }
    val isTask = state.type == "homework"; val isExam = state.type == "exam"; val isReminder = state.type == "reminder"
    val subject = subjects.firstOrNull { it.id == state.subjectId }
    val typeCol by animateColorAsState(typeColor(state.type), Motion.enter(level), label = "typeCol")
    val accent = if (isExam || isReminder) typeCol else subject?.let { subjectColor(it.colorArgb) } ?: typeCol
    val timetableSubjectIds = blocks.map { it.subjectId }.toSet()
    val subjectChoices = if (isExam) subjects.filter { it.id in timetableSubjectIds } else subjects
    fun classOn(subjectId: String?, date: String) = blocks.firstOrNull {
        it.subjectId == subjectId && it.weekday == LocalDate.parse(date).dayOfWeek.value
    }
    val classThatDay = if (isExam) classOn(state.subjectId, state.dueDate) else null
    val noun = when { isExam -> "examen"; isReminder -> "recordatorio"; else -> "tarea" }
    val isNew = state.id == null
    val instances = remember(state.dates, state.repeat, state.repeatDays, state.repeatUntil) { state.expandDates() }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(level)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar (el borrador se guarda)", tint = c.text) }
                Text(
                    if (isNew) (if (isTask) "Nueva tarea" else "Nuevo $noun") else "Editar $noun",
                    style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { confirming = true }, enabled = !state.saving,
                    colors = ButtonDefaults.buttonColors(containerColor = typeCol, contentColor = onSubjectColor(typeColor(state.type))),
                ) { Text("Guardar") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding)) {
                // Selector de tipo: la píldora de color se desliza al tipo elegido.
                if (isNew) TypeSwitcher(state.type, onPick = { t ->
                    if (t != state.type) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSwitchType(t) }
                })

                AnimatedVisibility(state.restoredDraft, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.md).clip(MaterialTheme.shapes.large).background(c.accent.copy(alpha = 0.14f)).padding(Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, tint = c.accent)
                        Spacer(Modifier.width(Space.sm))
                        Text("Recuperamos lo que estabas escribiendo", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDiscardDraft) { Text("Descartar") }
                    }
                }

                // Título: cada tipo entra con su propio pequeño deslizamiento.
                AnimatedContent(
                    targetState = state.type,
                    transitionSpec = { (fadeIn(Motion.enter(level)) + scaleIn(Motion.enter(level), initialScale = 0.96f)).togetherWith(fadeOut(Motion.exit(level))) },
                    label = "titleCard", modifier = Modifier.padding(top = Space.md),
                ) { _ ->
                    TitleCard(state, accent, typeCol, subject?.name, onChange)
                }
                AnimatedVisibility(state.error != null) {
                    Text(state.error.orEmpty(), color = c.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm))
                }

                // Asignatura
                if (!isReminder) {
                    Label(if (isExam) "Asignatura del horario · obligatoria" else "Asignatura · si hace falta")
                    if (isExam && subjectChoices.isEmpty()) {
                        Text("Primero agrega tus clases en Horario.", style = MaterialTheme.typography.bodyMedium, color = c.warning)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        if (isTask) FilterChip(selected = state.subjectId == null, onClick = { onChange { it.copy(subjectId = null) } }, label = { Text("Ninguna") })
                        subjectChoices.forEach { s ->
                            val sc = subjectColor(s.colorArgb)
                            FilterChip(
                                selected = state.subjectId == s.id,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onChange { st ->
                                        val t = if (st.type == "exam") classOn(s.id, st.dueDate)?.startTime ?: st.time else st.time
                                        val title = if (st.type == "exam" && !st.titleTouched) "Examen de ${s.name}" else st.title
                                        st.copy(subjectId = s.id, time = t, title = title)
                                    }
                                },
                                label = { Text(s.name, maxLines = 1) },
                                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(sc)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = sc.copy(alpha = 0.28f)),
                            )
                        }
                    }
                }

                // Cuándo, a qué hora y si se repite
                Label("Cuándo y a qué hora")
                if (isNew) RepeatSelector(state, typeCol, onChange)
                WhenSection(state, accent, busyDays, open, onOpen = { open = if (open == it) null else it }, onChange = onChange, classOn = ::classOn)
                if (classThatDay != null) {
                    Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.School, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            "Ese día tienes ${classThatDay.subjectName} de ${classThatDay.startTime} a ${classThatDay.endTime}",
                            style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                        )
                    }
                }
                AnimatedVisibility(isNew && instances.size > 1) {
                    Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Repeat, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                        RollingText("Se crearán ${instances.size}", MaterialTheme.typography.labelMedium, c.text)
                    }
                }

                // Aviso anclado a la hora (mismo diseño que en las clases)
                Label("Avisarme antes")
                ReminderPicker(
                    minutes = state.remindMinutes,
                    classStart = toMinutes(state.time),
                    onChange = { m -> onChange { it.copy(remindMinutes = m) } },
                )

                // Recordatorio: "Recordar que…" e imágenes
                if (isReminder) {
                    RememberThatCard(state.rememberThat, typeCol) { v -> onChange { it.copy(rememberThat = v) } }
                    Label("Imágenes")
                    ImagesRow(state.images, typeCol,
                        onAdd = { uris -> onChange { it.copy(images = (it.images + uris).distinct().take(10)) } },
                        onRemove = { p -> onChange { it.copy(images = it.images - p) } },
                    )
                }

                // Subtareas (solo tareas)
                if (isTask) {
                    Label("Subtareas")
                    GlassCard {
                        state.subtasks.forEachIndexed { i, (t, d) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedCheck(d, accent, onToggle = { onChange { st -> st.copy(subtasks = st.subtasks.mapIndexed { j, p -> if (j == i) p.first to !p.second else p }) } })
                                BasicTextField(
                                    value = t, onValueChange = { v -> onChange { st -> st.copy(subtasks = st.subtasks.mapIndexed { j, p -> if (j == i) v to p.second else p }) } },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text), cursorBrush = SolidColor(accent), modifier = Modifier.weight(1f),
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

                Label(if (isExam) "Notas" else "Nota")
                OutlinedTextField(
                    value = state.note, onValueChange = { v -> onChange { it.copy(note = v) } },
                    placeholder = { Text(when { isExam -> "Temas, materiales, aula…"; isReminder -> "Detalles"; else -> "Lo que necesites recordar" }) },
                    minLines = 3, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                )

                // Serie (al editar algo que se creó repetido)
                if (!isNew && state.seriesId != null && state.seriesSize > 1) {
                    Label("Serie")
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Repeat, contentDescription = null, tint = accent)
                            Spacer(Modifier.width(Space.md))
                            Text("Forma parte de ${state.seriesSize} fechas. Los cambios aplican solo a esta.", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                        }
                        TextButton(onClick = { confirmSeriesDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null); Spacer(Modifier.width(Space.xs)); Text("Borrar toda la serie")
                        }
                    }
                }
                Spacer(Modifier.height(Space.x4))
            }
        }
    }

    if (confirming) {
        val lines = buildList {
            if (isNew && instances.size > 1) {
                add(SummaryLine(Icons.Outlined.Repeat, "${instances.size} fechas · de ${shortDate(instances.first())} a ${shortDate(instances.last())}"))
                add(SummaryLine(Icons.Outlined.Schedule, "A las ${state.time}"))
            } else add(SummaryLine(Icons.Outlined.CalendarMonth, prettyDate(state.dueDate) + " · " + state.time))
            if (!isReminder) subject?.let { add(SummaryLine(Icons.Outlined.School, it.name)) }
            add(SummaryLine(
                if (state.remindMinutes == null || state.remindMinutes == 0) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                state.remindMinutes?.takeIf { it > 0 }?.let { "Aviso $it min antes · a las ${toHhmm(toMinutes(state.time) - it)}" } ?: "Sin aviso",
            ))
            if (isTask && state.subtasks.isNotEmpty()) add(SummaryLine(Icons.Outlined.Checklist, "${state.subtasks.size} subtareas"))
            if (isReminder && state.rememberThat.isNotBlank()) add(SummaryLine(Icons.Outlined.Lightbulb, "Recordar que: ${state.rememberThat.take(40)}"))
            if (isReminder && state.images.isNotEmpty()) add(SummaryLine(Icons.Outlined.Image, "${state.images.size} imágenes"))
        }
        ConfirmAddDialog(
            title = if (isNew) (if (isTask) "¿Agregar tarea?" else "¿Agregar $noun?") else "¿Guardar los cambios?",
            accent = typeColor(state.type),
            headline = state.title.ifBlank { "Sin título" },
            lines = lines,
            confirmLabel = if (isNew) "Agregar" else "Guardar",
            onConfirm = { confirming = false; haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSave() },
            onDismiss = { confirming = false },
        )
    }
    if (confirmSeriesDelete) {
        AlertDialog(
            onDismissRequest = { confirmSeriesDelete = false },
            title = { Text("¿Borrar las ${state.seriesSize} fechas?") },
            text = { Text("Se borran todas las fechas de esta serie, también las ya hechas.") },
            confirmButton = { TextButton(onClick = { confirmSeriesDelete = false; onDeleteSeries() }) { Text("Borrar", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmSeriesDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Tres tipos; la píldora de color se desliza con muelle ligero y cambia de color. */
@Composable
private fun TypeSwitcher(type: String, onPick: (String) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val index = TYPES.indexOfFirst { it.first == type }.coerceAtLeast(0)
    val pill by animateColorAsState(typeColor(type), Motion.enter(level), label = "pill")
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(c.surface).padding(4.dp)) {
        val cell = maxWidth / 3
        val x by animateDpAsState(cell * index, Motion.light(level), label = "pillX")
        Box(Modifier.offset(x = x).width(cell).height(44.dp).clip(RoundedCornerShape(999.dp)).background(pill))
        Row(Modifier.fillMaxWidth()) {
            TYPES.forEach { (t, label) ->
                val sel = t == type
                val fg by animateColorAsState(if (sel) onSubjectColor(typeColor(t)) else c.textMuted, label = "fg$t")
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(999.dp)).clickable { onPick(t) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(typeIcon(t), contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TitleCard(
    state: EventEditorState, accent: Color, typeCol: Color, subjectName: String?,
    onChange: ((EventEditorState) -> EventEditorState) -> Unit,
) {
    val c = Agenda.colors
    val isExam = state.type == "exam"
    val auto = if (isExam) "Examen de ${subjectName.orEmpty()}".trimEnd() else ""
    GlassCard(Modifier.border(1.5.dp, typeCol.copy(alpha = 0.55f), MaterialTheme.shapes.large)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.type == "homework") AnimatedCheck(state.done, accent, onToggle = { onChange { it.copy(done = !it.done) } })
            else Icon(typeIcon(state.type), contentDescription = null, tint = typeCol, modifier = Modifier.padding(Space.md))
            BasicTextField(
                value = state.title,
                onValueChange = { v -> onChange { it.copy(title = v, titleTouched = v.isNotBlank() && v != auto && v != "Examen de") } },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = c.text),
                cursorBrush = SolidColor(typeCol), modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (state.title.isEmpty()) Text(
                            when (state.type) { "exam" -> "Examen de…"; "reminder" -> "Nombre del recordatorio"; else -> "Nombre de la tarea" },
                            style = MaterialTheme.typography.headlineSmall, color = c.textMuted,
                        )
                        inner()
                    }
                },
            )
        }
        if (isExam && !state.titleTouched) {
            Text(
                if (subjectName == null) "Elige la asignatura y el título se completa solo" else "Título automático · escribe para cambiarlo",
                style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.padding(start = Space.md),
            )
        }
    }
}

/** Cómo se repite, según el tipo. La píldora se desliza como en el selector de tipo. */
@Composable
private fun RepeatSelector(state: EventEditorState, color: Color, onChange: ((EventEditorState) -> EventEditorState) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val options = when (state.type) {
        "exam" -> listOf(Repeat.None to "Una fecha", Repeat.Dates to "De 2 a 5 fechas")
        "reminder" -> listOf(Repeat.None to "Una vez", Repeat.Dates to "Varias fechas", Repeat.Routine to "Rutina")
        else -> listOf(Repeat.None to "Una vez", Repeat.Weekly to "Cada semana")
    }
    val index = options.indexOfFirst { it.first == state.repeat }.coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = Space.sm).clip(RoundedCornerShape(999.dp)).background(c.surface).padding(3.dp)) {
        val cell = maxWidth / options.size
        val x by animateDpAsState(cell * index, Motion.light(level), label = "repeatX")
        Box(Modifier.offset(x = x).width(cell).height(36.dp).clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.3f)).border(1.dp, color, RoundedCornerShape(999.dp)))
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (r, label) ->
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(999.dp)).clickable {
                        onChange { st ->
                            val first = st.dueDate
                            when (r) {
                                Repeat.None -> st.copy(repeat = r, dates = listOf(first))
                                Repeat.Dates -> st.copy(repeat = r, dates = listOf(first))
                                else -> st.copy(repeat = r, dates = listOf(first), repeatDays = st.repeatDays.ifEmpty { setOf(LocalDate.parse(first).dayOfWeek.value) })
                            }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (r == state.repeat) c.text else c.textMuted, maxLines = 1) }
            }
        }
    }
}

/**
 * Cuándo y a qué hora, en dos mosaicos:
 *  - Fecha: mes, número grande, día de la semana y "en N días" (o cuántas fechas / qué días si se repite).
 *  - Hora: el cielo del momento del día (sol o luna en su arco) con la hora encima.
 * Tocar uno lo destaca y despliega debajo su calendario o su reloj; abajo, la cuenta atrás.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenSection(
    state: EventEditorState, accent: Color, busyDays: Set<String>, open: String?,
    onOpen: (String) -> Unit, onChange: ((EventEditorState) -> EventEditorState) -> Unit,
    classOn: (String?, String) -> TimetableBlock?,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val repeat = state.repeat
    val multi = repeat == Repeat.Dates
    val weekly = repeat == Repeat.Weekly || repeat == Repeat.Routine
    val minutes = toMinutes(state.time)

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        WhenTile(selected = open == "date", dimmed = open == "time", accent = accent, modifier = Modifier.weight(1f), onClick = { onOpen("date") }) {
            DateTileContent(state, multi, weekly, accent)
        }
        WhenTile(selected = open == "time", dimmed = open == "date", accent = accent, modifier = Modifier.weight(1f), onClick = { onOpen("time") }, padded = false) {
            Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 132.dp)) {
                SkyCanvas(minutes, Modifier.matchParentSize())
                val p = periodOf(minutes)
                val onSky by animateColorAsState(p.onSky, Motion.enter(level, Motion.Slow), label = "tileOnSky")
                Column(Modifier.align(Alignment.BottomStart).padding(Space.md)) {
                    RollingText(state.time, MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), onSky)
                    RollingText(p.label, MaterialTheme.typography.labelMedium, onSky.copy(alpha = 0.85f))
                }
            }
        }
    }
    Countdown(state.dueDate, state.time, accent)

    AnimatedContent(
        targetState = open,
        transitionSpec = {
            (fadeIn(Motion.enter(level)) + expandVertically(Motion.enter(level)) + scaleIn(Motion.enter(level), initialScale = 0.97f))
                .togetherWith(fadeOut(Motion.exit(level)) + shrinkVertically(Motion.exit(level)))
        },
        label = "whenPanel",
    ) { panel ->
        when (panel) {
            "date" -> GlassCard(Modifier.padding(top = Space.md)) {
                if (multi) Text(
                    if (state.type == "exam") "Toca los días del examen (${state.dates.size}/$MAX_EXAM_DATES)" else "Toca todos los días que quieras (${state.dates.size})",
                    style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                )
                MonthCalendar(
                    selected = LocalDate.parse(state.dueDate), color = accent, busyDays = busyDays,
                    multiSelected = if (multi) state.dates.map { LocalDate.parse(it) } else emptyList(),
                    showShortcuts = !multi,
                    onSelect = { d ->
                        val iso = d.toString()
                        onChange { st ->
                            if (multi) {
                                val has = iso in st.dates
                                val limit = if (st.type == "exam") MAX_EXAM_DATES else 60
                                val next = when {
                                    has && st.dates.size > 1 -> st.dates - iso
                                    has -> st.dates
                                    st.dates.size >= limit -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); st.dates }
                                    else -> st.dates + iso
                                }
                                st.copy(dates = next.sorted())
                            } else {
                                val t = if (st.type == "exam") classOn(st.subjectId, iso)?.startTime ?: st.time else st.time
                                st.copy(dates = listOf(iso), time = t, repeatDays = if (weekly && st.repeatDays.isEmpty()) setOf(d.dayOfWeek.value) else st.repeatDays)
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = Space.sm),
                )
            }
            "time" -> GlassCard(Modifier.padding(top = Space.md)) {
                SingleTimeDial(
                    minutes = minutes, color = accent,
                    onChange = { m -> onChange { it.copy(time = toHhmm(m)) } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
                )
                // Atajos por momento del día
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    listOf("07:00", "13:00", "18:00", "21:00").forEach { t ->
                        val p = periodOf(t)
                        val sel = state.time == t
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(p.skyTop, p.skyBottom)))
                                .then(if (sel) Modifier.border(2.dp, c.text, RoundedCornerShape(999.dp)) else Modifier)
                                .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange { it.copy(time = t) } }
                                .padding(vertical = Space.sm),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(p.icon, contentDescription = null, tint = p.onSky, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t, style = MaterialTheme.typography.labelMedium, color = p.onSky)
                        }
                    }
                }
            }
            else -> Spacer(Modifier.height(0.dp))
        }
    }

    AnimatedVisibility(weekly, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        GlassCard(Modifier.padding(top = Space.md)) {
            Text(if (repeat == Repeat.Routine) "Rutina · qué días" else "Cada semana · qué días", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..7).forEach { dow ->
                    val sel = dow in state.repeatDays
                    val bg by animateColorAsState(if (sel) accent else c.surfaceHigh, label = "dow$dow")
                    val s by animateFloatAsState(if (sel) 1f else 0.9f, Motion.light(level), label = "dows$dow")
                    Box(
                        Modifier.size(40.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(bg)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onChange { st -> st.copy(repeatDays = if (sel && st.repeatDays.size > 1) st.repeatDays - dow else st.repeatDays + dow) }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(DayShort[dow - 1], style = MaterialTheme.typography.labelMedium, color = if (sel) onSubjectColor(accent) else c.text, textAlign = TextAlign.Center) }
                }
            }
            PickerRow(Icons.Outlined.EventRepeat, "Hasta ${prettyDate(state.repeatUntil)}", open == "until", accent) { onOpen("until") }
            AnimatedVisibility(open == "until", enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                MonthCalendar(
                    selected = LocalDate.parse(state.repeatUntil), color = accent, busyDays = emptySet(), showShortcuts = false,
                    onSelect = { d -> onChange { it.copy(repeatUntil = d.toString()) } },
                    modifier = Modifier.padding(vertical = Space.sm),
                )
            }
        }
    }
}

/** Mosaico tocable: al elegirlo se enmarca y crece un poco; el otro se encoge y se atenúa. */
@Composable
private fun WhenTile(
    selected: Boolean, dimmed: Boolean, accent: Color, modifier: Modifier = Modifier, padded: Boolean = true,
    onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val scale by animateFloatAsState(if (selected) 1.02f else if (dimmed) 0.96f else 1f, Motion.light(level), label = "tileScale")
    val alpha by animateFloatAsState(if (dimmed) 0.7f else 1f, label = "tileAlpha")
    val border by animateColorAsState(if (selected) accent else c.border, label = "tileBorder")
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxHeight().graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(shape).background(c.surface).border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .then(if (padded) Modifier.padding(Space.md) else Modifier),
        content = content,
    )
}

@Composable
private fun DateTileContent(state: EventEditorState, multi: Boolean, weekly: Boolean, accent: Color) {
    val c = Agenda.colors
    val d = LocalDate.parse(state.dueDate)
    val big = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
    when {
        multi && state.dates.size > 1 -> {
            Text("VARIAS FECHAS", style = MaterialTheme.typography.labelMedium, color = accent)
            RollingText("${state.dates.size}", big, c.text)
            Text(state.dates.sorted().take(3).joinToString(" · ") { shortDate(it) } + if (state.dates.size > 3) "…" else "",
                style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 2)
        }
        else -> {
            Text(d.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.').uppercase() + " " + d.year, style = MaterialTheme.typography.labelMedium, color = accent)
            RollingText("${d.dayOfMonth}", big, c.text)
            Text(d.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = c.text)
            Spacer(Modifier.height(Space.xs))
            val label = if (weekly) "Cada " + state.repeatDays.sorted().joinToString(", ") { DayShort[it - 1] } else relativeDay(d)
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(accent.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1)
            }
        }
    }
}

private fun relativeDay(d: LocalDate): String {
    val n = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        n == 0L -> "Hoy"; n == 1L -> "Mañana"; n == -1L -> "Ayer"
        n < 0 -> "Hace ${-n} días"; n < 7 -> "En $n días"
        n < 14 -> "En una semana"; else -> "En ${n / 7} semanas"
    }
}

/** "Faltan 2 d 3 h" bajo los mosaicos; se actualiza cada minuto. */
@Composable
private fun Countdown(date: String, time: String, accent: Color) {
    val c = Agenda.colors
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now = java.time.LocalDateTime.now() } }
    val due = runCatching { java.time.LocalDateTime.parse("${date}T$time") }.getOrNull() ?: return
    val mins = java.time.Duration.between(now, due).toMinutes()
    val text = when {
        mins < 0 -> "Esa hora ya pasó"
        mins < 60 -> "Faltan $mins min"
        mins < 24 * 60 -> "Faltan ${mins / 60} h ${mins % 60} min"
        else -> "Faltan ${mins / 1440} d ${(mins % 1440) / 60} h"
    }
    Row(Modifier.padding(top = Space.sm, start = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = if (mins < 0) c.warning else accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.xs))
        RollingText(text, MaterialTheme.typography.labelMedium, if (mins < 0) c.warning else c.textMuted)
    }
}

/** "Recordar que…": destacado cuando tiene texto; tenue cuando está vacío. */
@Composable
private fun RememberThatCard(text: String, color: Color, onChange: (String) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val used = text.isNotBlank()
    val alpha by animateFloatAsState(if (used) 1f else 0.55f, Motion.enter(level), label = "rtAlpha")
    val bg by animateColorAsState(if (used) color.copy(alpha = 0.18f) else c.surface, label = "rtBg")
    val border by animateColorAsState(if (used) color else c.border, label = "rtBorder")
    Label("Recordar que…")
    Row(
        Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }
            .clip(MaterialTheme.shapes.large).background(bg).border(if (used) 2.dp else 1.dp, border, MaterialTheme.shapes.large)
            .padding(Space.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = if (used) color else c.textMuted)
        Spacer(Modifier.width(Space.md))
        BasicTextField(
            value = text, onValueChange = onChange,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = c.text),
            cursorBrush = SolidColor(color), modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box { if (text.isEmpty()) Text("Lo importante, en una frase (llevar la credencial, pagar…)", style = MaterialTheme.typography.bodyLarge, color = c.textMuted); inner() }
            },
        )
    }
}

/** Miniaturas de las imágenes y botón para elegir más (selector de fotos del sistema). */
@Composable
private fun ImagesRow(images: List<String>, color: Color, onAdd: (List<String>) -> Unit, onRemove: (String) -> Unit) {
    val c = Agenda.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onAdd(uris.map { it.toString() })
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        images.forEach { p ->
            Box(Modifier.size(88.dp)) {
                Thumb(p, Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium))
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(c.bg.copy(alpha = 0.8f)).clickable { onRemove(p) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, contentDescription = "Quitar imagen", tint = c.text, modifier = Modifier.size(16.dp)) }
            }
        }
        Column(
            Modifier.size(88.dp).clip(MaterialTheme.shapes.medium).border(1.5.dp, color.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = color)
            Text("Adjuntar", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }
    }
}

@Composable
fun Thumb(path: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, path) { value = withContext(Dispatchers.IO) { decodeThumb(ctx, path, 256) } }
    Box(modifier.background(Agenda.colors.surfaceHigh), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Icon(Icons.Outlined.Image, contentDescription = null, tint = Agenda.colors.textMuted)
    }
}

private fun decodeThumb(ctx: Context, path: String, target: Int): ImageBitmap? = runCatching {
    fun open() = if (path.startsWith("content://")) ctx.contentResolver.openInputStream(Uri.parse(path)) else File(path).inputStream()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
    open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
}.getOrNull()

/** Fila que abre/cierra el calendario o el reloj; la flecha gira al abrir. */
@Composable
private fun PickerRow(icon: ImageVector, text: String, expanded: Boolean, accent: Color, onClick: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val rot by animateFloatAsState(if (expanded) 180f else 0f, Motion.enter(level), label = "chevron")
    val tint by animateColorAsState(if (expanded) accent else c.textMuted, label = "pickerTint")
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(Space.md))
        RollingText(text, MaterialTheme.typography.bodyLarge, c.text, Modifier.weight(1f))
        Icon(Icons.Outlined.ExpandMore, contentDescription = if (expanded) "Cerrar" else "Abrir", tint = c.textMuted, modifier = Modifier.graphicsLayer { rotationZ = rot })
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Agenda.colors.textMuted, modifier = Modifier.padding(top = Space.xl, bottom = Space.sm))
}
