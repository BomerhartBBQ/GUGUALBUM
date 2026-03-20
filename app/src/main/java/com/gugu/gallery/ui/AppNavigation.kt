package com.gugu.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import com.gugu.gallery.ui.screens.*
import com.gugu.gallery.data.PhotoEntity
import com.gugu.gallery.data.SharedFolderEntity
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.gugu.gallery.R // Corrected import for R file
import androidx.compose.ui.layout.ContentScale

sealed class Screen {
    object Gallery : Screen()
    object Folders : Screen()
    data class FolderDetail(val folder: SharedFolderEntity) : Screen()
    data class PhotoPreview(val photos: List<PhotoEntity>, val initialIndex: Int) : Screen()
    object Settings : Screen()
}

@Composable
fun AppNavigation() {
    val navigationStack = remember { mutableStateListOf<Screen>(Screen.Gallery) }
    val currentScreen = navigationStack.last()

    fun navigateTo(screen: Screen) {
        navigationStack.add(screen)
    }

    fun goBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.size - 1)
        }
    }

    BackHandler(enabled = navigationStack.size > 1) {
        goBack()
    }

    val showSideMenu = when (currentScreen) {
        is Screen.PhotoPreview, is Screen.FolderDetail -> false
        else -> true
    }

    Row(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (showSideMenu) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF121212))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.gugu_logo_text),
                    contentDescription = "GuGu Gallery Logo",
                    modifier = Modifier
                        .fillMaxWidth()
                        // Removed fixed height to let it adjust proportionally
                        .padding(bottom = 40.dp),
                    contentScale = ContentScale.FillWidth // Changed to FillWidth to ensure it occupies the full width
                )
                NavigationButton("所有照片", currentScreen is Screen.Gallery) {
                    if (navigationStack.last() !is Screen.Gallery) {
                        navigationStack.clear(); navigationStack.add(Screen.Gallery)
                    }
                }
                NavigationButton("相册", currentScreen is Screen.Folders) {
                    if (navigationStack.last() !is Screen.Folders) {
                        navigationStack.clear(); navigationStack.add(Screen.Folders)
                    }
                }
                NavigationButton("设置", currentScreen is Screen.Settings) {
                    if (navigationStack.last() !is Screen.Settings) {
                        navigationStack.clear(); navigationStack.add(Screen.Settings)
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.Black)) {
            when (val screen = currentScreen) {
                is Screen.Gallery -> MainGalleryScreen(onPhotoClick = { photos, index -> navigateTo(Screen.PhotoPreview(photos, index)) })
                is Screen.Folders -> FoldersScreen(onFolderClick = { folder -> navigateTo(Screen.FolderDetail(folder)) })
                is Screen.FolderDetail -> FolderDetailScreen(
                    folder = screen.folder,
                    onPhotoClick = { photos, index -> navigateTo(Screen.PhotoPreview(photos, index)) }
                )
                is Screen.PhotoPreview -> SinglePhotoScreen(
                    photos = screen.photos,
                    initialIndex = screen.initialIndex,
                    onBack = { goBack() }
                )
                is Screen.Settings -> SettingsScreen()
            }
        }
    }
}

@Composable
fun NavigationButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.White else Color.Transparent,
            contentColor = if (isSelected) Color.Black else Color.White
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}
