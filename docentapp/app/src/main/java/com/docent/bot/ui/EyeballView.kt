package com.docent.bot.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.docent.bot.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class EyeballView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val eyeOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val eyelidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
    }

    // Eye properties
    private var centerX = 0f
    private var centerY = 0f
    private var eyeRadius = 0f
    private var irisRadius = 0f
    private var pupilRadius = 0f

    // Animation properties
    private var pupilOffsetX = 0f
    private var pupilOffsetY = 0f
    private var targetPupilOffsetX = 0f
    private var targetPupilOffsetY = 0f
    private var blinkProgress = 0f  // 0 = open, 1 = closed

    // Animators
    private var wanderAnimator: ValueAnimator? = null
    private var blinkAnimator: ValueAnimator? = null
    private var pupilMoveAnimator: ValueAnimator? = null

    // Iris gradient shader
    private var irisGradient: RadialGradient? = null

    // State
    private var isIdleMode = true
    private var isSpeaking = false

    init {
        // Set iris color gradient (blue tones)
        setIrisColor(Color.parseColor("#4A90D9"))
    }

    fun setIrisColor(color: Int) {
        val lighterColor = adjustColorBrightness(color, 1.3f)
        val darkerColor = adjustColorBrightness(color, 0.7f)
        // Shader will be recreated in onSizeChanged
        irisPaint.color = color
        invalidate()
    }

    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        centerX = w / 2f
        centerY = h / 2f
        eyeRadius = min(w, h) / 2f * 0.9f
        irisRadius = eyeRadius * 0.55f
        pupilRadius = irisRadius * 0.45f

        // Create iris gradient
        val irisColor = irisPaint.color
        irisGradient = RadialGradient(
            0f, 0f, irisRadius,
            intArrayOf(
                adjustColorBrightness(irisColor, 1.2f),
                irisColor,
                adjustColorBrightness(irisColor, 0.6f)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw eye white (sclera)
        canvas.drawCircle(centerX, centerY, eyeRadius, eyeWhitePaint)
        canvas.drawCircle(centerX, centerY, eyeRadius, eyeOutlinePaint)

        // Calculate pupil position with constraints
        val maxOffset = eyeRadius - irisRadius - 5f
        val constrainedOffsetX = pupilOffsetX.coerceIn(-maxOffset, maxOffset)
        val constrainedOffsetY = pupilOffsetY.coerceIn(-maxOffset, maxOffset)

        val irisX = centerX + constrainedOffsetX
        val irisY = centerY + constrainedOffsetY

        // Draw iris with gradient
        canvas.save()
        canvas.translate(irisX, irisY)
        irisPaint.shader = irisGradient
        canvas.drawCircle(0f, 0f, irisRadius, irisPaint)
        irisPaint.shader = null
        canvas.restore()

        // Draw pupil
        canvas.drawCircle(irisX, irisY, pupilRadius, pupilPaint)

        // Draw highlights
        val highlightOffset = pupilRadius * 0.3f
        canvas.drawCircle(
            irisX - highlightOffset,
            irisY - highlightOffset,
            pupilRadius * 0.25f,
            highlightPaint
        )
        canvas.drawCircle(
            irisX + highlightOffset * 0.5f,
            irisY + highlightOffset * 0.5f,
            pupilRadius * 0.12f,
            highlightPaint
        )

        // Draw eyelids for blink
        if (blinkProgress > 0) {
            val eyelidHeight = eyeRadius * 2 * blinkProgress
            val topEyelidRect = RectF(
                centerX - eyeRadius - 10,
                centerY - eyeRadius - 10,
                centerX + eyeRadius + 10,
                centerY - eyeRadius + eyelidHeight
            )
            val bottomEyelidRect = RectF(
                centerX - eyeRadius - 10,
                centerY + eyeRadius - eyelidHeight,
                centerX + eyeRadius + 10,
                centerY + eyeRadius + 10
            )
            canvas.drawRect(topEyelidRect, eyelidPaint)
            canvas.drawRect(bottomEyelidRect, eyelidPaint)
        }
    }

    fun startIdleAnimation() {
        isIdleMode = true
        startWanderAnimation()
        startBlinkAnimation()
    }

    fun stopIdleAnimation() {
        isIdleMode = false
        wanderAnimator?.cancel()
        blinkAnimator?.cancel()
    }

    fun lookAt(x: Float, y: Float) {
        // Convert screen coordinates to pupil offset
        val dx = x - centerX
        val dy = y - centerY
        val maxOffset = eyeRadius - irisRadius - 5f

        targetPupilOffsetX = (dx * 0.3f).coerceIn(-maxOffset, maxOffset)
        targetPupilOffsetY = (dy * 0.3f).coerceIn(-maxOffset, maxOffset)

        animatePupilTo(targetPupilOffsetX, targetPupilOffsetY)
    }

    fun lookCenter() {
        animatePupilTo(0f, 0f)
    }

    fun setSpeaking(speaking: Boolean) {
        isSpeaking = speaking
        if (speaking) {
            // More active eye movement when speaking
            startSpeakingAnimation()
        } else {
            if (isIdleMode) {
                startWanderAnimation()
            }
        }
    }

    private fun startWanderAnimation() {
        wanderAnimator?.cancel()

        wanderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000 + Random.nextLong(2000)
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            var phase = Random.nextFloat() * Math.PI.toFloat() * 2

            addUpdateListener {
                if (!isIdleMode) return@addUpdateListener

                val progress = it.animatedValue as Float
                val angle = phase + progress * Math.PI.toFloat() * 2
                val maxOffset = (eyeRadius - irisRadius - 5f) * 0.4f

                // Slow figure-8 pattern
                pupilOffsetX = (sin(angle.toDouble()) * maxOffset).toFloat()
                pupilOffsetY = (sin(angle.toDouble() * 2) * maxOffset * 0.5f).toFloat()

                invalidate()
            }

            start()
        }
    }

    private fun startSpeakingAnimation() {
        wanderAnimator?.cancel()

        wanderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            addUpdateListener {
                if (!isSpeaking) return@addUpdateListener

                val progress = it.animatedValue as Float
                val angle = progress * Math.PI.toFloat() * 2
                val maxOffset = (eyeRadius - irisRadius - 5f) * 0.2f

                // Quick small movements
                pupilOffsetX = (sin(angle.toDouble() * 3) * maxOffset).toFloat()
                pupilOffsetY = (cos(angle.toDouble() * 2) * maxOffset * 0.5f).toFloat()

                invalidate()
            }

            start()
        }
    }

    private fun startBlinkAnimation() {
        blinkAnimator?.cancel()

        fun scheduleNextBlink() {
            if (!isIdleMode) return

            postDelayed({
                if (!isIdleMode) return@postDelayed
                blink {
                    scheduleNextBlink()
                }
            }, 2000 + Random.nextLong(4000))
        }

        scheduleNextBlink()
    }

    fun blink(onComplete: (() -> Unit)? = null) {
        blinkAnimator?.cancel()

        blinkAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener {
                blinkProgress = it.animatedValue as Float
                invalidate()
            }

            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete?.invoke()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })

            start()
        }
    }

    private fun animatePupilTo(targetX: Float, targetY: Float) {
        pupilMoveAnimator?.cancel()

        val startX = pupilOffsetX
        val startY = pupilOffsetY

        pupilMoveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener {
                val progress = it.animatedValue as Float
                pupilOffsetX = startX + (targetX - startX) * progress
                pupilOffsetY = startY + (targetY - startY) * progress
                invalidate()
            }

            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        wanderAnimator?.cancel()
        blinkAnimator?.cancel()
        pupilMoveAnimator?.cancel()
    }
}
