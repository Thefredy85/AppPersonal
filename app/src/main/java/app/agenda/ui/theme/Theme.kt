package app.agenda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemeMode { System, Dark, Light }

@Composable
fun AgendaTheme(mode: ThemeMode = ThemeMode.Dark, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Dark -> true; ThemeMode.Light -> false }
    val c = if (dark) DarkAgendaColors else LightAgendaColors
    // Color dinámico desactivado a propósito: los colores de asignatura deben ser estables.
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textMuted,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHigh, outline = c.borderInput,
        outlineVariant = c.border, error = c.danger, onError = c.onAccent,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textMuted,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHigh, outline = c.borderInput,
        outlineVariant = c.border, error = c.danger, onError = c.onAccent,
    )
    CompositionLocalProvider(LocalAgendaColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AgendaTypography, shapes = AgendaShapes, content = content)
    }
}

object Agenda {
    val colors: AgendaColors @Composable get() = LocalAgendaColors.current
}
