package com.crylo.photosize.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.crylo.photosize.core.DocSpec
import com.crylo.photosize.core.ImageEncoder
import com.crylo.photosize.core.JpegMarkers
import com.crylo.photosize.core.stripHeightPx
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Android-side image plumbing: decode, crop, compose, encode, save. */
object ImageIo {
    /** Longest side kept after decoding; far above any exam size, small enough for memory. */
    private const val MAX_DECODE_SIDE = 3000

    /**
     * Decodes [uri] upright. BitmapFactory never applies EXIF orientation, so the rotation (and
     * mirroring) is read with ExifInterface and applied explicitly; that behaves the same on
     * every Android version, unlike relying on a decoder to do it.
     */
    fun decodeUpright(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not a readable image" }
        val longest = max(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = ceil(longest.toDouble() / MAX_DECODE_SIDE).toInt().coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("not a readable image")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return decoded
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    fun rotate90(src: Bitmap): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)

    /**
     * Scales with repeated halving before the last step. A single bilinear scale from a 3000 px
     * photo down to 200 px skips most source pixels and looks jagged and noisy.
     */
    fun scaleSmooth(src: Bitmap, width: Int, height: Int): Bitmap {
        var current = src
        while (current.width >= width * 2 && current.height >= height * 2) {
            current = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
        }
        return if (current.width == width && current.height == height) current
        else Bitmap.createScaledBitmap(current, width, height, true)
    }

    /**
     * Cuts [rect] out of [src] at up to twice the output size, which leaves room for the
     * signature cleaner to work at a finer scale and for the encoder to downsample cleanly.
     */
    fun crop(src: Bitmap, rect: Rect, spec: DocSpec): Bitmap {
        val r = Rect(rect)
        require(r.intersect(0, 0, src.width, src.height)) { "crop is outside the image" }
        val region = Bitmap.createBitmap(src, r.left, r.top, r.width().coerceAtLeast(1), r.height().coerceAtLeast(1))
        val factor = minOf(1.0, spec.widthPx * 2.0 / region.width)
        val w = (region.width * factor).roundToInt().coerceAtLeast(1)
        val h = (region.height * factor).roundToInt().coerceAtLeast(1)
        return scaleSmooth(region, w, h)
    }

    fun applyPixels(src: Bitmap, transform: (IntArray, Int, Int) -> IntArray): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        return Bitmap.createBitmap(transform(pixels, w, h), w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Builds the final picture at the slot's exact pixel size. With a [strip], the bottom of the
     * frame becomes a white band carrying the name and date, and the photo fills the rest (the
     * crop screen hides that same band, so nothing the user framed is lost).
     */
    fun compose(photo: Bitmap, spec: DocSpec, strip: Pair<String, String>?): Bitmap {
        val w = spec.widthPx
        val h = spec.heightPx
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (strip == null) {
            canvas.drawBitmap(scaleSmooth(photo, w, h), 0f, 0f, paint)
            return out
        }
        val stripH = stripHeightPx(h)
        val photoH = h - stripH
        // Same band as the crop overlay: drop the bottom of the framed photo.
        val srcH = (photo.height * photoH.toFloat() / h).roundToInt().coerceAtLeast(1)
        val top = Bitmap.createBitmap(photo, 0, 0, photo.width, srcH)
        canvas.drawBitmap(scaleSmooth(top, w, photoH), 0f, 0f, paint)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val lines = listOf(strip.first, strip.second).filter { it.isNotBlank() }
        if (lines.isEmpty()) return out
        val lineH = stripH / lines.size.toFloat()
        lines.forEachIndexed { i, line ->
            text.textSize = lineH * 0.78f
            val width = text.measureText(line)
            if (width > w * 0.94f) text.textSize *= w * 0.94f / width
            val baseline = photoH + lineH * i + lineH / 2 - (text.descent() + text.ascent()) / 2
            canvas.drawText(line, w / 2f, baseline, text)
        }
        return out
    }

    /** Android's JPEG encoder over one composed picture, stamping the JFIF DPI if asked. */
    class JpegEncoder(private val picture: Bitmap, private val dpi: Int?) : ImageEncoder {
        private val scaled = HashMap<Long, Bitmap>()

        override fun encode(width: Int, height: Int, quality: Int): ByteArray {
            val bitmap = if (width == picture.width && height == picture.height) picture
            else scaled.getOrPut(width.toLong() shl 32 or height.toLong()) { scaleSmooth(picture, width, height) }
            val out = ByteArrayOutputStream(width * height / 2)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            return if (dpi != null) JpegMarkers.setDpi(bytes, dpi) else bytes
        }
    }

    /** Writes into Pictures/PhotoSize through MediaStore, which needs no storage permission. */
    fun saveToPictures(context: Context, bytes: ByteArray, fileName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PhotoSize")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("MediaStore refused the insert")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("cannot open output")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Copies the bytes into the cache for sharing; older shared copies are removed first. */
    fun shareFile(context: Context, bytes: ByteArray, fileName: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, fileName).apply { writeBytes(bytes) }
    }

    /** A fresh cache file for the camera app to write into. */
    fun newCameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }
}
