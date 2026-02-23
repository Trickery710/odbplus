package com.odbplus.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =============================================================================
// ODBPlus Dark Color Scheme -- the primary, intended experience
// =============================================================================
private val OdbDarkColorScheme = darkColorScheme(
    // Primary -- Electric Cyan
    primary             = CyanPrimary,
    onPrimary           = TextOnAccent,
    primaryContainer    = CyanContainer,
    onPrimaryContainer  = CyanOnContainer,

    // Secondary -- Amber
    secondary           = AmberSecondary,
    onSecondary         = TextOnAccent,
    secondaryContainer  = AmberContainer,
    onSecondaryContainer = AmberOnContainer,

    // Tertiary -- Success Green
    tertiary            = GreenSuccess,
    onTertiary          = TextOnAccent,
    tertiaryContainer   = GreenContainer,
    onTertiaryContainer = GreenOnContainer,

    // Error -- Vivid Red
    error               = RedError,
    onError             = Color.White,
    errorContainer      = RedContainer,
    onErrorContainer    = RedOnContainer,

    // Backgrounds and surfaces
    background          = DarkBackground,
    onBackground        = TextPrimary,
    surface             = DarkSurface,
    onSurface           = TextPrimary,
    surfaceVariant      = DarkSurfaceVariant,
    onSurfaceVariant    = TextSecondary,

    // Outline / dividers
    outline             = DarkBorder,
    outlineVariant      = DarkBorder,

    // Inverse (for snackbars, etc.)
    inverseSurface      = TextPrimary,
    inverseOnSurface    = DarkBackground,
    inversePrimary      = CyanDark,

    // Scrim
    scrim               = Color(0xFF000000),

    // Surface tint
    surfaceTint         = CyanPrimary
)

// =============================================================================
// ODBPlus Light Color Scheme -- provided for completeness
// =============================================================================
private val OdbLightColorScheme = lightColorScheme(
    primary             = LightPrimary,
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFFD4EFFF),
    onPrimaryContainer  = Color(0xFF001E2B),

    secondary           = LightSecondary,
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF2B1700),

    tertiary            = LightSuccess,
    onTertiary          = Color.White,
    tertiaryContainer   = Color(0xFFC8E6C9),
    onTertiaryContainer = Color(0xFF002108),

    error               = LightError,
    onError             = Color.White,
    errorContainer      = Color(0xFFFFDAD6),
    onErrorContainer    = Color(0xFF410002),

    background          = LightBackground,
    onBackground        = LightTextPrimary,
    surface             = LightSurface,
    onSurface           = LightTextPrimary,
    surfaceVariant      = LightSurfaceVariant,
    onSurfaceVariant    = LightTextSecondary,

    outline             = Color(0xFFD0D7DE),
    outlineVariant      = Color(0xFFD0D7DE)
)

// =============================================================================
// App Theme Composable
// =============================================================================
@Composable
fun Odbplus_multi_module_scaffoldTheme(
    darkTheme: Boolean = true,  // Dark is the default / preferred experience
    dynamicColor: Boolean = false, // Disabled -- we use our own curated palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color is intentionally disabled for a consistent brand experience.
        // If you want to re-enable it on Android 12+, set dynamicColor = true.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }
        darkTheme -> OdbDarkColorScheme
        else -> OdbLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
