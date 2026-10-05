package app.dropduo.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Brand colors from branding/README.md: ink, cobalt, pure white; dark mode keeps the same restraint. */
@Immutable data class Palette(
    val background: Color, val surface: Color, val surfaceHigh: Color, val hairline: Color,
    val text: Color, val secondary: Color, val tertiary: Color,
    val accent: Color, val onAccent: Color, val solid: Color, val onSolid: Color,
    val positive: Color, val pending: Color, val danger: Color, val dark: Boolean,
)

private val Light = Palette(
    background = Color(0xFFFFFFFF), surface = Color(0xFFF5F6F8), surfaceHigh = Color(0xFFECEEF1), hairline = Color(0xFFE6E8EC),
    text = Color(0xFF16171A), secondary = Color(0xFF6B6F76), tertiary = Color(0xFFA0A4AB),
    accent = Color(0xFF2558DD), onAccent = Color.White, solid = Color(0xFF202124), onSolid = Color.White,
    positive = Color(0xFF1F9D5B), pending = Color(0xFFD48A00), danger = Color(0xFFD23B30), dark = false,
)
private val Dark = Palette(
    background = Color(0xFF0B0B0D), surface = Color(0xFF16171A), surfaceHigh = Color(0xFF202126), hairline = Color(0xFF26272C),
    text = Color(0xFFF2F3F5), secondary = Color(0xFF9A9DA4), tertiary = Color(0xFF63666D),
    accent = Color(0xFF7896FF), onAccent = Color(0xFF0B0B0D), solid = Color(0xFFF2F3F5), onSolid = Color(0xFF0B0B0D),
    positive = Color(0xFF3DCB83), pending = Color(0xFFF0B43C), danger = Color(0xFFFF6B60), dark = true,
)

val LocalPalette = staticCompositionLocalOf { Light }
val colors: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

object Type {
    val display = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
    val title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = body.copy(fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.06.em)
}

@Composable fun DropDuoTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    val scheme = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.accent, onPrimary = p.onAccent, background = p.background, onBackground = p.text,
        surface = p.background, onSurface = p.text, surfaceVariant = p.surface, onSurfaceVariant = p.secondary,
        surfaceContainerLow = p.background, surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surfaceHigh, outline = p.hairline, outlineVariant = p.hairline, error = p.danger,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = Type.body, bodyMedium = Type.body, labelLarge = Type.bodyStrong), content = content)
    }
}
