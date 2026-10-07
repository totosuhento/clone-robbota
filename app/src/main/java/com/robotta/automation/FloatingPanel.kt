package com.robotta.automation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Panel kontrol melayang di atas aplikasi Facebook (seperti jendela kecil Robotta).
 * Menampilkan status sesi, progres, dan tombol aksi. Bisa digeser dan diperkecil
 * menjadi gelembung. Memakai TYPE_ACCESSIBILITY_OVERLAY sehingga tidak butuh izin
 * "tampil di atas aplikasi lain".
 */
class FloatingPanel(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val screenW = context.resources.displayMetrics.widthPixels
    private val screenH = context.resources.displayMetrics.heightPixels

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private lateinit var card: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var statusDot: View
    private lateinit var headerText: TextView
    private lateinit var titleText: TextView
    private lateinit var messageText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var buttonRow: LinearLayout

    private var collapsed = false
    /** Ditutup pengguna lewat tombol ✕; muncul lagi saat sesi baru dimulai. */
    var dismissed = false
        private set

    private fun dp(v: Int): Int = (v * density).roundToInt()

    // ---------------------------------------------------------------- tampil / sembunyi

    fun show() {
        if (root != null || dismissed) return
        try {
            val view = buildView()
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX ?: dp(8)
                y = savedY ?: (screenH * 0.35f).roundToInt()
            }
            wm.addView(view, lp)
            root = view
            params = lp
            Log.d(TAG, "Panel melayang ditampilkan")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menampilkan panel melayang", e)
            root = null
        }
    }

    fun hide() {
        val v = root ?: return
        try {
            params?.let {
                savedX = it.x
                savedY = it.y
            }
            wm.removeView(v)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menyembunyikan panel", e)
        }
        root = null
        params = null
    }

    fun isShowing(): Boolean = root != null

    /** Dipanggil saat sesi baru dimulai: panel boleh muncul lagi. */
    fun resetDismissed() {
        dismissed = false
    }

    // ---------------------------------------------------------------- isi

    /** Panel dibuat tembus sentuhan & transparan supaya tidak menghalangi ketukan otomatis. */
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val restoreTouch = Runnable { setPassThrough(false) }

    /** Tembus sentuhan sebentar, selama satu ketukan otomatis berlangsung. */
    fun passThroughBriefly(durationMs: Long = 500L) {
        if (root == null) return
        setPassThrough(true)
        handler.removeCallbacks(restoreTouch)
        handler.postDelayed(restoreTouch, durationMs)
    }

    private fun setPassThrough(enabled: Boolean) {
        val v = root ?: return
        val lp = params ?: return
        val flag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (enabled) lp.flags or flag else lp.flags and flag.inv()
        val newAlpha = if (enabled) 0.55f else 1f
        if (newFlags == lp.flags && lp.alpha == newAlpha) return
        lp.flags = newFlags
        lp.alpha = newAlpha
        runCatching { wm.updateViewLayout(v, lp) }
    }

    fun render(state: EngineState) {
        if (root == null) return
        try {
            buttonRow.removeAllViews()
            when (state) {
                is EngineState.Working -> {
                    setStatus(COLOR_WORKING, "Mengisi ${state.index}/${state.total}")
                    titleText.text = state.productTitle
                    messageText.text = state.step.label + "…"
                    setProgress(state.index - 1, state.total)
                    addButton("Berhenti", COLOR_STOP) { AutomationEngine.stop() }
                    bubble.text = "${state.index}/${state.total}"
                }
                is EngineState.NeedsUser -> {
                    setStatus(COLOR_ATTENTION, if (state.autoContinue) "Menunggu kamu" else "Butuh tindakan")
                    titleText.text = state.productTitle
                    messageText.text = state.message
                    setProgress(state.index - 1, state.total)
                    if (state.canRetry) addButton("Coba lagi", COLOR_PRIMARY) { AutomationEngine.retryCurrent() }
                    addButton("Lewati", COLOR_NEUTRAL) { AutomationEngine.skipCurrent() }
                    addButton("Stop", COLOR_STOP) { AutomationEngine.stop() }
                    bubble.text = "!"
                    expandIfCollapsed()
                }
                is EngineState.AwaitingPublish -> {
                    setStatus(COLOR_READY, "Siap ${state.index}/${state.total}")
                    titleText.text = state.productTitle
                    messageText.text = if (state.warnings.isEmpty()) {
                        "Periksa isian, lalu tekan Publikasikan di Facebook."
                    } else {
                        "Lengkapi: " + state.warnings.joinToString("; ")
                    }
                    setProgress(state.index - 1, state.total)
                    addButton("Sudah terbit", COLOR_PRIMARY) { AutomationEngine.markCurrentPosted() }
                    addButton("Lewati", COLOR_NEUTRAL) { AutomationEngine.skipCurrent() }
                    bubble.text = "✓?"
                }
                is EngineState.Posted -> {
                    setStatus(COLOR_READY, "Terposting ${state.index}/${state.total}")
                    titleText.text = state.productTitle
                    messageText.text = if (state.hasNext) "Lanjut ke produk berikutnya?" else "Ini produk terakhir."
                    setProgress(state.index, state.total)
                    if (state.hasNext) {
                        addButton("Berikutnya", COLOR_PRIMARY) { AutomationEngine.nextProduct() }
                        addButton("Selesai", COLOR_NEUTRAL) { AutomationEngine.stop() }
                    } else {
                        addButton("Selesai", COLOR_PRIMARY) { AutomationEngine.stop() }
                    }
                    bubble.text = "${state.index}/${state.total}"
                    expandIfCollapsed()
                }
                else -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memperbarui panel", e)
        }
    }

    private fun setStatus(color: Int, text: String) {
        (statusDot.background as? GradientDrawable)?.setColor(color)
        headerText.text = text
        (bubble.background as? GradientDrawable)?.setColor(color)
    }

    private fun setProgress(done: Int, total: Int) {
        progress.max = total.coerceAtLeast(1)
        progress.progress = done.coerceIn(0, progress.max)
    }

    private fun addButton(label: String, color: Int, onClick: () -> Unit) {
        val b = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(color, dp(8).toFloat())
            setOnClickListener {
                try {
                    onClick()
                } catch (e: Exception) {
                    Log.e(TAG, "Aksi panel gagal: $label", e)
                }
            }
        }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (buttonRow.childCount > 0) marginStart = dp(6)
        }
        buttonRow.addView(b, lp)
    }

    private fun expandIfCollapsed() {
        if (collapsed) toggleCollapsed()
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        card.visibility = if (collapsed) View.GONE else View.VISIBLE
        bubble.visibility = if (collapsed) View.VISIBLE else View.GONE
        root?.let { v -> params?.let { runCatching { wm.updateViewLayout(v, it) } } }
    }

    // ---------------------------------------------------------------- tampilan

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun buildView(): FrameLayout {
        val container = FrameLayout(context)

        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(COLOR_CARD, dp(14).toFloat())
            elevation = dp(8).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }

        // Header: titik status + teks + tombol perkecil & tutup. Header juga pegangan geser.
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_WORKING)
            }
        }
        header.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) })
        headerText = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            text = "Asisten"
        }
        header.addView(headerText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(iconButton("–") { toggleCollapsed() })
        header.addView(iconButton("✕") {
            dismissed = true
            hide()
        })
        card.addView(header)

        progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1
            progress = 0
        }
        card.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)).apply {
            topMargin = dp(6)
        })

        titleText = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        card.addView(titleText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
        messageText = TextView(context).apply {
            setTextColor(COLOR_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
        }
        card.addView(messageText)

        buttonRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        card.addView(buttonRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        container.addView(card, FrameLayout.LayoutParams(dp(PANEL_WIDTH_DP), FrameLayout.LayoutParams.WRAP_CONTENT))

        // Gelembung saat diperkecil.
        bubble = TextView(context).apply {
            text = "…"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_WORKING)
            }
            elevation = dp(8).toFloat()
            visibility = View.GONE
        }
        container.addView(bubble, FrameLayout.LayoutParams(dp(52), dp(52)))

        val drag = DragListener()
        header.setOnTouchListener(drag)
        bubble.setOnTouchListener(drag)
        return container
    }

    private fun iconButton(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(2), dp(4), dp(2))
        setOnClickListener { onClick() }
    }

    /** Menggeser panel; ketukan singkat pada gelembung membuka panel lagi. */
    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = params ?: return false
            val container = root ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    lp.x = (startX + dx).roundToInt().coerceIn(0, (screenW - dp(52)).coerceAtLeast(0))
                    lp.y = (startY + dy).roundToInt().coerceIn(0, (screenH - dp(52)).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(container, lp) }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && v === bubble) toggleCollapsed()
                    savedX = lp.x
                    savedY = lp.y
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val TAG = "FloatingPanel"
        private const val PANEL_WIDTH_DP = 250

        private val COLOR_CARD = Color.parseColor("#F0111827")
        private val COLOR_MUTED = Color.parseColor("#CBD5E1")
        private val COLOR_PRIMARY = Color.parseColor("#2563EB")
        private val COLOR_NEUTRAL = Color.parseColor("#475569")
        private val COLOR_STOP = Color.parseColor("#DC2626")
        private val COLOR_WORKING = Color.parseColor("#2563EB")
        private val COLOR_ATTENTION = Color.parseColor("#F59E0B")
        private val COLOR_READY = Color.parseColor("#16A34A")

        /** Posisi terakhir panel, dipertahankan selama aplikasi hidup. */
        private var savedX: Int? = null
        private var savedY: Int? = null
    }
}
