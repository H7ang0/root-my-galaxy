package com.h7ang0.root.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.h7ang0.root.R
import com.h7ang0.root.AccentColor
import com.h7ang0.root.AppThemeMode

/** Dot-matrix (点阵) pixel font used for the home page app title. */
val DotMatrixFont = FontFamily(Font(R.font.dotgothic16_regular))

// Relaxin-inspired light palette — cream paper background, ink text, matcha accent.
private val RxBackground       = Color(0xFFEAE6DD)
private val RxSurface          = Color(0xFFEAE6DD)
private val RxSurfaceContainer = Color(0xFFDED9CE)
private val RxSurfaceHigh      = Color(0xFFD2CDC0)
private val RxSurfaceHighest   = Color(0xFFC7C1B4)
private val RxOnSurface        = Color(0xFF141310)
private val RxOnSurfaceVariant = Color(0xFF7A7770)
private val RxOutline          = Color(0xFF3A362E)
private val RxOutlineVariant   = Color(0xFF9B978E)

private val RxGreen            = Color(0xFF7CBF2E)
private val RxGreenDim         = Color(0xFFD8E9BB)
private val RxOnGreen          = Color(0xFF0F1A00)

private val RxAmber            = Color(0xFFC97A18)
private val RxAmberDim         = Color(0xFFF1D7B2)
private val RxOnAmber          = Color(0xFF1A0F00)

private val RxCrimson          = Color(0xFFC0362E)
private val RxCrimsonDim       = Color(0xFFF1C8C4)
private val RxOnCrimson        = Color(0xFFFFFFFF)

private val RelaxinColors = lightColorScheme(
    primary            = RxGreen,
    onPrimary          = RxOnGreen,
    primaryContainer   = RxGreenDim,
    onPrimaryContainer = RxOnSurface,

    secondary            = RxOutline,
    onSecondary          = RxBackground,
    secondaryContainer   = RxSurfaceContainer,
    onSecondaryContainer = RxOnSurface,

    tertiary            = RxAmber,
    onTertiary          = RxOnAmber,
    tertiaryContainer   = RxAmberDim,
    onTertiaryContainer = RxOnSurface,

    error            = RxCrimson,
    onError          = RxOnCrimson,
    errorContainer   = RxCrimsonDim,
    onErrorContainer = RxOnSurface,

    background   = RxBackground,
    onBackground = RxOnSurface,

    surface                  = RxSurface,
    onSurface                = RxOnSurface,
    surfaceVariant           = RxSurfaceHigh,
    onSurfaceVariant         = RxOnSurfaceVariant,
    surfaceTint              = RxGreen,
    surfaceContainerLowest   = RxBackground,
    surfaceContainerLow      = RxSurface,
    surfaceContainer         = RxSurfaceContainer,
    surfaceContainerHigh     = RxSurfaceHigh,
    surfaceContainerHighest  = RxSurfaceHighest,

    outline        = RxOutline,
    outlineVariant = RxOutlineVariant,

    inverseSurface   = RxOnSurface,
    inverseOnSurface = RxBackground,
    inversePrimary   = RxGreen,

    scrim = Color(0xB3141310),
)

private val Mono = FontFamily.Monospace

private val RelaxinTypography = Typography(
    displaySmall  = TextStyle(fontFamily = Mono, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black,  letterSpacing = 0.5.sp),
    headlineLarge = TextStyle(fontFamily = Mono, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black,  letterSpacing = 1.0.sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold,   letterSpacing = 0.8.sp),
    titleLarge    = TextStyle(fontFamily = Mono, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold,   letterSpacing = 0.6.sp),
    titleMedium   = TextStyle(fontFamily = Mono, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold,   letterSpacing = 0.4.sp),
    titleSmall    = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,   letterSpacing = 0.4.sp),
    bodyLarge     = TextStyle(fontFamily = Mono, fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),
    bodyMedium    = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),
    bodySmall     = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),
    labelLarge    = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,   letterSpacing = 1.0.sp),
    labelMedium   = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold,   letterSpacing = 1.0.sp),
    labelSmall    = TextStyle(fontFamily = Mono, fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold,   letterSpacing = 1.2.sp),
)

@Composable
@Suppress("UNUSED_PARAMETER")
fun RootMyGalaxyTheme(
    accentColor: AccentColor,
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    SideEffect {
        val window = (context as Activity).window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    MaterialExpressiveTheme(
        colorScheme = RelaxinColors,
        typography = RelaxinTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
