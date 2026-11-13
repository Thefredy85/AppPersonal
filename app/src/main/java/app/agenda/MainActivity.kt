package app.agenda

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.agenda.ui.shell.AppShell
import app.agenda.ui.theme.AgendaTheme
import app.agenda.ui.theme.LocalMotionLevel
import app.agenda.ui.theme.MotionLevel
import app.agenda.ui.theme.ThemeMode
import app.agenda.ui.timetable.TimetableViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AgendaApp
        // Al abrir, recalcula los avisos (por si el sistema los perdió).
        lifecycleScope.launch { app.rescheduleAlarms() }

        // Movimiento reducido automático si el sistema tiene las animaciones apagadas.
        val systemScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val motion = if (systemScale == 0f) MotionLevel.Reduced else MotionLevel.Full

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TimetableViewModel(app.repository) as T
        }
        setContent {
            AgendaTheme(mode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalMotionLevel provides motion) {
                    AppShell(viewModel(factory = factory))
                }
            }
        }
    }
}
