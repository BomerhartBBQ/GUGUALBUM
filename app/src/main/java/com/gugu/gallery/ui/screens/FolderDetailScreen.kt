package com.gugu.gallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gugu.gallery.ui.viewmodel.GalleryViewModel
import com.gugu.gallery.data.PhotoEntity
import com.gugu.gallery.data.SharedFolderEntity

@Composable
fun FolderDetailScreen(
    folder: SharedFolderEntity,
    viewModel: GalleryViewModel = viewModel(),
    onPhotoClick: (List<PhotoEntity>, Int) -> Unit
) {
    val photos by viewModel.db.galleryDao().getPhotosForFolder(folder.id).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Text(
            text = folder.displayName,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp, start = 8.dp)
        )

        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "该相册暂无照片", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(photos) { index, photo ->
                    PhotoGridItem(
                        photo = photo,
                        onClick = { onPhotoClick(photos, index) }
                    )
                }
            }
        }
    }
}
