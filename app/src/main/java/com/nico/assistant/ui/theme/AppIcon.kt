package com.nico.assistant.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Icône réelle d'une app installée, chargée hors du fil principal. Pendant le chargement (ou
 * si l'app a disparu), un carré de verre garde la place : la liste ne saute pas.
 */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128).asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (bitmap == null) NicoColors.GlassFill else androidx.compose.ui.graphics.Color.Transparent, shape)
    ) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}
