package com.burhannexus.ballbounce.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Things that happened during one [GameWorld.update] call, used for sounds and effects. */
enum class EventType { PADDLE_HIT, WALL_HIT, LEVEL_UP, GAME_OVER }

class GameEvent(val type: EventType, val x: Float, val y: Float)

/** Difficulty rules: ball speed grows with play time AND with every 10 hits, up to a cap. */
object Difficulty {
    const val MAX_SPEED = 1.85f
    const val SCORE_STEP = 10            // every 10 hits
    const val SPEED_PER_SCORE_STEP = 0.07f
    const val TIME_STEP_SECONDS = 6f     // every 6 seconds survived
    const val SPEED_PER_TIME_STEP = 0.02f
    const val MIN_PADDLE_FACTOR = 0.68f
    const val PADDLE_SHRINK_PER_STEP = 0.04f

    fun targetSpeed(score: Int, elapsedSeconds: Float): Float {
        val byScore = (score / SCORE_STEP) * SPEED_PER_SCORE_STEP
        val byTime = floor(max(0f, elapsedSeconds) / TIME_STEP_SECONDS) * SPEED_PER_TIME_STEP
        return min(MAX_SPEED, 1f + byScore + byTime)
    }

    fun paddleFactor(score: Int): Float =
        max(MIN_PADDLE_FACTOR, 1f - (score / SCORE_STEP) * PADDLE_SHRINK_PER_STEP)

    fun level(score: Int): Int = score / SCORE_STEP + 1
}

object BouncePhysics {
    /** Maximum bounce angle from vertical (radians) when the ball hits the very edge of the paddle. */
    const val MAX_ANGLE = 0.96f // ~55 degrees

    /**
     * Velocity after a paddle hit.
     * @param offset -1 (left edge) .. 0 (centre) .. 1 (right edge)
     * @return floatArrayOf(vx, vy) with vy negative (upwards)
     */
    fun bounce(offset: Float, speed: Float, jitter: Float = 0f): FloatArray {
        val o = offset.coerceIn(-1f, 1f)
        val angle = (o * MAX_ANGLE + jitter).coerceIn(-MAX_ANGLE, MAX_ANGLE)
        return floatArrayOf(speed * sin(angle), -speed * cos(angle))
    }
}

/**
 * Pure game simulation (no Android code) so it can be unit tested.
 * Difficulty is applied by running the simulation clock faster ([speedMul]):
 * the ball keeps the same arc but everything happens quicker.
 */
class GameWorld(private val random: Random = Random.Default) {

    var width = 1f; private set
    var height = 1f; private set
    var ceiling = 0f; private set

    var ballX = 0f; private set
    var ballY = 0f; private set
    var ballVX = 0f; private set
    var ballVY = 0f; private set
    var ballR = 1f; private set

    var paddleX = 0f; private set
    var paddleY = 0f; private set
    var paddleW = 1f; private set
    var paddleH = 1f; private set
    private var paddleBaseW = 1f
    private var targetPaddleX = 0f

    var score = 0; private set
    var level = 1; private set
    var elapsed = 0f; private set
    var speedMul = 1f; private set
    var started = false; private set
    var over = false; private set

    private var gravity = 1f
    private var launchSpeed = 1f
    private var startY = 0f
    private var idleTime = 0f

    private val events = ArrayList<GameEvent>()

    /** Call whenever the screen size or safe insets change. */
    fun setup(w: Float, h: Float, safeTop: Float, safeBottom: Float) {
        val oldW = width
        width = w
        height = h
        ceiling = safeTop
        val unit = min(w, h * 0.6f)
        ballR = unit * 0.032f
        paddleY = h - safeBottom - h * 0.12f
        paddleH = max(ballR * 0.62f, 6f)
        paddleBaseW = min(w, h * 0.7f) * 0.30f
        paddleW = paddleBaseW * Difficulty.paddleFactor(score)

        // Arc: a straight (centre) hit rises to ~16% below the top, apex reached in 0.95 s at speed 1.0
        val rise = max(h * 0.2f, paddleY - (ceiling + h * 0.16f))
        val apexTime = 0.95f
        gravity = 2f * rise / (apexTime * apexTime)
        launchSpeed = 2f * rise / apexTime
        startY = ceiling + h * 0.22f

        if (!started) {
            reset()
        } else if (oldW > 1f) {
            val k = w / oldW
            ballX *= k; paddleX *= k; targetPaddleX *= k
        }
    }

    /** Completely fresh session: score 0, initial speed, ball back at the start. One life. */
    fun reset() {
        score = 0
        level = 1
        elapsed = 0f
        speedMul = 1f
        started = false
        over = false
        idleTime = 0f
        paddleW = paddleBaseW
        ballX = width / 2f
        ballY = startY
        ballVX = 0f
        ballVY = 0f
        paddleX = width / 2f
        targetPaddleX = paddleX
        events.clear()
    }

    /** Releases the ball. */
    fun start() {
        if (started || over) return
        started = true
        ballY = startY
        ballVY = 0f
        ballVX = (random.nextFloat() * 2f - 1f) * height * 0.06f
    }

    fun movePaddleTo(x: Float, immediate: Boolean = false) {
        targetPaddleX = x
        if (immediate) paddleX = clampPaddle(x)
    }

    private fun clampPaddle(x: Float): Float {
        val half = paddleW / 2f
        return if (width <= paddleW) width / 2f else x.coerceIn(half, width - half)
    }

    fun update(dtRaw: Float): List<GameEvent> {
        events.clear()
        val dt = dtRaw.coerceIn(0f, 0.05f)

        // Paddle follows the finger smoothly but quickly
        val k = 1f - exp(-30f * dt)
        paddleX = clampPaddle(paddleX + (clampPaddle(targetPaddleX) - paddleX) * k)

        if (over) return events
        if (!started) {
            idleTime += dt
            ballY = startY + sin(idleTime * 3f) * ballR * 0.6f
            return events
        }

        elapsed += dt
        val target = Difficulty.targetSpeed(score, elapsed)
        speedMul += (target - speedMul) * min(1f, dt * 1.5f)
        val wantW = paddleBaseW * Difficulty.paddleFactor(score)
        paddleW += (wantW - paddleW) * min(1f, dt * 3f)
        paddleX = clampPaddle(paddleX)

        val sim = dt * speedMul
        val steps = max(1, ceil(sim / STEP).toInt())
        val h = sim / steps
        for (i in 0 until steps) {
            if (over) break
            step(h)
        }
        return events
    }

    private fun step(h: Float) {
        val prevBottom = ballY + ballR
        ballVY += gravity * h
        ballX += ballVX * h
        ballY += ballVY * h

        if (ballX - ballR < 0f) {
            ballX = ballR; ballVX = abs(ballVX)
            events.add(GameEvent(EventType.WALL_HIT, ballX, ballY))
        } else if (ballX + ballR > width) {
            ballX = width - ballR; ballVX = -abs(ballVX)
            events.add(GameEvent(EventType.WALL_HIT, ballX, ballY))
        }
        if (ballY - ballR < ceiling) {
            ballY = ceiling + ballR; ballVY = abs(ballVY)
            events.add(GameEvent(EventType.WALL_HIT, ballX, ballY))
        }

        // Paddle: only the top surface counts, only while falling, only if the ball was above it.
        val bottom = ballY + ballR
        if (ballVY > 0f && prevBottom <= paddleY + 0.5f && bottom >= paddleY) {
            val half = paddleW / 2f
            val reach = half + ballR * 0.7f
            if (ballX >= paddleX - reach && ballX <= paddleX + reach) {
                ballY = paddleY - ballR
                val offset = (ballX - paddleX) / half
                val jitter = (random.nextFloat() * 2f - 1f) * 0.035f
                val v = BouncePhysics.bounce(offset, launchSpeed, jitter)
                ballVX = v[0]
                ballVY = v[1]
                score++
                events.add(GameEvent(EventType.PADDLE_HIT, ballX, paddleY))
                val newLevel = Difficulty.level(score)
                if (newLevel > level) {
                    level = newLevel
                    events.add(GameEvent(EventType.LEVEL_UP, ballX, ballY))
                }
            }
        }

        // One life: the moment the ball leaves the bottom of the screen the game is over.
        if (ballY - ballR > height) {
            over = true
            events.add(GameEvent(EventType.GAME_OVER, ballX, height))
        }
    }

    /** True once the ball has dropped below the paddle surface (a miss is certain). */
    val missed: Boolean get() = started && ballY - ballR > paddleY + paddleH

    companion object {
        /** Fixed physics sub-step (seconds of game time) - prevents tunnelling through the paddle. */
        const val STEP = 1f / 240f
    }
}
