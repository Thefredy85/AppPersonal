package app.agenda.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** 24 colores de asignatura (propios). Todos claros: el texto oscuro se lee sobre ellos. */
val SubjectPalette: List<Color> = listOf(
    0xFFF28B82, 0xFFF6AE6B, 0xFFFDD663, 0xFFC5E17A, 0xFFA8DAB5, 0xFF7DD3C0,
    0xFF6ED3E8, 0xFF8AB4F8, 0xFF9FA8FF, 0xFFB4A7F5, 0xFFD7A6F5, 0xFFF5A3D0,
    0xFFFF8FA3, 0xFFFFB38A, 0xFFE8C27A, 0xFFC9B79C, 0xFFAEB8C2, 0xFF8FD9A8,
    0xFF5EEAD4, 0xFF7CC4FF, 0xFFC3B1FF, 0xFFF9A8D4, 0xFFFCD34D, 0xFFA3E635,
).map { Color(it) }

fun subjectColor(argb: Int): Color = if (argb == 0) SubjectPalette[5] else Color(argb)

/** Texto sobre el color de la asignatura: oscuro si el color es claro, claro si es oscuro (contraste ≥ 4.5:1). */
fun onSubjectColor(bg: Color): Color = if (bg.luminance() > 0.36f) Color(0xFF101316) else Color(0xFFF4F6FA)

/** Color personalizado desde un tono 0–360 (saturación y brillo fijos para que siempre sea legible). */
fun colorFromHue(hue: Float, saturation: Float = 0.62f, value: Float = 0.95f): Color =
    Color.hsv(hue.coerceIn(0f, 360f), saturation, value)

fun Color.argb(): Int = toArgb()

/** Color de cada tipo de la agenda: tarea (menta), examen (ámbar intenso, el más importante), recordatorio (violeta). */
fun typeColor(type: String): Color = when (type) {
    "exam" -> Color(0xFFFF9F43)
    "reminder" -> Color(0xFFA78BFA)
    else -> Color(0xFF34E5B4)
}
