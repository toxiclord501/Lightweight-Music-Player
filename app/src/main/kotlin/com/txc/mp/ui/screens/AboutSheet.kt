package com.txc.mp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.txc.mp.ui.components.Icon

/** Halaman "Tentang": logo, versi, ringkasan fitur, dan kredit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(size = 96.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Lightweight Music Player",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                "Versi $versionName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
            Text(
                "Pemutar musik ringan untuk file di perangkatmu, dengan suara yang bisa kamu atur sendiri.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp, bottom = 20.dp)
            )

            FeatureRow(Icons.Filled.Equalizer, "Equalizer 10 / 15 band", "Slider vertikal, 16 preset, preamp, bass boost, dan lebar stereo.")
            FeatureRow(Icons.Filled.Mic, "Lirik tersinkron", "Efek karaoke halus ala Apple Music, pilih versi lirik dan atur waktunya.")
            FeatureRow(Icons.Filled.HighQuality, "Mode Bit-Perfect", "Lewati semua pengolahan sinyal; mixer bit-perfect untuk DAC USB (Android 14+).")
            FeatureRow(Icons.Filled.Palette, "Material You", "Warna dinamis dari wallpaper, dan aksen dari sampul album.")
            FeatureRow(Icons.Filled.QueueMusic, "Antrean & playlist", "Putar berikutnya, favorit, playlist, dan sleep timer.")
            FeatureRow(Icons.Filled.Speed, "Kecepatan putar", "0,5x sampai 2x tanpa mengubah nada.")

            Text(
                "Dibuat dengan Kotlin, Jetpack Compose, dan Media3 (ExoPlayer).\nLirik online dari LRCLIB.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp, bottom = 28.dp)
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Logo app (sama dengan ikon launcher): lima batang equalizer di atas lingkaran konsentris. */
@Composable
fun AppLogo(size: Dp) {
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Color(0xFF0F0B24))
    ) {
        val u = this.size.width / 108f
        drawCircle(Color(0xFF1A1038), radius = 52f * u, center = Offset(54f * u, 54f * u))
        drawCircle(Color(0xFF261866), radius = 40f * u, center = Offset(54f * u, 54f * u))
        val xs = listOf(27f, 40.5f, 54f, 67.5f, 81f)
        val heights = listOf(20f, 38f, 54f, 30f, 44f)
        val colors = listOf(0xFF22D3EE, 0xFF60A5FA, 0xFFA78BFA, 0xFFE879F9, 0xFFF472B6)
        for (i in xs.indices) {
            val y1 = 54f - heights[i] / 2f + 4f
            val y2 = 54f + heights[i] / 2f - 4f
            drawLine(
                color = Color(colors[i]),
                start = Offset(xs[i] * u, y1 * u),
                end = Offset(xs[i] * u, y2 * u),
                strokeWidth = 8f * u,
                cap = StrokeCap.Round
            )
        }
    }
}
