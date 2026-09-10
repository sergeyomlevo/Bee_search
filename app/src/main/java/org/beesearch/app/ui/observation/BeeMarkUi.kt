@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.observation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun MarkColorSwatch(markColor: String, size: Int = 28) {
    val color = markColorValue(markColor)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape),
    )
}

internal fun markColorValue(markColor: String): Color = when (markColor) {
    "WHITE" -> Color.White
    "YELLOW" -> Color(0xFFFFD54F)
    "BLUE" -> Color(0xFF1565C0)
    "RED" -> Color(0xFFC62828)
    "GREEN" -> Color(0xFF2E7D32)
    else -> Color.Gray
}
