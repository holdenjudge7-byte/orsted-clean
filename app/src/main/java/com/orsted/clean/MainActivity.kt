package com.orsted.clean

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.bhlangonijr.chesslib.Board
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var overlay: ArrowOverlayView
    private lateinit var debugText: TextView
    private var lastFen = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)

        // 1. Setup WebView
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        webView.addJavascriptInterface(WebAppInterface(), "OrstedBridge")
        webView.visibility = View.GONE // Hide until platform is chosen
        root.addView(webView)

        // 2. Setup Overlay
        overlay = ArrowOverlayView(this)
        overlay.isClickable = false
        root.addView(overlay)

        // 3. Setup Debug Text
        debugText = TextView(this).apply {
            setTextColor(Color.RED)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            textSize = 12f
            setPadding(20, 20, 20, 20)
            text = "Select a platform"
        }
        val textParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        textParams.setMargins(20, 100, 0, 0)
        root.addView(debugText, textParams)

        // 4. Show Home Screen
        showHomeScreen()
    }

    private fun showHomeScreen() {
        val homeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0a0a0a"))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val title = TextView(this).apply {
            text = "⚡ ORSTED"
            setTextColor(Color.parseColor("#2ecc71"))
            textSize = 32f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }
        homeLayout.addView(title)

        val lichessBtn = Button(this).apply {
            text = "Play on Lichess"
            setBackgroundColor(Color.parseColor("#2ecc71"))
            setTextColor(Color.BLACK)
            setOnClickListener { startSite("https://lichess.org", "lichess") }
        }
        homeLayout.addView(lichessBtn, LinearLayout.LayoutParams(600, 150).apply { bottomMargin = 30 })

        val chesscomBtn = Button(this).apply {
            text = "Play on Chess.com"
            setBackgroundColor(Color.parseColor("#81b64c"))
            setTextColor(Color.BLACK)
            setOnClickListener { startSite("https://www.chess.com", "chesscom") }
        }
        homeLayout.addView(chesscomBtn, LinearLayout.LayoutParams(600, 150))

        root.addView(homeLayout)
    }

    private fun startSite(url: String, site: String) {
        // Remove home layout
        root.removeViewAt(root.childCount - 1) 
        
        webView.visibility = View.VISIBLE
        debugText.text = "Loading $site..."
        
        webView.loadUrl(url)
        setupInjectedJS(site)
    }

    private fun setupInjectedJS(site: String) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val js = if (site == "lichess") {
                    """
                    if (!window.__orsted) {
                        window.__orsted = true;
                        setInterval(() => {
                            const board = document.querySelector('cg-board');
                            if (!board) return;
                            const rect = board.getBoundingClientRect();
                            
                            // Try FEN first
                            let fenEl = document.querySelector('[data-fen]');
                            if (fenEl) {
                                window.OrstedBridge.onFen(fenEl.getAttribute('data-fen'), rect.left, rect.top, rect.width, rect.height);
                                return;
                            }
                            
                            // Fallback to move list
                            const moves = [];
                            document.querySelectorAll('move').forEach(m => {
                                const san = m.textContent.trim().replace(/[+#?!]+/g, '');
                                if (san) moves.push(san);
                            });
                            if (moves.length > 0) {
                                window.OrstedBridge.onMoves(moves.join(","), rect.left, rect.top, rect.width, rect.height);
                            }
                        }, 1000);
                    }
                    """.trimIndent()
                } else {
                    """
                    if (!window.__orsted) {
                        window.__orsted = true;
                        setInterval(() => {
                            const board = document.querySelector('chess-board') || document.querySelector('wc-chess-board');
                            if (!board) return;
                            const rect = board.getBoundingClientRect();
                            
                            // Read move list on chess.com
                            const moves = [];
                            document.querySelectorAll('.move-list .node, wc-simple-move-list .node').forEach(m => {
                                const san = m.textContent.trim().replace(/[+#?!]+/g, '');
                                if (san) moves.push(san);
                            });
                            
                            if (moves.length > 0) {
                                window.OrstedBridge.onMoves(moves.join(","), rect.left, rect.top, rect.width, rect.height);
                            } else {
                                window.OrstedBridge.log("Waiting for Chess.com moves...");
                            }
                        }, 1000);
                    }
                    """.trimIndent()
                }
                webView.evaluateJavascript(js, null)
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun log(msg: String) {
            runOnUiThread { debugText.text = msg }
        }

        @JavascriptInterface
        fun onFen(fen: String, x: Float, y: Float, w: Float, h: Float) {
            if (fen == lastFen) return
            lastFen = fen
            runOnUiThread { debugText.text = "FEN received. Analyzing..." }

            thread {
                val bestMove = KotlinEngine.getBestMove(fen)
                handleResult(bestMove, x, y, w, h)
            }
        }

        @JavascriptInterface
        fun onMoves(movesStr: String, x: Float, y: Float, w: Float, h: Float) {
            if (movesStr == lastFen) return
            lastFen = movesStr
            
            runOnUiThread { debugText.text = "Moves: $movesStr\nReplaying..." }

            thread {
                try {
                    val board = Board()
                    val moves = movesStr.split(",")
                    for (m in moves) {
                        board.doMove(m) // Replay the SAN moves
                    }
                    val fen = board.fen
                    val bestMove = KotlinEngine.getBestMove(fen)
                    handleResult(bestMove, x, y, w, h)
                } catch (e: Exception) {
                    runOnUiThread { debugText.text = "Replay error: ${e.message}" }
                }
            }
        }

        private fun handleResult(bestMove: String, x: Float, y: Float, w: Float, h: Float) {
            if (bestMove.length >= 4) {
                runOnUiThread {
                    val density = resources.displayMetrics.density
                    overlay.setArrow(bestMove, x * density, y * density, w * density)
                    debugText.text = "Arrow: $bestMove"
                }
            } else {
                runOnUiThread { debugText.text = "Engine returned no move" }
            }
        }
    }
}

class ArrowOverlayView(context: android.content.Context) : View(context) {
    private var arrow: String? = null
    private var bx = 0f; private var by = 0f; private var bs = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2ecc71"); style = Paint.Style.STROKE; strokeWidth = 15f }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2ecc71"); style = Paint.Style.FILL }

    fun setArrow(move: String, x: Float, y: Float, size: Float) {
        arrow = move; bx = x; by = y; bs = size; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = arrow ?: return
        if (bs <= 0f) return
        
        val sq = bs / 8f
        fun cx(f: Int) = bx + f * sq + sq / 2f
        fun cy(r: Int) = by + (7 - r) * sq + sq / 2f

        val c1 = m[0] - 'a'; val r1 = m[1] - '1'
        val c2 = m[2] - 'a'; val r2 = m[3] - '1'
        val x1 = cx(c1); val y1 = cy(r1); val x2 = cx(c2); val y2 = cy(r2)

        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val endX = (x2 - Math.cos(angle) * sq * 0.3).toFloat()
        val endY = (y2 - Math.sin(angle) * sq * 0.3).toFloat()

        canvas.drawLine(x1, y1, endX, endY, paint)
        
        val arrowSize = sq * 0.4f
        val path = Path()
        path.moveTo(x2, y2)
        path.lineTo((x2 - arrowSize * Math.cos(angle - Math.PI / 6)).toFloat(), (y2 - arrowSize * Math.sin(angle - Math.PI / 6)).toFloat())
        path.lineTo((x2 - arrowSize * Math.cos(angle + Math.PI / 6)).toFloat(), (y2 - arrowSize * Math.sin(angle + Math.PI / 6)).toFloat())
        path.close()
        canvas.drawPath(path, headPaint)
    }
}
