package app.agenda.ui.shell

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.AgendaApp
import app.agenda.ui.agenda.AgendaScreen
import app.agenda.ui.agenda.AgendaViewModel
import app.agenda.ui.agenda.EventEditor
import app.agenda.ui.common.DoneCelebration
import app.agenda.ui.today.GreetingHeader
import app.agenda.ui.today.TodayScreen
import app.agenda.data.TimetableBlock
import app.agenda.ui.common.ConfirmAddDialog
import app.agenda.ui.common.SummaryLine
import app.agenda.alarm.AlarmScheduler
import app.agenda.update.UpdateChecker
import app.agenda.update.UpdateInfo
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.LessonEditor
import app.agenda.ui.timetable.LessonSheet
import app.agenda.ui.timetable.TimetableGrid
import app.agenda.ui.timetable.TimetableViewModel
import kotlinx.coroutines.launch

enum class Dest(val label: String, val icon: ImageVector) {
    Today("Hoy", Icons.Outlined.WbSunny),
    Agenda("Agenda", Icons.Outlined.Checklist),
    Timetable("Horario", Icons.Outlined.GridView),
    Calendar("Calendario", Icons.Outlined.CalendarMonth),
    More("Más", Icons.Outlined.MoreHoriz),
}

@Composable
fun AppShell(vm: TimetableViewModel, agendaVm: AgendaViewModel) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    var dest by rememberSaveable { mutableStateOf(Dest.Today) }
    val rows by agendaVm.rows.collectAsStateWithLifecycle()
    val eventEditor by agendaVm.editor.collectAsStateWithLifecycle()
    val lastDeletedEvent by agendaVm.lastDeleted.collectAsStateWithLifecycle()
    val justAdded by agendaVm.justAdded.collectAsStateWithLifecycle()
    val completed by agendaVm.completed.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val lastDeleted by vm.lastDeleted.collectAsStateWithLifecycle()
    val justSaved by vm.justSaved.collectAsStateWithLifecycle()
    // Pantalla encendida mientras la app está abierta (ajuste en Más › Apariencia).
    val app = LocalContext.current.applicationContext as AgendaApp
    val view = androidx.compose.ui.platform.LocalView.current
    val keepOn = app.keepScreenOn.value
    DisposableEffect(keepOn) { view.keepScreenOn = keepOn; onDispose { view.keepScreenOn = false } }
    var openOccurrence by remember { mutableStateOf<String?>(null) }
    var confirmDuplicate by remember { mutableStateOf<TimetableBlock?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) { update = UpdateChecker.check(ctx).getOrNull() }

    LaunchedEffect(lastDeletedEvent) {
        if (lastDeletedEvent != null) {
            val r = snackbar.showSnackbar("Borrada", actionLabel = "Deshacer", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) agendaVm.undoDelete() else agendaVm.clearDeleted()
        }
    }

    // Lo recién agregado se "plasma" en el tablero de la Agenda.
    LaunchedEffect(justAdded) { if (justAdded != null) dest = Dest.Agenda }

    LaunchedEffect(lastDeleted) {
        if (lastDeleted != null) {
            val r = snackbar.showSnackbar("Clase borrada", actionLabel = "Deshacer", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(level)

        Scaffold(
            containerColor = c.bg.copy(alpha = 0f),
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = Dimens.bottomBar)) },
            floatingActionButton = {
                if (dest == Dest.Today || dest == Dest.Agenda) {
                    AddMenu(
                        modifier = Modifier.padding(bottom = Dimens.bottomBar),
                        onPick = { type -> agendaVm.newEvent(type) },
                    )
                } else if (dest == Dest.Timetable && blocks.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { vm.newLesson() },
                        containerColor = c.accent, contentColor = c.onAccent,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.padding(bottom = Dimens.bottomBar),
                    ) { Icon(Icons.Outlined.Add, contentDescription = "Nueva clase") }
                }
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                if (dest == Dest.Today) GreetingHeader(
                    app.userName.value,
                    rows.count { it.doneAt == null && it.dueDate <= java.time.LocalDate.now().toString() },
                    blocks.count { it.weekday == java.time.LocalDate.now().dayOfWeek.value },
                    Modifier.padding(horizontal = Dimens.screenPadding, vertical = Space.md),
                ) else Text(
                    dest.label, style = MaterialTheme.typography.displaySmall, color = c.text,
                    modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Space.md),
                )
                // M14: cambio de pestaña = desvanecido cruzado + 8dp.
                AnimatedContent(
                    targetState = dest,
                    transitionSpec = {
                        (fadeIn(Motion.enter(level, 200)) + slideInVertically(Motion.enter(level, 200)) { it / 40 })
                            .togetherWith(fadeOut(Motion.exit(level, 140)))
                    },
                    label = "tabs",
                ) { target ->
                    when (target) {
                        Dest.Timetable -> TimetableGrid(
                            blocks = blocks,
                            justSavedLessonId = justSaved,
                            onSavedAnimationDone = { vm.clearJustSaved() },
                            onBlockClick = { openOccurrence = it.occurrenceId },
                            onEmptySlot = { day, start -> vm.newLesson(day, start) },
                            onEmptyCreate = { vm.newLesson() },
                        )
                        Dest.Today -> TodayScreen(
                            blocks = blocks, rows = rows,
                            onToggle = { agendaVm.toggleDone(it) },
                            onEditEvent = { agendaVm.edit(it) },
                            onOpenClass = { openOccurrence = it.occurrenceId },
                        )
                        Dest.Agenda -> AgendaScreen(agendaVm, rows, onEdit = { agendaVm.edit(it) })
                        Dest.More -> MoreScreen()
                        else -> ComingSoon(target)
                    }
                }
            }
        }

        GlassBottomBar(dest, onSelect = { dest = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }

    update?.let { u ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Hay una versión nueva: ${u.version}") },
            text = {
                Text(
                    "Descarga ${u.apkName ?: "el APK"} en Chrome e instálalo encima de esta versión. " +
                        "Tus clases y ajustes se conservan."
                )
            },
            confirmButton = {
                TextButton(onClick = { UpdateChecker.openDownloadPage(ctx, u.pageUrl); update = null }) { Text("Descargar") }
            },
            dismissButton = { TextButton(onClick = { update = null }) { Text("Luego") } },
        )
    }

    // Confirmación antes de duplicar (agregar) una clase
    confirmDuplicate?.let { b ->
        ConfirmAddDialog(
            title = "¿Duplicar esta clase?",
            accent = subjectColor(b.colorArgb),
            headline = b.subjectName,
            lines = listOf(SummaryLine(Icons.Outlined.ContentCopy, "Se crea una copia y se abre para editarla")),
            confirmLabel = "Duplicar",
            onConfirm = { confirmDuplicate = null; vm.duplicate(b.lessonId) },
            onDismiss = { confirmDuplicate = null },
        )
    }

    // Hoja de detalle
    val sheetBlock = blocks.firstOrNull { it.occurrenceId == openOccurrence }
    if (sheetBlock != null) {
        LessonSheet(
            block = sheetBlock,
            allOfLesson = blocks.filter { it.lessonId == sheetBlock.lessonId },
            onEdit = { openOccurrence = null; vm.editLesson(sheetBlock.lessonId) },
            onDuplicate = { openOccurrence = null; confirmDuplicate = sheetBlock },
            onDelete = { openOccurrence = null; vm.delete(sheetBlock.lessonId) },
            onDismiss = { openOccurrence = null },
        )
    }

    // Editor de tareas a pantalla completa
    eventEditor?.let { st ->
        EventEditor(
            state = st, subjects = subjects, blocks = blocks,
            busyDays = rows.filter { it.doneAt == null }.map { it.dueDate }.toSet(),
            onChange = { agendaVm.update(it) },
            onSwitchType = { t -> agendaVm.switchType(t, subjects.firstOrNull { it.id == st.subjectId }?.name) },
            onSave = { agendaVm.save() },
            onDiscardDraft = { agendaVm.discardDraft() },
            onDeleteSeries = { st.seriesId?.let { agendaVm.deleteSeries(it) } },
            onClose = { agendaVm.close() },
        )
    }

    // Editor a pantalla completa
    editor?.let { st ->
        LessonEditor(
            state = st, subjects = subjects, blocks = blocks, existingMatch = vm::existingMatch,
            onChange = { vm.updateEditor(it) }, onSave = { vm.save() }, onClose = { vm.closeEditor() },
        )
    }

    // "¡Felicidades!" al marcar algo como hecho (encima de todo, no bloquea los toques).
    completed?.let { done ->
        key(done.stamp) { DoneCelebration(done.type, done.title, done.stamp, onDone = { agendaVm.clearCompleted() }) }
    }
}

/** M07: barra inferior de cristal, flotante; el destino activo hace un pequeño pop. */
@Composable
private fun GlassBottomBar(current: Dest, onSelect: (Dest) -> Unit, modifier: Modifier = Modifier) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(Space.md)
            .fillMaxWidth()
            .height(Dimens.bottomBar - Space.md)
            .glassSurface(c, shape, strong = true)
            .clip(shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dest.entries.forEach { d ->
            val sel = d == current
            val scale by animateFloatAsState(if (sel) 1.12f else 1f, Motion.light(level), label = "navPop")
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable { onSelect(d) }
                    .semantics { role = Role.Tab; selected = sel },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (sel) c.accent.copy(alpha = 0.16f) else c.accent.copy(alpha = 0f))
                        .padding(horizontal = Space.md, vertical = 2.dp)
                ) {
                    Icon(
                        d.icon, contentDescription = null, tint = if (sel) c.accent else c.textMuted,
                        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
                Text(d.label, style = MaterialTheme.typography.labelMedium, color = if (sel) c.accent else c.textMuted)
            }
        }
    }
}

@Composable
private fun ComingSoon(d: Dest) {
    val c = Agenda.colors
    Column(Modifier.fillMaxSize().padding(Space.x3), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(d.icon, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Space.lg))
        Text("${d.label} llega en la siguiente etapa", style = MaterialTheme.typography.titleMedium, color = c.text)
        Text("Por ahora, prueba el horario y los avisos.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}



/** "Anotar": se despliega en Tarea, Examen y Recordatorio (entran escalonadas con un muelle ligero). */
@Composable
private fun AddMenu(modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 45f else 0f, Motion.light(level), label = "fabRot")
    val options = listOf(
        Triple("reminder", "Recordatorio", Icons.Outlined.NotificationsNone),
        Triple("exam", "Examen", Icons.Outlined.Quiz),
        Triple("homework", "Tarea", Icons.Outlined.Assignment),
    )
    Column(modifier, horizontalAlignment = Alignment.End) {
        options.forEachIndexed { i, (type, label, icon) ->
            val delayMs = (options.size - 1 - i) * Motion.StaggerMs
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(Motion.enter(level, 180 + delayMs)) + scaleIn(Motion.enter(level, 200 + delayMs), initialScale = 0.6f) +
                    slideInVertically(Motion.enter(level, 200 + delayMs)) { it / 2 },
                exit = fadeOut(Motion.exit(level, 120)) + scaleOut(Motion.exit(level, 120), targetScale = 0.8f),
            ) {
                Row(
                    Modifier.padding(bottom = Space.md).clip(MaterialTheme.shapes.large)
                        .glassSurface(c, MaterialTheme.shapes.large, strong = true)
                        .clickable { open = false; onPick(type) }
                        .padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = c.text)
                    Spacer(Modifier.width(Space.md))
                    Icon(icon, contentDescription = null, tint = c.accent)
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { open = !open },
            containerColor = c.accent, contentColor = c.onAccent,
            shape = MaterialTheme.shapes.large,
            icon = { Icon(Icons.Outlined.Add, contentDescription = if (open) "Cerrar menú" else "Anotar", modifier = Modifier.graphicsLayer { rotationZ = rot }) },
            text = { Text(if (open) "Cerrar" else "Anotar") },
        )
    }
}
