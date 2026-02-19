package com.odbplus.app.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// ODBPlus Premium Automotive Color System
// =============================================================================
// A dark-first palette designed for automotive diagnostics. Deep backgrounds
// with vibrant accent colors evoke dashboard instrument clusters and
// professional diagnostic tooling.
// =============================================================================

// ---------------------------------------------------------------------------
// Core Backgrounds -- rich dark surfaces, never pure black
// ---------------------------------------------------------------------------
val DarkBackground      = Color(0xFF0D1117)   // Deep navy-charcoal base
val DarkSurface         = Color(0xFF151B23)   // Slightly lighter surface
val DarkSurfaceVariant  = Color(0xFF1A2332)   // Card/elevated surface
val DarkSurfaceHigh     = Color(0xFF1E2D3D)   // Highest elevation cards
val DarkBorder          = Color(0xFF2A3A4E)   // Subtle border/divider

// ---------------------------------------------------------------------------
// Primary -- Electric Cyan / Teal
// Technology, diagnostics, data readouts
// ---------------------------------------------------------------------------
val CyanPrimary         = Color(0xFF00D4FF)   // Main accent
val CyanLight           = Color(0xFF5CE1FF)   // Lighter variant
val CyanDark            = Color(0xFF009DBF)   // Pressed/darker variant
val CyanContainer       = Color(0xFF003544)   // Container / subtle bg
val CyanOnContainer     = Color(0xFFB8EFFF)   // Text on container

// ---------------------------------------------------------------------------
// Secondary -- Amber / Orange
// Warnings, pending states, status indicators, automotive feel
// ---------------------------------------------------------------------------
val AmberSecondary      = Color(0xFFFF8C00)   // Main amber accent
val AmberLight          = Color(0xFFFFAB40)   // Lighter amber
val AmberDark           = Color(0xFFCC7000)   // Pressed amber
val AmberContainer      = Color(0xFF3D2600)   // Container / subtle bg
val AmberOnContainer    = Color(0xFFFFDDB3)   // Text on container

// ---------------------------------------------------------------------------
// Success -- Vibrant Green
// Healthy readings, connected states, clear codes
// ---------------------------------------------------------------------------
val GreenSuccess        = Color(0xFF00E676)   // Main success
val GreenLight          = Color(0xFF69F0AE)   // Lighter success
val GreenDark           = Color(0xFF00C853)   // Pressed success
val GreenContainer      = Color(0xFF003D1A)   // Container / subtle bg
val GreenOnContainer    = Color(0xFFB9F6CA)   // Text on container

// ---------------------------------------------------------------------------
// Error -- Vivid Red
// Critical codes, disconnected, failures
// ---------------------------------------------------------------------------
val RedError            = Color(0xFFFF1744)   // Main error
val RedLight            = Color(0xFFFF616F)   // Lighter error
val RedDark             = Color(0xFFD50000)   // Pressed error
val RedContainer        = Color(0xFF3D0011)   // Container / subtle bg
val RedOnContainer      = Color(0xFFFFB3B3)   // Text on container

// ---------------------------------------------------------------------------
// Neutral Text Tones
// ---------------------------------------------------------------------------
val TextPrimary         = Color(0xFFE6EDF3)   // Primary text on dark
val TextSecondary       = Color(0xFF8B949E)   // Secondary / muted text
val TextTertiary        = Color(0xFF6E7681)   // Tertiary / disabled text
val TextOnAccent        = Color(0xFF001E2B)   // Dark text on bright accents

// ---------------------------------------------------------------------------
// Semantic / Specialty
// ---------------------------------------------------------------------------
val ReplayBlue          = Color(0xFF2196F3)   // Replay mode indicator
val RecordingPulse      = Color(0xFFFF1744)   // Recording indicator
val GoogleBlue          = Color(0xFF4285F4)   // Google brand color
val FreeBadgeGreen      = Color(0xFF00E676)   // "FREE" badge
val TerminalBg          = Color(0xFF0A0E14)   // Terminal background
val TerminalSent        = Color(0xFF00D4FF)   // Terminal: sent commands
val TerminalReceived    = Color(0xFF00E676)   // Terminal: received responses
val TerminalError       = Color(0xFFFF616F)   // Terminal: error lines
val TerminalInfo        = Color(0xFFFFAB40)   // Terminal: info lines
val TerminalMuted       = Color(0xFF6E7681)   // Terminal: generic text

// ---------------------------------------------------------------------------
// Light Theme Variants (for completeness -- dark is primary)
// ---------------------------------------------------------------------------
val LightBackground     = Color(0xFFF6F8FA)
val LightSurface        = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEAEEF2)
val LightPrimary        = Color(0xFF0078A8)
val LightSecondary      = Color(0xFFCC7000)
val LightError          = Color(0xFFD50000)
val LightSuccess        = Color(0xFF00C853)
val LightTextPrimary    = Color(0xFF1B1F23)
val LightTextSecondary  = Color(0xFF57606A)
