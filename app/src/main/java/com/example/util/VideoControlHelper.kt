package com.example.util

object VideoControlHelper {

    /**
     * Script to toggle Play/Pause across JWPlayer, HTML5 Video, iframes, and synthetic click simulation.
     */
    fun buildTogglePlayPauseScript(): String {
        return """
        (function() {
            try {
                let success = false;
                
                // 1. Try JWPlayer instance in window or iframes
                try {
                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                        const player = window.jwplayer();
                        if (player && typeof player.getState === 'function') {
                            const state = player.getState();
                            if (state === 'playing') {
                                player.pause();
                                return 'paused';
                            } else {
                                player.play();
                                return 'playing';
                            }
                        }
                    }
                } catch(e) {}

                // 2. Try HTML5 Video elements in document
                const videos = document.querySelectorAll('video');
                if (videos && videos.length > 0) {
                    videos.forEach(video => {
                        if (video.paused) {
                            video.play().catch(function() {
                                video.muted = true;
                                video.play().catch(function() {});
                            });
                            success = true;
                        } else {
                            video.pause();
                            success = true;
                        }
                    });
                    if (success) return 'toggled';
                }

                // 3. Try checking all iframes (same-origin and cross-origin click simulation)
                const iframes = document.querySelectorAll('iframe');
                iframes.forEach(ifr => {
                    try {
                        const idoc = ifr.contentDocument || ifr.contentWindow.document;
                        if (idoc) {
                            const ivideos = idoc.querySelectorAll('video');
                            ivideos.forEach(v => {
                                if (v.paused) {
                                    v.play().catch(_ => {});
                                } else {
                                    v.pause();
                                }
                                success = true;
                            });
                        }
                    } catch(err) {
                        // Cross-origin iframe: simulate click on iframe to toggle player state
                        try {
                            ifr.click();
                            success = true;
                        } catch(e) {}
                    }
                });

                // 4. Fallback click on major player overlay buttons
                const playBtn = document.querySelector('.jw-display-icon-container, .vjs-big-play-button, .play-button, [aria-label*="Play"], .ytp-large-play-button');
                if (playBtn) {
                    playBtn.click();
                    return 'playing';
                }

                return success ? 'toggled' : 'unknown';
            } catch(err) {
                console.error('[VideoControl] Toggle error:', err);
                return 'error';
            }
        })();
        """.trimIndent()
    }

    /**
     * Script to seek forward or backward by a relative number of seconds (e.g. +10, -10).
     */
    fun buildSeekRelativeScript(seconds: Int): String {
        return """
        (function() {
            try {
                let win = window;
                let doc = document;
                const ifr = document.querySelector('iframe');
                if (ifr && ifr.contentWindow) {
                    try {
                        win = ifr.contentWindow;
                        doc = ifr.contentWindow.document || doc;
                    } catch(e) {}
                }

                // 1. JWPlayer seek
                if (win.jwplayer && typeof win.jwplayer === 'function') {
                    const player = win.jwplayer();
                    if (player && typeof player.getPosition === 'function' && typeof player.seek === 'function') {
                        const current = player.getPosition();
                        const target = Math.max(0, current + ($seconds));
                        player.seek(target);
                        return target;
                    }
                }

                // 2. HTML5 Video seek
                let video = doc.querySelector('video') || document.querySelector('video');
                if (video) {
                    video.currentTime = Math.max(0, video.currentTime + ($seconds));
                    return video.currentTime;
                }

                return -1;
            } catch(err) {
                console.error('[VideoControl] Seek error:', err);
                return -1;
            }
        })();
        """.trimIndent()
    }

    /**
     * Script to restart video playback from beginning (00:00).
     */
    fun buildRestartScript(): String {
        return """
        (function() {
            try {
                let win = window;
                let doc = document;
                const ifr = document.querySelector('iframe');
                if (ifr && ifr.contentWindow) {
                    try {
                        win = ifr.contentWindow;
                        doc = ifr.contentWindow.document || doc;
                    } catch(e) {}
                }

                if (win.jwplayer && typeof win.jwplayer === 'function') {
                    const player = win.jwplayer();
                    if (typeof player.seek === 'function') {
                        player.seek(0);
                        if (typeof player.play === 'function') player.play();
                        return true;
                    }
                }

                let video = doc.querySelector('video') || document.querySelector('video');
                if (video) {
                    video.currentTime = 0;
                    video.play().catch(function() {
                        video.muted = true;
                        video.play().catch(function() {});
                    });
                    return true;
                }
                return false;
            } catch(err) {
                return false;
            }
        })();
        """.trimIndent()
    }

    /**
     * Script to adjust playback speed (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x).
     */
    fun buildPlaybackRateScript(rate: Float): String {
        return """
        (function() {
            try {
                let win = window;
                let doc = document;
                const ifr = document.querySelector('iframe');
                if (ifr && ifr.contentWindow) {
                    try {
                        win = ifr.contentWindow;
                        doc = ifr.contentWindow.document || doc;
                    } catch(e) {}
                }

                if (win.jwplayer && typeof win.jwplayer === 'function') {
                    const player = win.jwplayer();
                    if (typeof player.setPlaybackRate === 'function') {
                        player.setPlaybackRate($rate);
                    }
                }

                let video = doc.querySelector('video') || document.querySelector('video');
                if (video) {
                    video.playbackRate = $rate;
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }

    /**
     * Script to mute or unmute video.
     */
    fun buildMuteScript(mute: Boolean): String {
        return """
        (function() {
            try {
                let win = window;
                let doc = document;
                const ifr = document.querySelector('iframe');
                if (ifr && ifr.contentWindow) {
                    try {
                        win = ifr.contentWindow;
                        doc = ifr.contentWindow.document || doc;
                    } catch(e) {}
                }
                if (win.jwplayer && typeof win.jwplayer === 'function') {
                    const p = win.jwplayer();
                    if (typeof p.setMute === 'function') {
                        p.setMute($mute);
                    }
                }
                const videos = doc.querySelectorAll('video, audio');
                videos.forEach(v => { v.muted = $mute; });
            } catch(e) {}
        })();
        """.trimIndent()
    }

    /**
     * Script to set exact volume level (0.0 to 1.0).
     */
    fun buildVolumeScript(volume: Float): String {
        return """
        (function() {
            try {
                let win = window;
                let doc = document;
                const ifr = document.querySelector('iframe');
                if (ifr && ifr.contentWindow) {
                    try {
                        win = ifr.contentWindow;
                        doc = ifr.contentWindow.document || doc;
                    } catch(e) {}
                }
                if (win.jwplayer && typeof win.jwplayer === 'function') {
                    const jw = win.jwplayer();
                    if (typeof jw.setVolume === 'function') {
                        jw.setVolume($volume * 100);
                    }
                }
                const videos = doc.querySelectorAll('video, audio');
                videos.forEach(v => {
                    v.volume = $volume;
                    v.muted = ($volume == 0);
                });
            } catch(e) {}
        })();
        """.trimIndent()
    }

    /**
     * Script for smooth chunked streaming and aggressive 30s pre-buffering optimization.
     */
    fun buildSmoothStreamingBufferScript(): String {
        return """
        (function() {
            if (window.__smoothStreamingInitialized) return;
            window.__smoothStreamingInitialized = true;
            
            setInterval(function() {
                try {
                    const videos = document.querySelectorAll('video');
                    videos.forEach(video => {
                        if (video) {
                            if (video.preload !== 'auto') {
                                video.preload = 'auto';
                            }
                            const current = video.currentTime;
                            const buffered = video.buffered;
                            if (buffered && buffered.length > 0) {
                                let bufferedEnd = 0;
                                for (let i = 0; i < buffered.length; i++) {
                                    if (buffered.start(i) <= current && buffered.end(i) >= current) {
                                        bufferedEnd = buffered.end(i);
                                        break;
                                    }
                                }
                                const ahead = bufferedEnd - current;
                                if (ahead < 30 && video.duration && bufferedEnd < video.duration) {
                                    if (typeof video.load === 'function' && video.paused && current === 0) {
                                        video.load();
                                    }
                                }
                            }
                        }
                    });
                } catch(e) {}
            }, 3000);
        })();
        """.trimIndent()
    }
}

