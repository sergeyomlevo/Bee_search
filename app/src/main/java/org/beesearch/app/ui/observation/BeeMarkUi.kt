package org.beesearch.app.ui.observation

import androidx.compose.ui.graphics.Color

internal fun markColorValue(markColor: String): Color = when (markColor) {
    "WHITE" -> Color.White
    "YELLOW" -> Color(0xFFFFD54F)
    "BLUE" -> Color(0xFF1565C0)
    "RED" -> Color(0xFFC62828)
    "GREEN" -> Color(0xFF2E7D32)
    else -> Color.Gray
}
