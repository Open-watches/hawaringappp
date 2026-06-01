package com.handwriting.app.domain.generation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.handwriting.app.data.model.Stroke
import com.handwriting.app.data.model.PageBackground
import com.handwriting.app.domain.generation.style.HandwritingStyle

/**
 * Paper renderer for generating realistic notebook pages.
 * Supports ruled, graph, and blank paper types.
 * 
 * This component handles:
 * - Programmatic line drawing (memory efficient)
 * - Custom margins and line spacing
 * - Export to bitmap for sharing/saving
 */
class PaperRenderer {

    /**
     * Paint for ruled lines
     */
    private val ruledLinePaint = Paint().apply {
        color = Color.parseColor("#A0C4E8") // Light blue for ruled lines
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    /**
     * Paint for grid lines
     */
    private val gridLinePaint = Paint().apply {
        color = Color.parseColor("#D0D0D0") // Light gray for grid
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    /**
     * Paint for margin lines
     */
    private val marginPaint = Paint().apply {
        color = Color.parseColor("#FFB6C1") // Light red/pink for margins
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    /**
     * Default line spacing in pixels (will be scaled by density)
     */
    var ruledLineSpacing: Float = 24f
    var gridSpacing: Float = 16f
    var leftMargin: Float = 40f
    var topMargin: Float = 40f
    var rightMargin: Float = 20f
    var bottomMargin: Float = 20f

    /**
     * Draw paper background on the provided canvas.
     * 
     * @param canvas The canvas to draw on
     * @param width Canvas width
     * @param height Canvas height
     * @param backgroundType Type of paper background
     */
    fun drawBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        backgroundType: PageBackground
    ) {
        when (backgroundType) {
            PageBackground.BLANK -> {
                // No background lines
            }
            PageBackground.RULED -> {
                drawRuledPaper(canvas, width, height)
            }
            PageBackground.GRAPH -> {
                drawGraphPaper(canvas, width, height)
            }
        }
    }

    /**
     * Draw ruled paper background with horizontal lines.
     */
    private fun drawRuledPaper(canvas: Canvas, width: Int, height: Int) {
        // Draw horizontal ruled lines
        var y = topMargin + ruledLineSpacing
        while (y < height - bottomMargin) {
            canvas.drawLine(leftMargin, y, width - rightMargin, y, ruledLinePaint)
            y += ruledLineSpacing
        }

        // Draw vertical margin line
        canvas.drawLine(leftMargin, topMargin, leftMargin, height - bottomMargin, marginPaint)
    }

    /**
     * Draw graph paper background with grid pattern.
     */
    private fun drawGraphPaper(canvas: Canvas, width: Int, height: Int) {
        // Draw horizontal lines
        var y = topMargin
        while (y < height - bottomMargin) {
            canvas.drawLine(leftMargin, y, width - rightMargin, y, gridLinePaint)
            y += gridSpacing
        }

        // Draw vertical lines
        var x = leftMargin
        while (x < width - rightMargin) {
            canvas.drawLine(x, topMargin, x, height - bottomMargin, gridLinePaint)
            x += gridSpacing
        }

        // Draw darker margin lines
        canvas.drawLine(leftMargin, topMargin, leftMargin, height - bottomMargin, marginPaint)
    }

    /**
     * Render handwriting strokes onto a bitmap with paper background.
     * 
     * @param strokes The handwriting strokes to render
     * @param width Bitmap width in pixels
     * @param height Bitmap height in pixels
     * @param backgroundType Paper background type
     * @param backgroundColor Background color (default white)
     * @return Bitmap containing rendered handwriting on paper
     */
    fun renderToBitmap(
        strokes: List<Stroke>,
        width: Int,
        height: Int,
        backgroundType: PageBackground,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        canvas.drawColor(backgroundColor)
        drawBackground(canvas, width, height, backgroundType)

        // Draw strokes
        drawStrokes(canvas, strokes)

        return bitmap
    }

    /**
     * Draw handwriting strokes on canvas.
     */
    private fun drawStrokes(canvas: Canvas, strokes: List<Stroke>) {
        val strokePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 8f
            isAntiAlias = true
        }

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue

            val path = Path()
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
     * Create a page with custom dimensions and background.
     * Useful for matching specific notebook sizes.
     */
    fun createPage(
        width: Int,
        height: Int,
        backgroundType: PageBackground,
        lineSpacing: Float? = null,
        margin: Float? = null
    ): PageSpec {
        return PageSpec(
            width = width,
            height = height,
            backgroundType = backgroundType,
            ruledLineSpacing = lineSpacing ?: ruledLineSpacing,
            gridSpacing = lineSpacing ?: gridSpacing,
            leftMargin = margin ?: leftMargin,
            topMargin = topMargin,
            rightMargin = margin ?: rightMargin,
            bottomMargin = bottomMargin
        )
    }

    /**
     * Page specification for custom paper layouts.
     */
    data class PageSpec(
        val width: Int,
        val height: Int,
        val backgroundType: PageBackground,
        val ruledLineSpacing: Float,
        val gridSpacing: Float,
        val leftMargin: Float,
        val topMargin: Float,
        val rightMargin: Float,
        val bottomMargin: Float
    )
}
