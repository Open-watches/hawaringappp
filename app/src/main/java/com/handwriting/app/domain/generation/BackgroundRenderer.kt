package com.handwriting.app.domain.generation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.handwriting.app.data.model.Stroke

/**
 * Background renderer for user-provided images.
 * 
 * Supports:
 * - Loading custom background images (notebook photos, worksheets, forms)
 * - Perspective adjustment
 * - Brightness/contrast matching
 * - Positioning and scaling
 * 
 * This enables rendering generated handwriting onto real-world backgrounds.
 */
class BackgroundRenderer {

    /**
     * Paint for drawing strokes with blending
     */
    private val strokePaint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
        isAntiAlias = true
        isDither = true
    }

    /**
     * Color matrix for brightness/contrast adjustments
     */
    private val colorMatrix = ColorMatrix()

    /**
     * Current background image
     */
    private var backgroundImage: Bitmap? = null

    /**
     * Background display rectangle (for positioning)
     */
    private var backgroundRect: RectF? = null

    /**
     * Set the background image to render onto.
     * @param bitmap The background image (notebook photo, worksheet, etc.)
     */
    fun setBackgroundImage(bitmap: Bitmap?) {
        backgroundImage = bitmap
        invalidateBackgroundRect()
    }

    /**
     * Clear the current background image.
     */
    fun clearBackground() {
        backgroundImage = null
        backgroundRect = null
    }

    /**
     * Check if a background image is set.
     */
    fun hasBackground(): Boolean = backgroundImage != null

    /**
     * Get the current background image dimensions.
     */
    fun getBackgroundDimensions(): Pair<Int, Int>? {
        return backgroundImage?.let { it.width to it.height }
    }

    /**
     * Adjust background brightness.
     * @param brightness Brightness multiplier (1.0 = normal, <1 darker, >1 brighter)
     */
    fun adjustBrightness(brightness: Float) {
        val contrast = 1.0f // Keep contrast neutral
        updateColorMatrix(brightness, contrast)
    }

    /**
     * Adjust background contrast.
     * @param contrast Contrast multiplier (1.0 = normal, <1 lower, >1 higher)
     */
    fun adjustContrast(contrast: Float) {
        val brightness = 1.0f // Keep brightness neutral
        updateColorMatrix(brightness, contrast)
    }

    /**
     * Update the color matrix for brightness and contrast.
     */
    private fun updateColorMatrix(brightness: Float, contrast: Float) {
        // Reset matrix
        colorMatrix.reset()
        
        // Apply brightness
        val brightnessScale = brightness
        val brightnessOffset = (1.0f - brightness) * 255
        
        // Apply contrast
        val scale = (contrast + 1.0f) * 0.5f
        val translate = (1.0f - scale) * 128f
        
        colorMatrix.setScale(scale, scale, scale, 1.0f)
        
        // Apply translation and brightness offset together
        val totalTranslateR = translate + brightnessOffset * contrast
        val totalTranslateG = translate + brightnessOffset * contrast
        val totalTranslateB = translate + brightnessOffset * contrast
        
        val tempMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                scale, 0f, 0f, 0f, totalTranslateR,
                0f, scale, 0f, 0f, totalTranslateG,
                0f, 0f, scale, 0f, totalTranslateB,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        colorMatrix.set(tempMatrix)
    }

    /**
     * Set background positioning and scaling.
     * @param left Left position
     * @param top Top position
     * @param right Right position
     * @param bottom Bottom position
     */
    fun setBackgroundPosition(left: Float, top: Float, right: Float, bottom: Float) {
        backgroundRect = RectF(left, top, right, bottom)
    }

    /**
     * Auto-fit background to canvas while maintaining aspect ratio.
     * @param canvasWidth Canvas width
     * @param canvasHeight Canvas height
     * @param scaleType How to scale (FIT, FILL, CROP)
     */
    fun fitBackgroundToCanvas(
        canvasWidth: Int,
        canvasHeight: Int,
        scaleType: ScaleType = ScaleType.FIT
    ) {
        val bg = backgroundImage ?: return
        
        val bgAspect = bg.width.toFloat() / bg.height.toFloat()
        val canvasAspect = canvasWidth.toFloat() / canvasHeight.toFloat()
        
        val (left, top, right, bottom) = when (scaleType) {
            ScaleType.FIT -> {
                if (canvasAspect > bgAspect) {
                    // Canvas is wider, letterbox horizontally
                    val newWidth = canvasHeight * bgAspect
                    val horizontalOffset = (canvasWidth - newWidth) / 2
                    Triple(Triple(horizontalOffset, 0f), horizontalOffset + newWidth) to canvasHeight.toFloat()
                } else {
                    // Canvas is taller, letterbox vertically
                    val newHeight = canvasWidth / bgAspect
                    val verticalOffset = (canvasHeight - newHeight) / 2
                    Triple(Triple(0f, verticalOffset), canvasWidth.toFloat()) to (verticalOffset + newHeight)
                }
            }
            ScaleType.FILL -> {
                // Stretch to fill (may distort)
                Triple(Triple(0f, 0f), canvasWidth.toFloat()) to canvasHeight.toFloat()
            }
            ScaleType.CROP -> {
                if (canvasAspect > bgAspect) {
                    // Crop vertically
                    val newHeight = canvasWidth / bgAspect
                    val verticalOffset = (newHeight - canvasHeight) / 2
                    Triple(Triple(0f, -verticalOffset), canvasWidth.toFloat()) to (canvasHeight - verticalOffset)
                } else {
                    // Crop horizontally
                    val newWidth = canvasHeight * bgAspect
                    val horizontalOffset = (newWidth - canvasWidth) / 2
                    Triple(Triple(-horizontalOffset, 0f), (canvasWidth - horizontalOffset)) to canvasHeight.toFloat()
                }
            }
        }
        
        backgroundRect = RectF(left.first.first.first, left.first.first.second, left.first.second, left.second)
    }

    /**
     * Invalidate the background rectangle (force recalculation).
     */
    private fun invalidateBackgroundRect() {
        backgroundRect = null
    }

    /**
     * Render handwriting strokes onto the background image.
     * 
     * @param strokes Handwriting strokes to render
     * @param canvasWidth Canvas width for positioning
     * @param canvasHeight Canvas height for positioning
     * @return Bitmap with handwriting composited onto background
     */
    fun renderHandwritingOnBackground(
        strokes: List<Stroke>,
        canvasWidth: Int,
        canvasHeight: Int
    ): Bitmap? {
        val bg = backgroundImage ?: return null
        
        // Create output bitmap
        val outputBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        
        // Draw background
        drawBackground(canvas, canvasWidth, canvasHeight)
        
        // Draw strokes
        drawStrokes(canvas, strokes)
        
        return outputBitmap
    }

    /**
     * Draw the background image on canvas.
     */
    private fun drawBackground(canvas: Canvas, canvasWidth: Int, canvasHeight: Int) {
        val bg = backgroundImage ?: return
        
        // Ensure background rect is calculated
        val rect = backgroundRect ?: run {
            fitBackgroundToCanvas(canvasWidth, canvasHeight)
            backgroundRect ?: RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())
        }
        
        // Apply color matrix filter if adjusted
        val paint = Paint()
        val isIdentityMatrix = colorMatrix.values.contentEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        if (!isIdentityMatrix) {
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        paint.isFilterBitmap = true
        
        canvas.drawBitmap(bg, null, rect, paint)
    }

    /**
     * Draw handwriting strokes on canvas.
     */
    private fun drawStrokes(canvas: Canvas, strokes: List<Stroke>) {
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            
            val path = android.graphics.Path()
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            
            for (i in 1 until stroke.points.size) {
                val prev = stroke.points[i - 1]
                val curr = stroke.points[i]
                
                // Apply pressure sensitivity
                val pressure = if (curr.pressure < 1.0f) curr.pressure else 1.0f
                strokePaint.strokeWidth = 8f * pressure
                
                path.lineTo(curr.x, curr.y)
            }
            
            canvas.drawPath(path, strokePaint)
        }
    }

    /**
     * Apply perspective transformation to match photographed paper.
     * This is a simplified version - full perspective would require 
     * homography calculation from corner points.
     * 
     * @param corners Four corner points of the paper in the photo
     * @param targetWidth Target width for transformed image
     * @param targetHeight Target height for transformed image
     * @return Transformed bitmap ready for handwriting rendering
     */
    fun applyPerspectiveCorrection(
        corners: List<android.graphics.PointF>,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val bg = backgroundImage ?: return null
        
        if (corners.size != 4) return bg
        
        // Create destination corners (rectangle)
        val dstCorners = listOf(
            android.graphics.PointF(0f, 0f),
            android.graphics.PointF(targetWidth.toFloat(), 0f),
            android.graphics.PointF(targetWidth.toFloat(), targetHeight.toFloat()),
            android.graphics.PointF(0f, targetHeight.toFloat())
        )
        
        // Use Android's Matrix for perspective transform
        val matrix = android.graphics.Matrix()
        val srcPoints = corners.toFloatArray()
        val dstPoints = dstCorners.toFloatArray()
        
        // Set poly-to-poly mapping
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        
        // Create transformed bitmap
        val transformedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(transformedBitmap)
        canvas.concat(matrix)
        canvas.drawBitmap(bg, 0f, 0f, null)
        
        backgroundImage = transformedBitmap
        invalidateBackgroundRect()
        
        return transformedBitmap
    }

    /**
     * Convert list of PointF to float array for matrix operations.
     */
    private fun List<android.graphics.PointF>.toFloatArray(): FloatArray {
        val result = FloatArray(size * 2)
        forEachIndexed { index, point ->
            result[index * 2] = point.x
            result[index * 2 + 1] = point.y
        }
        return result
    }

    /**
     * Scale type for background fitting.
     */
    enum class ScaleType {
        FIT,    // Fit entire image within canvas (may have letterboxing)
        FILL,   // Stretch to fill canvas (may distort)
        CROP    // Fill canvas by cropping edges (no distortion)
    }
}
