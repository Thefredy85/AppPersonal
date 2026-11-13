package app.agenda.ui.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.agenda.alarm.AlarmScheduler
import app.agenda.data.TimetableBlock
import app.agenda.ui.theme.*
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/** S05b · Detalle de clase v3: cabecera del color de la asignatura, cuenta atrás y acciones rápidas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonSheet(
    block: TimetableBlock,
    allOfLesson: List<TimetableBlock>,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val color = subjectColor(block.colorArgb)
    val fg = onSubjectColor(color)

    // Próxima vez que empieza cualquiera de sus ocurrencias.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    val next = allOfLesson.mapNotNull { AlarmScheduler.nextStart(it.weekday, it.startTime, now.minusMinutes(1)) }.minOrNull()
    val running = allOfLesson.any {
        it.weekday == now.dayOfWeek.value && toMinutes(it.startTime) <= now.hour * 60 + now.minute && now.hour * 60 + now.minute < toMinutes(it.endTime)
    }

    // Entrada del contenido: sube y aparece (M02/M05).
    val enter = remember { Animatable(if (level == MotionLevel.Full) 0f else 1f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(Motion.Standard + 80, easing = Motion.EaseEmphasized)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh.copy(alpha = 0.97f),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        // Cabecera del color de la clase
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.75f))))
                .padding(horizontal = Space.xxl, vertical = Space.xl)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = 0.4f)))
            Spacer(Modifier.height(Space.lg))
            Text(block.subjectName, style = MaterialTheme.typography.headlineSmall, color = fg)
            Spacer(Modifier.height(Space.xs))
            val countdown = when {
                running -> "En curso ahora"
                next != null -> "Empieza en " + human(Duration.between(now, next))
                else -> ""
            }
            RollingText(countdown, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), fg)
        }

        Column(
            Modifier.padding(horizontal = Space.xxl).padding(top = Space.lg, bottom = Space.x3)
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 16.dp.toPx() }
        ) {
            allOfLesson.groupBy { it.startTime to it.endTime }.forEach { (time, list) ->
                InfoRow(Icons.Outlined.Schedule, "${formatDays(list.map { it.weekday })} · ${time.first} → ${time.second}")
            }
            block.room?.let { InfoRow(Icons.Outlined.Place, "Aula $it") }
            InfoRow(
                if (block.remindMinutesBefore != null) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                block.remindMinutesBefore?.let { "Aviso $it min antes" } ?: "Sin aviso",
            )
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.fillMaxWidth()) {
                QuickAction(Icons.Outlined.Edit, "Editar", c.accent, Modifier.weight(1f), onEdit)
                QuickAction(Icons.Outlined.ContentCopy, "Duplicar", c.text, Modifier.weight(1f), onDuplicate)
                QuickAction(Icons.Outlined.DeleteOutline, "Borrar", c.danger, Modifier.weight(1f), onDelete)
            }
        }
    }
}

private fun human(d: Duration): String {
    val mins = d.toMinutes().coerceAtLeast(0)
    val days = mins / 1440; val h = (mins % 1440) / 60; val m = mins % 60
    return when {
        days > 0 -> "$days d $h h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Agenda.colors
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(c.surface).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(Space.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    val c = Agenda.colors
    Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.text)
    }
}
