package app.agenda.ui.theme

// Cristal y fondo aurora. Requiere la librería Haze (Apache 2.0):
//   implementation("dev.chrisbanes.haze:haze:<versión>")
//   implementation("dev.chrisbanes.haze:haze-blur:<versión>")
// La API exacta de Haze se fija en replica-build con la versión que se instale.
// En Android 11 o anterior Haze usa una capa translúcida en vez de desenfoque.

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Glass {
    val blur: Dp = 20.dp
    val blurStrong: Dp = 28.dp
    val border: Dp = 1.dp
}

/**
 * Fondo con 4 manchas de color muy difuminadas (M10).
 * La deriva se lee DENTRO del bloque de dibujo (fase draw): el fondo no recompone en cada fotograma.
 * El desenfoque es fijo; nunca se anima. Quieto si el nivel de movimiento no es Full.
 */
@Composable
fun AuroraBackground(level: MotionLevel, modifier: Modifier = Modifier) {
    val c = Agenda.colors
    val drift: State<Float> = if (level == MotionLevel.Full) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(12_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "auroraDrift",
        )
    } else remember { mutableFloatStateOf(0.5f) }
    Box(modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize().blur(120.dp)) {
            val t = drift.value
            val w = size.width; val h = size.height; val r = w * 0.55f
            val spots = listOf(
                Offset(w * (0.15f + 0.10f * t), h * 0.12f), Offset(w * 0.90f, h * (0.25f + 0.08f * t)),
                Offset(w * (0.70f - 0.12f * t), h * 0.70f), Offset(w * 0.10f, h * (0.85f - 0.06f * t)),
            )
            spots.forEachIndexed { i, p ->
                drawCircle(Brush.radialGradient(listOf(c.aurora[i].copy(alpha = c.auroraAlpha), c.aurora[i].copy(alpha = 0f)), p, r), r, p)
            }
        }
    }
}

/**
 * Superficie de cristal. Con Haze: aplicar el efecto de desenfoque del contenido de detrás
 * (blur = Glass.blur o Glass.blurStrong) y encima este tinte + borde.
 * Con reduceTransparency = true: superficie sólida (surface), sin desenfoque.
 */
fun Modifier.glassSurface(
    colors: AgendaColors, shape: Shape, strong: Boolean = false, reduceTransparency: Boolean = false,
): Modifier =
    if (reduceTransparency) this.background(colors.surface, shape).border(Glass.border, colors.border, shape)
    else this
        .background(colors.glassTint.copy(alpha = if (strong) colors.glassStrongAlpha else colors.glassAlpha), shape)
        .border(Glass.border, Brush.verticalGradient(listOf(colors.glassHighlight, colors.glassBorder)), shape)
