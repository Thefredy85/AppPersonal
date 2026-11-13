package app.agenda.ui.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.agenda.data.Subject
import app.agenda.ui.common.ConfirmAddDialog
import app.agenda.ui.common.SummaryLine
import app.agenda.ui.theme.*
import kotlinx.coroutines.delay

/** S06 · Editor de clase v3: vista previa en vivo, reloj propio, 24 colores + personalizado, aviso animado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonEditor(
    state: EditorState,
    subjects: List<Subject>,
    blocks: List<app.agenda.data.TimetableBlock>,
    existingMatch: (String) -> Subject?,
    onChange: ((EditorState) -> EditorState) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val color = subjectColor(state.colorArgb)
    val group = state.groups.getOrElse(state.activeGroup) { state.groups.first() }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(level)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Barra superior
            Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar sin guardar", tint = c.text) }
                Text(
                    if (state.lessonId == null) "Nueva clase" else "Editar clase",
                    style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f),
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding)) {
                PreviewCard(state, color)

                Label("Asignatura")
                OutlinedTextField(
                    value = state.subjectName,
                    onValueChange = { v -> onChange { it.copy(subjectName = v) } },
                    placeholder = { Text("Por ejemplo: Química aplicada") },
                    singleLine = true,
                    isError = state.error != null && state.subjectName.isBlank(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                val match = existingMatch(state.subjectName)
                AnimatedVisibility(visible = match != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    if (match != null) {
                        AssistChip(
                            onClick = { onChange { it.copy(subjectName = match.name, colorArgb = match.colorArgb) } },
                            label = { Text("Ya tienes «${match.name}»: se usará esa") },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(subjectColor(match.colorArgb))) },
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
                if (state.subjectName.isBlank() && subjects.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                        subjects.take(8).forEach { s ->
                            SuggestionChip(
                                onClick = { onChange { it.copy(subjectName = s.name, colorArgb = s.colorArgb) } },
                                label = { Text(s.name, maxLines = 1) },
                                icon = { Box(Modifier.size(10.dp).clip(CircleShape).background(subjectColor(s.colorArgb))) },
                            )
                        }
                    }
                }

                // Días + horario con el reloj
                Label("Días y horario")
                if (state.groups.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        state.groups.forEachIndexed { i, g ->
                            FilterChip(
                                selected = i == state.activeGroup,
                                onClick = { onChange { it.copy(activeGroup = i) } },
                                label = { Text(if (g.days.isEmpty()) "Horario ${i + 1}" else formatDays(g.days)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.sm))
                }
                GlassCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        (1..7).forEach { d ->
                            DayChip(DayShort[d - 1], DayLong[d - 1], d in group.days) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onChange { st ->
                                    st.copy(groups = st.groups.mapIndexed { i, x ->
                                        if (i != st.activeGroup) x else x.copy(days = if (d in x.days) x.days - d else x.days + d)
                                    })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    ClockDial(
                        start = toMinutes(group.start), end = toMinutes(group.end), color = color,
                        busy = busyRanges(state, group, blocks),
                        onChange = { s, e ->
                            onChange { st ->
                                st.copy(groups = st.groups.mapIndexed { i, x -> if (i != st.activeGroup) x else x.copy(start = toHhmm(s), end = toHhmm(e)) })
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onChange { it.copy(groups = it.groups + SlotGroup(), activeGroup = it.groups.size) } }) {
                            Icon(Icons.Outlined.Add, contentDescription = null); Spacer(Modifier.width(Space.xs)); Text("Otros días con otra hora")
                        }
                        if (state.groups.size > 1) {
                            IconButton(onClick = {
                                onChange { st -> st.copy(groups = st.groups.filterIndexed { i, _ -> i != st.activeGroup }, activeGroup = 0) }
                            }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Quitar este horario", tint = c.textMuted) }
                        }
                    }
                }

                val conflicts = findConflicts(state, blocks)
                AnimatedVisibility(visible = conflicts.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    ConflictCard(conflicts) { gi ->
                        nextFreeStart(state, gi, blocks)?.let { ns ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onChange { st ->
                                st.copy(activeGroup = gi, groups = st.groups.mapIndexed { i, x ->
                                    if (i != gi) x else {
                                        val dur = toMinutes(x.end) - toMinutes(x.start)
                                        x.copy(start = toHhmm(ns), end = toHhmm(ns + dur))
                                    }
                                })
                            }
                        }
                    }
                }

                Label("Color")
                ColorPicker(state.colorArgb) { argb -> onChange { it.copy(colorArgb = argb) } }

                Label("Aula")
                OutlinedTextField(
                    value = state.room, onValueChange = { v -> onChange { it.copy(room = v) } },
                    placeholder = { Text("Por ejemplo: B12") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Label("Avisarme antes de la clase")
                ReminderPicker(
                    minutes = state.remindMinutes,
                    classStart = toMinutes(state.groups.first().start),
                    onChange = { m -> onChange { it.copy(remindMinutes = m) } },
                )

                AnimatedVisibility(visible = state.error != null) {
                    Text(state.error.orEmpty(), color = c.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.lg))
                }
                Spacer(Modifier.height(Space.lg))
            }

            var confirming by remember { mutableStateOf(false) }
            var go by remember { mutableIntStateOf(0) }
            SaveButton(saving = state.saving, trigger = go, onRequest = { confirming = true }, onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSave()
            })
            if (confirming) {
                val lines = buildList {
                    state.groups.filter { it.days.isNotEmpty() }.forEach { g ->
                        add(SummaryLine(Icons.Outlined.Schedule, "${formatDays(g.days)} · ${g.start} → ${g.end}"))
                    }
                    if (state.room.isNotBlank()) add(SummaryLine(Icons.Outlined.Place, "Aula ${state.room.trim()}"))
                    add(SummaryLine(
                        if (state.remindMinutes != null) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                        state.remindMinutes?.let { "Aviso $it min antes" } ?: "Sin aviso",
                    ))
                    val n = findConflicts(state, blocks).size
                    if (n > 0) add(SummaryLine(Icons.Outlined.WarningAmber, "Se cruza con $n clase${if (n > 1) "s" else ""}"))
                }
                ConfirmAddDialog(
                    title = if (state.lessonId == null) "¿Agregar esta clase?" else "¿Guardar los cambios?",
                    accent = color,
                    headline = state.subjectName.ifBlank { "Clase sin nombre" },
                    lines = lines,
                    confirmLabel = if (state.lessonId == null) "Agregar" else "Guardar",
                    onConfirm = { confirming = false; go++ },
                    onDismiss = { confirming = false },
                )
            }
        }
    }
}

/** Aviso de cruce: con qué clase choca y botón para moverla a la siguiente hora libre. */
@Composable
private fun ConflictCard(conflicts: List<Conflict>, onMove: (groupIndex: Int) -> Unit) {
    val c = Agenda.colors
    Column(
        Modifier.fillMaxWidth().padding(top = Space.md).clip(MaterialTheme.shapes.large)
            .background(c.warning.copy(alpha = 0.14f)).padding(Space.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = c.warning)
            Spacer(Modifier.width(Space.sm))
            Text("Se cruza con otra clase", style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        conflicts.take(3).forEach { cf ->
            val b = cf.block
            Text(
                "${b.subjectName} · ${DayLong[b.weekday - 1]} de ${b.startTime} a ${b.endTime}",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(top = Space.xs),
            )
        }
        Text(
            "Puedes guardarla igual (se verán lado a lado) o moverla.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(top = Space.sm),
        )
        FilledTonalButton(onClick = { onMove(conflicts.first().groupIndex) }, modifier = Modifier.padding(top = Space.sm)) {
            Icon(Icons.Outlined.Schedule, contentDescription = null); Spacer(Modifier.width(Space.sm)); Text("Mover a la siguiente hora libre")
        }
    }
}

/** Vista previa en vivo: así se verá el bloque en el horario. */
@Composable
private fun PreviewCard(state: EditorState, color: Color) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val animColor by animateColorAsState(color, Motion.enter(level), label = "previewColor")
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 68.dp, height = 76.dp).clip(MaterialTheme.shapes.medium)
                    .background(Brush.verticalGradient(listOf(animColor, animColor.copy(alpha = 0.85f))))
                    .padding(Space.sm)
            ) {
                Column {
                    Text(state.subjectName.ifBlank { "Clase" }, style = MaterialTheme.typography.labelMedium, color = onSubjectColor(animColor), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (state.room.isNotBlank()) Text(state.room, style = MaterialTheme.typography.labelMedium, color = onSubjectColor(animColor).copy(alpha = 0.75f), maxLines = 1)
                }
            }
            Spacer(Modifier.width(Space.lg))
            Column {
                Text(state.subjectName.ifBlank { "Nueva clase" }, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                state.groups.forEach { gr ->
                    Text("${formatDays(gr.days)} · ${gr.start} → ${gr.end}", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
                Text(
                    state.remindMinutes?.let { "Aviso $it min antes" } ?: "Sin aviso",
                    style = MaterialTheme.typography.labelMedium, color = c.accent,
                )
            }
        }
    }
}

/** 24 colores + personalizado por tono. La selección lleva palomita (no depende solo del color). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selectedArgb: Int, onSelect: (Int) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val inPalette = SubjectPalette.any { it.argb() == selectedArgb }
    var hue by remember { mutableFloatStateOf(170f) }
    GlassCard {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm),
            maxItemsInEachRow = 8, modifier = Modifier.fillMaxWidth(),
        ) {
            SubjectPalette.forEachIndexed { i, col ->
                val sel = col.argb() == selectedArgb
                val scale by animateFloatAsState(if (sel) 1.15f else 1f, Motion.light(level), label = "sw$i")
                Box(
                    Modifier.size(32.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape).background(col)
                        .then(if (sel) Modifier.border(3.dp, c.text, CircleShape) else Modifier)
                        .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(col.argb()) }
                        .semantics { contentDescription = "Color ${i + 1}"; selected = sel },
                    contentAlignment = Alignment.Center,
                ) { if (sel) Icon(Icons.Outlined.Check, contentDescription = null, tint = onSubjectColor(col), modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val custom = colorFromHue(hue)
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(custom)
                    .then(if (!inPalette) Modifier.border(3.dp, c.text, CircleShape) else Modifier)
                    .clickable { onSelect(custom.argb()) }
                    .semantics { contentDescription = "Color personalizado"; selected = !inPalette }
            )
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Personalizado", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Slider(
                    value = hue, onValueChange = { hue = it; onSelect(colorFromHue(it).argb()) }, valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = custom, activeTrackColor = custom, inactiveTrackColor = c.surfaceHigh),
                )
            }
        }
    }
}

/** "Avisarme antes": regla de 0 a 60 min; la campana se agita y la hora del aviso rueda al cambiar. Se usa en clases y en la agenda. */
@Composable
fun ReminderPicker(minutes: Int?, classStart: Int, onChange: (Int?) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val value = minutes ?: 0
    val swing = remember { Animatable(0f) }
    LaunchedEffect(value) {
        if (value > 0 && level == MotionLevel.Full) {
            swing.animateTo(0f, keyframes { durationMillis = 520; 18f at 90; -14f at 200; 8f at 310; -4f at 420; 0f at 520 })
        }
    }
    val bellTint by animateColorAsState(if (value > 0) c.accent else c.textMuted, Motion.enter(level), label = "bell")
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (value > 0) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                contentDescription = null, tint = bellTint,
                modifier = Modifier.size(36.dp).graphicsLayer { rotationZ = swing.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.1f) },
            )
            Spacer(Modifier.width(Space.lg))
            Column {
                RollingText(if (value > 0) "$value min antes" else "Sin aviso", MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), c.text)
                RollingText(
                    if (value > 0) "Te aviso a las ${toHhmm((classStart - value).coerceAtLeast(0))}" else "No sonará ningún aviso",
                    MaterialTheme.typography.bodyMedium, c.textMuted,
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val m = (v / 5).toInt() * 5
                if (m != value) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(if (m == 0) null else m)
            },
            valueRange = 0f..60f, steps = 11,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surfaceHigh),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Sin aviso", "15", "30", "45", "60 min").forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textMuted) }
        }
    }
}

/** Guardar: pide confirmación; al confirmar, el botón se encoge hasta un círculo con palomita (M01) y guarda. */
@Composable
private fun SaveButton(saving: Boolean, trigger: Int, onRequest: () -> Unit, onClick: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    var morph by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (morph) 56.dp else 600.dp, Motion.enter(level, 300), label = "saveWidth")
    val radius by animateDpAsState(if (morph) 28.dp else 18.dp, Motion.enter(level, 300), label = "saveRadius")
    LaunchedEffect(trigger) { if (trigger > 0) morph = true }
    LaunchedEffect(morph) { if (morph) { delay(260); onClick() } }
    LaunchedEffect(saving) { if (!saving) morph = false }
    Box(Modifier.fillMaxWidth().padding(Dimens.screenPadding), contentAlignment = Alignment.Center) {
        Box(
            Modifier.height(56.dp).widthIn(max = width).fillMaxWidth()
                .clip(RoundedCornerShape(radius)).background(c.accent)
                .clickable(enabled = !morph) { onRequest() },
            contentAlignment = Alignment.Center,
        ) {
            if (morph) Icon(Icons.Outlined.Check, contentDescription = "Guardando", tint = c.onAccent)
            else Text("Guardar", style = MaterialTheme.typography.titleMedium, color = c.onAccent)
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Agenda.colors
    val shape = MaterialTheme.shapes.large
    Column(modifier.fillMaxWidth().glassSurface(c, shape).clip(shape).padding(Space.lg), content = content)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Agenda.colors.textMuted, modifier = Modifier.padding(top = Space.xl, bottom = Space.sm))
}

@Composable
private fun DayChip(short: String, long: String, selected: Boolean, onClick: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, Motion.light(level), label = "dayChip")
    val bg by animateColorAsState(if (selected) c.accent else c.surfaceHigh, Motion.enter(level, 150), label = "dayBg")
    Box(
        Modifier.size(38.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(bg)
            .clickable(onClick = onClick)
            .semantics { contentDescription = long; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { Text(short, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onAccent else c.text) }
}
