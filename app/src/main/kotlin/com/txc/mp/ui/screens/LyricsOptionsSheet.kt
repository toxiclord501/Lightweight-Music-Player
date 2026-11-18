package com.txc.mp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.txc.mp.data.repository.LyricCandidate
import com.txc.mp.ui.LyricCandidatesUi
import com.txc.mp.util.formatDuration
import java.util.Locale

/**
 * Pengaturan lirik: geser waktu lirik (kalau terasa telat/terlalu cepat) dan pilih
 * versi lirik lain dari hasil pencarian online (tiap versi punya timestamp berbeda).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOptionsSheet(
    offsetMs: Long,
    candidates: LyricCandidatesUi,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    onSearch: () -> Unit,
    onChoose: (LyricCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (!candidates.searched && !candidates.loading) onSearch()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Sinkronisasi lirik", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Lirik terasa telat? Naikkan (+). Terlalu cepat? Turunkan (-).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton("-0,5s") { onAdjust(-500) }
                StepButton("-0,1s") { onAdjust(-100) }
                Text(
                    String.format(Locale.US, "%+.2f s", offsetMs / 1000f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                StepButton("+0,1s") { onAdjust(100) }
                StepButton("+0,5s") { onAdjust(500) }
            }
            TextButton(onClick = onReset) { Text("Kembalikan ke bawaan") }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pilih versi lirik", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onSearch) { Text("Cari ulang") }
            }

            when {
                candidates.loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }

                candidates.items.isEmpty() -> Text(
                    if (candidates.searched) "Tidak ada versi lirik lain yang ditemukan." else "Mencari...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(candidates.items, key = { it.id }) { candidate ->
                        CandidateRow(candidate, selected = candidate.id == candidates.selectedId) { onChoose(candidate) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun CandidateRow(candidate: LyricCandidate, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            "${candidate.title} - ${candidate.artist}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val kind = if (candidate.isSynced) "Tersinkron" else "Teks saja"
        val album = candidate.album.ifBlank { "-" }
        Text(
            "$kind  |  $album  |  ${formatDuration(candidate.durationSec * 1000L)}",
            style = MaterialTheme.typography.labelMedium,
            color = if (candidate.isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val preview = candidate.syncedLines.firstOrNull()?.text ?: candidate.plainText?.lineSequence()?.firstOrNull { it.isNotBlank() }
        if (!preview.isNullOrBlank()) {
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
