package app.agenda.ui.agenda

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.agenda.data.EventRow
import app.agenda.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate

fun typeLabel(type: String) = when (type) { "exam" -> "Examen"; "reminder" -> "Recordatorio"; else -> "Tarea" }

/** Casilla con animación M01: pop con muelle, la palomita se dibuja y vibra al marcar. */
@Composable
fun AnimatedCheck(checked: Boolean, color: Color, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val haptics = LocalHapticFeedback.current
    val draw by animateFloatAsState(if (checked) 1f else 0f, if (level == MotionLevel.Off) tween(0) else tween(220, delayMillis = 60, easing = Motion.EaseEmphasized), label = "checkDraw")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked && level == MotionLevel.Full) { pop.snapTo(0.8f); pop.animateTo(1f, Motion.springLight()) }
    }
    val fill by animateColorAsState(if (checked) color else Color.Transparent, Motion.enter(level, Motion.Quick), label = "checkFill")
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .clickable { if (!checked) haptics.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() }
            .semantics { role = Role.Checkbox; stateDescription = if (checked) "Hecha" else "Pendiente" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            val r = 7.dp.toPx()
            drawRoundRect(fill, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            if (!checked || draw < 1f) {
                drawRoundRect(if (checked) color else c.borderInput, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(2.dp.toPx()))
            }
            if (draw > 0f) {
                val p = Path().apply {
                    moveTo(size.width * 0.25f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.70f)
                    lineTo(size.width * 0.76f, size.height * 0.33f)
                }
                val pm = PathMeasure().apply { setPath(p, false) }
                val seg = Path(); pm.getSegment(0f, pm.length * draw, seg, true)
                drawPath(seg, onSubjectColor(color), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Fila de la agenda: franja del color del tipo, casilla, título (se tacha al completar), asignatura, hora y chips.
 * [stamp] ≠ null = recién agregado → se "plasma" en el tablero:
 *  - Tarea: cae desde arriba con muelle ligero y un brillo menta.
 *  - Recordatorio: sube flotando, suave, con brillo violeta.
 *  - Examen (rotunda): llega grande e inclinado, golpea con peso, rebota una vez y el brillo ámbar late dos veces.
 */
@Composable
fun EventItem(row: EventRow, onToggle: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier, stamp: Long? = null) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val done = row.doneAt != null
    var optimistic by remember(row.id, done) { mutableStateOf(done) }
    val typeCol = typeColor(row.type)
    val subjectCol = row.colorArgb?.let { subjectColor(it) } ?: typeCol
    val strike by animateFloatAsState(if (optimistic) 1f else 0f, Motion.enter(level, 200), label = "strike")
    val overdue = !done && row.dueDate < LocalDate.now().toString()
    val titleColor by animateColorAsState(if (optimistic) c.textMuted else c.text, label = "titleColor")

    val scale = remember { Animatable(1f) }
    val dy = remember { Animatable(0f) }
    val rot = remember { Animatable(0f) }
    val appear = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(stamp) {
        if (stamp == null || level == MotionLevel.Off) return@LaunchedEffect
        if (level == MotionLevel.Reduced) {
            glow.snapTo(1f); glow.animateTo(0f, tween(900)); return@LaunchedEffect
        }
        when (row.type) {
            "exam" -> {
                scale.snapTo(1.8f); rot.snapTo(-7f); appear.snapTo(0f); dy.snapTo(-24f); glow.snapTo(1f)
                launch { appear.animateTo(1f, tween(140)) }
                launch { rot.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
                launch { dy.animateTo(0f, tween(260, easing = Motion.EaseEmphasized)) }
                scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 320f))
                repeat(2) { glow.animateTo(0.35f, tween(220)); glow.animateTo(1f, tween(220)) }
                glow.animateTo(0f, tween(700))
            }
            "reminder" -> {
                dy.snapTo(48f); appear.snapTo(0f); scale.snapTo(0.96f); glow.snapTo(1f)
                launch { appear.animateTo(1f, tween(420, easing = Motion.EaseEmphasized)) }
                launch { scale.animateTo(1f, tween(520, easing = Motion.EaseEmphasized)) }
                dy.animateTo(0f, tween(560, easing = Motion.EaseEmphasized))
                glow.animateTo(0f, tween(900))
            }
            else -> {
                dy.snapTo(-56f); appear.snapTo(0f); scale.snapTo(1.08f); glow.snapTo(1f)
                launch { appear.animateTo(1f, tween(160)) }
                launch { scale.animateTo(1f, Motion.springLight()) }
                dy.animateTo(0f, Motion.springLight())
                glow.animateTo(0f, tween(800))
            }
        }
    }

    Row(
        modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value; rotationZ = rot.value
                translationY = dy.value.dp.toPx(); alpha = appear.value
            }
            .drawBehind {
                if (glow.value > 0f) drawRoundRect(
                    typeCol.copy(alpha = 0.55f * glow.value),
                    topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                    size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                )
            }
            .clip(MaterialTheme.shapes.large).background(c.surface)
            .drawBehind { drawRect(typeCol, size = Size(4.dp.toPx(), size.height)) }
            .clickable(onClick = onClick).padding(start = 4.dp, end = Space.lg, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCheck(optimistic, subjectCol, onToggle = { optimistic = !optimistic; onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                row.title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    if (strike > 0f) drawLine(c.textMuted, Offset(0f, size.height / 2), Offset(size.width * strike, size.height / 2), 2.dp.toPx())
                },
            )
            if (!row.rememberThat.isNullOrBlank()) {
                Text(
                    "Recordar que: ${row.rememberThat}", style = MaterialTheme.typography.labelMedium, color = typeCol,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.subjectName != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(subjectCol))
                    Spacer(Modifier.width(Space.xs))
                    Text(row.subjectName, style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(" · ", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                }
                Text(
                    listOfNotNull(typeLabel(row.type), row.dueTime).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = if (overdue) c.danger else c.textMuted,
                )
            }
        }
        if (row.seriesId != null) Icon(Icons.Outlined.Repeat, contentDescription = "Se repite", tint = c.textMuted, modifier = Modifier.padding(start = Space.xs).size(16.dp))
        if (row.images > 0) Chip("${row.images}", Icons.Outlined.Image)
        if (row.subtasksTotal > 0) Chip("${row.subtasksDone}/${row.subtasksTotal}", Icons.Outlined.Checklist)
        if (row.reminders > 0) Icon(Icons.Outlined.NotificationsActive, contentDescription = "Con aviso", tint = c.textMuted, modifier = Modifier.padding(start = Space.xs).size(16.dp))
        if (row.type == "exam") {
            Spacer(Modifier.width(Space.xs))
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(typeCol).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("Examen", style = MaterialTheme.typography.labelMedium, color = onSubjectColor(typeCol))
            }
        }
    }
}

@Composable
private fun Chip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = Agenda.colors
    Row(
        Modifier.padding(start = Space.xs).clip(RoundedCornerShape(999.dp)).background(c.surfaceHigh).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.text)
    }
}

