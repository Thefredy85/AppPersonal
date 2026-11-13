package app.agenda.ui.timetable

import app.agenda.data.TimetableBlock

/** Una clase existente que se cruza con el horario que se está editando. */
data class Conflict(val block: TimetableBlock, val groupIndex: Int)

private fun overlaps(s1: Int, e1: Int, s2: Int, e2: Int) = s1 < e2 && s2 < e1

/** Clases (de otras lecciones) que se cruzan con algún grupo de días/horas del editor. */
fun findConflicts(state: EditorState, blocks: List<TimetableBlock>): List<Conflict> =
    state.groups.flatMapIndexed { gi, g ->
        val s = toMinutes(g.start); val e = toMinutes(g.end)
        blocks.filter { b ->
            b.lessonId != state.lessonId && b.weekday in g.days &&
                overlaps(s, e, toMinutes(b.startTime), toMinutes(b.endTime))
        }.map { Conflict(it, gi) }
    }.distinctBy { it.block.occurrenceId }

/** Horas ocupadas (inicio, fin) en los días de un grupo, para pintarlas en gris en el reloj. */
fun busyRanges(state: EditorState, group: SlotGroup, blocks: List<TimetableBlock>): List<Pair<Int, Int>> =
    blocks.filter { it.lessonId != state.lessonId && it.weekday in group.days }
        .map { toMinutes(it.startTime) to toMinutes(it.endTime) }

/** Primer inicio libre (mismo largo) después de la hora actual del grupo, en medias horas; null si no cabe hoy. */
fun nextFreeStart(state: EditorState, groupIndex: Int, blocks: List<TimetableBlock>): Int? {
    val g = state.groups.getOrNull(groupIndex) ?: return null
    val dur = toMinutes(g.end) - toMinutes(g.start)
    val busy = busyRanges(state, g, blocks)
    var s = toMinutes(g.start)
    while (s + dur <= 1435) {
        if (busy.none { (bs, be) -> overlaps(s, s + dur, bs, be) }) return s
        s = (s / SNAP + 1) * SNAP
    }
    return null
}
