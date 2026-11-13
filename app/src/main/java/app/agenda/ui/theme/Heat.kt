package app.agenda.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.agenda.data.EventRow
import app.agenda.data.TimetableBlock
import java.time.LocalDate

/**
 * Carga de un día: examen pesa 3, tarea 1,5, recordatorio 1 y cada hora de clase 0,5.
 * Escala de calor propia: libre (azul frío) → ligero (menta) → medio (miel) → pesado (coral) → al tope (magenta).
 */
fun dayLoad(date: LocalDate, rows: List<EventRow>, blocks: List<TimetableBlock>): Float {
    val d = date.toString()
    val ev = rows.filter { it.doneAt == null && it.dueDate == d }.sumOf {
        when (it.type) { "exam" -> 3.0; "reminder" -> 1.0; else -> 1.5 }
    }
    val classH = blocks.filter { it.weekday == date.dayOfWeek.value }.sumOf {
        (it.endTime.toMin() - it.startTime.toMin()) / 60.0
    }
    return (ev + classH * 0.5).toFloat()
}

private fun String.toMin() = split(":").let { it[0].toInt() * 60 + it[1].toInt() }

private val Stops = listOf(
    0f to Color(0xFF2A3350),
    1f to Color(0xFF2BD4A4),
    4f to Color(0xFFFFC857),
    7f to Color(0xFFFF7A59),
    10f to Color(0xFFE8457C),
)

fun heatColor(load: Float): Color {
    if (load <= 0f) return Stops.first().second
    for (i in 1 until Stops.size) {
        val (a, ca) = Stops[i - 1]; val (b, cb) = Stops[i]
        if (load <= b) return lerp(ca, cb, (load - a) / (b - a))
    }
    return Stops.last().second
}

fun heatLabel(load: Float): String = when {
    load <= 0f -> "Libre"
    load < 2.5f -> "Ligero"
    load < 5.5f -> "Medio"
    load < 8.5f -> "Pesado"
    else -> "Al tope"
}
