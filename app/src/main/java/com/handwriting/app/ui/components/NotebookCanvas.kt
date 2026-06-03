package com.handwriting.app.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.handwriting.app.data.model.*
import java.util.*

/**
 * Enhanced handwriting canvas with multi-page support and per-page undo/redo.
 * Supports notebook-style navigation between pages.
 */
class NotebookCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Current page data
    private var currentPage: Page = Page()
    
    // Current stroke being drawn
    private val currentPath = Path()
    private val currentPoints = mutableListOf<StrokePoint>()

    // Completed strokes for current page
    private val completedStrokes = mutableListOf<Stroke>()
    
    // Per-page undo/redo stacks
    private val undoStacks = mutableMapOf<Long, MutableList<PageState>>()
    private val redoStacks = mutableMapOf<Long, MutableList<PageState>>()

    // Background paint configuration
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#D3E5EF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    private var ruledLineSpacingPx = 24f
    private var gridSpacingPx = 16f

    private val strokePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
        isAntiAlias = true
    }

    private var pressureMultiplier = 1.0f
    private var recognitionDebounceMs = 400L
    private var lastStrokeEndTime = 0L
    private var autoRecognitionEnabled = true

    // Callbacks
    var onStrokeStarted: (() -> Unit)? = null
    var onStrokeEnded: ((Stroke) -> Unit)? = null
    var onStrokesChanged: ((List<Stroke>) -> Unit)? = null
    var onPageChanged: ((Page) -> Unit)? = null
    var onContentChanged: (() -> Unit)? = null

    /**
     * Set the current page to display and edit.
     */
    fun setCurrentPage(page: Page) {
        // Save current page state before switching
        saveCurrentState()
        
        currentPage = page
        loadPageStrokes(page)
        invalidate()
        onPageChanged?.invoke(page)
    }

    /**
     * Get the current page.
     */
    fun getCurrentPage(): Page {
        return currentPage.copy(strokes = completedStrokes.toList())
    }

    /**
     * Load strokes from a page into the canvas.
     */
    private fun loadPageStrokes(page: Page) {
        completedStrokes.clear()
        completedStrokes.addAll(page.strokes)
        currentPoints.clear()
        currentPath.reset()
        backgroundType = page.backgroundType
        invalidate()
    }

    /**
     * Save the current state to the undo stack for the current page.
     */
    private fun saveCurrentState() {
        val state = PageState(
            strokes = completedStrokes.map { it.copy() },
            backgroundType = currentPage.backgroundType
        )
        
        val stack = undoStacks.getOrPut(currentPage.id) { mutableListOf() }
        stack.add(state)
        
        // Clear redo stack when new action is performed
        redoStacks[currentPage.id]?.clear()
    }

    /**
     * Initialize line spacing based on screen density.
     */
    fun initializeSpacing() {
        val density = context.resources.displayMetrics.density
        ruledLineSpacingPx = 24f * density
        gridSpacingPx = 16f * density
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        initializeSpacing()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas)

        for (stroke in completedStrokes) {
            drawStroke(canvas, stroke)
        }

        if (currentPoints.isNotEmpty()) {
            drawCurrentStroke(canvas)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        when (currentPage.backgroundType) {
            PageBackground.BLANK -> {}
            PageBackground.RULED -> drawRuledBackground(canvas)
            PageBackground.GRAPH -> drawGraphBackground(canvas)
        }
    }

    private fun drawRuledBackground(canvas: Canvas) {
        val width = width.toFloat()
        var y = ruledLineSpacingPx
        
        while (y < height) {
            canvas.drawLine(0f, y, width, y, backgroundPaint)
            y += ruledLineSpacingPx
        }
    }

    private fun drawGraphBackground(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        
        var y = gridSpacingPx
        while (y < height) {
            canvas.drawLine(0f, y, width, y, gridPaint)
            y += gridSpacingPx
        }
        
        var x = gridSpacingPx
        while (x < width) {
            canvas.drawLine(x, 0f, x, height, gridPaint)
            x += gridSpacingPx
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.isEmpty()) return

        val path = Path()
        path.moveTo(stroke.points[0].x, stroke.points[0].y)

        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            
            val pressure = if (curr.pressure < 1.0f) curr.pressure else 1.0f
            strokePaint.strokeWidth = 8f * pressure * pressureMultiplier
            
            path.lineTo(curr.x, curr.y)
        }

        canvas.drawPath(path, strokePaint)
    }

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
                currentPath.reset()
                currentPath.moveTo(x, y)
                currentPoints.clear()
                
                val pressure = event.pressure
                currentPoints.add(StrokePoint(x, y, pressure, timestamp))
                
                onStrokeStarted?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pressure = event.pressure
                currentPoints.add(StrokePoint(x, y, pressure, timestamp))
                
                currentPath.lineTo(x, y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val pressure = event.pressure
                currentPoints.add(StrokePoint(x, y, pressure, timestamp))

                if (currentPoints.isNotEmpty()) {
                    val stroke = Stroke(
                        strokeId = System.currentTimeMillis(),
                        points = currentPoints.toList()
                    )
                    
                    saveCurrentState()
                    completedStrokes.add(stroke)
                    
                    onStrokeEnded?.invoke(stroke)
                    onStrokesChanged?.invoke(getAllStrokes())
                    onContentChanged?.invoke()
                    
                    lastStrokeEndTime = timestamp
                    
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

    private fun triggerRecognitionIfDebounced() {
        val timeSinceLastStroke = System.currentTimeMillis() - lastStrokeEndTime
        if (timeSinceLastStroke >= recognitionDebounceMs && completedStrokes.isNotEmpty()) {
            onStrokesChanged?.invoke(getAllStrokes())
        }
    }

    fun getAllStrokes(): List<Stroke> {
        return completedStrokes + 
            if (currentPoints.isNotEmpty()) {
                listOf(Stroke(points = currentPoints.toList()))
            } else {
                emptyList()
            }
    }

    /**
     * Undo the last action on the current page.
     */
    fun undo(): Boolean {
        val stack = undoStacks[currentPage.id]
        if (stack.isNullOrEmpty()) return false
        
        // Save current state for redo
        val currentState = PageState(
            strokes = completedStrokes.map { it.copy() },
            backgroundType = currentPage.backgroundType
        )
        val redoStack = redoStacks.getOrPut(currentPage.id) { mutableListOf() }
        redoStack.add(currentState)
        
        // Pop from undo stack and restore
        val previousState = stack.removeAt(stack.size - 1)
        completedStrokes.clear()
        completedStrokes.addAll(previousState.strokes)
        
        invalidate()
        onStrokesChanged?.invoke(getAllStrokes())
        onContentChanged?.invoke()
        return true
    }

    /**
     * Redo a previously undone action on the current page.
     */
    fun redo(): Boolean {
        val stack = redoStacks[currentPage.id]
        if (stack.isNullOrEmpty()) return false
        
        // Save current state for undo
        val currentState = PageState(
            strokes = completedStrokes.map { it.copy() },
            backgroundType = currentPage.backgroundType
        )
        val undoStack = undoStacks.getOrPut(currentPage.id) { mutableListOf() }
        undoStack.add(currentState)
        
        // Pop from redo stack and restore
        val nextState = stack.removeAt(stack.size - 1)
        completedStrokes.clear()
        completedStrokes.addAll(nextState.strokes)
        
        invalidate()
        onStrokesChanged?.invoke(getAllStrokes())
        onContentChanged?.invoke()
        return true
    }

    /**
     * Clear all strokes from the current page.
     */
    fun clear() {
        if (completedStrokes.isNotEmpty() || currentPoints.isNotEmpty()) {
            saveCurrentState()
        }
        
        completedStrokes.clear()
        currentPoints.clear()
        currentPath.reset()
        invalidate()
        onStrokesChanged?.invoke(emptyList())
        onContentChanged?.invoke()
    }

    fun hasContent(): Boolean {
        return completedStrokes.isNotEmpty() || currentPoints.isNotEmpty()
    }

    fun getStrokeCount(): Int {
        return completedStrokes.size
    }

    fun loadStrokes(strokes: List<Stroke>) {
        completedStrokes.clear()
        completedStrokes.addAll(strokes)
        currentPoints.clear()
        invalidate()
    }

    fun exportToBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        
        drawBackground(canvas)
        
        for (stroke in completedStrokes) {
            drawStroke(canvas, stroke)
        }
        
        return bitmap
    }

    fun setAutoRecognition(enabled: Boolean) {
        autoRecognitionEnabled = enabled
    }

    fun setRecognitionDebounce(debounceMs: Long) {
        recognitionDebounceMs = debounceMs
    }

    fun setStrokeColor(color: Int) {
        strokePaint.color = color
        invalidate()
    }

    fun setStrokeWidth(width: Float) {
        strokePaint.strokeWidth = width
        invalidate()
    }

    fun setBackgroundType(type: PageBackground) {
        currentPage = currentPage.copy(backgroundType = type)
        invalidate()
    }

    companion object {
        const val DEFAULT_STROKE_WIDTH = 8f
        const val DEFAULT_DEBOUNCE_MS = 400L
    }
}
