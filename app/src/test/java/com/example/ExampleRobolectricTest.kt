package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.AdBypassHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Embed Player", appName)
    }

    @Test
    fun `detects ad domains and redirects accurately`() {
        val basePlayer = "https://player.abyssplayer.com/"
        val currentUrl = "https://player.abyssplayer.com/qVAy3O_2p"

        // Known ad domain
        val isAd = AdBypassHelper.isAdOrUnwantedRedirect(
            "https://popads.net/serve?ad=123",
            currentUrl,
            basePlayer
        )
        assertTrue(isAd)

        // Third-party external landing page
        val isExternalLanding = AdBypassHelper.isAdOrUnwantedRedirect(
            "https://random-betting-site.com/bonus",
            currentUrl,
            basePlayer
        )
        assertTrue(isExternalLanding)

        // Valid player stream asset
        val isStream = AdBypassHelper.isAdOrUnwantedRedirect(
            "https://player.abyssplayer.com/hls/qVAy3O_2p/stream.m3u8",
            currentUrl,
            basePlayer
        )
        assertFalse(isStream)
    }

    @Test
    fun `fast bypass script contains necessary protections`() {
        val script = AdBypassHelper.buildFastAdBypassScript()
        assertTrue(script.contains("window.open"))
        assertTrue(script.contains("video.play()"))
        assertTrue(script.contains("overlay"))
        assertTrue(script.contains("AndroidBridge"))
    }

    @Test
    fun `video control helper generates valid scripts for play pause and seek`() {
        val toggleScript = com.example.util.VideoControlHelper.buildTogglePlayPauseScript()
        assertTrue(toggleScript.contains("jwplayer"))
        assertTrue(toggleScript.contains("video.play()"))
        assertTrue(toggleScript.contains("video.pause()"))

        val seekForward = com.example.util.VideoControlHelper.buildSeekRelativeScript(10)
        assertTrue(seekForward.contains("10"))
        assertTrue(seekForward.contains("currentTime"))

        val seekBackward = com.example.util.VideoControlHelper.buildSeekRelativeScript(-10)
        assertTrue(seekBackward.contains("-10"))

        val restartScript = com.example.util.VideoControlHelper.buildRestartScript()
        assertTrue(restartScript.contains("currentTime = 0"))
    }
}
