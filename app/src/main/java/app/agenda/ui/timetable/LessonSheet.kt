package app.agenda.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.agenda.data.TimetableBlock
import app.agenda.ui.theme.*

/** S05b · Detalle de clase en hoja de cristal (sube sin rebote: springHeavy de Material 3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonSheet(
    block: TimetableBlock,
    allOfLesson: List<TimetableBlock>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Agenda.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Sin desenfoque real (llega con Haze), el cristal translúcido deja ver los bloques de color detrás.
        // Por legibilidad, la hoja es casi opaca y el fondo se oscurece más.
        containerColor = c.surfaceHigh.copy(alpha = 0.97f),
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = Space.xxl).padding(bottom = Space.x3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(MaterialTheme.shapes.small).background(c.subjects[block.colorIndex.coerceIn(0, 9)]))
                Spacer(Modifier.width(Space.md))
                Text(block.subjectName, style = MaterialTheme.typography.headlineSmall, color = c.text)
            }
            Spacer(Modifier.height(Space.lg))
            allOfLesson.sortedWith(compareBy({ it.weekday }, { it.startTime })).forEach { o ->
                Text(DayLong[o.weekday - 1].replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Text("${o.startTime} → ${o.endTime}", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.padding(bottom = Space.sm))
            }
            block.room?.let { InfoRow(Icons.Outlined.Place, "Aula $it") }
            InfoRow(
                Icons.Outlined.NotificationsNone,
                block.remindMinutesBefore?.let { "Aviso $it min antes" } ?: "Sin aviso",
            )
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null); Spacer(Modifier.width(Space.sm)); Text("Editar")
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null); Spacer(Modifier.width(Space.sm)); Text("Borrar")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = Agenda.colors
    Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.text)
    }
}
