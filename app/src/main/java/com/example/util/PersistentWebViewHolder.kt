package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.PlayerState

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

class PersistentAdBridge(
    private val onAdDeflected: (String) -> Unit,
    private val onPositionUpdate: (Float, Boolean) -> Unit
) {
    @JavascriptInterface
    fun onAdDeflected(url: String) {
        onAdDeflected(url)
    }

    @JavascriptInterface
    fun onPlaybackUpdate(position: Float, isPlaying: Boolean) {
        onPositionUpdate(position, isPlaying)
    }
}

/**
 * Retains a single persistent WebView instance across Portrait and Fullscreen/Landscape transitions.
 * Prevents the video from reloading, pausing, or restarting from 00:00 when switching screen orientations.
 */
@SuppressLint("SetJavaScriptEnabled")
class PersistentWebViewHolder(
    context: Context,
    private val onProgressUpdate: (Int) -> Unit = {},
    private val onTitleReceived: (String) -> Unit = {},
    private val onErrorOccurred: (String?) -> Unit = {},
    private val onAdBlocked: (String) -> Unit = {}
) {
    @Volatile
    var lastKnownPosition: Float = 0f
        private set

    @Volatile
    var wasPlayingBeforeTransition: Boolean = false
        private set

    val webView: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.BLACK)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        try {
            context.cacheDir?.mkdirs()
        } catch (_: Exception) {}

        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
        } catch (_: Exception) {}

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = MOBILE_USER_AGENT
        }

        addJavascriptInterface(
            PersistentAdBridge(
                onAdDeflected = { url -> onAdBlocked(url) },
                onPositionUpdate = { pos, isPlaying ->
                    if (pos > 0.5f) {
                        lastKnownPosition = pos
                    }
                    wasPlayingBeforeTransition = isPlaying
                }
            ),
            "AndroidBridge"
        )

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressUpdate(newProgress)
                if (newProgress in 40..85) {
                    view?.evaluateJavascript(AdBypassHelper.buildFastAdBypassScript(), null)
                    view?.evaluateJavascript(VideoControlHelper.buildSmoothStreamingBufferScript(), null)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let { onTitleReceived(it) }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (adRedirectCount < 2) {
                    adRedirectCount++
                    onAdBlocked("Smart Popup Deflected ($adRedirectCount/2)")
                    return true
                }
                return false
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                onErrorOccurred(null)
                view?.evaluateJavascript(AdBypassHelper.buildFastAdBypassScript(), null)
                view?.evaluateJavascript(VideoControlHelper.buildSmoothStreamingBufferScript(), null)
                view?.evaluateJavascript(buildPositionTrackerScript(), null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                onProgressUpdate(100)
                view?.evaluateJavascript(AdBypassHelper.buildFastAdBypassScript(), null)
                view?.evaluateJavascript(VideoControlHelper.buildSmoothStreamingBufferScript(), null)
                view?.evaluateJavascript(buildPositionTrackerScript(), null)
                // Do not auto-play on initial load so video respects manual user control
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                onErrorOccurred("Renderer recovered. Tap retry if video stopped.")
                return true
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val base = lastLoadedBaseUrl

                if (AdBypassHelper.shouldDeflectAd(url, view?.url, base, adRedirectCount)) {
                    adRedirectCount++
                    onAdBlocked("Smart Ad Deflected ($adRedirectCount/2): $url")
                    return true
                }

                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    false
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                    true
                }
            }
        }
    }

    private var lastLoadedCode: String = ""
    private var lastLoadedBaseUrl: String = "https://player.abyssplayer.com/"
    private var lastDesktopMode: Boolean = false
    private var adRedirectCount: Int = 0

    fun detachFromParent() {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }

    fun loadCodeIfChanged(state: PlayerState, forceReload: Boolean = false) {
        if (state.activeCode.isBlank()) return

        val codeChanged = state.activeCode != lastLoadedCode
        val desktopChanged = state.isDesktopMode != lastDesktopMode

        if (codeChanged || forceReload || desktopChanged) {
            lastLoadedCode = state.activeCode
            lastDesktopMode = state.isDesktopMode
            adRedirectCount = 0

            if (state.isDesktopMode) {
                webView.settings.userAgentString = DESKTOP_USER_AGENT
            } else {
                webView.settings.userAgentString = MOBILE_USER_AGENT
            }

            val extractedUrl = state.extractedUrl.ifBlank {
                EmbedParser.extractStreamUrl(state.activeCode)
            }

            lastLoadedBaseUrl = if (extractedUrl.isNotBlank()) {
                EmbedParser.getBaseUrl(extractedUrl)
            } else {
                "https://player.abyssplayer.com/"
            }

            val responsiveHtml = EmbedParser.buildResponsiveHtml(state.activeCode, extractedUrl)
            webView.loadDataWithBaseURL(
                lastLoadedBaseUrl,
                responsiveHtml,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    fun resumePlaybackIfPaused() {
        val restorePos = lastKnownPosition
        webView.evaluateJavascript(
            """
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
                    
                    let isPlaying = false;
                    let currentPos = 0;

                    if (win.jwplayer && typeof win.jwplayer === 'function') {
                        const jw = win.jwplayer();
                        if (jw && typeof jw.getState === 'function') {
                            isPlaying = (jw.getState() === 'playing');
                        }
                        if (jw && typeof jw.getPosition === 'function') {
                            currentPos = jw.getPosition() || 0;
                        }
                    }

                    const v = doc.querySelector('video') || document.querySelector('video');
                    if (v) {
                        if (!isPlaying) isPlaying = !v.paused;
                        if (currentPos === 0) currentPos = v.currentTime || 0;
                    }

                    // Only restore position if video reset to 0 unexpectedly and we have a valid saved position > 2s
                    if (currentPos < 0.5 && $restorePos > 2.0) {
                        if (win.jwplayer && typeof win.jwplayer === 'function') {
                            const jw = win.jwplayer();
                            if (typeof jw.seek === 'function') jw.seek($restorePos);
                        }
                        if (v) {
                            try { v.currentTime = $restorePos; } catch(e) {}
                        }
                    }

                    // Only play if video is currently not playing
                    if (!isPlaying) {
                        if (win.jwplayer && typeof win.jwplayer === 'function') {
                            const jw = win.jwplayer();
                            if (typeof jw.play === 'function') jw.play();
                        }
                        if (v && v.paused) {
                            const p = v.play();
                            if (p !== undefined) {
                                p.catch(function() {
                                    v.muted = true;
                                    v.play().catch(function() {});
                                });
                            }
                        }
                    }
                } catch(e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    private fun buildPositionTrackerScript(): String {
        return """
        (function() {
            if (window._posTrackerActive) return;
            window._posTrackerActive = true;
            setInterval(function() {
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
                    let pos = 0;
                    let playing = false;
                    if (win.jwplayer && typeof win.jwplayer === 'function') {
                        const p = win.jwplayer();
                        if (p && typeof p.getPosition === 'function') {
                            pos = p.getPosition();
                            playing = p.getState && p.getState() === 'playing';
                        }
                    }
                    const v = doc.querySelector('video') || document.querySelector('video');
                    if (v) {
                        if (pos === 0) pos = v.currentTime || 0;
                        if (!playing) playing = !v.paused;
                    }
                    if (window.AndroidBridge && window.AndroidBridge.onPlaybackUpdate) {
                        window.AndroidBridge.onPlaybackUpdate(pos, playing);
                    }
                } catch(e) {}
            }, 1000);
        })();
        """.trimIndent()
    }

    fun reload() {
        if (lastLoadedCode.isNotBlank()) {
            val extractedUrl = EmbedParser.extractStreamUrl(lastLoadedCode)
            val responsiveHtml = EmbedParser.buildResponsiveHtml(lastLoadedCode, extractedUrl)
            webView.loadDataWithBaseURL(
                lastLoadedBaseUrl,
                responsiveHtml,
                "text/html",
                "UTF-8",
                null
            )
        } else {
            webView.reload()
        }
    }

    fun destroy() {
        detachFromParent()
        webView.destroy()
    }
}
