package com.handwriting.app.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.handwriting.app.data.model.Stroke
import com.handwriting.app.data.model.StrokePoint
import java.util.*

/**
 * High-performance custom view for capturing handwriting strokes.
 * Supports both finger and stylus input with pressure sensitivity.
 */
class HandwritingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Current stroke being drawn
    private val currentPath = Path()
    private val currentPoints = mutableListOf<StrokePoint>()

    // Completed strokes
    private val completedStrokes = mutableListOf<Stroke>()
    
    // Undo stack for redo functionality
    private val undoStack = mutableListOf<List<Stroke>>()

    // Paint configuration
    private val strokePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
        isAntiAlias = true
    }

    // Pressure sensitivity multiplier
    private var pressureMultiplier = 1.0f

    // Callbacks
    var onStrokeStarted: (() -> Unit)? = null
    var onStrokeEnded: ((Stroke) -> Unit)? = null
    var onStrokesChanged: ((List<Stroke>) -> Unit)? = null

    // Debounce timer for auto-recognition
    private var recognitionDebounceMs = 400L
    private var lastStrokeEndTime = 0L
    private var autoRecognitionEnabled = true

    /**
     * Enable or disable auto-recognition trigger.
     */
    fun setAutoRecognition(enabled: Boolean) {
        autoRecognitionEnabled = enabled
    }

    /**
     * Set the debounce time for auto-recognition (in milliseconds).
     */
    fun setRecognitionDebounce(debounceMs: Long) {
        recognitionDebounceMs = debounceMs
    }

    /**
     * Set stroke color.
     */
    fun setStrokeColor(color: Int) {
        strokePaint.color = color
        invalidate()
    }

    /**
     * Set stroke width.
     */
    fun setStrokeWidth(width: Float) {
        strokePaint.strokeWidth = width
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw all completed strokes
        for (stroke in completedStrokes) {
            drawStroke(canvas, stroke)
        }

        // Draw current stroke being drawn
        if (currentPoints.isNotEmpty()) {
            drawCurrentStroke(canvas)
        }
    }

    /**
     * Draw a completed stroke.
     */
    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.isEmpty()) return

        val path = Path()
        path.moveTo(stroke.points[0].x, stroke.points[0].y)

        // Use quadratic bezier curves for smoother rendering
        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            
            // Apply pressure to stroke width if available
            val pressure = if (curr.pressure < 1.0f) curr.pressure else 1.0f
            strokePaint.strokeWidth = 8f * pressure * pressureMultiplier
            
            path.lineTo(curr.x, curr.y)
        }

        canvas.drawPath(path, strokePaint)
    }

    /**
     * Draw the stroke currently being created.
     */
    private fun drawCurrentStroke(canvas: Canvas) {
        if (currentPoints.isEmpty()) return

        val path = Path()
        path.moveTo(currentPoints[0].x, currentPoints[0].y)

        for (i in 1 until currentPoints.size) {
            val prev = currentPoints[i - 1]
            val curr = currentPoints[i]
            
            val pressure = if (curr.pressure < 1.0f) curr.pressure else 1.0f
            strokePaint.strokeWidth = 8f * pressure * pressureMultiplier
            
            path.lineTo(curr.x, curr.y)
        }

        canvas.drawPath(path, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val timestamp = System.currentTimeMillis()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Start new stroke
                currentPath.reset()
                currentPath.moveTo(x, y)
                currentPoints.clear()
                
                // Get pressure (stylus) or default to 1.0 (finger)
                val pressure = event.pressure
                
                currentPoints.add(StrokePoint(x, y, pressure, timestamp))
                
                onStrokeStarted?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Continue stroke
                val pressure = event.pressure
                currentPoints.add(StrokePoint(x, y, pressure, timestamp))
                
                currentPath.lineTo(x, y)
                invalidate() // Redraw
                return true
            }

            MotionEvent.ACTION_UP -> {
                // End stroke
                val pressure = event.pressure
                currentPoints.add(StrokePoint(x, y, pressure, timestamp))

                if (currentPoints.isNotEmpty()) {
                    val stroke = Stroke(
                        strokeId = System.currentTimeMillis(),
                        points = currentPoints.toList()
                    )
                    
                    // Save to undo stack before adding
                    undoStack.add(completedStrokes.map { it.copy() })
                    
                    completedStrokes.add(stroke)
                    
                    onStrokeEnded?.invoke(stroke)
                    onStrokesChanged?.invoke(getAllStrokes())
                    
                    // Record last stroke end time for debounce
                    lastStrokeEndTime = timestamp
                    
                    // Trigger auto-recognition after debounce
                    if (autoRecognitionEnabled) {
                        postDelayed({
                            triggerRecognitionIfDebounced()
                        }, recognitionDebounceMs)
                    }
                }

                currentPoints.clear()
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * Check if enough time has passed since last stroke to trigger recognition.
     */
    private fun triggerRecognitionIfDebounced() {
        val timeSinceLastStroke = System.currentTimeMillis() - lastStrokeEndTime
        if (timeSinceLastStroke >= recognitionDebounceMs && completedStrokes.isNotEmpty()) {
            onStrokesChanged?.invoke(getAllStrokes())
        }
    }

    /**
     * Get all current strokes (completed + in-progress).
     */
    fun getAllStrokes(): List<Stroke> {
        return completedStrokes + 
            if (currentPoints.isNotEmpty()) {
                listOf(Stroke(points = currentPoints.toList()))
            } else {
                emptyList()
            }
    }

    /**
     * Undo the last stroke.
     */
    fun undo(): Boolean {
        if (completedStrokes.isEmpty()) return false
        
        // Save current state for redo
        undoStack.add(completedStrokes.map { it.copy() })
        
        completedStrokes.removeAt(completedStrokes.size - 1)
        invalidate()
        onStrokesChanged?.invoke(getAllStrokes())
        return true
    }

    /**
     * Redo a previously undone stroke.
     */
    fun redo(): Boolean {
        if (undoStack.isEmpty()) return false
        
        val previousState = undoStack.removeAt(undoStack.size - 1)
        completedStrokes.clear()
        completedStrokes.addAll(previousState)
        invalidate()
        onStrokesChanged?.invoke(getAllStrokes())
        return true
    }

    /**
     * Clear all strokes from the canvas.
     */
    fun clear() {
        if (completedStrokes.isNotEmpty() || currentPoints.isNotEmpty()) {
            undoStack.add(completedStrokes.map { it.copy() })
        }
        
        completedStrokes.clear()
        currentPoints.clear()
        currentPath.reset()
        invalidate()
        onStrokesChanged?.invoke(emptyList())
    }

    /**
     * Check if canvas has any content.
     */
    fun hasContent(): Boolean {
        return completedStrokes.isNotEmpty() || currentPoints.isNotEmpty()
    }

    /**
     * Get stroke count.
     */
    fun getStrokeCount(): Int {
        return completedStrokes.size
    }

    /**
     * Load strokes onto the canvas (for viewing saved samples).
     */
    fun loadStrokes(strokes: List<Stroke>) {
        completedStrokes.clear()
        completedStrokes.addAll(strokes)
        currentPoints.clear()
        invalidate()
    }

    /**
     * Export current strokes as an image bitmap.
     */
    fun exportToBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        
        for (stroke in completedStrokes) {
            drawStroke(canvas, stroke)
        }
        
        return bitmap
    }

    companion object {
        const val DEFAULT_STROKE_WIDTH = 8f
        const val DEFAULT_DEBOUNCE_MS = 400L
    }
}
