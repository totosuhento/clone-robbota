package com.robotta.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Template bingkai toko. Satu foto menghasilkan satu foto berbingkai. */
enum class FrameStyle(val label: String) {
    SQUARE_WHITE("Persegi putih"),
    COLOR_BORDER("Bingkai + nama toko"),
    WATERMARK("Watermark toko"),
    PRICE_TAG("Label harga")
}

/** Warna aksen yang bisa dipilih untuk bingkai. */
object FrameColors {
    val ALL: List<Pair<String, Int>> = listOf(
        "Hijau" to Color.parseColor("#0F766E"),
        "Biru" to Color.parseColor("#1D4ED8"),
        "Merah" to Color.parseColor("#B91C1C"),
        "Oranye" to Color.parseColor("#C2410C"),
        "Hitam" to Color.parseColor("#1F2937")
    )
}

/**
 * Menerapkan bingkai/branding toko ke foto produk (Bitmap + Canvas).
 * Tujuannya merapikan tampilan foto dan menandai foto milik toko,
 * bukan memperbanyak foto yang sama.
 */
class FrameProcessor(context: Context) {

    private val store = PhotoStore(context)

    /** Mengembalikan daftar path baru. Foto yang gagal diproses dikembalikan apa adanya. */
    fun applyToAll(
        paths: List<String>,
        style: FrameStyle,
        storeName: String,
        priceText: String,
        accentColor: Int
    ): List<String> = paths.map { path ->
        applyFrame(path, style, storeName, priceText, accentColor) ?: path
    }

    fun applyFrame(
        sourcePath: String,
        style: FrameStyle,
        storeName: String,
        priceText: String,
        accentColor: Int
    ): String? {
        var source: Bitmap? = null
        var result: Bitmap? = null
        return try {
            val src = ImageUtils.decodeSampledFile(sourcePath, 1400) ?: return null
            source = src
            val framed = when (style) {
                FrameStyle.SQUARE_WHITE -> squareWhite(src)
                FrameStyle.COLOR_BORDER -> colorBorder(src, storeName, accentColor)
                FrameStyle.WATERMARK -> watermark(src, storeName)
                FrameStyle.PRICE_TAG -> priceTag(src, priceText, accentColor)
            }
            result = framed
            val file = store.newFile("frame_${style.name.lowercase()}")
            if (ImageUtils.saveJpeg(framed, file, 92)) {
                Log.d(TAG, "Bingkai ${style.name} -> ${file.name}")
                file.absolutePath
            } else null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Memori habis saat memberi bingkai", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memberi bingkai $sourcePath", e)
            null
        } finally {
            if (result != null && result != source) result.recycle()
            source?.recycle()
        }
    }

    private fun squareWhite(src: Bitmap): Bitmap {
        val side = (max(src.width, src.height) * 1.08f).roundToInt()
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val pad = side * 0.04f
        drawFit(canvas, src, RectF(pad, pad, side - pad, side - pad))
        return out
    }

    private fun colorBorder(src: Bitmap, storeName: String, accent: Int): Bitmap {
        val side = (max(src.width, src.height) * 1.12f).roundToInt()
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(accent)

        val border = side * 0.035f
        val strip = if (storeName.isBlank()) 0f else side * 0.10f
        val photoArea = RectF(border, border, side - border, side - border - strip)
        canvas.drawRect(photoArea, Paint().apply { color = Color.WHITE })
        drawFit(canvas, src, photoArea)

        if (strip > 0f) {
            val paint = textPaint(Color.WHITE, strip * 0.45f)
            fitTextWidth(paint, storeName, side - border * 4)
            val cy = side - border - strip / 2f
            canvas.drawText(storeName, side / 2f, baseline(paint, cy), paint)
        }
        return out
    }

    private fun watermark(src: Bitmap, storeName: String): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        if (storeName.isBlank()) return out
        val canvas = Canvas(out)
        val size = min(out.width, out.height) * 0.06f
        val paint = textPaint(Color.argb(200, 255, 255, 255), size).apply {
            textAlign = Paint.Align.RIGHT
            setShadowLayer(size * 0.15f, 0f, 0f, Color.argb(160, 0, 0, 0))
        }
        fitTextWidth(paint, storeName, out.width * 0.8f)
        val margin = size * 0.8f
        canvas.drawText(storeName, out.width - margin, out.height - margin, paint)
        return out
    }

    private fun priceTag(src: Bitmap, priceText: String, accent: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        if (priceText.isBlank()) return out
        val canvas = Canvas(out)
        val size = min(out.width, out.height) * 0.065f
        val paint = textPaint(Color.WHITE, size).apply { textAlign = Paint.Align.LEFT }
        fitTextWidth(paint, priceText, out.width * 0.7f)
        val padH = paint.textSize * 0.6f
        val padV = paint.textSize * 0.4f
        val margin = paint.textSize * 0.6f
        val textW = paint.measureText(priceText)
        val rect = RectF(margin, margin, margin + textW + padH * 2, margin + paint.textSize + padV * 2)
        val radius = rect.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        canvas.drawText(priceText, rect.left + padH, baseline(paint, rect.centerY()), paint)
        return out
    }

    private fun drawFit(canvas: Canvas, src: Bitmap, area: RectF) {
        val scale = min(area.width() / src.width, area.height() / src.height)
        val w = src.width * scale
        val h = src.height * scale
        val left = area.left + (area.width() - w) / 2f
        val top = area.top + (area.height() - h) / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun textPaint(color: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private fun fitTextWidth(paint: Paint, text: String, maxWidth: Float) {
        val w = paint.measureText(text)
        if (w > maxWidth && w > 0f) paint.textSize = paint.textSize * (maxWidth / w)
    }

    private fun baseline(paint: Paint, centerY: Float): Float = centerY - (paint.descent() + paint.ascent()) / 2f

    private companion object {
        const val TAG = "FrameProcessor"
    }
}
