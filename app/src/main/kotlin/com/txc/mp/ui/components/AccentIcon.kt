package com.txc.mp.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon as MaterialIcon

/**
 * Pengganti `Icon` Material: bila `tint` tidak diberikan, ikon otomatis memakai
 * warna aksen tema (primary). Dengan begini semua ikon di app mengikuti pilihan
 * warna aksen di Setting, tanpa perlu mengatur tint satu per satu.
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    // Ganti ikon Material bawaan dengan ikon milik app (bila ada padanannya).
    val vector = remember(imageVector) { AppIcons.forMaterialName(imageVector.name) ?: imageVector }
    MaterialIcon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
