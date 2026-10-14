package app.dropduo.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Brand colors come from the two halves of the mark: ink and cobalt, on cool neutral grays.
 * `hero` is the ink device card on Home; `accent` fills primary actions and `accentText` is cobalt that reads as text.
 */
@Immutable data class Palette(
    val background: Color, val surface: Color, val surfaceHigh: Color, val hairline: Color,
    val text: Color, val secondary: Color, val tertiary: Color,
    val accent: Color, val onAccent: Color, val accentText: Color, val accentSoft: Color,
    val hero: Color, val onHero: Color, val heroSecondary: Color, val heroTile: Color, val heroLine: Color, val heroAccent: Color,
    val solid: Color, val onSolid: Color,
    val positive: Color, val pending: Color, val danger: Color, val dangerSoft: Color, val dark: Boolean,
)

private val Light = Palette(
    background = Color(0xFFF3F4F6), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFEDEEF1), hairline = Color(0xFFE3E5E9),
    text = Color(0xFF1B1C1F), secondary = Color(0xFF5E626A), tertiary = Color(0xFF6C7078),
    accent = Color(0xFF2558DD), onAccent = Color.White, accentText = Color(0xFF2050CF), accentSoft = Color(0xFFE8EEFC),
    hero = Color(0xFF1D1E22), onHero = Color(0xFFF4F5F7), heroSecondary = Color(0xFFA6AAB2), heroTile = Color(0xFF2C2E34), heroLine = Color(0xFF4A4D55), heroAccent = Color(0xFF7097F5),
    solid = Color(0xFF1D1E22), onSolid = Color.White,
    positive = Color(0xFF1C8C52), pending = Color(0xFF996000), danger = Color(0xFFCF3A2E), dangerSoft = Color(0xFFFBEAE8), dark = false,
)
private val Dark = Palette(
    background = Color(0xFF0C0D0F), surface = Color(0xFF17181B), surfaceHigh = Color(0xFF212328), hairline = Color(0xFF282A2F),
    text = Color(0xFFEDEEF0), secondary = Color(0xFFA0A4AC), tertiary = Color(0xFF8B909A),
    accent = Color(0xFF3366EE), onAccent = Color.White, accentText = Color(0xFF8EA9F7), accentSoft = Color(0xFF1A2443),
    hero = Color(0xFF191B20), onHero = Color(0xFFEDEEF0), heroSecondary = Color(0xFFA0A4AC), heroTile = Color(0xFF25282E), heroLine = Color(0xFF3A3D44), heroAccent = Color(0xFF8EA9F7),
    solid = Color(0xFFEDEEF0), onSolid = Color(0xFF0C0D0F),
    positive = Color(0xFF43C383), pending = Color(0xFFE8AE3C), danger = Color(0xFFFF6F64), dangerSoft = Color(0xFF3A1C1A), dark = true,
)

val LocalPalette = staticCompositionLocalOf { Light }
val colors: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

/** Geist, the typeface of the DropDuo website, so the app and site read as one brand. */
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

object Type {
    val display = TextStyle(fontFamily = Geist, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.035).em)
    val title = TextStyle(fontFamily = Geist, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.025).em)
    val headline = TextStyle(fontFamily = Geist, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.015).em)
    val body = TextStyle(fontFamily = Geist, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.005).em)
    val bodyStrong = body.copy(fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontFamily = Geist, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val captionStrong = caption.copy(fontWeight = FontWeight.Medium)
}

@Composable fun DropDuoTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    val scheme = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.accent, onPrimary = p.onAccent, background = p.background, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.secondary,
        surfaceContainerLow = p.surface, surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surfaceHigh, outline = p.hairline, outlineVariant = p.hairline, error = p.danger,
    )
    // Material components (dialogs, snackbars, text buttons) pick up Geist too.
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Geist), displayMedium = base.displayMedium.copy(fontFamily = Geist), displaySmall = base.displaySmall.copy(fontFamily = Geist),
        headlineLarge = base.headlineLarge.copy(fontFamily = Geist), headlineMedium = base.headlineMedium.copy(fontFamily = Geist), headlineSmall = base.headlineSmall.copy(fontFamily = Geist),
        titleLarge = Type.title, titleMedium = Type.headline, titleSmall = Type.bodyStrong,
        bodyLarge = Type.body, bodyMedium = Type.body, bodySmall = Type.caption,
        labelLarge = Type.bodyStrong, labelMedium = Type.captionStrong, labelSmall = Type.caption,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
