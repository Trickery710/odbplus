# ODBPlus UI/UX Design Memory

## Project Stack
- **Framework**: Jetpack Compose with Material3
- **DI**: Hilt
- **Navigation**: Jetpack Navigation Compose with bottom nav + nested nav
- **Build**: Gradle KTS, multi-module (app, core-transport, core-protocol)
- **Theme function**: `Odbplus_multi_module_scaffoldTheme` in Theme.kt

## Design System (established Feb 2026)
- **Dark-first** automotive aesthetic. Dynamic color DISABLED for brand consistency.
- **Primary**: Electric Cyan `#00D4FF` -- tech/diagnostics accent
- **Secondary**: Amber `#FF8C00` -- warnings, automotive feel
- **Success**: Green `#00E676` -- healthy/connected states
- **Error**: Red `#FF1744` -- critical/disconnected
- **Backgrounds**: Deep navy `#0D1117` base, `#151B23` surface, `#1A2332` cards
- **Borders**: `#2A3A4E` subtle border on cards/dividers
- **Text**: `#E6EDF3` primary, `#8B949E` secondary, `#6E7681` tertiary
- Color tokens in `/app/src/main/java/com/odbplus/app/ui/theme/Color.kt`

## Key Patterns
- Cards use `DarkSurfaceVariant` bg with 1dp `DarkBorder` border, 14-16dp corner radius
- Buttons: CyanPrimary with TextOnAccent, 12dp corner radius
- Section headers: ALL CAPS labelSmall with 1.sp letterSpacing
- Empty states: CircleShape icon container with 0.1 alpha accent bg
- Terminal screens use `TerminalBg` (#0A0E14) with monospace font, syntax-colored lines
- Bottom nav: CyanPrimary selected, TextTertiary unselected, 0.12 alpha indicator

## File Locations
- Theme: `app/src/main/java/com/odbplus/app/ui/theme/` (Color.kt, Theme.kt, Type.kt)
- Screens: `app/src/main/java/com/odbplus/app/ui/`
- Nav: `app/src/main/java/com/odbplus/app/nav/` (AppNav.kt, BottomNavItem.kt)

## Build Notes
- `window.statusBarColor` and `window.navigationBarColor` are deprecated but still work
- kapt language version 2.0+ alpha warning is expected
