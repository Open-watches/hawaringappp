package com.example.handwriting;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * High-performance custom UI View for hyper-realistic handwriting simulation
 * over procedurally generated paper using AGSL shaders and native text layout.
 * 
 * Targets API 33+ (Android 13+) for RenderEffect and RuntimeShader support.
 */
public class HandwritingPaperView extends View {

    // ========================================================================
    // COMPONENT 1: Core Paper Logic (AGSL Shader)
    // ========================================================================

    /**
     * AGSL shader string for procedural paper generation.
     * Generates micro-pulp texturing, notebook rule grid lines, and margin indicator.
     */
    private static final String PAPER_SHADER_CODE = """
        uniform float2 resolution;
        uniform float lineSpacing;
        uniform float marginWidth;
        uniform float4 lineColor;
        uniform float4 marginColor;
        uniform float4 paperBaseColor;
        
        // High-speed pseudo-random floating point hash generator
        // Uses fractal noise technique for micro-pulp texturing
        float hash(float2 p) {
            p = fract(p * vec2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }
        
        // Multi-octave noise for paper fiber texture
        float noise(float2 p) {
            float n = 0.0;
            float amplitude = 0.5;
            float frequency = 1.0;
            
            for (int i = 0; i < 4; i++) {
                n += amplitude * hash(p * frequency);
                amplitude *= 0.5;
                frequency *= 2.0;
            }
            return n;
        }
        
        vec4 main(vec2 fragCoord) {
            // Normalize coordinates
            vec2 uv = fragCoord / resolution;
            
            // Generate micro-pulp paper texture (tooth/fiber)
            float pulpNoise = noise(fragCoord * 0.5) * 0.08;
            vec4 baseColor = paperBaseColor + vec4(pulpNoise);
            
            // Calculate horizontal rule lines using mod()
            float lineHeight = 2.0;
            float linePosition = mod(fragCoord.y, lineSpacing);
            float isLine = step(linePosition, lineHeight);
            
            // Soften line edges with smoothstep
            float lineAlpha = smoothstep(lineHeight, lineHeight - 1.0, linePosition);
            lineAlpha *= step(marginWidth * resolution.x, fragCoord.x); // Don't draw on margin
            
            // Horizontal margin boundary detector
            float marginX = marginWidth * resolution.x;
            float isMargin = step(fragCoord.x, marginX + 2.0) * (1.0 - step(fragCoord.x, marginX));
            
            // Composite layers
            vec4 result = baseColor;
            
            // Apply rule lines
            result = mix(result, lineColor, lineAlpha * 0.6);
            
            // Apply margin indicator strip
            result = mix(result, marginColor, isMargin * 0.7);
            
            return result;
        }
        """;

    // Shader uniforms
    private RuntimeShader mPaperShader;
    private RenderEffect mPaperRenderEffect;
    
    // Paper configuration
    private float mLineSpacing = 80.0f; // pixels between lines
    private float mMarginWidth = 0.1f; // 10% of screen width
    private int mLineColor = Color.argb(255, 180, 200, 255); // Light blue
    private int mMarginColor = Color.argb(255, 255, 150, 150); // Light red
    private int mPaperBaseColor = Color.argb(255, 250, 248, 240); // Off-white paper

    // ========================================================================
    // COMPONENT 2: Core Handwriting & Layout Logic
    // ========================================================================

    // Text rendering
    private TextPaint mTextPaint;
    private String mTextContent = "";
    private List<LineLayout> mLineLayouts;
    
    // Handwriting simulation parameters
    private float mTremorAmplitude = 1.5f; // Y-axis jitter amplitude
    private float mTremorFrequency = 0.03f; // Sine wave frequency
    private Random mRandom;
    private long mTremorSeed;
    
    // Ink pooling simulation
    private float mInkVariationFactor = 0.15f; // Alpha variation range
    
    // Layout state
    private boolean mNeedsRelayout = true;
    private float mContentTopPadding = 60.0f;
    private float mContentLeftPadding = 20.0f;

    /**
     * Internal class to hold layout information for each line
     */
    private static class LineLayout {
        List<WordToken> words = new ArrayList<>();
        float baselineY;
        float lineWidth;
    }

    /**
     * Internal class to hold word token data with positioning and effects
     */
    private static class WordToken {
        String text;
        float x;
        float y;
        float width;
        int alpha;
        float tremorOffset;
    }

    // ========================================================================
    // Constructors and Initialization
    // ========================================================================

    public HandwritingPaperView(@NonNull Context context) {
        super(context);
        init();
    }

    public HandwritingPaperView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HandwritingPaperView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mRandom = new Random();
        mTremorSeed = System.currentTimeMillis();
        mLineLayouts = new ArrayList<>();
        
        // Initialize text paint with handwriting-style properties
        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        mTextPaint.setTextSize(42.0f);
        mTextPaint.setColor(Color.BLACK);
        mTextPaint.setStyle(Paint.Style.FILL);
        
        // Initialize AGSL shader and render effect
        initializePaperShader();
    }

    /**
     * Initialize the AGSL paper shader and apply it as a RenderEffect
     */
    private void initializePaperShader() {
        try {
            mPaperShader = new RuntimeShader(PAPER_SHADER_CODE);
            
            // Set initial uniform values
            updateShaderUniforms();
            
            // Create RenderEffect that applies the shader to content
            mPaperRenderEffect = RenderEffect.createRuntimeShaderEffect(mPaperShader, "content");
            
            // Apply the render effect to this view
            setRenderEffect(mPaperRenderEffect);
            
        } catch (Exception e) {
            // Fallback for devices that don't support AGSL
            mPaperShader = null;
            mPaperRenderEffect = null;
        }
    }

    /**
     * Update shader uniform values based on current configuration
     */
    private void updateShaderUniforms() {
        if (mPaperShader == null) return;
        
        mPaperShader.setUniform("resolution", new float[]{getWidth(), getHeight()});
        mPaperShader.setUniform("lineSpacing", mLineSpacing);
        mPaperShader.setUniform("marginWidth", mMarginWidth);
        mPaperShader.setUniform("lineColor", colorToVec4(mLineColor));
        mPaperShader.setUniform("marginColor", colorToVec4(mMarginColor));
        mPaperShader.setUniform("paperBaseColor", colorToVec4(mPaperBaseColor));
    }

    /**
     * Convert Android color int to vec4 float array for shader
     */
    private float[] colorToVec4(int color) {
        return new float[]{
            Color.red(color) / 255.0f,
            Color.green(color) / 255.0f,
            Color.blue(color) / 255.0f,
            Color.alpha(color) / 255.0f
        };
    }

    // ========================================================================
    // Text Layout and Wrapping Logic
    // ========================================================================

    /**
     * Set the text content to be rendered
     * @param text Raw multi-line string
     */
    public void setText(String text) {
        if (text == null) text = "";
        mTextContent = text;
        mNeedsRelayout = true;
        invalidate();
    }

    /**
     * Get the current text content
     */
    public String getText() {
        return mTextContent;
    }

    /**
     * Calculate text layout with line wrapping and handwriting effects
     */
    private void calculateLayout() {
        if (!mNeedsRelayout) return;
        
        mLineLayouts.clear();
        
        float availableWidth = getWidth() - mContentLeftPadding - (mMarginWidth * getWidth()) - mContentLeftPadding;
        float currentX = mContentLeftPadding + (mMarginWidth * getWidth());
        float currentY = mContentTopPadding;
        
        LineLayout currentLine = new LineLayout();
        currentLine.baselineY = currentY;
        
        // Split text into paragraphs (preserve explicit line breaks)
        String[] paragraphs = mTextContent.split("\n", -1);
        
        for (int p = 0; p < paragraphs.length; p++) {
            String paragraph = paragraphs[p];
            
            // Tokenize by words
            String[] words = paragraph.split("\\s+");
            
            for (String word : words) {
                if (word.isEmpty()) continue;
                
                float wordWidth = mTextPaint.measureText(word);
                
                // Check if word fits on current line
                if (currentLine.words.isEmpty()) {
                    // First word on line - always place it
                    addWordToLine(currentLine, word, currentX, wordWidth);
                    currentX += wordWidth + mTextPaint.measureText(" ");
                } else if (currentX + wordWidth <= mContentLeftPadding + (mMarginWidth * getWidth()) + availableWidth) {
                    // Word fits - add to current line
                    addWordToLine(currentLine, word, currentX, wordWidth);
                    currentX += wordWidth + mTextPaint.measureText(" ");
                } else {
                    // Word doesn't fit - finalize current line and start new one
                    if (!currentLine.words.isEmpty()) {
                        mLineLayouts.add(currentLine);
                    }
                    
                    currentLine = new LineLayout();
                    currentY += mLineSpacing;
                    currentLine.baselineY = currentY;
                    currentX = mContentLeftPadding + (mMarginWidth * getWidth());
                    
                    addWordToLine(currentLine, word, currentX, wordWidth);
                    currentX += wordWidth + mTextPaint.measureText(" ");
                }
            }
            
            // End of paragraph - force new line
            if (!currentLine.words.isEmpty()) {
                mLineLayouts.add(currentLine);
                currentLine = new LineLayout();
                currentY += mLineSpacing;
                currentLine.baselineY = currentY;
                currentX = mContentLeftPadding + (mMarginWidth * getWidth());
            }
        }
        
        // Add final line if not empty
        if (!currentLine.words.isEmpty()) {
            mLineLayouts.add(currentLine);
        }
        
        mNeedsRelayout = false;
    }

    /**
     * Add a word token to a line with handwriting simulation effects
     */
    private void addWordToLine(LineLayout line, String word, float x, float width) {
        WordToken token = new WordToken();
        token.text = word;
        token.x = x;
        token.width = width;
        
        // Calculate handwriting tremor jitter (organic Y-axis variation)
        token.tremorOffset = calculateTremor(x, line.words.size());
        token.y = line.baselineY + token.tremorOffset;
        
        // Calculate dynamic ink pooling (alpha variation)
        token.alpha = calculateInkAlpha(line.words.size());
        
        line.words.add(token);
        line.lineWidth += width + mTextPaint.measureText(" ");
    }

    /**
     * Calculate organic, low-frequency vector variations using multi-phase sine wave
     */
    private float calculateTremor(float x, int wordIndex) {
        mRandom.setSeed(mTremorSeed + wordIndex);
        
        // Multi-phase sine wave for natural variation
        float phase1 = (float) Math.sin(x * mTremorFrequency + wordIndex * 0.5f);
        float phase2 = (float) Math.sin(x * mTremorFrequency * 2.3f + wordIndex * 0.3f);
        float phase3 = (float) Math.sin(x * mTremorFrequency * 0.7f + wordIndex * 0.8f);
        
        // Combine phases with random scalar offset
        float combinedWave = (phase1 * 0.5f + phase2 * 0.3f + phase3 * 0.2f);
        float randomOffset = (mRandom.nextFloat() - 0.5f) * 0.8f;
        
        return combinedWave * mTremorAmplitude + randomOffset;
    }

    /**
     * Calculate dynamic ink alpha opacity (190-255 range) simulating pressure variance
     */
    private int calculateInkAlpha(int wordIndex) {
        mRandom.setSeed(mTremorSeed + wordIndex * 7);
        
        // Base alpha with variation for ink pooling effect
        float baseAlpha = 222.0f; // Midpoint of 190-255
        float variation = (mRandom.nextFloat() - 0.5f) * 2.0f * mInkVariationFactor * 65.0f;
        
        int alpha = (int) (baseAlpha + variation);
        
        // Clamp to valid range [190, 255]
        return Math.max(190, Math.min(255, alpha));
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // Update shader uniforms when size changes
        updateShaderUniforms();
        
        // Mark layout as needing recalculation
        mNeedsRelayout = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Calculate layout if needed
        if (mNeedsRelayout) {
            calculateLayout();
        }
        
        // Draw all text lines with handwriting effects
        for (LineLayout line : mLineLayouts) {
            for (WordToken token : line.words) {
                // Set alpha for ink pooling effect
                int originalAlpha = mTextPaint.getAlpha();
                mTextPaint.setAlpha(token.alpha);
                
                // Draw word with tremor offset applied to Y coordinate
                // Native canvas.drawText ensures HarfBuzz text shaping for complex scripts
                canvas.drawText(token.text, token.x, token.y, mTextPaint);
                
                // Restore original alpha
                mTextPaint.setAlpha(originalAlpha);
            }
        }
    }

    // ========================================================================
    // EXPORT PIPELINE
    // ========================================================================

    /**
     * Export the current view content to a high-resolution PNG Bitmap.
     * Creates a hardware-backed bitmap, renders the complete layout, and returns
     * the image stream for storage or sharing.
     * 
     * @return Bitmap containing the rendered handwriting on paper
     */
    @NonNull
    public Bitmap exportToPNG() {
        // Ensure layout is calculated
        if (mNeedsRelayout) {
            calculateLayout();
        }
        
        // Create hardware-backed bitmap matching view dimensions
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.HARDWARE);
        
        // Create fresh Canvas context
        Canvas renderCanvas = new Canvas(bitmap);
        
        // Save canvas state
        int saveCount = renderCanvas.save();
        
        // Force standalone render cycle execution
        // This calls our draw logic directly on the export canvas
        renderCanvas.drawColor(Color.TRANSPARENT);
        
        // Apply paper background manually for export (since RenderEffect may not apply)
        drawPaperBackground(renderCanvas);
        
        // Draw text content
        drawTextContent(renderCanvas);
        
        // Restore canvas state
        renderCanvas.restoreToCount(saveCount);
        
        return bitmap;
    }

    /**
     * Draw paper background directly on canvas for export
     */
    private void drawPaperBackground(Canvas canvas) {
        if (mPaperShader != null && mPaperRenderEffect != null) {
            // If shader is available, we can simulate its effect
            // For export, we'll create a simplified version
            canvas.drawColor(mPaperBaseColor);
            
            // Draw rule lines
            Paint linePaint = new Paint();
            linePaint.setColor(mLineColor);
            linePaint.setAlpha(153); // ~60% opacity
            
            float marginX = mMarginWidth * getWidth();
            float startY = mContentTopPadding;
            
            for (float y = startY; y < getHeight(); y += mLineSpacing) {
                canvas.drawLine(marginX, y, getWidth(), y, linePaint);
            }
            
            // Draw margin line
            Paint marginPaint = new Paint();
            marginPaint.setColor(mMarginColor);
            marginPaint.setAlpha(178); // ~70% opacity
            marginPaint.setStrokeWidth(2.0f);
            canvas.drawLine(marginX, 0, marginX, getHeight(), marginPaint);
            
            // Add subtle noise texture
            addNoiseTexture(canvas);
            
        } else {
            // Fallback solid color
            canvas.drawColor(mPaperBaseColor);
        }
    }

    /**
     * Add subtle noise texture to simulate paper pulp
     */
    private void addNoiseTexture(Canvas canvas) {
        Paint noisePaint = new Paint();
        noisePaint.setAlpha(20);
        
        mRandom.setSeed(mTremorSeed);
        int numDots = getWidth() * getHeight() / 50;
        
        for (int i = 0; i < numDots; i++) {
            float x = mRandom.nextFloat() * getWidth();
            float y = mRandom.nextFloat() * getHeight();
            float radius = mRandom.nextFloat() * 1.5f + 0.5f;
            
            noisePaint.setColor(Color.argb(20, 
                mRandom.nextInt(50), 
                mRandom.nextInt(50), 
                mRandom.nextInt(50)));
            
            canvas.drawCircle(x, y, radius, noisePaint);
        }
    }

    /**
     * Draw text content directly on canvas for export
     */
    private void drawTextContent(Canvas canvas) {
        for (LineLayout line : mLineLayouts) {
            for (WordToken token : line.words) {
                int originalAlpha = mTextPaint.getAlpha();
                mTextPaint.setAlpha(token.alpha);
                
                // Native canvas.drawText ensures HarfBuzz text shaping
                canvas.drawText(token.text, token.x, token.y, mTextPaint);
                
                mTextPaint.setAlpha(originalAlpha);
            }
        }
    }

    // ========================================================================
    // Configuration Methods
    // ========================================================================

    /**
     * Set the spacing between horizontal rule lines
     * @param spacing Distance in pixels
     */
    public void setLineSpacing(float spacing) {
        mLineSpacing = Math.max(40.0f, spacing);
        updateShaderUniforms();
        mNeedsRelayout = true;
        invalidate();
    }

    /**
     * Set the width of the left margin as a fraction of screen width
     * @param margin Fraction value (0.0 to 1.0)
     */
    public void setMarginWidth(float margin) {
        mMarginWidth = Math.max(0.0f, Math.min(0.5f, margin));
        updateShaderUniforms();
        mNeedsRelayout = true;
        invalidate();
    }

    /**
     * Set the text size for handwriting
     * @param size Size in pixels
     */
    public void setTextSize(float size) {
        mTextPaint.setTextSize(Math.max(12.0f, size));
        mNeedsRelayout = true;
        invalidate();
    }

    /**
     * Set the typeface for handwriting
     * @param typeface Typeface to use
     */
    public void setTypeface(Typeface typeface) {
        mTextPaint.setTypeface(typeface);
        mNeedsRelayout = true;
        invalidate();
    }

    /**
     * Set the amplitude of handwriting tremor jitter
     * @param amplitude Jitter amplitude in pixels
     */
    public void setTremorAmplitude(float amplitude) {
        mTremorAmplitude = Math.max(0.0f, Math.min(10.0f, amplitude));
        mNeedsRelayout = true;
        invalidate();
    }

    /**
     * Set the ink variation factor for pressure simulation
     * @param factor Variation factor (0.0 to 1.0)
     */
    public void setInkVariationFactor(float factor) {
        mInkVariationFactor = Math.max(0.0f, Math.min(1.0f, factor));
        mNeedsRelayout = true;
        invalidate();
    }

    /**
     * Set a custom seed for reproducible handwriting patterns
     * @param seed Random seed value
     */
    public void setTremorSeed(long seed) {
        mTremorSeed = seed;
        mNeedsRelayout = true;
        invalidate();
    }

    /**
     * Set the base color of the paper
     * @param color Android color int
     */
    public void setPaperBaseColor(int color) {
        mPaperBaseColor = color;
        updateShaderUniforms();
        invalidate();
    }

    /**
     * Set the color of the rule lines
     * @param color Android color int
     */
    public void setLineColor(int color) {
        mLineColor = color;
        updateShaderUniforms();
        invalidate();
    }

    /**
     * Set the color of the margin line
     * @param color Android color int
     */
    public void setMarginColor(int color) {
        mMarginColor = color;
        updateShaderUniforms();
        invalidate();
    }
}
