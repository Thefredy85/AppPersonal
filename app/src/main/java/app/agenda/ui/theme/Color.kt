package app.agenda.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Generado desde tokens.json / tokens-light.json (v2: cristal + aurora). No editar a mano.
@Immutable
data class AgendaColors(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderInput: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val onSubject: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassAlpha: Float,
    val glassStrongAlpha: Float,
    val aurora: List<Color>,
    val auroraAlpha: Float,
    val subjects: List<Color>,
)

val DarkAgendaColors = AgendaColors(
    bg = Color(0xFF0B0E14),
    surface = Color(0xFF191D21),
    surfaceHigh = Color(0xFF23282D),
    border = Color(0xFF2E343A),
    borderInput = Color(0xFF7A8494),
    text = Color(0xFFF4F6FA),
    textMuted = Color(0xFFB5BDC9),
    accent = Color(0xFF34E5B4),
    onAccent = Color(0xFF03241A),
    danger = Color(0xFFFF7A70),
    success = Color(0xFF6FDC9A),
    warning = Color(0xFFFFC65C),
    onSubject = Color(0xFF101316),
    glassTint = Color(0xFF121722),
    glassBorder = Color(255, 255, 255, alpha = 36),
    glassHighlight = Color(255, 255, 255, alpha = 71),
    glassAlpha = 0.55f,
    glassStrongAlpha = 0.7f,
    aurora = listOf(Color(0xFF2EE6B0), Color(0xFF7C5CFF), Color(0xFF2F80FF), Color(0xFFFF4FA3)),
    auroraAlpha = 0.4f,
    subjects = listOf(Color(0xFFF28B82), Color(0xFFF6AE6B), Color(0xFFFDD663), Color(0xFFA8DAB5), Color(0xFF7DD3C0), Color(0xFF8AB4F8), Color(0xFFB4A7F5), Color(0xFFF5A3D0), Color(0xFFC9B79C), Color(0xFFAEB8C2)),
)

val LightAgendaColors = AgendaColors(
    bg = Color(0xFFF4F6FB),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEEF1F4),
    border = Color(0xFFDDE2E7),
    borderInput = Color(0xFF7A848E),
    text = Color(0xFF10141A),
    textMuted = Color(0xFF4A5361),
    accent = Color(0xFF006B50),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFB3261E),
    success = Color(0xFF1E7A47),
    warning = Color(0xFF8A5A00),
    onSubject = Color(0xFF101316),
    glassTint = Color(0xFFFFFFFF),
    glassBorder = Color(255, 255, 255, alpha = 178),
    glassHighlight = Color(255, 255, 255, alpha = 230),
    glassAlpha = 0.55f,
    glassStrongAlpha = 0.72f,
    aurora = listOf(Color(0xFF7CF0CF), Color(0xFFB9A8FF), Color(0xFF9CC3FF), Color(0xFFFFB3D6)),
    auroraAlpha = 0.55f,
    subjects = listOf(Color(0xFFF28B82), Color(0xFFF6AE6B), Color(0xFFFDD663), Color(0xFFA8DAB5), Color(0xFF7DD3C0), Color(0xFF8AB4F8), Color(0xFFB4A7F5), Color(0xFFF5A3D0), Color(0xFFC9B79C), Color(0xFFAEB8C2)),
)

val LocalAgendaColors = staticCompositionLocalOf { DarkAgendaColors }
