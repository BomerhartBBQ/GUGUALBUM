package com.gugu.gallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gugu.gallery.ui.viewmodel.GalleryViewModel
import com.gugu.gallery.data.PhotoEntity
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning

@Composable
fun MainGalleryScreen(
    viewModel: GalleryViewModel = viewModel(),
    onPhotoClick: (List<PhotoEntity>, Int) -> Unit
) {
    val photos by viewModel.allPhotos.collectAsState(initial = emptyList())
    val gridState = rememberLazyGridState()
    
    val scrollProgress by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems > 0) {
                gridState.firstVisibleItemIndex.toFloat() / totalItems.toFloat()
            } else {
                0f
            }
        }
    }

    val groupedPhotos = remember(photos) {
        photos.groupBy { it.yearMonth }
    }

    Row(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxHeight().width(32.dp).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.2f)))
            Box(
                modifier = Modifier.fillMaxHeight(0.8f).width(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .align(BiasAlignment(0f, scrollProgress * 2 - 1))
                        .size(6.dp)
                        .background(Color.White, shape = CircleShape)
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (photos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "暂无照片，请在设置中更新索引", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    groupedPhotos.forEach { (yearMonth, monthPhotos) ->
                        item(span = { GridItemSpan(5) }) {
                            Text(
                                text = formatYearMonth(yearMonth),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp, start = 8.dp)
                            )
                        }

                        itemsIndexed(monthPhotos) { _, photo ->
                            val originalIndex = photos.indexOf(photo)
                            PhotoGridItem(
                                photo = photo,
                                onClick = { onPhotoClick(photos, originalIndex) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatYearMonth(yearMonth: String): String {
    val parts = yearMonth.split("-")
    if (parts.size != 2) return yearMonth
    val monthName = when(parts[1].toInt()) {
        1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
        7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
        else -> parts[1]
    }
    return "${parts[0]} $monthName"
}

@Composable
fun PhotoGridItem(photo: PhotoEntity, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent {
                // 显式捕获遥控器确定键
                if (it.key == Key.DirectionCenter || it.key == Key.Enter) {
                    if (it.type == KeyEventType.KeyUp) {
                        onClick()
                    }
                    true
                } else false
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        SubcomposeAsyncImage(
            model = photo.localThumbnailPath ?: photo.smbPath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { 
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.5f))
                }
            },
            error = { 
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Warning, null, tint = Color.DarkGray)
                }
            }
        )
        
        if (photo.isVideo) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(4.dp)) {
                Text(text = "▶", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (isFocused) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx()))
            }
        }
    }
}
