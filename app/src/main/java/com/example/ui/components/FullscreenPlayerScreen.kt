package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.model.AspectRatioOption
import com.example.model.PlayerState
import com.example.ui.theme.*
import com.example.util.AdBypassHelper
import com.example.util.PersistentWebViewHolder
import com.example.util.VideoControlHelper
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullscreenPlayerScreen(
    state: PlayerState,
    webViewHolder: PersistentWebViewHolder,
    onExitFullscreen: () -> Unit,
    onToggleMute: () -> Unit = {},
    onToggleDesktop: () -> Unit = {},
    onAspectRatioChange: (AspectRatioOption) -> Unit = {},
    onFastAdBypass: () -> Unit = {},
    onReload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = context as? Activity
    var showControls by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var volumeLevel by remember { mutableFloatStateOf(1.0f) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var feedbackHud by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (isScreenLocked) {
            isScreenLocked = false
            feedbackHud = "Screen Unlocked 🔓"
        } else {
            onExitFullscreen()
        }
    }

    LaunchedEffect(feedbackHud) {
        if (feedbackHud != null) {
            delay(1500)
            feedbackHud = null
        }
    }

    LaunchedEffect(Unit) {
        delay(30)
        webViewHolder.resumePlaybackIfPaused()
        delay(100)
        webViewHolder.resumePlaybackIfPaused()
        delay(300)
        webViewHolder.resumePlaybackIfPaused()
    }

    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            webViewHolder.detachFromParent()
            activity?.requestedOrientation = originalOrientation
            window?.let {
                val controller = WindowCompat.getInsetsController(it, it.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("fullscreen_player_container")
    ) {
        AndroidView(
            factory = { _ ->
                webViewHolder.detachFromParent()
                webViewHolder.webView
            },
            update = { _ ->
                webViewHolder.resumePlaybackIfPaused()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating pill when controls hidden
        if (!showControls && !isScreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CinemaSurfaceVariant.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                    modifier = Modifier.clickable {
                        showControls = true
                        feedbackHud = "Controls Shown"
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Show", tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show Controls", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Floating Lock Indicator
        if (isScreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CinemaSurfaceVariant.copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber),
                    modifier = Modifier.clickable {
                        isScreenLocked = false
                        showControls = true
                        feedbackHud = "Controls Unlocked 🔓"
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = AccentAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tap to Unlock 🔒", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Feedback HUD Pill
        AnimatedVisibility(
            visible = feedbackHud != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CinemaBackground.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonCyan)
            ) {
                Text(
                    text = feedbackHud ?: "",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp)
                )
            }
        }

        // LANDSCAPE EXIT BUTTON & SETTINGS (Hides when controls hide)
        if (showControls && !isScreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onExitFullscreen,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("exit_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit Landscape",
                            tint = NeonCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }

    // SETTINGS MODAL DIALOG IN LANDSCAPE
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = CinemaSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NeonCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Player Settings & Features", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Playback Speed Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Playback Speed", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                val selected = currentSpeed == speed
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) NeonCyan.copy(alpha = 0.25f) else CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) NeonCyan else CinemaBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            currentSpeed = speed
                                            webViewHolder.webView.evaluateJavascript(
                                                VideoControlHelper.buildPlaybackRateScript(speed),
                                                null
                                            )
                                            feedbackHud = "Speed: ${speed}x"
                                        }
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        Text("${speed}x", color = if (selected) NeonCyan else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Aspect Ratio Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Aspect Ratio / Screen Fit", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AspectRatioOption.values().forEach { option ->
                                val selected = state.aspectRatio == option
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) NeonCyan.copy(alpha = 0.25f) else CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) NeonCyan else CinemaBorder),
                                    modifier = Modifier.clickable { onAspectRatioChange(option) }
                                ) {
                                    Text(
                                        text = option.displayName,
                                        color = if (selected) NeonCyan else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. User Agent Mode (Mobile vs Desktop)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Stream Render Mode", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (!state.isDesktopMode) NeonPurple.copy(alpha = 0.25f) else CinemaSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (!state.isDesktopMode) NeonPurple else CinemaBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { if (state.isDesktopMode) onToggleDesktop() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Smartphone, contentDescription = "Mobile", tint = if (!state.isDesktopMode) NeonPurple else TextMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mobile UA", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.isDesktopMode) NeonPurple.copy(alpha = 0.25f) else CinemaSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (state.isDesktopMode) NeonPurple else CinemaBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { if (!state.isDesktopMode) onToggleDesktop() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Laptop, contentDescription = "Desktop", tint = if (state.isDesktopMode) NeonPurple else TextMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desktop UA", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 4. Quick Action Buttons
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Advanced Controls", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Button(
                            onClick = {
                                onFastAdBypass()
                                webViewHolder.webView.evaluateJavascript(AdBypassHelper.buildFastAdBypassScript(), null)
                                feedbackHud = "2-Ad Bypass Executed ⚡"
                                showSettingsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f), contentColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = "Bypass", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fast 2-Ad Auto-Bypass ⚡", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                webViewHolder.webView.evaluateJavascript(VideoControlHelper.buildRestartScript(), null)
                                feedbackHud = "Restarted from 00:00 🔄"
                                showSettingsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = "Restart", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restart Video (00:00)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                onReload()
                                webViewHolder.reload()
                                feedbackHud = "Stream Reloaded 🔄"
                                showSettingsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reload Stream", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (state.extractedUrl.isNotBlank()) {
                                        clipboardManager.setText(AnnotatedString(state.extractedUrl))
                                        Toast.makeText(context, "URL Copied!", Toast.LENGTH_SHORT).show()
                                        showSettingsDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = TextPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy URL", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val url = state.extractedUrl.ifBlank { "https://player.abyssplayer.com" }
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                        showSettingsDialog = false
                                    } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = TextPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Browser", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Browser", fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
