package com.txc.mp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import com.txc.mp.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.txc.mp.playback.EQ_MAX_GAIN_DB
import com.txc.mp.playback.EQ_MIN_GAIN_DB
import com.txc.mp.playback.EqLayout
import com.txc.mp.playback.EqPresets
import com.txc.mp.playback.EqualizerController
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    controller: EqualizerController,
    onBack: () -> Unit
) {
    val state by controller.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
                actions = {
                    IconButton(onClick = controller::resetAll) { Icon(Icons.Filled.Refresh, contentDescription = "Reset") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            val bitPerfect by com.txc.mp.playback.BitPerfect.enabled.collectAsState()
            if (bitPerfect) {
                Text(
                    "Mode Bit-Perfect sedang aktif, jadi equalizer dilewati. Matikan di Setting untuk memakai equalizer.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            // Kartu saklar: diberi ruang lega di semua sisi (tidak mepet tepi layar maupun komponen lain).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Aktifkan Equalizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Geser pita untuk mengatur suara. Ketuk dua kali untuk reset satu band.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = controller::setEnabled)
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Pilihan jumlah band
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqLayout.values().forEach { layout ->
                    EqChip(
                        text = layout.label,
                        selected = state.layoutId == layout.id,
                        onClick = { controller.setLayout(layout.id) }
                    )
                }
            }

            // Preset
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EqPresets, key = { it.id }) { preset ->
                    EqChip(
                        text = preset.label,
                        selected = state.selectedPresetId == preset.id,
                        onClick = { controller.applyPreset(preset.id) }
                    )
                }
            }

            ResponseCurve(
                response = state.response,
                active = state.enabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).height(84.dp)
            )

            // Slider vertikal per band
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                val count = state.gainsDb.size.coerceAtLeast(1)
                val scaleWidth = 30.dp
                val bandWidth = maxOf((maxWidth - scaleWidth) / count, 34.dp)
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .widthIn(min = maxWidth)
                        .height(270.dp)
                ) {
                    Column(
                        modifier = Modifier.width(scaleWidth).fillMaxSize().padding(top = 22.dp, bottom = 34.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("+12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.gainsDb.forEachIndexed { index, gain ->
                        EqBandSlider(
                            label = formatFreq(state.bandFreqs.getOrNull(index) ?: 0),
                            value = gain,
                            active = state.enabled,
                            onValueChange = { controller.setBandGain(index, it) },
                            onReset = { controller.setBandGain(index, 0f) },
                            modifier = Modifier.width(bandWidth).fillMaxSize()
                        )
                    }
                }
            }

            EffectSlider(
                title = "Preamp",
                valueLabel = formatDb(state.preampDb) + " dB",
                value = state.preampDb,
                range = -12f..12f,
                onValueChange = controller::setPreamp
            )
            EffectSlider(
                title = "Bass Boost",
                valueLabel = "+" + formatDb(state.bassDb).removePrefix("+") + " dB",
                value = state.bassDb,
                range = 0f..12f,
                onValueChange = controller::setBassBoost
            )
            EffectSlider(
                title = "Lebar Stereo",
                valueLabel = "${(state.stereoWidth * 100).roundToInt()}%",
                value = state.stereoWidth,
                range = 0f..2f,
                onValueChange = controller::setStereoWidth
            )
            // Diagnostik jalur audio: memastikan equalizer benar-benar terpasang di pemutar.
            var pipeline by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    val p = controller.processor
                    pipeline = p.pipelineInfo + "  |  " + if (p.lastBufferProcessed) "sedang memproses" else "dilewati"
                    kotlinx.coroutines.delay(600)
                }
            }
            Text(
                "Jalur audio: $pipeline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                "Equalizer ini diproses langsung di dalam pemutar, jadi jumlah band tidak dibatasi perangkat dan tetap berfungsi setelah app dibuka kembali.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun EqChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Slider vertikal satu band: sentuh/geser di mana saja pada pita untuk mengubah gain. */
@Composable
private fun EqBandSlider(
    label: String,
    value: Float,
    active: Boolean,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnReset by rememberUpdatedState(onReset)
    val accent = if (active) primary else primary.copy(alpha = 0.45f)

    // "Tik" haptik tiap langkah 0,5 dB saat menggeser, seperti detent pada fader mixer.
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var lastValue by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(value) }
    androidx.compose.runtime.LaunchedEffect(value) {
        if (value != lastValue) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            lastValue = value
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatDb(value),
            style = MaterialTheme.typography.labelSmall,
            color = if (value != 0f) primary else onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(22.dp)
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput("tap") {
                    detectTapGestures(
                        onTap = { offset -> currentOnChange(yToGain(offset.y, size.height.toFloat(), 12.dp.toPx())) },
                        onDoubleTap = { currentOnReset() }
                    )
                }
                .pointerInput("drag") {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> currentOnChange(yToGain(offset.y, size.height.toFloat(), 12.dp.toPx())) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            currentOnChange(yToGain(change.position.y, size.height.toFloat(), 12.dp.toPx()))
                        }
                    )
                }
        ) {
            val pad = 12.dp.toPx()
            val trackW = 5.dp.toPx()
            val cx = size.width / 2f
            val h = size.height - 2 * pad
            fun yFor(v: Float): Float = pad + (EQ_MAX_GAIN_DB - v) / (EQ_MAX_GAIN_DB - EQ_MIN_GAIN_DB) * h

            // rel
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(cx - trackW / 2f, pad),
                size = Size(trackW, h),
                cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
            )
            // garis tengah (0 dB)
            val zeroY = yFor(0f)
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.5f),
                start = Offset(cx - 9.dp.toPx(), zeroY),
                end = Offset(cx + 9.dp.toPx(), zeroY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            // isian dari 0 dB ke posisi thumb
            val thumbY = yFor(value)
            drawRoundRect(
                color = accent,
                topLeft = Offset(cx - trackW / 2f, minOf(zeroY, thumbY)),
                size = Size(trackW, abs(zeroY - thumbY).coerceAtLeast(0.1f)),
                cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
            )
            // thumb
            drawCircle(color = accent, radius = 10.dp.toPx(), center = Offset(cx, thumbY))
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 4.dp.toPx(), center = Offset(cx, thumbY))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(34.dp).padding(top = 8.dp)
        )
    }
}

/** Posisi sentuh (px) -> gain dB, dengan snap 0,5 dB dan "magnet" ke 0 dB. */
private fun yToGain(y: Float, heightPx: Float, padPx: Float): Float {
    val h = (heightPx - 2 * padPx).coerceAtLeast(1f)
    val fraction = ((y - padPx) / h).coerceIn(0f, 1f)
    val raw = EQ_MAX_GAIN_DB - fraction * (EQ_MAX_GAIN_DB - EQ_MIN_GAIN_DB)
    val snapped = (raw * 2f).roundToInt() / 2f
    return if (abs(snapped) < 0.75f) 0f else snapped
}

@Composable
private fun ResponseCurve(response: List<Float>, active: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
        if (response.size < 2) return@Canvas
        val range = 15f
        fun yFor(db: Float): Float = size.height / 2f - (db.coerceIn(-range, range) / range) * (size.height / 2f - 6.dp.toPx())
        val stepX = size.width / (response.size - 1)

        drawLine(grid, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())

        val line = Path()
        response.forEachIndexed { i, db ->
            val x = i * stepX
            val y = yFor(db)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height / 2f)
            close()
        }
        val color = if (active) primary else primary.copy(alpha = 0.4f)
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun EffectSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

private fun formatDb(v: Float): String {
    if (v == 0f) return "0"
    return String.format(Locale.US, "%+.1f", v).removeSuffix(".0")
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        if (k == k.toInt().toFloat()) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
    } else hz.toString()
