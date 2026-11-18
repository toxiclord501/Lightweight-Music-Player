package com.txc.mp.ui.theme

import androidx.compose.ui.graphics.Color

data class ThemePreset(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

private val BackgroundDark = Color(0xFF0B0B0F)
private val SurfaceDark = Color(0xFF15141B)
private val SurfaceVariantDark = Color(0xFF27242F)
private val OnBackgroundDark = Color(0xFFF3F1F8)
private val OnSurfaceVariantDark = Color(0xFFA9A5B5)

val ThemePresets = listOf(
    ThemePreset("default", "Default", Color(0xFFF3F1F8), Color(0xFFA9A5B5), Color(0xFF8B5CF6)),
    ThemePreset("violet", "Violet", Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFF472B6)),
    ThemePreset("cyan", "Cyan", Color(0xFF22D3EE), Color(0xFF8B5CF6), Color(0xFF34D399)),
    ThemePreset("rose", "Rose", Color(0xFFF43F86), Color(0xFFF472B6), Color(0xFFFB923C)),
    ThemePreset("emerald", "Emerald", Color(0xFF10B981), Color(0xFF34D399), Color(0xFF22D3EE)),
    ThemePreset("amber", "Amber", Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFF43F86)),
    ThemePreset("crimson", "Crimson", Color(0xFFEF4444), Color(0xFFF87171), Color(0xFFF59E0B)),
    ThemePreset("indigo", "Indigo", Color(0xFF6366F1), Color(0xFF818CF8), Color(0xFF22D3EE)),
    ThemePreset("mono", "Monochrome", Color(0xFFE5E5E5), Color(0xFFA3A3A3), Color(0xFF737373)),
    ThemePreset("ocean", "Ocean", Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF06B6D4)),
    ThemePreset("teal", "Teal", Color(0xFF14B8A6), Color(0xFF2DD4BF), Color(0xFF8B5CF6)),
    ThemePreset("sunset", "Sunset", Color(0xFFFB923C), Color(0xFFF97316), Color(0xFFEF4444)),
    ThemePreset("lavender", "Lavender", Color(0xFFA78BFA), Color(0xFFC4B5FD), Color(0xFFF472B6)),
    ThemePreset("forest", "Forest", Color(0xFF22C55E), Color(0xFF4ADE80), Color(0xFFFBBF24)),
    ThemePreset("magenta", "Magenta", Color(0xFFD946EF), Color(0xFFE879F9), Color(0xFF8B5CF6)),
    ThemePreset("gold", "Gold", Color(0xFFEAB308), Color(0xFFFDE047), Color(0xFFF97316)),
    ThemePreset("slate", "Slate", Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFF3B82F6))
)

fun themePresetById(id: String): ThemePreset = ThemePresets.find { it.id == id } ?: ThemePresets.first()
