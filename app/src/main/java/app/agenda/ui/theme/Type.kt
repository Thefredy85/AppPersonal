package app.agenda.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Fuente: Plus Jakarta Sans (OFL) como fuente descargable de Google Fonts; si no está, Roboto del sistema.
// (La familia se conecta en el build; aquí van tamaños y pesos de tokens.json.)
private fun s(size: Int, line: Int, w: Int) = TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(w))

val AgendaTypography = Typography(
    displaySmall = s(32, 40, 800),
    headlineSmall = s(24, 32, 700),
    titleMedium = s(18, 24, 700),
    bodyLarge = s(16, 24, 400),
    bodyMedium = s(14, 20, 400),
    labelMedium = s(12, 16, 600),
)

val AgendaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)
