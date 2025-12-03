package com.docent.bot.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

class MouthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B6B")
        style = Paint.Style.FILL
    }

    private val mouthOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E55555")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8A8A")
        style = Paint.Style.FILL
    }

    private val teethPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E55555")
        style = Paint.Style.FILL
    }

    // Dimensions
    private var centerX = 0f
    private var centerY = 0f
    private var mouthWidth = 0f
    private var mouthHeight = 0f

    // Animation properties
    private var openAmount = 0f  // 0 = closed, 1 = fully open
    private var smileAmount = 0.3f  // 0 = straight, 1 = big smile
    private var speakingPhase = 0f

    // Animators
    private var speakingAnimator: ValueAnimator? = null
    private var transitionAnimator: ValueAnimator? = null

    // State
    enum class MouthState {
        CLOSED,
        SMILING,
        SPEAKING,
        SURPRISED
    }

    private var currentState = MouthState.CLOSED

    // Path for mouth shape
    private val mouthPath = Path()
    private val lipPath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        centerX = w / 2f
        centerY = h / 2f
        mouthWidth = w * 0.8f
        mouthHeight = h * 0.6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (currentState) {
            MouthState.CLOSED -> drawClosedMouth(canvas)
            MouthState.SMILING -> drawSmilingMouth(canvas)
            MouthState.SPEAKING -> drawSpeakingMouth(canvas)
            MouthState.SURPRISED -> drawSurprisedMouth(canvas)
        }
    }

    private fun drawClosedMouth(canvas: Canvas) {
        // Simple curved line for closed mouth
        mouthPath.reset()

        val startX = centerX - mouthWidth / 2
        val endX = centerX + mouthWidth / 2
        val curveHeight = mouthHeight * smileAmount * 0.3f

        mouthPath.moveTo(startX, centerY)
        mouthPath.quadTo(centerX, centerY + curveHeight, endX, centerY)

        // Draw thick line for lips
        lipPaint.style = Paint.Style.STROKE
        lipPaint.strokeWidth = mouthHeight * 0.15f
        lipPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(mouthPath, lipPaint)
    }

    private fun drawSmilingMouth(canvas: Canvas) {
        mouthPath.reset()

        val startX = centerX - mouthWidth / 2
        val endX = centerX + mouthWidth / 2
        val openHeight = mouthHeight * openAmount * 0.5f
        val smileCurve = mouthHeight * smileAmount * 0.4f

        // Top lip curve
        mouthPath.moveTo(startX, centerY)
        mouthPath.quadTo(centerX, centerY - openHeight * 0.3f, endX, centerY)

        // Bottom lip curve (smile)
        mouthPath.quadTo(centerX, centerY + openHeight + smileCurve, startX, centerY)
        mouthPath.close()

        // Fill mouth interior
        canvas.drawPath(mouthPath, mouthPaint)
        canvas.drawPath(mouthPath, mouthOutlinePaint)

        // Draw teeth if mouth is open enough
        if (openAmount > 0.3f) {
            drawTeeth(canvas, startX, endX, centerY - openHeight * 0.2f, openHeight * 0.4f)
        }

        // Draw tongue if mouth is open
        if (openAmount > 0.5f) {
            drawTongue(canvas, openHeight + smileCurve * 0.5f)
        }
    }

    private fun drawSpeakingMouth(canvas: Canvas) {
        mouthPath.reset()

        val startX = centerX - mouthWidth / 2
        val endX = centerX + mouthWidth / 2

        // Animated open amount based on speaking phase
        val animatedOpen = openAmount * (0.5f + 0.5f * sin(speakingPhase * Math.PI * 2).toFloat())
        val openHeight = mouthHeight * animatedOpen

        // Slight width variation
        val widthMod = 1f - 0.1f * sin(speakingPhase * Math.PI * 4).toFloat()
        val modStartX = centerX - (mouthWidth / 2) * widthMod
        val modEndX = centerX + (mouthWidth / 2) * widthMod

        // Oval-ish shape for speaking
        mouthPath.addOval(
            RectF(
                modStartX,
                centerY - openHeight / 2,
                modEndX,
                centerY + openHeight / 2
            ),
            Path.Direction.CW
        )

        // Fill mouth
        canvas.drawPath(mouthPath, mouthPaint)
        canvas.drawPath(mouthPath, mouthOutlinePaint)

        // Draw teeth
        if (animatedOpen > 0.3f) {
            drawTeeth(canvas, modStartX, modEndX, centerY - openHeight / 2 + openHeight * 0.1f, openHeight * 0.25f)
        }

        // Draw tongue
        if (animatedOpen > 0.4f) {
            val tongueY = centerY + openHeight * 0.15f
            val tongueHeight = openHeight * 0.3f
            drawTongue(canvas, tongueHeight, tongueY)
        }
    }

    private fun drawSurprisedMouth(canvas: Canvas) {
        // Big O shape
        val ovalWidth = mouthWidth * 0.5f
        val ovalHeight = mouthHeight * 0.8f

        mouthPath.reset()
        mouthPath.addOval(
            RectF(
                centerX - ovalWidth / 2,
                centerY - ovalHeight / 2,
                centerX + ovalWidth / 2,
                centerY + ovalHeight / 2
            ),
            Path.Direction.CW
        )

        canvas.drawPath(mouthPath, mouthPaint)
        canvas.drawPath(mouthPath, mouthOutlinePaint)
    }

    private fun drawTeeth(canvas: Canvas, startX: Float, endX: Float, topY: Float, height: Float) {
        val teethCount = 6
        val teethWidth = (endX - startX) / teethCount
        val teethMargin = teethWidth * 0.1f

        for (i in 0 until teethCount) {
            val left = startX + i * teethWidth + teethMargin
            val right = left + teethWidth - teethMargin * 2
            val rect = RectF(left, topY, right, topY + height)
            canvas.drawRoundRect(rect, 4f, 4f, teethPaint)
        }
    }

    private fun drawTongue(canvas: Canvas, height: Float, customY: Float = centerY) {
        val tongueWidth = mouthWidth * 0.4f
        val tongueHeight = height * 0.6f

        val tongueRect = RectF(
            centerX - tongueWidth / 2,
            customY,
            centerX + tongueWidth / 2,
            customY + tongueHeight
        )

        canvas.drawRoundRect(tongueRect, tongueWidth / 2, tongueHeight / 2, tonguePaint)
    }

    fun setState(state: MouthState, animate: Boolean = true) {
        if (currentState == state) return

        val previousState = currentState
        currentState = state

        speakingAnimator?.cancel()

        when (state) {
            MouthState.CLOSED -> {
                if (animate) {
                    animateTo(openAmount = 0f, smileAmount = 0.3f)
                } else {
                    openAmount = 0f
                    smileAmount = 0.3f
                    invalidate()
                }
            }
            MouthState.SMILING -> {
                if (animate) {
                    animateTo(openAmount = 0.6f, smileAmount = 0.8f)
                } else {
                    openAmount = 0.6f
                    smileAmount = 0.8f
                    invalidate()
                }
            }
            MouthState.SPEAKING -> {
                openAmount = 0.7f
                startSpeakingAnimation()
            }
            MouthState.SURPRISED -> {
                if (animate) {
                    animateTo(openAmount = 1f, smileAmount = 0f)
                } else {
                    openAmount = 1f
                    smileAmount = 0f
                    invalidate()
                }
            }
        }
    }

    private fun animateTo(openAmount: Float, smileAmount: Float) {
        transitionAnimator?.cancel()

        val startOpen = this.openAmount
        val startSmile = this.smileAmount

        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener {
                val progress = it.animatedValue as Float
                this@MouthView.openAmount = startOpen + (openAmount - startOpen) * progress
                this@MouthView.smileAmount = startSmile + (smileAmount - startSmile) * progress
                invalidate()
            }

            start()
        }
    }

    private fun startSpeakingAnimation() {
        speakingAnimator?.cancel()

        speakingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400 + Random.nextLong(200)
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            addUpdateListener {
                speakingPhase = it.animatedValue as Float
                invalidate()
            }

            start()
        }
    }

    fun stopSpeaking() {
        speakingAnimator?.cancel()
        if (currentState == MouthState.SPEAKING) {
            setState(MouthState.SMILING)
        }
    }

    fun setSmileAmount(amount: Float) {
        smileAmount = amount.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        speakingAnimator?.cancel()
        transitionAnimator?.cancel()
    }
}
