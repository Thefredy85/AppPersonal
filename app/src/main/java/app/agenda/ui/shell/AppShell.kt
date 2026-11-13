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
fun AppShell(vm: TimetableViewModel) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    // M1: el horario es la pantalla principal; Hoy, Agenda y Calendario llegan en M2.
    var dest by rememberSaveable { mutableStateOf(Dest.Timetable) }
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
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) { update = UpdateChecker.check(ctx).getOrNull() }

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
                if (dest == Dest.Timetable && blocks.isNotEmpty()) {
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
                Text(
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

    // Hoja de detalle
    val sheetBlock = blocks.firstOrNull { it.occurrenceId == openOccurrence }
    if (sheetBlock != null) {
        LessonSheet(
            block = sheetBlock,
            allOfLesson = blocks.filter { it.lessonId == sheetBlock.lessonId },
            onEdit = { openOccurrence = null; vm.editLesson(sheetBlock.lessonId) },
            onDuplicate = { openOccurrence = null; vm.duplicate(sheetBlock.lessonId) },
            onDelete = { openOccurrence = null; vm.delete(sheetBlock.lessonId) },
            onDismiss = { openOccurrence = null },
        )
    }

    // Editor a pantalla completa
    editor?.let { st ->
        LessonEditor(
            state = st, subjects = subjects, blocks = blocks, existingMatch = vm::existingMatch,
            onChange = { vm.updateEditor(it) }, onSave = { vm.save() }, onClose = { vm.closeEditor() },
        )
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

