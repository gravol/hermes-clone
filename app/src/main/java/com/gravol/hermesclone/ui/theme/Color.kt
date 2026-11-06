// Official Hermes app palette — sampled from rendered reference screenshots.
package com.gravol.hermesclone.ui.theme

import androidx.compose.ui.graphics.Color

// Core canvas
val HermesBackground = Color(0xFF000022)   // home/chat canvas
val HermesSurface = Color(0xFF0C1A3C)      // pills, composer, search bar
val HermesSurfaceActive = Color(0xFF29294C) // selected pill / active tab
val HermesSurfaceRaised = Color(0xFF14224A) // menus, cards on surface
val HermesBackdrop = Color(0xFF101A3C)

// Text
val HermesTextPrimary = Color(0xFFF2F4FA)
val HermesTextSecondary = Color(0xFF8A93B8)  // subtitles, times
val HermesTextTertiary = Color(0xFF5D6690)   // hints, row times
val HermesMuted = Color(0xFF676571)           // "N more sessions" (sampled)

// Accents
val HermesAccent = Color(0xFF447CFF)        // unread dots, Working, links
val HermesFab = Color(0xFF1D2761)           // compose FAB / send circle
val HermesFabBright = Color(0xFF2A3A8C)
val HermesError = Color(0xFFFF6B6B)
val HermesErrorBg = Color(0xFF3A1220)

// Tree
val HermesRail = Color(0xFF2A3560)          // tree connector lines
val HermesDivider = Color(0xFF1A2450)

// Legacy aliases kept so existing screens keep compiling (re-pointed at the
// official palette so every screen inherits the new look in one step).
val DarkBackground = HermesBackground
val DarkSurface = HermesSurface
val DarkSurfaceVariant = HermesSurfaceActive
val Primary = HermesAccent
val PrimaryVariant = HermesFabBright
val Secondary = HermesAccent
val SecondaryVariant = HermesFabBright
val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFFFFFFFF)
val OnBackground = HermesTextPrimary
val OnSurface = HermesTextPrimary
val OnSurfaceVariant = HermesTextSecondary
val Error = HermesError

// Agent avatar accents (provisional hash-derived fallback; the exact official
// derivation lands with OrbAvatar once the RE pass pins it down).
val HermesAvatarFallbacks = listOf(
    Color(0xFF7C50F5), // purple
    Color(0xFF48D8C0), // teal
    Color(0xFF66DD4D), // green
    Color(0xFF5B8DEF), // blue
    Color(0xFFE8785F), // coral
    Color(0xFFD050C8), // magenta
)

data class HeaderLogoPreset(val name: String, val color: Color)
val HeaderLogoColors = listOf(
    HeaderLogoPreset("Yellow", Color(0xFFFFD700)),
    HeaderLogoPreset("Blue", Color(0xFF5B7CFF)),
    HeaderLogoPreset("Purple", Color(0xFFAF52DE)),
    HeaderLogoPreset("Red", Color(0xFFFF3B30)),
    HeaderLogoPreset("Green", Color(0xFF34C759)),
    HeaderLogoPreset("White", Color(0xFFFFFFFF)),
    HeaderLogoPreset("Custom", Color(0xFFFFD700)),
)

fun hexToColor(hex: String): Color {
    val cleanHex = hex.trim().removePrefix("#")
    return when {
        cleanHex.length == 6 -> Color(
            red = ((cleanHex.substring(0, 2)).toInt(16) / 255.0f),
            green = ((cleanHex.substring(2, 4)).toInt(16) / 255.0f),
            blue = ((cleanHex.substring(4, 6)).toInt(16) / 255.0f)
        )
        else -> Color(0xFFFFD700)
    }
}

fun colorToHex(color: Color): String {
    return String.format("#%02X%02X%02X",
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
}

fun isDarkForeground(color: Color): Boolean {
    val luminance = 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
    return luminance > 0.62
}
