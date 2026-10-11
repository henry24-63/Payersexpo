package com.example.util

import java.net.URI
import java.util.regex.Pattern

object EmbedParser {

    private val SRC_PATTERN = Pattern.compile("(?i)src\\s*=\\s*[\"']([^\"']+)[\"']|src\\s*=\\s*([^\\s>]+)")
    private val IFRAME_TAG_PATTERN = Pattern.compile("(?i)<iframe[^>]*>.*?</iframe>|<iframe[^>]*/>|<iframe[^>]*>")

    /**
     * Extracts video or iframe stream URL from raw embed code or raw URL string.
     */
    fun extractStreamUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        // Check if raw input is directly an HTTP/HTTPS URL
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val spaceIndex = trimmed.indexOfAny(charArrayOf(' ', '\n', '\t', '<', '>', '"', '\''))
            return if (spaceIndex > 0) trimmed.substring(0, spaceIndex) else trimmed
        }

        // Protocol-relative URL
        if (trimmed.startsWith("//")) {
            val spaceIndex = trimmed.indexOfAny(charArrayOf(' ', '\n', '\t', '<', '>', '"', '\''))
            val clean = if (spaceIndex > 0) trimmed.substring(0, spaceIndex) else trimmed
            return "https:$clean"
        }

        // Extract from src attribute
        val matcher = SRC_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            val extracted = matcher.group(1) ?: matcher.group(2) ?: ""
            if (extracted.startsWith("//")) {
                return "https:$extracted"
            }
            return extracted
        }

        return ""
    }

    /**
     * Derives base URL from the target stream for CORS and relative referrer resolution.
     */
    fun getBaseUrl(url: String): String {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return "https://player.abyssplayer.com/"
            "$scheme://$host/"
        } catch (_: Exception) {
            "https://player.abyssplayer.com/"
        }
    }

    /**
     * Wraps user embed code or URL inside a pristine, responsive full-bleed HTML player container
     * with built-in ad bypasser scripts and overlay suppression.
     */
    fun buildResponsiveHtml(rawInput: String, extractedUrl: String): String {
        val trimmed = rawInput.trim()

        val lowerExtracted = extractedUrl.lowercase()
        val isDirectVideo = lowerExtracted.endsWith(".mp4") || lowerExtracted.endsWith(".webm") || 
                            lowerExtracted.endsWith(".m3u8") || lowerExtracted.contains(".mp4?") ||
                            lowerExtracted.contains(".m3u8?")

        val embedContent = when {
            isDirectVideo -> {
                """
                <video controls playsinline preload="auto" style="width:100%;height:100%;background:#000;">
                    <source src="$extractedUrl">
                    Your browser does not support video playback.
                </video>
                """.trimIndent()
            }
            // If user pasted a full iframe or tag, inject full responsive styling
            trimmed.contains("<iframe", ignoreCase = true) -> {
                var processed = trimmed
                if (!processed.contains("allowfullscreen", ignoreCase = true)) {
                    processed = processed.replace("<iframe", "<iframe allowfullscreen", ignoreCase = true)
                }
                if (!processed.contains("allow=", ignoreCase = true)) {
                    processed = processed.replace(
                        "<iframe",
                        "<iframe allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen\"",
                        ignoreCase = true
                    )
                }
                processed
            }
            // If user pasted a raw video tag
            trimmed.contains("<video", ignoreCase = true) -> {
                trimmed
            }
            // If user entered direct URL or we have an extracted URL
            extractedUrl.isNotBlank() -> {
                """
                <iframe 
                    src="$extractedUrl" 
                    frameborder="0" 
                    scrolling="0" 
                    allowfullscreen 
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen">
                </iframe>
                """.trimIndent()
            }
            else -> {
                trimmed
            }
        }

        val fastScript = AdBypassHelper.buildFastAdBypassScript()

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta name="referrer" content="no-referrer-when-downgrade">
                <style>
                    * {
                        box-sizing: border-box;
                        margin: 0;
                        padding: 0;
                    }
                    html, body {
                        width: 100%;
                        height: 100%;
                        background-color: #000000;
                        overflow: hidden;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    iframe, video, object, embed {
                        width: 100% !important;
                        height: 100% !important;
                        max-width: 100% !important;
                        max-height: 100% !important;
                        border: 0 !important;
                        outline: none !important;
                        display: block;
                    }
                </style>
                <script>
                    $fastScript
                </script>
            </head>
            <body>
                $embedContent
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Detects stream service name from URL for visual badges.
     */
    fun detectProvider(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("abyssplayer") -> "AbyssPlayer"
            lower.contains("youtube") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("vimeo") -> "Vimeo"
            lower.contains("dailymotion") -> "Dailymotion"
            lower.contains("twitch") -> "Twitch"
            lower.contains("streamtape") -> "Streamtape"
            lower.contains("doodstream") || lower.contains("dood") -> "DoodStream"
            lower.contains(".mp4") -> "Direct MP4"
            lower.contains(".m3u8") -> "HLS Stream"
            url.isBlank() -> "None"
            else -> "Web IFrame"
        }
    }
}
