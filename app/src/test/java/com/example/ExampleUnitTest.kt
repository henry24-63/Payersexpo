package com.example

import com.example.util.EmbedParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun parseAbyssPlayerEmbedCode() {
        val embed = """<iframe width="640" height="360" src="https://player.abyssplayer.com/qVAy3O_2p" frameborder="0" scrolling="0" allowfullscreen></iframe>"""
        val extractedUrl = EmbedParser.extractStreamUrl(embed)
        assertEquals("https://player.abyssplayer.com/qVAy3O_2p", extractedUrl)

        val provider = EmbedParser.detectProvider(extractedUrl)
        assertEquals("AbyssPlayer", provider)

        val responsiveHtml = EmbedParser.buildResponsiveHtml(embed, extractedUrl)
        assertTrue(responsiveHtml.contains("player.abyssplayer.com/qVAy3O_2p"))
        assertTrue(responsiveHtml.contains("allowfullscreen"))
    }

    @Test
    fun parseDirectUrl() {
        val url = "https://player.abyssplayer.com/qVAy3O_2p"
        val extractedUrl = EmbedParser.extractStreamUrl(url)
        assertEquals("https://player.abyssplayer.com/qVAy3O_2p", extractedUrl)
    }
}
