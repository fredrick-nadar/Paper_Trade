package com.fredrick.papertrade

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.clevertap.android.sdk.CleverTapAPI
import com.clevertap.android.sdk.displayunits.model.CleverTapDisplayUnit
import kotlinx.coroutines.flow.MutableStateFlow

/** What the banner shows, read from a CleverTap Native Display unit. */
class Banner(val unitId: String, val title: String, val message: String, val imageUrl: String, val actionUrl: String)

/**
 * CleverTap Native Display: the dashboard campaign decides who gets the banner (e.g. users who have not
 * traded); the app only draws it and reports views and taps. Units can be "simple" (title, message, image,
 * action URL) or key-value, which uses the keys title, message, imageUrl and action.
 */
object NativeDisplay {
    val banner = MutableStateFlow<Banner?>(null)

    // Set once the user trades or closes the banner, so a stale unit cannot bring it back this session.
    @Volatile
    private var hidden = false

    fun register(ct: CleverTapAPI) {
        ct.setDisplayUnitListener { units ->
            banner.value = if (hidden) null else units?.firstNotNullOfOrNull(::toBanner)
        }
    }

    fun hide() {
        hidden = true
        banner.value = null
    }

    /** Forget everything when the active user changes. */
    fun reset() {
        hidden = false
        banner.value = null
    }

    private fun toBanner(unit: CleverTapDisplayUnit): Banner? {
        val id = unit.unitID ?: return null
        val content = unit.contents?.firstOrNull()
        val extras = unit.customExtras.orEmpty()
        val title = extras["title"] ?: content?.title.orEmpty()
        val message = extras["message"] ?: content?.message.orEmpty()
        val imageUrl = extras["imageUrl"] ?: content?.media.orEmpty()
        // An image-only unit is fine; a unit with nothing to draw is skipped.
        if (title.isBlank() && message.isBlank() && !imageUrl.startsWith("https://")) return null
        return Banner(id, title, message, imageUrl, extras["action"] ?: content?.actionUrl.orEmpty())
    }

    fun viewed(context: Context, unitId: String) {
        CleverTapAPI.getDefaultInstance(context.applicationContext)?.pushDisplayUnitViewedEventForID(unitId)
    }

    fun clicked(context: Context, banner: Banner) {
        CleverTapAPI.getDefaultInstance(context.applicationContext)?.pushDisplayUnitClickedEventForID(banner.unitId)
        // Only our own papertrade:// links are followed; they switch tabs and never trade.
        if (banner.actionUrl.isNotBlank()) DeepLinks.handle(Uri.parse(banner.actionUrl))
    }
}

/** Shows the current Native Display banner, if any. */
@Composable
fun NativeDisplayBanner(modifier: Modifier = Modifier) {
    val banner by NativeDisplay.banner.collectAsState()
    val b = banner ?: return
    val context = LocalContext.current
    LaunchedEffect(b.unitId) { NativeDisplay.viewed(context, b.unitId) }

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4FAF5))
            .clickable { NativeDisplay.clicked(context, b) }
    ) {
        if (b.imageUrl.startsWith("https://")) {
            AsyncImage(
                model = b.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        }
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (b.title.isNotBlank()) Text(b.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F3D2A))
                if (b.message.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(b.message, fontSize = 14.sp, color = Color(0xFF4A6B55))
                }
            }
            TextButton(onClick = NativeDisplay::hide) { Text("✕", color = Color(0xFF4A6B55)) }
        }
    }
}
