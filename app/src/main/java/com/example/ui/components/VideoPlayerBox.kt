package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
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
import com.example.model.AspectRatioOption
import com.example.model.PlayerState
import com.example.ui.theme.*
import com.example.util.AdBypassHelper
import com.example.util.PersistentWebViewHolder
import com.example.util.VideoControlHelper
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

/**
 * Player box with completely transparent, borderless glassmorphic overlay controls.
 * Features frequent controls directly on player (Play/Pause, Rewind -10s, Forward +10s, Mute, Volume Slider, Settings, Fullscreen/Landscape toggle).
 * Title is hidden on player. All extra features are housed inside the Settings dialog.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayerBox(
    state: PlayerState,
    webViewHolder: PersistentWebViewHolder,
    onToggleFullscreen: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleMute: () -> Unit,
    onFastAdBypass: () -> Unit,
    onReload: () -> Unit,
    onAspectRatioChange: (AspectRatioOption) -> Unit,
    onLoadSample: (String) -> Unit,
    onForceStopLoading: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isPlayingState by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var volumeLevel by remember { mutableFloatStateOf(1.0f) }
    var isPlayerLocked by remember { mutableStateOf(false) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var feedbackHud by remember { mutableStateOf<String?>(null) }

    // No autoplay on initial load - user controls playback manually
    LaunchedEffect(Unit) {
        // Keep video paused on load so user can manually control playback
    }

    LaunchedEffect(feedbackHud) {
        if (feedbackHud != null) {
            delay(1500)
            feedbackHud = null
        }
    }

    // Safety timeout to ensure loading spinner never gets stuck
    LaunchedEffect(state.isLoading) {
        if (state.isLoading) {
            delay(4000)
            if (state.isLoading) {
                onForceStopLoading()
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_player_box"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(CinemaBorder)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val aspectModifier = when (val ratio = state.aspectRatio.ratio) {
                null -> Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                else -> Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
            }

            Box(
                modifier = aspectModifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (state.activeCode.isNotBlank()) {
                    AndroidView(
                        factory = { _ ->
                            webViewHolder.detachFromParent()
                            webViewHolder.webView
                        },
                        update = { _ ->
                            webViewHolder.resumePlaybackIfPaused()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("embed_webview")
                    )

                    // Loading Indicator
                    if (state.isLoading && state.loadProgress < 100) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = NeonCyan,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Buffering ${state.loadProgress}%",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Error Overlay
                    if (state.errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = AccentAmber,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.errorMessage,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onReload,
                                    colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant)
                                ) {
                                    Text("Retry Stream", color = NeonCyan, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Feedback HUD Pill
                    if (feedbackHud != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CinemaBackground.copy(alpha = 0.9f),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonCyan)
                            ) {
                                Text(
                                    text = feedbackHud ?: "",
                                    color = NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Floating Show Controls Pill if hidden
                    if (!showPlayerControls && !isPlayerLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CinemaSurfaceVariant.copy(alpha = 0.88f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                modifier = Modifier.clickable {
                                    showPlayerControls = true
                                    feedbackHud = "Controls Visible"
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = "Show", tint = NeonCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Controls", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Floating Lock Indicator
                    if (isPlayerLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CinemaSurfaceVariant.copy(alpha = 0.9f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber),
                                modifier = Modifier.clickable {
                                    isPlayerLocked = false
                                    showPlayerControls = true
                                    feedbackHud = "Player Unlocked 🔓"
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = AccentAmber, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Locked (Tap)", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Controls are always accessible via top bar without intercepting video player taps

                    // LANDSCAPE / FULLSCREEN TOGGLE BUTTON & SETTINGS (Hides when controls hide)
                    if (showPlayerControls && !isPlayerLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .padding(12.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onToggleFullscreen,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("fullscreen_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (state.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Landscape View",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Empty State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Empty Player",
                            tint = NeonPurple.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Video Loaded",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Paste your embed code above to start instant playback",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onLoadSample(PlayerViewModel.SAMPLE_ABYSS) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple.copy(alpha = 0.2f),
                                contentColor = NeonPurple
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Load Default Abyss Stream", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // SETTINGS MODAL DIALOG FOR EXTRA FEATURES
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
