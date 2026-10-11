package com.example.util

object AdBypassHelper {

    /**
     * Smart Ad Control System & Theme Customizer:
     * - Intercepts the first 2 ad popups/redirects when user clicks the player.
     * - Injects custom cyberpunk neon CSS theme into Abyss player.
     * - Hides native web video controls to eliminate double layer controls, leaving only clean app overlay controls.
     * - Ensures continuous unmuted audio and playback.
     */
    fun shouldDeflectAd(targetUrl: String, currentUrl: String?, playerBaseUrl: String, adRedirectCount: Int): Boolean {
        // Deflect the first 2 ad redirects/popups to background
        if (adRedirectCount < 2) {
            if (targetUrl.isNotBlank() && !targetUrl.contains("about:blank")) {
                return true
            }
        }
        return false
    }

    /**
     * JavaScript snippet injected into the player to style the theme, hide native controls (single layer),
     * intercept ad redirects, and ensure audio is fully unmuted and playing.
     */
    fun buildFastAdBypassScript(): String {
        return """
            (function() {
                try {
                    let adClickCount = 0;
                    
                    // Inject custom CSS for cyberpunk theme and hiding native web player controls (single layer merge)
                    if (!document.getElementById('cyber-player-theme-style')) {
                        const style = document.createElement('style');
                        style.id = 'cyber-player-theme-style';
                        style.innerHTML = `
                            body, html {
                                background-color: #0A0F1D !important;
                                color: #FFFFFF !important;
                                overflow: hidden !important;
                                margin: 0 !important;
                                padding: 0 !important;
                            }
                            /* Hide native web player duplicate controls to eliminate double layer */
                            video::-webkit-media-controls,
                            video::-webkit-media-controls-panel,
                            video::-webkit-media-controls-play-button,
                            video::-webkit-media-controls-start-playback-button,
                            video::-webkit-media-controls-enclosure {
                                display: none !important;
                                opacity: 0 !important;
                                pointer-events: none !important;
                            }
                            .vjs-control-bar, .jw-controlbar, .fp-controls, .plyr__controls, .flowplayer .fp-controls {
                                display: none !important;
                                opacity: 0 !important;
                                pointer-events: none !important;
                            }
                            /* Hide intrusive ad banners and floating overlays */
                            iframe[src*="ads"], div[class*="ads"], .ad-container, .adsbygoogle, div[id*="banner"] {
                                display: none !important;
                                opacity: 0 !important;
                                pointer-events: none !important;
                            }
                        `;
                        document.head.appendChild(style);
                    }

                    // Intercept window.open for the first 2 popup attempts
                    const originalOpen = window.open;
                    window.open = function(url, target, features) {
                        adClickCount++;
                        if (adClickCount <= 2) {
                            console.log("Deflected popup ad in background: " + url);
                            if (window.AndroidBridge && window.AndroidBridge.onAdDeflected) {
                                window.AndroidBridge.onAdDeflected(url || "");
                            }
                            return null; // Suppress popup
                        }
                        return originalOpen.apply(this, arguments);
                    };

                    // Intercept document clicks that try to open new tabs/redirects
                    document.addEventListener('click', function(e) {
                        const target = e.target.closest('a');
                        if (target && target.target === '_blank') {
                            adClickCount++;
                            if (adClickCount <= 2) {
                                e.preventDefault();
                                e.stopPropagation();
                                console.log("Deflected anchor ad click in background");
                                if (window.AndroidBridge && window.AndroidBridge.onAdDeflected) {
                                    window.AndroidBridge.onAdDeflected(target.href || "");
                                }
                            }
                        }
                    }, true);

                    // Ensure audio is active and unmuted when user plays, but DO NOT auto-play on load
                    function forceAudioActive() {
                        const videos = document.querySelectorAll('video, audio');
                        videos.forEach(v => {
                            v.muted = false;
                            v.volume = 1.0;
                        });
                        
                        const iframes = document.querySelectorAll('iframe');
                        iframes.forEach(ifr => {
                            try {
                                const idoc = ifr.contentDocument || ifr.contentWindow.document;
                                if (idoc) {
                                    const ivideos = idoc.querySelectorAll('video, audio');
                                    ivideos.forEach(v => {
                                        v.muted = false;
                                        v.volume = 1.0;
                                    });
                                }
                            } catch(err) {}
                        });
                    }

                    window.addEventListener('load', forceAudioActive);
                    document.addEventListener('click', forceAudioActive, true);
                } catch(err) {
                    console.log("Audio/Ad script error:", err);
                }
            })();
        """.trimIndent()
    }
}
