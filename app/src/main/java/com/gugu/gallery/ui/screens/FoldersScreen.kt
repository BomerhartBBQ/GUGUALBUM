package com.gugu.gallery.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gugu.gallery.ui.viewmodel.GalleryViewModel
import com.gugu.gallery.data.SharedFolderEntity
import androidx.compose.ui.draw.scale

@Composable
fun FoldersScreen(
    viewModel: GalleryViewModel = viewModel(),
    onFolderClick: (SharedFolderEntity) -> Unit
) {
    val folders by viewModel.db.galleryDao().getAllFolders().collectAsState(initial = emptyList()) 

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp)) {
        Text(
            text = "相册",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无相册，请在设置中添加并索引文件夹", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                items(folders) { folder ->
                    FolderCard(folder = folder, viewModel = viewModel, onClick = { onFolderClick(folder) })
                }
            }
        }
    }
}

@Composable
fun FolderCard(
    folder: SharedFolderEntity, 
    viewModel: GalleryViewModel,
    onClick: () -> Unit
) {
    val photos by viewModel.db.galleryDao().getPhotosForFolder(folder.id).collectAsState(initial = emptyList())
    var isFocused by remember { mutableStateOf(false) }
    
    // 封面图片轮播逻辑
    var currentThumbIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(photos) {
        if (photos.size > 1) {
            while (true) {
                delay(3000) // 每3秒切换一次
                currentThumbIndex = (currentThumbIndex + 1) % photos.size.coerceAtMost(3)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(if (isFocused) 1.05f else 1.0f) // 焦点放大效果
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .focusable()
    ) {
        // 卡片堆叠效果
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
            contentAlignment = Alignment.Center
        ) {
            // 背景卡片2 (最底层)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .offset(y = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray.copy(alpha = 0.5f))
            )
            // 背景卡片1 (中间层)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .offset(y = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Gray.copy(alpha = 0.5f))
            )

            // 前景卡片 (显示轮播图)
            Card(
                modifier = Modifier.fillMaxSize().shadow(elevation = if(isFocused) 16.dp else 4.dp, shape = RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                border = if (isFocused) BorderStroke(3.dp, Color.White) else null
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (photos.isNotEmpty()) {
                        val displayPhotos = photos.take(3)
                        val photoToShow = displayPhotos.getOrNull(currentThumbIndex) ?: displayPhotos.first()
                        
                        AsyncImage(
                            model = photoToShow.localThumbnailPath ?: photoToShow.smbPath,
                            contentDescription = "相册封面",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(text = "📁", style = MaterialTheme.typography.displayLarge)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = folder.displayName, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(text = "${photos.size} 张照片", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}
