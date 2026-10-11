package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.model.AspectRatioOption
import com.example.model.PlayerState
import com.example.model.RenderMode
import com.example.model.SavedEmbed
import com.example.util.EmbedParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("embed_player_prefs", Context.MODE_PRIVATE)

    companion object {
        const val SAMPLE_ABYSS = """<iframe width="640" height="360" src="https://player.abyssplayer.com/F79dpdMrz" frameborder="0" scrolling="0" allowfullscreen></iframe>"""
        const val SAMPLE_BIG_BUCK = """<iframe width="640" height="360" src="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" frameborder="0" allowfullscreen></iframe>"""
        const val SAMPLE_YOUTUBE = """<iframe width="640" height="360" src="https://www.youtube.com/embed/dQw4w9WgXcQ" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>"""
    }

    private val _uiState = MutableStateFlow(PlayerState())
    val uiState: StateFlow<PlayerState> = _uiState.asStateFlow()

    private val _savedList = MutableStateFlow<List<SavedEmbed>>(emptyList())
    val savedList: StateFlow<List<SavedEmbed>> = _savedList.asStateFlow()

    init {
        loadSavedEmbeds()
        // Initialize with user's requested AbyssPlayer embed code
        setInputCode(SAMPLE_ABYSS)
        loadPlayer()
    }

    fun setInputCode(code: String) {
        _uiState.update { it.copy(rawInput = code) }
    }

    fun clearInput() {
        _uiState.update { it.copy(rawInput = "") }
    }

    fun loadSample(sample: String) {
        _uiState.update { it.copy(rawInput = sample) }
        loadPlayer()
    }

    fun loadPlayer() {
        val currentInput = _uiState.value.rawInput.trim()
        if (currentInput.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please enter an embed code or video URL",
                    activeCode = "",
                    extractedUrl = ""
                )
            }
            return
        }

        val extracted = EmbedParser.extractStreamUrl(currentInput)
        _uiState.update {
            it.copy(
                activeCode = currentInput,
                extractedUrl = extracted,
                isLoading = true,
                loadProgress = 10,
                errorMessage = null,
                reloadTrigger = System.currentTimeMillis(),
                blockedAdsCount = 0,
                adBypassTrigger = System.currentTimeMillis()
            )
        }
    }

    fun reloadPlayer() {
        _uiState.update {
            it.copy(
                isLoading = true,
                loadProgress = 15,
                reloadTrigger = System.currentTimeMillis(),
                adBypassTrigger = System.currentTimeMillis()
            )
        }
    }

    fun triggerFastAdBypass() {
        _uiState.update {
            it.copy(adBypassTrigger = System.currentTimeMillis())
        }
    }

    fun toggleAdBlocker() {
        _uiState.update {
            it.copy(isAdBlockerActive = !it.isAdBlockerActive)
        }
    }

    fun recordAdBlocked(url: String) {
        _uiState.update {
            it.copy(
                blockedAdsCount = it.blockedAdsCount + 1,
                lastBlockedAdUrl = url
            )
        }
    }

    fun setAspectRatio(ratio: AspectRatioOption) {
        _uiState.update { it.copy(aspectRatio = ratio) }
    }

    fun setRenderMode(mode: RenderMode) {
        _uiState.update {
            it.copy(
                renderMode = mode,
                reloadTrigger = System.currentTimeMillis()
            )
        }
    }

    fun toggleDesktopMode() {
        _uiState.update {
            it.copy(
                isDesktopMode = !it.isDesktopMode,
                reloadTrigger = System.currentTimeMillis()
            )
        }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun setFullscreen(isFullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = isFullscreen) }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun updateLoadingProgress(progress: Int) {
        _uiState.update {
            it.copy(
                loadProgress = progress,
                isLoading = progress < 100
            )
        }
    }

    fun forceStopLoading() {
        _uiState.update {
            it.copy(
                loadProgress = 100,
                isLoading = false
            )
        }
    }

    fun setPageTitle(title: String) {
        if (title.isNotBlank() && !title.startsWith("http")) {
            _uiState.update { it.copy(pageTitle = title) }
        }
    }

    fun setLoadError(error: String?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = error
            )
        }
    }

    fun saveCurrentEmbed(customTitle: String? = null) {
        val state = _uiState.value
        val codeToSave = state.activeCode.ifBlank { state.rawInput }
        if (codeToSave.isBlank()) return

        val provider = EmbedParser.detectProvider(state.extractedUrl)
        val title = customTitle ?: state.pageTitle.ifBlank {
            "$provider Stream (${System.currentTimeMillis() % 10000})"
        }

        val newEmbed = SavedEmbed(
            id = UUID.randomUUID().toString(),
            title = title,
            code = codeToSave,
            extractedUrl = state.extractedUrl
        )

        val updated = listOf(newEmbed) + _savedList.value.filter { it.code != codeToSave }
        _savedList.value = updated
        persistSavedEmbeds(updated)
    }

    fun deleteSavedEmbed(id: String) {
        val updated = _savedList.value.filter { it.id != id }
        _savedList.value = updated
        persistSavedEmbeds(updated)
    }

    private fun loadSavedEmbeds() {
        val jsonString = prefs.getString("saved_embeds", null)
        if (!jsonString.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<SavedEmbed>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SavedEmbed(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            code = obj.getString("code"),
                            extractedUrl = obj.optString("extractedUrl", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                _savedList.value = list
                return
            } catch (_: Exception) {
                // fallback to default
            }
        }

        // Default initial items
        val defaults = listOf(
            SavedEmbed(
                id = "sample_abyss",
                title = "AbyssPlayer Stream",
                code = SAMPLE_ABYSS,
                extractedUrl = "https://player.abyssplayer.com/F79dpdMrz"
            ),
            SavedEmbed(
                id = "sample_youtube",
                title = "YouTube Embed",
                code = SAMPLE_YOUTUBE,
                extractedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ"
            ),
            SavedEmbed(
                id = "sample_bunny",
                title = "Direct MP4 Test Video",
                code = SAMPLE_BIG_BUCK,
                extractedUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            )
        )
        _savedList.value = defaults
        persistSavedEmbeds(defaults)
    }

    private fun persistSavedEmbeds(list: List<SavedEmbed>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("code", item.code)
                    put("extractedUrl", item.extractedUrl)
                    put("timestamp", item.timestamp)
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_embeds", array.toString()).apply()
        } catch (_: Exception) {
        }
    }
}
