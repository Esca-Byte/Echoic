package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.SomifyTheme
import com.example.ui.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unlockHighestDisplayRefreshRate()
        configureCoilDiskCache()
        enableEdgeToEdge()

        setContent {
            val themeState by playerViewModel.themeState.collectAsStateWithLifecycle()
            SomifyTheme(themeState = themeState) {
                // Request POST_NOTIFICATIONS permission on Android 13+ for lock-screen & notification media controls
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { /* Continues gracefully whether granted or denied */ }
                    )
                    LaunchedEffect(Unit) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                HomeScreen(
                    viewModel = playerViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            unlockHighestDisplayRefreshRate()
        }
    }

    /**
     * Unlocks the panel's maximum supported refresh rate (90Hz / 120Hz / 144Hz) matching the
     * current display resolution so OEM dynamic-refresh schedulers do not clamp Compose scrolling to 60Hz.
     */
    private fun unlockHighestDisplayRefreshRate() {
        runCatching {
            val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return
            val currentMode = activeDisplay.mode
            val bestMode = activeDisplay.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: activeDisplay.supportedModes.maxByOrNull { it.refreshRate }
                ?: return

            val params = window.attributes
            if (params.preferredDisplayModeId != bestMode.modeId ||
                params.preferredRefreshRate != bestMode.refreshRate
            ) {
                params.preferredDisplayModeId = bestMode.modeId
                params.preferredRefreshRate = bestMode.refreshRate
                window.attributes = params
            }
        }
    }

    private fun configureCoilDiskCache() {
        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("somify_artwork_cache"))
                    .maxSizeBytes(120L * 1024L * 1024L) // 120MB dedicated artwork disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
