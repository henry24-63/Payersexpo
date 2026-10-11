package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.PlayerState
import com.example.ui.components.EmbedInputBox
import com.example.ui.components.FullscreenPlayerScreen
import com.example.ui.components.SavedEmbedsSheet
import com.example.ui.components.VideoPlayerBox
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.AdBypassHelper
import com.example.util.PersistentWebViewHolder
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                EmbedPlayerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbedPlayerApp(
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedList by viewModel.savedList.collectAsStateWithLifecycle()
    var showSavedSheet by remember { mutableStateOf(false) }

    // Persistent web view holder preserved across portrait and landscape
    val webViewHolder = remember(context) {
        PersistentWebViewHolder(
            context = context,
            onProgressUpdate = { viewModel.updateLoadingProgress(it) },
            onTitleReceived = { viewModel.setPageTitle(it) },
            onErrorOccurred = { viewModel.setLoadError(it) },
            onAdBlocked = { viewModel.recordAdBlocked(it) }
        )
    }

    var lastReloadTrigger by remember { mutableStateOf(0L) }

    // Load initial embed code or react to user code changes
    LaunchedEffect(state.activeCode, state.reloadTrigger, state.isDesktopMode) {
        val isExplicitReload = state.reloadTrigger > 0L && state.reloadTrigger != lastReloadTrigger
        lastReloadTrigger = state.reloadTrigger
        webViewHolder.loadCodeIfChanged(state, forceReload = isExplicitReload)
    }

    // Auto-resume playback seamlessly when orientation/fullscreen changes
    LaunchedEffect(state.isFullscreen) {
        delay(40)
        webViewHolder.resumePlaybackIfPaused()
        delay(150)
        webViewHolder.resumePlaybackIfPaused()
    }

    // Trigger AdBypass script if requested
    LaunchedEffect(state.adBypassTrigger) {
        if (state.adBypassTrigger > 0L) {
            webViewHolder.webView.evaluateJavascript(AdBypassHelper.buildFastAdBypassScript(), null)
        }
    }

    // JS Mute injection when mute state changes
    LaunchedEffect(state.isMuted) {
        val jsMute = if (state.isMuted) "true" else "false"
        webViewHolder.webView.evaluateJavascript(
            """
            (function() {
                try {
                    let win = window;
                    let doc = document;
                    const ifr = document.querySelector('iframe');
                    if (ifr && ifr.contentWindow) {
                        win = ifr.contentWindow;
                        doc = ifr.contentWindow.document || doc;
                    }
                    const media = doc.querySelectorAll('video, audio');
                    media.forEach(m => { m.muted = $jsMute; });
                    if (win.jwplayer && typeof win.jwplayer === 'function') {
                        win.jwplayer().setMute($jsMute);
                    }
                } catch(e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    var currentTab by remember { mutableStateOf(0) } // 0 = User View (Player), 1 = Admin View (Box 1)

    if (state.isFullscreen) {
        FullscreenPlayerScreen(
            state = state,
            webViewHolder = webViewHolder,
            onExitFullscreen = { viewModel.setFullscreen(false) },
            onToggleMute = { viewModel.toggleMute() }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = CinemaBackground,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(NeonCyan, NeonPurple)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartDisplay,
                                        contentDescription = "Logo",
                                        tint = CinemaBackground,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (currentTab == 0) "User View (Player)" else "Admin View (Box 1)",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = if (currentTab == 0) "Watching active video stream" else "Embed Code Management",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showSavedSheet = true },
                                modifier = Modifier.testTag("saved_streams_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = "Saved Streams",
                                    tint = NeonCyan
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CinemaBackground
                        )
                    )

                    // Tab Switcher (User View vs Admin View)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentTab == 0) NeonCyan.copy(alpha = 0.2f) else CinemaSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (currentTab == 0) NeonCyan else CinemaBorder
                            ),
                            onClick = { currentTab = 0 }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartDisplay,
                                    contentDescription = "User View",
                                    tint = if (currentTab == 0) NeonCyan else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "User View (Player)",
                                    color = if (currentTab == 0) NeonCyan else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentTab == 1) NeonPurple.copy(alpha = 0.2f) else CinemaSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (currentTab == 1) NeonPurple else CinemaBorder
                            ),
                            onClick = { currentTab = 1 }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Admin View",
                                    tint = if (currentTab == 1) NeonPurple else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Admin View (Box 1)",
                                    color = if (currentTab == 1) NeonPurple else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (currentTab == 0) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            StatusWorkflowBanner()
                        }
                        item {
                            VideoPlayerBox(
                                state = state,
                                webViewHolder = webViewHolder,
                                onToggleFullscreen = { viewModel.toggleFullscreen() },
                                onToggleDesktop = { viewModel.toggleDesktopMode() },
                                onToggleMute = { viewModel.toggleMute() },
                                onFastAdBypass = { viewModel.triggerFastAdBypass() },
                                onReload = {
                                    viewModel.reloadPlayer()
                                    webViewHolder.reload()
                                },
                                onAspectRatioChange = { viewModel.setAspectRatio(it) },
                                onLoadSample = { sample ->
                                    viewModel.loadSample(sample)
                                },
                                onForceStopLoading = { viewModel.forceStopLoading() }
                            )
                        }
                        item {
                            SupportInfoCard()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            EmbedInputBox(
                                state = state,
                                onInputChange = { viewModel.setInputCode(it) },
                                onLoadPlayer = {
                                    viewModel.loadPlayer()
                                    Toast.makeText(context, "Streaming to User View...", Toast.LENGTH_SHORT).show()
                                    currentTab = 0
                                },
                                onClear = { viewModel.clearInput() },
                                onLoadSample = { sample ->
                                    viewModel.loadSample(sample)
                                    Toast.makeText(context, "Loaded sample into Player", Toast.LENGTH_SHORT).show()
                                    currentTab = 0
                                },
                                onSaveToFavorites = {
                                    viewModel.saveCurrentEmbed()
                                    Toast.makeText(context, "Saved to library!", Toast.LENGTH_SHORT).show()
                                },
                                onOpenSavedSheet = { showSavedSheet = true }
                            )
                        }
                        item {
                            FlowIndicator()
                        }
                        item {
                            SupportInfoCard()
                        }
                    }
                }
            }
        }
    }

    if (showSavedSheet) {
        SavedEmbedsSheet(
            savedList = savedList,
            onSelectEmbed = { saved ->
                viewModel.setInputCode(saved.code)
                viewModel.loadPlayer()
            },
            onDeleteEmbed = { id ->
                viewModel.deleteSavedEmbed(id)
            },
            onDismiss = { showSavedSheet = false }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.destroy()
        }
    }
}

@Composable
fun StatusWorkflowBanner() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CinemaSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Box 1: Paste Code  ➔  Box 2: Instant Video Stream",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "⚡ Continuous Playback: Video never pauses or restarts from 0:00 when switching to landscape mode.",
                    color = AccentGreen,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun FlowIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(CinemaBorder)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CinemaSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STREAMS DIRECTLY TO PLAYER",
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(CinemaBorder)
        )
    }
}

@Composable
fun SupportInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(CinemaBorder)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Protection Info",
                    tint = AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continuous Video & Playback Controls",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "• Zero-Reset Landscape Transition: The player engine is persistent and never reloads the webpage or restarts the video.\n" +
                        "• Full Controls in Landscape: ⏪ -10s Rewind, ▶/❚❚ Play & Pause, ⏩ +10s Forward, 00:00 Restart, Speed (1.0x-2.0x), Screen Lock.\n" +
                        "• Pop-up Ad Defense: 2 popup ad triggers are safely bypassed in milliseconds without interrupting playback.",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 18.sp
            )
        }
    }
}
