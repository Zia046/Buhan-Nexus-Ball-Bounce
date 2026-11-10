package com.burhannexus.ballbounce.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class GameWorldTest {

    private fun world(seed: Int = 7): GameWorld =
        GameWorld(Random(seed)).apply { setup(1080f, 2340f, 80f, 60f) }

    @Test
    fun newGameStartsFresh() {
        val w = world()
        assertEquals(0, w.score)
        assertEquals(1, w.level)
        assertEquals(1f, w.speedMul, 0.0001f)
        assertFalse(w.started)
        assertFalse(w.over)
    }

    @Test
    fun ballWaitsUntilStarted() {
        val w = world()
        repeat(600) { w.update(1f / 60f) }
        assertFalse(w.over)
        assertEquals(0, w.score)
    }

    @Test
    fun paddleHitScoresOnePoint() {
        val w = world()
        w.start()
        var t = 0f
        while (w.score == 0 && t < 5f) {
            w.movePaddleTo(w.ballX, immediate = true)
            w.update(1f / 60f); t += 1f / 60f
        }
        assertEquals(1, w.score)
        assertFalse(w.over)
        assertTrue("ball must go up after the hit", w.ballVY < 0f)
    }

    @Test
    fun missWithNoExtraLifeEndsGame() {
        val w = world()
        w.start()
        var gameOverEvents = 0
        var t = 0f
        while (!w.over && t < 10f) {
            w.movePaddleTo(0f, immediate = true) // paddle far left, ball near centre
            gameOverEvents += w.update(1f / 60f).count { it.type == EventType.GAME_OVER }
            t += 1f / 60f
        }
        assertTrue(w.over)
        assertEquals(1, gameOverEvents)
        assertEquals(0, w.score)
        // No second chance: further updates never revive the ball or add points
        repeat(300) { w.update(1f / 60f) }
        assertTrue(w.over)
        assertEquals(0, w.score)
    }

    @Test
    fun resetGivesCompletelyFreshSession() {
        val w = world()
        w.start()
        var t = 0f
        while (w.score < 12 && t < 120f) {
            w.movePaddleTo(w.ballX, immediate = true)
            w.update(1f / 120f); t += 1f / 120f
        }
        assertTrue(w.score >= 12)
        w.reset()
        assertEquals(0, w.score)
        assertEquals(1, w.level)
        assertEquals(0f, w.elapsed, 0f)
        assertEquals(1f, w.speedMul, 0f)
        assertFalse(w.started)
        assertFalse(w.over)
        assertEquals(540f, w.ballX, 0.01f)
    }

    @Test
    fun perfectPlayerNeverTunnelsThroughPaddleEvenAtMaxSpeed() {
        val w = world(3)
        w.start()
        var t = 0f
        var maxSpeedSeen = 0f
        while (t < 400f) {
            w.movePaddleTo(w.ballX, immediate = true)
            w.update(1f / 120f)
            t += 1f / 120f
            maxSpeedSeen = maxOf(maxSpeedSeen, w.speedMul)
            assertFalse("ball passed through the paddle at t=$t score=${w.score}", w.over)
        }
        assertTrue(w.score > 150)
        assertTrue(maxSpeedSeen <= Difficulty.MAX_SPEED + 0.0001f)
        assertTrue("speed should reach the cap in a long game", maxSpeedSeen > Difficulty.MAX_SPEED - 0.05f)
        assertTrue(w.level > 10)
    }

    @Test
    fun ballStaysInsideSideWalls() {
        val w = world(11)
        w.start()
        var t = 0f
        while (t < 60f) {
            w.movePaddleTo(w.ballX, immediate = true)
            w.update(1f / 60f); t += 1f / 60f
            assertTrue(w.ballX - w.ballR >= -0.01f)
            assertTrue(w.ballX + w.ballR <= w.width + 0.01f)
        }
    }

    @Test
    fun edgeHitsBounceSharperThanCentreHits() {
        val centre = BouncePhysics.bounce(0f, 100f)
        val right = BouncePhysics.bounce(1f, 100f)
        val left = BouncePhysics.bounce(-1f, 100f)
        assertEquals(0f, centre[0], 0.001f)
        assertEquals(-100f, centre[1], 0.001f)
        assertTrue(right[0] > 70f)
        assertTrue(left[0] < -70f)
        assertEquals(abs(left[0]), right[0], 0.001f)
        assertTrue(right[1] < 0f && left[1] < 0f)
        // speed is preserved
        assertEquals(100f, kotlin.math.hypot(right[0], right[1]), 0.01f)
    }

    @Test
    fun difficultyGrowsWithTimeAndScoreButIsCapped() {
        assertEquals(1f, Difficulty.targetSpeed(0, 0f), 0f)
        assertEquals(1f, Difficulty.targetSpeed(9, 5.9f), 0f)
        assertTrue(Difficulty.targetSpeed(10, 0f) > Difficulty.targetSpeed(9, 0f))
        assertTrue(Difficulty.targetSpeed(0, 12f) > Difficulty.targetSpeed(0, 5f))
        // small steps, not jumps
        assertTrue(Difficulty.targetSpeed(10, 0f) - 1f <= 0.1f)
        assertTrue(Difficulty.targetSpeed(0, 6f) - 1f <= 0.05f)
        assertEquals(Difficulty.MAX_SPEED, Difficulty.targetSpeed(1000, 10000f), 0f)
        assertEquals(1f, Difficulty.paddleFactor(0), 0f)
        assertTrue(Difficulty.paddleFactor(20) < 1f)
        assertEquals(Difficulty.MIN_PADDLE_FACTOR, Difficulty.paddleFactor(10000), 0f)
        assertEquals(1, Difficulty.level(9))
        assertEquals(2, Difficulty.level(10))
    }

    @Test
    fun levelUpEventEveryTenHits() {
        val w = world()
        w.start()
        var levelUps = 0
        var t = 0f
        while (w.score < 30 && t < 200f) {
            w.movePaddleTo(w.ballX, immediate = true)
            levelUps += w.update(1f / 120f).count { it.type == EventType.LEVEL_UP }
            t += 1f / 120f
        }
        assertEquals(30, w.score)
        assertEquals(3, levelUps)
        assertEquals(4, w.level)
    }

    @Test
    fun worksOnSmallAndTabletScreens() {
        for (size in listOf(floatArrayOf(480f, 800f), floatArrayOf(720f, 1280f), floatArrayOf(1600f, 2560f))) {
            val w = GameWorld(Random(5))
            w.setup(size[0], size[1], 0f, 0f)
            w.start()
            var t = 0f
            while (t < 60f) {
                w.movePaddleTo(w.ballX, immediate = true)
                w.update(1f / 60f); t += 1f / 60f
            }
            assertFalse(w.over)
            assertTrue(w.score > 20)
        }
    }

    // ------------------------------------------------------------ v1.1 lives + bonus life

    private fun run(w: GameWorld, seconds: Float, dt: Float = 1f / 60f, each: (List<GameEvent>) -> Unit = {}) {
        var t = 0f
        while (t < seconds) { each(w.update(dt)); t += dt }
    }

    @Test
    fun paddleFollowsFingerInstantly() {
        val w = world()
        w.movePaddleTo(w.width * 0.9f)
        assertEquals(w.width * 0.9f, w.paddleX, 0.01f)       // same frame, no lag
        w.movePaddleTo(0f)
        assertEquals(w.paddleW / 2f, w.paddleX, 0.01f)       // clamped to the left edge
        w.movePaddleTo(w.width * 5f)
        assertEquals(w.width - w.paddleW / 2f, w.paddleX, 0.01f)
        w.update(1f / 60f)
        assertEquals(w.width - w.paddleW / 2f, w.paddleX, 0.01f)
    }

    @Test
    fun bonusHeartSpawnsEveryTenHits() {
        val w = world(21)
        w.start()
        var spawns = 0
        var t = 0f
        while (w.score < 30 && t < 200f) {
            w.movePaddleTo(w.ballX)
            spawns += w.update(1f / 120f).count { it.type == EventType.BONUS_SPAWNED }
            t += 1f / 120f
        }
        assertEquals(30, w.score)
        assertEquals(3, spawns)
    }

    @Test
    fun bonusFallsSlowlyAndIsOnlyAwardedWhenTouchingPaddle() {
        val w = world()
        w.movePaddleTo(w.width / 2f)
        w.spawnBonus(w.width / 2f)
        val startY = w.bonuses[0].y
        run(w, 2f)
        assertEquals("not awarded while still falling", 1, w.lives)
        assertTrue(w.bonuses[0].y > startY)
        var collected = 0
        run(w, 4f) { ev -> collected += ev.count { it.type == EventType.BONUS_COLLECTED } }
        assertEquals(1, collected)
        assertEquals(2, w.lives)
        assertTrue(w.bonuses.isEmpty())
    }

    @Test
    fun missedBonusDisappearsAndCostsNothing() {
        val w = world()
        w.movePaddleTo(w.width)            // paddle far right
        w.spawnBonus(w.width * 0.1f)       // heart far left
        var missed = 0
        run(w, 7f) { ev -> missed += ev.count { it.type == EventType.BONUS_MISSED } }
        assertEquals(1, missed)
        assertTrue(w.bonuses.isEmpty())
        assertEquals(1, w.lives)
        assertFalse(w.over)
    }

    @Test
    fun losingALifeKeepsScoreAndDifficultyAndDropsANewBall() {
        val w = world(9)
        // collect two hearts before starting -> 3 lives
        w.movePaddleTo(w.width / 2f)
        w.spawnBonus(w.width / 2f); w.spawnBonus(w.width / 2f)
        run(w, 6f)
        assertEquals(3, w.lives)

        w.start()
        var t = 0f
        while (w.score < 14 && t < 120f) {
            w.movePaddleTo(w.ballX)
            w.update(1f / 120f); t += 1f / 120f
        }
        assertEquals(14, w.score)
        w.bonuses.clear()
        val livesBefore = w.lives
        val speedBefore = w.speedMul
        val levelBefore = w.level

        // miss on purpose
        var lost = 0
        t = 0f
        while (lost == 0 && t < 10f) {
            w.movePaddleTo(if (w.ballX < w.width / 2f) w.width else 0f)
            lost += w.update(1f / 60f).count { it.type == EventType.LIFE_LOST }
            t += 1f / 60f
        }
        assertEquals(1, lost)
        assertEquals(livesBefore - 1, w.lives)
        assertFalse(w.over)
        assertEquals(14, w.score)
        assertEquals(levelBefore, w.level)
        assertTrue(w.speedMul >= speedBefore * 0.99f)
        assertTrue(w.relaunchTimer > 0f)

        // replacement ball waits at the top, then drops
        val elapsedAtLoss = w.elapsed
        var relaunched = 0
        run(w, GameWorld.RELAUNCH_DELAY + 0.1f) { ev -> relaunched += ev.count { it.type == EventType.BALL_RELAUNCHED } }
        assertEquals(1, relaunched)
        assertTrue(w.ballY < w.height * 0.4f)
        assertTrue(w.elapsed - elapsedAtLoss < 0.2f)

        // play on: the score keeps growing from 14
        t = 0f
        while (w.score < 16 && t < 20f) {
            w.movePaddleTo(w.ballX)
            w.update(1f / 120f); t += 1f / 120f
        }
        assertEquals(16, w.score)
    }

    @Test
    fun gameOverOnlyWhenAllLivesAreUsed() {
        val w = world(4)
        w.movePaddleTo(w.width / 2f)
        w.spawnBonus(w.width / 2f)
        run(w, 6f)
        assertEquals(2, w.lives)
        w.start()
        var lifeLost = 0
        var gameOver = 0
        var t = 0f
        while (!w.over && t < 30f) {
            w.movePaddleTo(if (w.ballX < w.width / 2f) w.width else 0f)
            for (e in w.update(1f / 60f)) {
                if (e.type == EventType.LIFE_LOST) lifeLost++
                if (e.type == EventType.GAME_OVER) gameOver++
            }
            t += 1f / 60f
        }
        assertTrue(w.over)
        assertEquals(1, lifeLost)
        assertEquals(1, gameOver)
        assertEquals(0, w.lives)
    }

    @Test
    fun newGameResetsLivesAndBonuses() {
        val w = world()
        w.movePaddleTo(w.width / 2f)
        w.spawnBonus(w.width / 2f)
        run(w, 6f)
        w.spawnBonus()
        assertEquals(2, w.lives)
        w.reset()
        assertEquals(1, w.lives)
        assertTrue(w.bonuses.isEmpty())
        assertEquals(0f, w.relaunchTimer, 0f)
    }
}
