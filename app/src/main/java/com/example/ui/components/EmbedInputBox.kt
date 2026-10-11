package com.example.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayerState
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.EmbedParser
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmbedInputBox(
    state: PlayerState,
    onInputChange: (String) -> Unit,
    onLoadPlayer: () -> Unit,
    onClear: () -> Unit,
    onLoadSample: (String) -> Unit,
    onSaveToFavorites: () -> Unit,
    onOpenSavedSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val detectedProvider = remember(state.rawInput) {
        val extracted = EmbedParser.extractStreamUrl(state.rawInput)
        EmbedParser.detectProvider(extracted)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("embed_input_box"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(CinemaBorder)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Box 1 Identifier & Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .border(1.dp, NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "1",
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "BOX 1: EMBED CODE INPUT",
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Paste <iframe...> or video player URL",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Provider badge
                if (state.rawInput.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeonPurple.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = detectedProvider,
                            color = NeonPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Actions Bar
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Paste Button
                SuggestionChip(
                    onClick = {
                        val clip = clipboardManager.getText()
                        if (clip != null && clip.text.isNotBlank()) {
                            onInputChange(clip.text)
                        }
                    },
                    label = { Text("Paste Clip", fontSize = 12.sp) },
                    icon = {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = CinemaSurfaceVariant,
                        labelColor = TextPrimary,
                        iconContentColor = NeonCyan
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = CinemaBorder
                    )
                )

                // Load AbyssPlayer Sample (User prompt specific)
                SuggestionChip(
                    onClick = {
                        onLoadSample(PlayerViewModel.SAMPLE_ABYSS)
                    },
                    label = { Text("Abyss Sample", fontSize = 12.sp) },
                    icon = {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = "Sample",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = NeonCyan.copy(alpha = 0.12f),
                        labelColor = NeonCyan,
                        iconContentColor = NeonCyan
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = NeonCyan.copy(alpha = 0.4f)
                    )
                )

                // YouTube Sample
                SuggestionChip(
                    onClick = {
                        onLoadSample(PlayerViewModel.SAMPLE_YOUTUBE)
                    },
                    label = { Text("YouTube", fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = CinemaSurfaceVariant,
                        labelColor = TextSecondary
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = CinemaBorder
                    )
                )

                // Saved Library
                SuggestionChip(
                    onClick = onOpenSavedSheet,
                    label = { Text("Saved Streams", fontSize = 12.sp) },
                    icon = {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = "Saved",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = CinemaSurfaceVariant,
                        labelColor = TextPrimary,
                        iconContentColor = NeonPurple
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = CinemaBorder
                    )
                )

                if (state.rawInput.isNotBlank()) {
                    SuggestionChip(
                        onClick = onClear,
                        label = { Text("Clear", fontSize = 12.sp) },
                        icon = {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = CinemaSurfaceVariant,
                            labelColor = TextSecondary,
                            iconContentColor = TextSecondary
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = CinemaBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Code Input Box (TextField)
            OutlinedTextField(
                value = state.rawInput,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("embed_code_input"),
                placeholder = {
                    Text(
                        text = "<iframe width=\"640\" height=\"360\" src=\"https://player.abyssplayer.com/...\" allowfullscreen></iframe>\n\nPaste embed code or video player URL here...",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = NeonCyan,
                    lineHeight = 16.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CodeBackground,
                    unfocusedContainerColor = CodeBackground,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CinemaBorder,
                    cursorColor = NeonCyan
                ),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLoadPlayer() }),
                trailingIcon = {
                    if (state.rawInput.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear text",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row: Primary Play button + Save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLoadPlayer,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("load_player_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = CinemaBackground
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PLAY IN BOX 2",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                OutlinedButton(
                    onClick = onSaveToFavorites,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("save_embed_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonPurple
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f))
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Save Embed",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
