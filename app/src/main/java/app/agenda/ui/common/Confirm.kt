package app.agenda.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.agenda.ui.theme.*

/** Una línea del resumen: icono + texto. */
data class SummaryLine(val icon: ImageVector, val text: String)

/**
 * Ventana de confirmación antes de agregar o guardar algo.
 * Muestra un resumen de lo que se va a guardar; entra con un pequeño pop (muelle ligero).
 */
@Composable
fun ConfirmAddDialog(
    title: String,
    accent: Color,
    headline: String,
    lines: List<SummaryLine>,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val pop = remember { Animatable(if (level == MotionLevel.Full) 0.85f else 1f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, if (level == MotionLevel.Full) Motion.springLight() else tween(Motion.Quick)) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.85f) / 0.15f).coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(28.dp)).background(c.surfaceHigh)
        ) {
            // Franja del color de lo que se va a guardar
            Column(Modifier.fillMaxWidth().background(accent).padding(horizontal = Space.xxl, vertical = Space.lg)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = onSubjectColor(accent).copy(alpha = 0.8f))
                Text(headline, style = MaterialTheme.typography.headlineSmall, color = onSubjectColor(accent))
            }
            Column(Modifier.padding(horizontal = Space.xxl, vertical = Space.lg)) {
                lines.forEach { l ->
                    Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(l.icon, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.md))
                        Text(l.text, style = MaterialTheme.typography.bodyLarge, color = c.text)
                    }
                }
                Spacer(Modifier.height(Space.lg))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Revisar") }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(confirmLabel) }
                }
            }
        }
    }
}
