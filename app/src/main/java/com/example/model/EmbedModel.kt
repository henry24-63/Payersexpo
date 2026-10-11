package com.example.model

enum class AspectRatioOption(val ratio: Float?, val displayName: String) {
    RATIO_16_9(16f / 9f, "16:9"),
    RATIO_4_3(4f / 3f, "4:3"),
    RATIO_1_1(1f / 1f, "1:1"),
    RATIO_21_9(21f / 9f, "21:9"),
    RATIO_FILL(null, "Fill")
}

enum class RenderMode(val label: String) {
    AUTO("IFrame Embed"),
    RESPONSIVE_WRAPPER("HTML Embed"),
    DIRECT_URL("Direct URL")
}

data class SavedEmbed(
    val id: String,
    val title: String,
    val code: String,
    val extractedUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)

const val DEFAULT_EMBED_CODE = """<iframe width="640" height="360" src="https://player.abyssplayer.com/qVAy3O_2p" frameborder="0" scrolling="0" allowfullscreen></iframe>"""

data class PlayerState(
    val rawInput: String = DEFAULT_EMBED_CODE,
    val activeCode: String = DEFAULT_EMBED_CODE,
    val extractedUrl: String = "https://player.abyssplayer.com/qVAy3O_2p",
    val pageTitle: String = "AbyssPlayer Stream",
    val isLoading: Boolean = false,
    val loadProgress: Int = 10,
    val isMuted: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isFullscreen: Boolean = false,
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_16_9,
    val renderMode: RenderMode = RenderMode.AUTO,
    val errorMessage: String? = null,
    val reloadTrigger: Long = 0L,
    val isAdBlockerActive: Boolean = true,
    val blockedAdsCount: Int = 0,
    val lastBlockedAdUrl: String? = null,
    val adBypassTrigger: Long = 0L
)
