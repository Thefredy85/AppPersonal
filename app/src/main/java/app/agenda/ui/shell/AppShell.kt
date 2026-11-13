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
    var openOccurrence by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

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
                            onBlockClick = { openOccurrence = it.occurrenceId },
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

    // Hoja de detalle
    val sheetBlock = blocks.firstOrNull { it.occurrenceId == openOccurrence }
    if (sheetBlock != null) {
        LessonSheet(
            block = sheetBlock,
            allOfLesson = blocks.filter { it.lessonId == sheetBlock.lessonId },
            onEdit = { openOccurrence = null; vm.editLesson(sheetBlock.lessonId) },
            onDelete = { openOccurrence = null; vm.delete(sheetBlock.lessonId) },
            onDismiss = { openOccurrence = null },
        )
    }

    // Editor a pantalla completa
    editor?.let { st ->
        LessonEditor(
            state = st, subjects = subjects, existingMatch = vm::existingMatch,
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

/** Más → ajustes de avisos: permisos, batería del OPPO y aviso de prueba. */
@Composable
private fun MoreScreen() {
    val c = Agenda.colors
    val ctx = LocalContext.current
    val app = ctx.applicationContext as AgendaApp
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val pm = ctx.getSystemService(PowerManager::class.java)
    val exactOk = remember(refresh) { app.alarms.canScheduleExact() }
    val batteryOk = remember(refresh) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    var scheduled by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.bottomBar + Space.x4)
    ) {
        Text("Avisos", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(vertical = Space.md))
        SettingCard(
            title = "Notificaciones",
            body = "Necesarias para que veas los avisos de clase.",
            action = "Permitir",
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            },
        )
        SettingCard(
            title = if (exactOk) "Alarmas exactas: activadas" else "Alarmas exactas: desactivadas",
            body = "Sin este permiso, Android puede retrasar los avisos varios minutos.",
            action = if (exactOk) null else "Activar",
            onClick = {
                if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            },
        )
        SettingCard(
            title = if (batteryOk) "Ahorro de batería: sin restricción" else "Ahorro de batería: restringido",
            body = "En teléfonos OPPO (ColorOS), el ahorro de batería puede cortar los avisos. " +
                "Después de pulsar el botón, revisa también: Ajustes › Batería › esta app › Permitir actividad en segundo plano, y activa \"Inicio automático\".",
            action = if (batteryOk) null else "Quitar restricción",
            onClick = {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            },
        )
        SettingCard(
            title = "Probar un aviso",
            body = "Envía un aviso en 15 segundos. Bloquea el teléfono y espera: si llega, los avisos de clase funcionarán.",
            action = "Probar",
            onClick = { app.alarms.scheduleTest(15) },
        )
        SettingCard(
            title = "Reprogramar avisos",
            body = scheduled?.let { "Avisos programados: $it" } ?: "Vuelve a calcular el próximo aviso de cada clase.",
            action = "Reprogramar",
            onClick = { scope.launch { scheduled = app.rescheduleAlarms(); refresh++ } },
        )
    }
    // Al volver de los ajustes del sistema, refresca el estado.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
}

@Composable
private fun SettingCard(title: String, body: String, action: String?, onClick: () -> Unit) {
    val c = Agenda.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = Space.md)
            .clip(MaterialTheme.shapes.large).background(c.surface).padding(Space.lg)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(Space.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        if (action != null) {
            Spacer(Modifier.height(Space.md))
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}
