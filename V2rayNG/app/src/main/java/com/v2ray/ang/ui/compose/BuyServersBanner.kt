package com.v2ray.ang.ui.compose

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * A small, tappable promo pill that opens a Telegram chat to buy tunneled
 * servers. Deliberately compact and understated (not a full-width bar):
 * a glossy gradient chip with a soft drop shadow and a top highlight sheen,
 * so it reads as a refined 3D control rather than a big flat banner.
 */
@Composable
fun BuyServersBanner(
    text: String,
    telegramUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val base = MaterialTheme.colorScheme.primary
    val top = lightenColor(base, 0.30f)
    val bottom = darkenColor(base, 0.22f)
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wrapContentWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(top, base, bottom),
                        start = Offset(0f, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                )
                .clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)))
                    } catch (_: ActivityNotFoundException) {
                        // No app/browser can handle it; silently ignore.
                    }
                }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Subtle glossy sheen behind the icon, matching the top-bar badges.
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                                center = Offset(6f, 5f),
                                radius = 12f
                            )
                        )
                )
                Icon(
                    painter = painterResource(R.drawable.ic_telegram_24dp),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

private fun lightenColor(color: Color, factor: Float): Color = Color(
    red = color.red + (1f - color.red) * factor,
    green = color.green + (1f - color.green) * factor,
    blue = color.blue + (1f - color.blue) * factor,
    alpha = color.alpha
)

private fun darkenColor(color: Color, factor: Float): Color = Color(
    red = color.red * (1f - factor),
    green = color.green * (1f - factor),
    blue = color.blue * (1f - factor),
    alpha = color.alpha
)
