package cc.namekuji.tumugi.util

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

object ImageProcessor {

    fun cropMargins(bitmap: Bitmap, isBgWhite: Boolean? = null): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 10 || height <= 10) return bitmap

        // Sample corner pixels to guess background color (usually white or black)
        val corner1 = bitmap.getPixel(0, 0)
        val corner2 = bitmap.getPixel(width - 1, 0)
        val corner3 = bitmap.getPixel(0, height - 1)
        val corner4 = bitmap.getPixel(width - 1, height - 1)

        fun isPixelWhite(color: Int): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return r > 240 && g > 240 && b > 240
        }

        fun isPixelBlack(color: Int): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return r < 20 && g < 20 && b < 20
        }

        val whiteVotes = (if (isPixelWhite(corner1)) 1 else 0) +
                (if (isPixelWhite(corner2)) 1 else 0) +
                (if (isPixelWhite(corner3)) 1 else 0) +
                (if (isPixelWhite(corner4)) 1 else 0)

        val blackVotes = (if (isPixelBlack(corner1)) 1 else 0) +
                (if (isPixelBlack(corner2)) 1 else 0) +
                (if (isPixelBlack(corner3)) 1 else 0) +
                (if (isPixelBlack(corner4)) 1 else 0)

        val bgWhite = isBgWhite ?: (whiteVotes >= 2)
        val bgBlack = !bgWhite && (blackVotes >= 2)

        if (!bgWhite && !bgBlack) return bitmap // No clear margin background detected

        fun isMarginColor(color: Int): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return if (bgWhite) {
                r > 240 && g > 240 && b > 240
            } else {
                r < 20 && g < 20 && b < 20
            }
        }

        var left = 0
        var right = width - 1
        var top = 0
        var bottom = height - 1

        // Scan Left
        while (left < right) {
            var hasContent = false
            for (y in 0 until height step 8) {
                if (!isMarginColor(bitmap.getPixel(left, y))) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) break
            left++
        }

        // Scan Right
        while (right > left) {
            var hasContent = false
            for (y in 0 until height step 8) {
                if (!isMarginColor(bitmap.getPixel(right, y))) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) break
            right--
        }

        // Scan Top
        while (top < bottom) {
            var hasContent = false
            for (x in left..right step 8) {
                if (!isMarginColor(bitmap.getPixel(x, top))) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) break
            top++
        }

        // Scan Bottom
        while (bottom > top) {
            var hasContent = false
            for (x in left..right step 8) {
                if (!isMarginColor(bitmap.getPixel(x, bottom))) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) break
            bottom--
        }

        val newWidth = right - left + 1
        val newHeight = bottom - top + 1
        if (newWidth <= 10 || newHeight <= 10 || (newWidth == width && newHeight == height)) return bitmap

        return Bitmap.createBitmap(bitmap, left, top, newWidth, newHeight)
    }
}

class CropMarginTransformation : Transformation {
    override val cacheKey: String = "CropMarginTransformation"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return ImageProcessor.cropMargins(input)
    }
}
