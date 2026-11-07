package com.orsted.clean

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        webView.addJavascriptInterface(WebAppInterface(), "OrstedBridge")
        webView.visibility = View.GONE
        root.addView(webView)

        overlay = ArrowOverlayView(this)
        overlay.isClickable = false
        root.addView(overlay)

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

        showHomeScreen()
        setupInjectedJS()
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
            setOnClickListener { startSite("https://lichess.org") }
        }
        homeLayout.addView(lichessBtn, LinearLayout.LayoutParams(600, 150).apply { bottomMargin = 30 })

        val chesscomBtn = Button(this).apply {
            text = "Play on Chess.com"
            setBackgroundColor(Color.parseColor("#81b64c"))
            setTextColor(Color.BLACK)
            setOnClickListener { startSite("https://www.chess.com") }
        }
        homeLayout.addView(chesscomBtn, LinearLayout.LayoutParams(600, 150))

        root.addView(homeLayout)
    }

    private fun startSite(url: String) {
        root.removeViewAt(root.childCount - 1)
        webView.visibility = View.VISIBLE
        debugText.text = "Loading..."
        webView.loadUrl(url)
    }

    private fun setupInjectedJS() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("lichess.org") == true || url?.contains("chess.com") == true) {
                    val js = """
                    if (!window.__orsted) {
                        window.__orsted = true;
                        setInterval(() => {
                            const host = location.hostname;
                            let grid = null, turn = 'w', isFlipped = false, boardEl = null, rect = null;

                            if (host.includes('lichess.org')) {
                                boardEl = document.querySelector('cg-board');
                                if (!boardEl) { window.OrstedBridge.log("Lichess: Waiting for game board..."); return; }
                                const wrap = boardEl.closest('.cg-wrap') || boardEl.parentElement;
                                isFlipped = wrap && wrap.classList.contains('orientation-black');
                                rect = boardEl.getBoundingClientRect();
                                const sq = rect.width / 8;
                                if (sq < 1) return;
                                grid = Array.from({length:8}, () => Array(8).fill(''));
                                boardEl.querySelectorAll('piece').forEach(p => {
                                    const cls = p.classList;
                                    const isW = cls.contains('white'), isB = cls.contains('black');
                                    if (!isW && !isB) return;
                                    let sym = cls.contains('pawn') ? 'p' : cls.contains('knight') ? 'n' : cls.contains('bishop') ? 'b' : cls.contains('rook') ? 'r' : cls.contains('queen') ? 'q' : cls.contains('king') ? 'k' : null;
                                    if (!sym) return;
                                    if (isW) sym = sym.toUpperCase();
                                    const m = (p.getAttribute('style') || '').match(/translate(?:3d)?\(\s*(-?[\d.]+)px\s*,\s*(-?[\d.]+)px/);
                                    if (!m) return;
                                    let file = Math.round(parseFloat(m[1]) / sq);
                                    let rankTop = Math.round(parseFloat(m[2]) / sq);
                                    file = Math.max(0, Math.min(7, file));
                                    rankTop = Math.max(0, Math.min(7, rankTop));
                                    const gf = isFlipped ? 7 - file : file;
                                    const gr = isFlipped ? 7 - rankTop : rankTop;
                                    grid[gr][gf] = sym;
                                });
                                const moves = document.querySelectorAll('move, rm6 move, l4x move');
                                turn = moves.length % 2 === 0 ? 'w' : 'b';
                            } else if (host.includes('chess.com')) {
                                boardEl = document.querySelector('chess-board') || document.querySelector('wc-chess-board');
                                if (!boardEl) { window.OrstedBridge.log("Chess.com: Waiting for game board..."); return; }
                                isFlipped = boardEl.classList.contains('flipped');
                                rect = boardEl.getBoundingClientRect();
                                grid = Array.from({length:8}, () => Array(8).fill(''));
                                boardEl.querySelectorAll('.piece').forEach(p => {
                                    const cls = [...p.classList];
                                    const pc = cls.find(c => /^[wb][pnbrqk]$/.test(c)); if (!pc) return;
                                    const sq = cls.find(c => c.startsWith('square-')); if (!sq) return;
                                    const n = sq.split('-')[1];
                                    const file = parseInt(n[0], 10) - 1;
                                    const rank = parseInt(n[1], 10) - 1;
                                    const gr = 7 - rank;
                                    let sym = pc[1]; if (pc[0] === 'w') sym = sym.toUpperCase();
                                    if (file >= 0 && file < 8 && gr >= 0 && gr < 8) grid[gr][file] = sym;
                                });
                                const nodes = document.querySelectorAll('wc-simple-move-list .node, .move-list .node');
                                turn = nodes.length % 2 === 0 ? 'w' : 'b';
                            }

                            if (grid) {
                                let wK = 0, bK = 0;
                                const fenRanks = grid.map((row) => {
                                    let s = '', empty = 0;
                                    row.forEach((c) => {
                                        if (c === '') empty++;
                                        else { if (c === 'K') wK++; if (c === 'k') bK++; if (empty) { s += empty; empty = 0; } s += c; }
                                    });
                                    if (empty) s += empty; return s;
                                });
                                if (wK === 1 && bK === 1) {
                                    // Castling rights are hardcoded to '-' to prevent chesslib crashes
                                    const fen = fenRanks.join('/') + ' ' + turn + ' - - 0 1';
                                    window.OrstedBridge.onFen(fen, isFlipped, rect.left, rect.top, rect.width, rect.height);
                                }
                            }
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
        fun log(msg: String) {
            runOnUiThread { debugText.text = msg }
        }

        @JavascriptInterface
        fun onFen(fen: String, isFlipped: Boolean, x: Float, y: Float, w: Float, h: Float) {
            if (fen == lastFen) return
            lastFen = fen
            runOnUiThread { debugText.text = "FEN received. Analyzing..." }

            thread {
                val bestMove = KotlinEngine.getBestMove(fen)
                if (bestMove.length >= 4) {
                    runOnUiThread {
                        val density = resources.displayMetrics.density
                        overlay.setArrow(bestMove, isFlipped, x * density, y * density, w * density)
                        debugText.text = "Arrow: $bestMove"
                    }
                } else {
                    runOnUiThread { debugText.text = "Engine returned no move" }
                }
            }
        }
    }
}

class ArrowOverlayView(context: android.content.Context) : View(context) {
    private var arrow: String? = null
    private var bx = 0f; private var by = 0f; private var bs = 0f
    private var isFlipped = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2ecc71"); style = Paint.Style.STROKE; strokeWidth = 15f }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2ecc71"); style = Paint.Style.FILL }

    fun setArrow(move: String, flipped: Boolean, x: Float, y: Float, size: Float) {
        arrow = move; isFlipped = flipped; bx = x; by = y; bs = size; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = arrow ?: return
        if (bs <= 0f) return
        
        val sq = bs / 8f
        // Flipped logic ensures the arrow goes to the correct square whether you are White or Black
        fun cx(f: Int) = bx + (if (isFlipped) (7 - f) else f) * sq + sq / 2f
        fun cy(r: Int) = by + (if (isFlipped) r else (7 - r)) * sq + sq / 2f

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
