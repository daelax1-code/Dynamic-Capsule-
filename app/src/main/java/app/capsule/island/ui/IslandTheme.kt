package app.capsule.island.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val IslandTypography = Typography().let { t ->
    t.copy(
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Die Island benutzt immer das dunkle Material-You-Farbschema (passend zur Kamera). */
@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicDarkColorScheme(LocalContext.current),
        typography = IslandTypography,
        content = content,
    )
}

/** Hellt dunkle Akzentfarben (z. B. aus Albumcovern) auf, damit sie auf Schwarz lesbar bleiben. */
fun readableAccent(argb: Int?, fallback: Color): Color {
    if (argb == null) return fallback
    var c = Color(argb)
    var tries = 0
    while (c.luminance() < 0.28f && tries < 6) {
        c = Color.White.copy(alpha = 0.25f).compositeOver(c)
        tries++
    }
    return c
}

val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
val BigTimeStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = (-1).sp)
