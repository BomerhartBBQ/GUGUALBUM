package com.gugu.gallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import coil.compose.SubcomposeAsyncImage
import com.gugu.gallery.data.PhotoEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoPreviewScreen(
    photos: List<PhotoEntity>,
    initialIndex: Int,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var isAutoPlay by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy MMM dd, HH:mm:ss", Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()
    val pagerFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isAutoPlay) {
        if (isAutoPlay) {
            while (true) {
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
                when (it.key) {
                    Key.DirectionRight -> {
                        if (pagerState.currentPage < photos.size - 1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        buttonFocusRequester.requestFocus()
                        true
                    }
                    else -> false
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            val photo = photos[page]
            SubcomposeAsyncImage(
                model = photo.localPreviewPath ?: photo.smbPath,
                contentDescription = photo.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = { CircularProgressIndicator(modifier = Modifier.wrapContentSize(Alignment.Center)) },
                error = { Icon(Icons.Default.Warning, "Error", tint = Color.Gray) }
            )
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出", tint = Color.White)
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.7f)
        ) {
            val currentPhoto = photos.getOrNull(pagerState.currentPage)
            if (currentPhoto != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FocusableButton(
                        onClick = { isAutoPlay = !isAutoPlay },
                        text = if (isAutoPlay) "暂停轮播" else "自动轮播",
                        // 核心修复：使用正确的参数名
                        unfocusedContainerColor = if (isAutoPlay) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                        modifier = Modifier.focusRequester(buttonFocusRequester)
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dateFormat.format(Date(currentPhoto.dateTaken)), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(currentPhoto.fileName, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val exifInfo = listOfNotNull(
                            currentPhoto.fStop,
                            currentPhoto.exposureTime,
                            currentPhoto.iso?.let { "ISO $it" }
                        ).joinToString(" ")
                        
                        if (exifInfo.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(exifInfo, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
