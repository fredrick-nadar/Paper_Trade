package com.fredrick.papertrade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val CardBackground = Color(0xFFF4FAF5)
private val TitleColor = Color(0xFF1F3D2A)
private val BodyColor = Color(0xFF4A6B55)
private val DefaultAccent = Color(0xFF315D3C)

private fun accentColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(DefaultAccent)

/** Draws the CleverTap custom-template card (if any) above the app. Back or outside tap counts as "Not now". */
@Composable
fun InAppOverlayHost() {
    val card = InAppOverlay.current ?: return
    val accent = accentColor(card.accent)
    Dialog(onDismissRequest = card.onSecondary) {
        Column(
            Modifier.fillMaxWidth().background(CardBackground, RoundedCornerShape(24.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(card.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TitleColor, textAlign = TextAlign.Center)
            if (card.message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(card.message, fontSize = 15.sp, color = BodyColor, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(22.dp))
            if (card.primaryText.isNotBlank()) {
                Button(
                    onClick = card.onPrimary,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(card.primaryText, fontSize = 16.sp) }
            }
            if (card.secondaryText.isNotBlank()) {
                TextButton(onClick = card.onSecondary) { Text(card.secondaryText, color = accent) }
            }
        }
    }
}
