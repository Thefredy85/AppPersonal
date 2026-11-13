package app.agenda.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Nivel de animación elegido en Ajustes (Reducidas se activa sola si el sistema tiene la escala en 0). */
enum class MotionLevel { Full, Reduced, Off }
val LocalMotionLevel = compositionLocalOf { MotionLevel.Full }

/** Tokens de motion.md. */
object Motion {
    const val Quick = 120
    const val Standard = 240
    const val Slow = 400
    const val StaggerMs = 30
    const val StaggerMaxItems = 8

    val EaseEmphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)   // curva firma: entradas
    val EaseExit = CubicBezierEasing(0.3f, 0f, 1f, 1f)              // salidas
    val EaseStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)          // en pantalla

    fun <T> springLight(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 700f)      // ~10 % rebote
    fun <T> springHeavy(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 380f)        // sin rebote

    /** Elige la especificación según el nivel de movimiento (reducido: sin rebote, mitad de duración). */
    fun <T> enter(level: MotionLevel, durationMs: Int = Standard): FiniteAnimationSpec<T> = when (level) {
        MotionLevel.Full -> tween(durationMs, easing = EaseEmphasized)
        MotionLevel.Reduced -> tween(durationMs / 2, easing = LinearOutSlowInEasing)
        MotionLevel.Off -> snap()
    }
    fun <T> exit(level: MotionLevel, durationMs: Int = (Standard * 0.7f).toInt()): FiniteAnimationSpec<T> = when (level) {
        MotionLevel.Full -> tween(durationMs, easing = EaseExit)
        MotionLevel.Reduced -> tween(durationMs / 2)
        MotionLevel.Off -> snap()
    }
    fun <T> light(level: MotionLevel): FiniteAnimationSpec<T> = when (level) {
        MotionLevel.Full -> springLight()
        MotionLevel.Reduced -> tween(Quick)
        MotionLevel.Off -> snap()
    }
}

/**
 * Entrada "sube 12dp + aparece" con escalonado de 30 ms (máx. 8 elementos).
 * progress (0→1) lo da la pantalla con una sola transición; aquí se lee en la fase de dibujo
 * (graphicsLayer con lambda), así no recompone en cada fotograma.
 */
fun Modifier.enterRise(progress: () -> Float, level: MotionLevel): Modifier = graphicsLayer {
    val p = progress()
    alpha = p
    if (level == MotionLevel.Full) translationY = (1f - p) * 12.dp.toPx()
}

/** Retraso del elemento i dentro del escalonado. */
fun staggerDelay(index: Int): Int = minOf(index, Motion.StaggerMaxItems - 1) * Motion.StaggerMs
