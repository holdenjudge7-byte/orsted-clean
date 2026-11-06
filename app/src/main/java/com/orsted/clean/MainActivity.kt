package com.orsted.clean

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var overlay: ArrowOverlayView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        setContentView(root)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        
        webView.addJavascriptInterface(WebAppInterface(), "OrstedBridge")
        webView.loadUrl("https://lichess.org")
        root.addView(webView)

        overlay = ArrowOverlayView(this)
        overlay.isClickable = false
        root.addView(overlay)

        setupInjectedJS()
    }

    private fun setupInjectedJS() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("lichess.org") == true) {
                    val js = """
                        if (!window.__orsted) {
                            window.__orsted = true;
                            setInterval(() => {
                                const board = document.querySelector('cg-board');
                                if (!board) return;
                                const rect = board.getBoundingClientRect();
                                // (Use your advanced FEN logic here, this is simplified for demo)
                                const fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"; 
                                window.OrstedBridge.onFen(fen, rect.left, rect.top, rect.width, rect.height, false);
                            }, 1000);
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onFen(fen: String, x: Float, y: Float, w: Float, h: Float, flipped: Boolean) {
            thread {
                val bestMove = KotlinEngine.getBestMove(fen)
                if (bestMove.length >= 4) {
                    runOnUiThread {
                        val density = resources.displayMetrics.density
                        overlay.setArrow(bestMove, flipped, x * density, y * density, w * density)
                    }
                }
            }
        }
    }
}

class ArrowOverlayView(context: android.content.Context) : View(context) {
    private var arrow: String? = null
    private var bx = 0f; private var by = 0f; private var bs = 0f
    private var flip = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2ecc71"); style = Paint.Style.FILL }

    fun setArrow(move: String, isFlipped: Boolean, x: Float, y: Float, size: Float) {
        arrow = move; flip = isFlipped; bx = x; by = y; bs = size; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = arrow ?: return
        val sq = bs / 8f
        fun cx(f: Int) = bx + (if (flip) 7 - f else f) * sq + sq / 2f
        fun cy(r: Int) = by + (if (flip) r else 7 - r) * sq + sq / 2f

        val c1 = m[0] - 'a'; val r1 = m[1] - '1'
        val c2 = m[2] - 'a'; val r2 = m[3] - '1'
        val x1 = cx(c1); val y1 = cy(r1); val x2 = cx(c2); val y2 = cy(r2)

        val path = Path()
        path.moveTo(x1, y1); path.lineTo(x2, y2)
        paint.strokeWidth = sq * 0.2f
        paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)
    }
}
