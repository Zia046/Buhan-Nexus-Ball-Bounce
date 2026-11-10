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
enum class EventType {
    PADDLE_HIT, WALL_HIT, LEVEL_UP,
    BONUS_SPAWNED, BONUS_COLLECTED, BONUS_MISSED,
    LIFE_LOST, BALL_RELAUNCHED, GAME_OVER
}

/** A falling bonus-life heart. Only collected when it touches the paddle. */
class Bonus(var x: Float, var y: Float, val vy: Float) {
    var age = 0f
}

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

    /** Lives remaining including the ball in play. Starts at 1; +1 for every collected bonus heart. */
    var lives = 1; private set
    /** Seconds until a replacement ball is dropped after a lost life (0 = ball in play). */
    var relaunchTimer = 0f; private set
    val bonuses = ArrayList<Bonus>()
    var bonusR = 1f; private set
    private var bonusFallSpeed = 1f

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
        bonusR = ballR * 1.25f
        // Bonus hearts fall slowly: about 4.5 seconds from the top to the paddle (real time).
        bonusFallSpeed = max(h * 0.1f, paddleY - (ceiling + h * 0.06f)) / BONUS_FALL_SECONDS

        if (!started) {
            reset()
        } else if (oldW > 1f) {
            val k = w / oldW
            ballX *= k; paddleX *= k; targetPaddleX *= k
            for (b in bonuses) b.x *= k
        }
    }

    /** Completely fresh session: score 0, initial speed, ball back at the start, 1 life, no bonuses. */
    fun reset() {
        lives = 1
        relaunchTimer = 0f
        bonuses.clear()
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
        dropBall()
    }

    private fun dropBall() {
        ballX = width / 2f
        ballY = startY
        ballVY = 0f
        ballVX = (random.nextFloat() * 2f - 1f) * height * 0.06f
    }

    /** Creates a falling bonus heart near the top of the screen. */
    fun spawnBonus(x: Float = width * (0.15f + random.nextFloat() * 0.7f)) {
        val b = Bonus(x.coerceIn(bonusR, width - bonusR), ceiling + height * 0.06f, bonusFallSpeed)
        bonuses.add(b)
        events.add(GameEvent(EventType.BONUS_SPAWNED, b.x, b.y))
    }

    private fun updateBonuses(dt: Float) {
        if (bonuses.isEmpty()) return
        val half = paddleW / 2f
        val it = bonuses.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val prevY = b.y
            b.age += dt
            b.y += b.vy * dt
            // collected only when the heart actually touches the paddle
            val touchesTop = b.y + bonusR >= paddleY && prevY - bonusR <= paddleY + paddleH
            if (touchesTop && abs(b.x - paddleX) <= half + bonusR * 0.8f) {
                lives++
                events.add(GameEvent(EventType.BONUS_COLLECTED, b.x, paddleY))
                it.remove()
                continue
            }
            if (b.y - bonusR > height) {
                events.add(GameEvent(EventType.BONUS_MISSED, b.x, height))
                it.remove()
            }
        }
    }

    /**
     * Moves the paddle. Touch input uses immediate = true so the paddle sits exactly under the finger
     * with no lag; the non-immediate path (very fast smoothing) is only a fallback.
     */
    fun movePaddleTo(x: Float, immediate: Boolean = true) {
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

        // Fallback smoothing for non-immediate moves (touch input is immediate)
        val k = 1f - exp(-60f * dt)
        paddleX = clampPaddle(paddleX + (clampPaddle(targetPaddleX) - paddleX) * k)

        if (over) return events
        updateBonuses(dt)
        if (over) return events
        if (!started) {
            idleTime += dt
            ballY = startY + sin(idleTime * 3f) * ballR * 0.6f
            return events
        }

        if (relaunchTimer > 0f) {
            // Replacement ball hovers at the top while the player gets ready. Score and difficulty are kept.
            relaunchTimer -= dt
            idleTime += dt
            ballY = startY + sin(idleTime * 3f) * ballR * 0.6f
            if (relaunchTimer <= 0f) {
                relaunchTimer = 0f
                dropBall()
                events.add(GameEvent(EventType.BALL_RELAUNCHED, ballX, ballY))
            }
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
            if (over || relaunchTimer > 0f) break
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
                if (score % BONUS_EVERY == 0) spawnBonus()
            }
        }

        // Ball left the bottom of the screen: one life is used. Game over only when none are left.
        if (ballY - ballR > height) {
            val missX = ballX
            lives--
            if (lives <= 0) {
                lives = 0
                over = true
                events.add(GameEvent(EventType.GAME_OVER, missX, height))
            } else {
                events.add(GameEvent(EventType.LIFE_LOST, missX, height))
                relaunchTimer = RELAUNCH_DELAY
                idleTime = 0f
                ballX = width / 2f
                ballY = startY
                ballVX = 0f
                ballVY = 0f
            }
        }
    }

    /** True once the ball has dropped below the paddle surface (a miss is certain). */
    val missed: Boolean get() = started && ballY - ballR > paddleY + paddleH

    companion object {
        /** Fixed physics sub-step (seconds of game time) - prevents tunnelling through the paddle. */
        const val STEP = 1f / 240f
        /** A bonus heart appears after every 10 paddle hits. */
        const val BONUS_EVERY = 10
        const val BONUS_FALL_SECONDS = 4.5f
        /** Pause before a replacement ball drops after a lost life. */
        const val RELAUNCH_DELAY = 1.4f
    }
}
