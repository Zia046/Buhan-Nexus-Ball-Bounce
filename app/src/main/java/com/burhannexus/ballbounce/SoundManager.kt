package com.burhannexus.ballbounce

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Short sound effects played through SoundPool. All sounds are generated offline and bundled. */
class SoundManager(context: Context, var enabled: Boolean) {

    enum class Sfx { BOUNCE, WALL, CLICK, GAME_OVER, HIGH_SCORE, BONUS, LIFE_LOST }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val bounce = pool.load(context, R.raw.sfx_bounce, 1)
    private val click = pool.load(context, R.raw.sfx_click, 1)
    private val gameOver = pool.load(context, R.raw.sfx_gameover, 1)
    private val highScore = pool.load(context, R.raw.sfx_highscore, 1)
    private val bonus = pool.load(context, R.raw.sfx_bonus, 1)
    private val lifeLost = pool.load(context, R.raw.sfx_lifelost, 1)

    fun play(sfx: Sfx) {
        if (!enabled) return
        when (sfx) {
            Sfx.BOUNCE -> pool.play(bounce, 0.9f, 0.9f, 1, 0, 1f)
            Sfx.WALL -> pool.play(bounce, 0.25f, 0.25f, 0, 0, 0.75f)
            Sfx.CLICK -> pool.play(click, 0.8f, 0.8f, 1, 0, 1f)
            Sfx.GAME_OVER -> pool.play(gameOver, 1f, 1f, 2, 0, 1f)
            Sfx.HIGH_SCORE -> pool.play(highScore, 1f, 1f, 2, 0, 1f)
            Sfx.BONUS -> pool.play(bonus, 1f, 1f, 2, 0, 1f)
            Sfx.LIFE_LOST -> pool.play(lifeLost, 0.9f, 0.9f, 2, 0, 1f)
        }
    }

    fun stopAll() {
        pool.autoPause()
    }

    fun resumeAll() {
        pool.autoResume()
    }

    fun release() {
        pool.release()
    }
}
