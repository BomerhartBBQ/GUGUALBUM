package com.gugu.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.gugu.gallery.ui.theme.GuGuGalleryTheme

@OptIn(ExperimentalTvMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuGuGalleryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    com.gugu.gallery.ui.AppNavigation()
                }
            }
        }
    }
}
