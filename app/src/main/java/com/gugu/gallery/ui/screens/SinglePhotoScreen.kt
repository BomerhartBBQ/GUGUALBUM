package com.gugu.gallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.gugu.gallery.data.PhotoEntity
import com.gugu.gallery.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SinglePhotoScreen(
    photos: List<PhotoEntity>,
    initialIndex: Int,
    onBack: () -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var isAutoPlay by remember { mutableStateOf(false) }
    var showOSD by remember { mutableStateOf(true) }
    val dateFormat = remember { SimpleDateFormat("yyyy MMM dd, HH:mm:ss", Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()
    
    val pagerFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }
    val rotateButtonFocusRequester = remember { FocusRequester() }
    
    val rotationAngles = remember { mutableStateMapOf<Long, Int>() }

    var activityCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(activityCounter, showOSD) {
        if (showOSD) {
            delay(5000)
            showOSD = false
        }
    }

    LaunchedEffect(isAutoPlay) {
        if (isAutoPlay) {
            while (isActive) {
                delay(5000)
                if (pagerState.pageCount > 0) {
                    val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        pagerFocusRequester.requestFocus()
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(pagerFocusRequester)
            .focusable()
            .onKeyEvent {
                showOSD = true
                activityCounter++
                
                when (it.key) {
                    Key.DirectionRight -> {
                        if (it.type == KeyEventType.KeyUp && pagerState.currentPage < photos.size - 1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (it.type == KeyEventType.KeyUp && pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        playButtonFocusRequester.requestFocus()
                        true
                    }
                    Key.Back -> {
                        if (it.type == KeyEventType.KeyUp) onBack()
                        true
                    }
                    else -> false
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
            pageSpacing = 0.dp,
            beyondViewportPageCount = 1
        ) { page ->
            val photo = photos[page]
            val rotation = rotationAngles.getOrPut(photo.id) { photo.rotationDegrees }
            SubcomposeAsyncImage(
                model = photo.localPreviewPath ?: photo.smbPath,
                contentDescription = photo.fileName,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation.toFloat()),
                contentScale = ContentScale.Fit,
                loading = { 
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.3f))
                    }
                },
                error = { 
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Warning, null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showOSD,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                val currentPhoto = photos.getOrNull(pagerState.currentPage)
                if (currentPhoto != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            ControlItem(
                                text = if (isAutoPlay) "自动播放：开" else "自动播放：关",
                                icon = Icons.Default.PlayArrow,
                                isFocused = isPlayFocused,
                                modifier = Modifier
                                    .focusRequester(playButtonFocusRequester)
                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                    .clickable { isAutoPlay = !isAutoPlay }
                                    .onKeyEvent {
                                        if (it.type != KeyEventType.KeyUp) return@onKeyEvent false
                                        when (it.key) {
                                            Key.DirectionRight -> {
                                                rotateButtonFocusRequester.requestFocus()
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                pagerFocusRequester.requestFocus()
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                            )

                            var isRotateFocused by remember { mutableStateOf(false) }
                            ControlItem(
                                text = "旋转",
                                icon = Icons.Default.Rotate90DegreesCcw,
                                isFocused = isRotateFocused,
                                modifier = Modifier
                                    .focusRequester(rotateButtonFocusRequester)
                                    .onFocusChanged { isRotateFocused = it.isFocused }
                                    .clickable {
                                        val photo = photos[pagerState.currentPage]
                                        val currentRotation =
                                            rotationAngles.getOrPut(photo.id) { photo.rotationDegrees }
                                        val newRotation = (currentRotation + 90) % 360
                                        rotationAngles[photo.id] = newRotation
                                        viewModel.updatePhotoRotation(photo.id, newRotation)
                                    }
                                    .onKeyEvent {
                                        if (it.type != KeyEventType.KeyUp) return@onKeyEvent false
                                        when (it.key) {
                                            Key.DirectionLeft -> {
                                                playButtonFocusRequester.requestFocus()
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                pagerFocusRequester.requestFocus()
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                            )
                        }
                        
                        Text(
                            text = dateFormat.format(Date(currentPhoto.dateTaken)),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            currentPhoto.locationName?.let { loc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(loc, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                 }
                            }
                            
                            val exifInfo = listOfNotNull(
                                currentPhoto.fStop,
                                currentPhoto.exposureTime,
                                currentPhoto.iso?.let { "ISO $it" }
                            ).joinToString("  ")
                            
                            if (exifInfo.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(exifInfo, color = Color.LightGray, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isFocused: Boolean,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .focusable()
            .background(
                if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
