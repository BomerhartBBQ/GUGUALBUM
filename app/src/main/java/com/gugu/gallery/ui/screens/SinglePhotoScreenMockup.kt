package com.gugu.gallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
fun SinglePhotoScreenMockup() {
    // Shows standard immersive view with OSD overlay at bottom
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        
        // Mocking Full Image
        Text(
            text = "Full Screen 4K Image Placeholder",
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )

        // Bottom OSD (On-Screen Display) Info Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f)) // Semi-transparent Glass effect
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Auto Play Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Auto Play", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "自动播放 开", color = Color.White)
            }

            // Middle: Date and Time
            Text(text = "2026 Apr 30, 14:22:15", color = Color.White, style = MaterialTheme.typography.titleMedium)

            // Right: Location and EXIF
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Location", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "California, USA", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "EXIF", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "f/1.8 1/60 ISO 100", color = Color.LightGray)
                }
            }
        }
    }
}
