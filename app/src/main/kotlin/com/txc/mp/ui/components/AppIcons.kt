package com.txc.mp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Set ikon milik app: gaya garis membulat (24x24). Ikon Material bawaan otomatis
 * diganti ke versi ini lewat [Icon] di AccentIcon.kt berdasarkan namanya, jadi
 * semua layar langsung memakainya tanpa perubahan di tiap pemanggilan.
 *
 * Mode jalur: S = garis, F = isi + garis halus (bentuk membulat), D = titik isi.
 */
object AppIcons {

    private val cache = HashMap<String, ImageVector>()

    /** Ikon kustom untuk nama ikon Material (mis. "Filled.PlayArrow"), atau null bila tidak ada. */
    fun forMaterialName(materialName: String): ImageVector? {
        val key = materialName.substringAfterLast('.')
        cache[key]?.let { return it }
        val paths = DEFS[key] ?: return null
        val vector = build(key, paths)
        cache[key] = vector
        return vector
    }

    private fun build(name: String, paths: List<Pair<String, Char>>): ImageVector {
        val builder = ImageVector.Builder(
            name = "App.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        val ink = SolidColor(Color.Black)
        for ((d, mode) in paths) {
            val nodes = PathParser().parsePathString(d).toNodes()
            when (mode) {
                'F' -> builder.addPath(
                    pathData = nodes, fill = ink, stroke = ink, strokeLineWidth = 1.4f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
                )
                'D' -> builder.addPath(pathData = nodes, fill = ink)
                else -> builder.addPath(
                    pathData = nodes, stroke = ink, strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
                )
            }
        }
        return builder.build()
    }

    private val DEFS: Map<String, List<Pair<String, Char>>> = mapOf(
        "ArrowBack" to listOf("M19 12H5 M11 6l-6 6 6 6" to 'S'),
        "KeyboardArrowDown" to listOf("M6 9l6 6 6-6" to 'S'),
        "Close" to listOf("M6 6l12 12 M18 6L6 18" to 'S'),
        "Add" to listOf("M12 5v14 M5 12h14" to 'S'),
        "Check" to listOf("M5 12.5l4.5 4.5L19 7.5" to 'S'),
        "Menu" to listOf("M4 7h16 M4 12h16 M4 17h10" to 'S'),
        "Apps" to listOf("M5 5h5v5H5z M14 5h5v5h-5z M5 14h5v5H5z M14 14h5v5h-5z" to 'S'),
        "Search" to listOf("M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z M16.5 16.5L21 21" to 'S'),
        "Settings" to listOf("M4 8h8 M16 8h4 M4 16h4 M12 16h8 M14 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z M10 14a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" to 'S'),
        "MoreVert" to listOf("M12 5.2a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6z M12 10.7a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6z M12 16.2a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6z" to 'D'),
        "Refresh" to listOf("M19.5 12a7.5 7.5 0 1 1-2.2-5.3 M19.5 4.5v4h-4" to 'S'),
        "Delete" to listOf("M4 7h16 M9 7V4.5h6V7 M6.5 7l1 12.5h9l1-12.5 M10 11v5 M14 11v5" to 'S'),
        "Info" to listOf("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17z M12 11v5 M12 7.8v.01" to 'S'),
        "Palette" to listOf("M12 3a9 9 0 1 0 0 18c1.2 0 1.8-.9 1.4-2-.5-1.3.4-2.5 1.7-2.5H17a4 4 0 0 0 4-4C21 6.6 16.9 3 12 3z M7.5 11v.01 M10 7.5v.01 M14.5 7.5v.01" to 'S'),
        "Speed" to listOf("M3.5 17a8.5 8.5 0 0 1 17 0 M12 17l4-5 M12 17v.01" to 'S'),
        "HighQuality" to listOf("M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z M7.5 9v6 M7.5 12h3 M10.5 9v6 M15 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z M16.6 14.6l1.4 1.4" to 'S'),
        "BugReport" to listOf("M9 8a3 3 0 0 1 6 0v1H9z M8 9h8v6a4 4 0 0 1-8 0z M4 11h4 M16 11h4 M4 17h4 M16 17h4 M9.5 5L8 3.5 M14.5 5L16 3.5" to 'S'),
        "Timer" to listOf("M12 8v4l2.5 2 M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16z M10 2h4" to 'S'),
        "Equalizer" to listOf("M6 4v5 M6 13v7 M12 4v9 M12 17v3 M18 4v3 M18 11v9 M6 9a2 2 0 1 0 0 4 2 2 0 0 0 0-4z M12 13a2 2 0 1 0 0 4 2 2 0 0 0 0-4z M18 7a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" to 'S'),
        "GraphicEq" to listOf("M3 11v2 M7 7v10 M11 4v16 M15 8v8 M19 10v4" to 'S'),
        "LibraryMusic" to listOf("M9 3h11a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M4 8v12a1 1 0 0 0 1 1h12 M15.5 12.5V7l3 .8 M15.5 12.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0z" to 'S'),
        "MusicNote" to listOf("M9 18V6l10-2v12 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M19 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z" to 'S'),
        "PlaylistPlay" to listOf("M4 7h12 M4 12h8 M4 17h6 M16 13l5 3.5-5 3.5z" to 'S'),
        "QueueMusic" to listOf("M4 7h12 M4 12h8 M4 17h5 M17 18V8l4 1.5 M17 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0z" to 'S'),
        "Shuffle" to listOf("M16 4h4v4 M20 4L4 20 M20 16v4h-4 M14.5 14.5L20 20 M4 4l5 5" to 'S'),
        "Repeat" to listOf("M4 11V9a3 3 0 0 1 3-3h13 M17 3l3 3-3 3 M20 13v2a3 3 0 0 1-3 3H4 M7 21l-3-3 3-3" to 'S'),
        "RepeatOne" to listOf("M4 11V9a3 3 0 0 1 3-3h13 M17 3l3 3-3 3 M20 13v2a3 3 0 0 1-3 3H4 M7 21l-3-3 3-3 M11.2 10.2l1.4-1v5.6" to 'S'),
        "SkipNext" to listOf("M6 5.5v13l9-6.5z" to 'F', "M18.5 5.5v13" to 'S'),
        "SkipPrevious" to listOf("M18 5.5v13l-9-6.5z" to 'F', "M5.5 5.5v13" to 'S'),
        "FastForward" to listOf("M4 6.5v11l8-5.5z M12 6.5v11l8-5.5z" to 'F'),
        "FastRewind" to listOf("M20 6.5v11l-8-5.5z M12 6.5v11l-8-5.5z" to 'F'),
        "PlayArrow" to listOf("M8 5.5v13l11-6.5z" to 'F'),
        "Pause" to listOf("M7.5 5h2.5v14H7.5z M14 5h2.5v14H14z" to 'F'),
        "Favorite" to listOf("M12 20.3C5.5 15.6 3 12.4 3 9A4.7 4.7 0 0 1 12 7a4.7 4.7 0 0 1 9 2c0 3.4-2.5 6.6-9 11.3z" to 'F'),
        "FavoriteBorder" to listOf("M12 20.3C5.5 15.6 3 12.4 3 9A4.7 4.7 0 0 1 12 7a4.7 4.7 0 0 1 9 2c0 3.4-2.5 6.6-9 11.3z" to 'S'),
        "Mic" to listOf("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M6 11a6 6 0 0 0 12 0 M12 17v4 M9 21h6" to 'S'),
        "MicOff" to listOf("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M6 11a6 6 0 0 0 12 0 M12 17v4 M9 21h6 M4 4l16 16" to 'S'),
        "Person" to listOf("M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M4.5 20a7.5 7.5 0 0 1 15 0" to 'S'),
        "SpeakerGroup" to listOf("M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M12 7v.01 M12 17.2a2.8 2.8 0 1 0 0-5.6 2.8 2.8 0 0 0 0 5.6z" to 'S'),
        "SwapVert" to listOf("M8 20V4 M4.5 7.5L8 4l3.5 3.5 M16 4v16 M12.5 16.5L16 20l3.5-3.5" to 'S'),
    )
}
