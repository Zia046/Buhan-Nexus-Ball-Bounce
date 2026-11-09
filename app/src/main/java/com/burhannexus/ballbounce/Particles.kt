package com.burhannexus.ballbounce

import android.graphics.Canvas
import android.graphics.Paint

/** Allocation-free particle pool (sparks on hits, confetti on a new high score). */
class Particles(private val capacity: Int) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val gravityScale = FloatArray(capacity)
    var count = 0
        private set

    fun spawn(px: Float, py: Float, pvx: Float, pvy: Float, lifeSeconds: Float, sz: Float, col: Int, gScale: Float = 1f) {
        val i: Int
        if (count < capacity) {
            i = count; count++
        } else {
            // overwrite the oldest-looking (shortest remaining life) slot
            var m = 0
            for (k in 1 until capacity) if (life[k] < life[m]) m = k
            i = m
        }
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = lifeSeconds; maxLife[i] = lifeSeconds
        size[i] = sz; color[i] = col; gravityScale[i] = gScale
    }

    fun update(dt: Float, gravity: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]
                color[i] = color[last]; gravityScale[i] = gravityScale[last]
                count--
                continue
            }
            vy[i] += gravity * gravityScale[i] * dt
            vx[i] *= (1f - 0.8f * dt)
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        for (i in 0 until count) {
            val a = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            paint.color = color[i]
            paint.alpha = (a * 255).toInt()
            canvas.drawCircle(x[i], y[i], size[i] * (0.35f + 0.65f * a), paint)
        }
    }

    fun clear() {
        count = 0
    }
}
