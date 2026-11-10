package com.burhannexus.ballbounce

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.burhannexus.ballbounce.engine.EventType
import com.burhannexus.ballbounce.engine.GameWorld
import com.burhannexus.ballbounce.engine.ScoreBook
import com.burhannexus.ballbounce.engine.ScoreEntry
import com.burhannexus.ballbounce.engine.SubmitResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The whole game is drawn by this single Canvas view: menu, gameplay, pause, game over,
 * TOP 3 leaderboard, how-to-play and about screens.
 */
@SuppressLint("ViewConstructor")
class GameView(
    private val activity: MainActivity,
    private val prefs: PrefsStore,
    private val sounds: SoundManager
) : View(activity) {

    enum class Screen { MENU, HOW_TO, LEADERBOARD, ABOUT, PLAYING, PAUSED, GAME_OVER }
    enum class Action { PLAY, TOP3, HOW_TO, ABOUT, SOUND, BACK, RESUME, MAIN_MENU, NEW_GAME, EXIT, PAUSE }
    enum class Style { PRIMARY, SECONDARY, DANGER, LINK, ICON }

    class UiButton(val action: Action, var label: String, val style: Style, var sub: String = "") {
        val rect = RectF()
        var shader: Shader? = null
    }

    // ---------------------------------------------------------------- colours
    private val cCyan = 0xFF22E6FF.toInt()
    private val cBlue = 0xFF3D7BFF.toInt()
    private val cPurple = 0xFFB44CFF.toInt()
    private val cPink = 0xFFFF3D7F.toInt()
    private val cGold = 0xFFFFD54A.toInt()
    private val cSilver = 0xFFD5DEE8.toInt()
    private val cBronze = 0xFFE0904A.toInt()
    private val cWhite = 0xFFFFFFFF.toInt()
    private val cText = 0xFFE8ECFF.toInt()
    private val cMuted = 0xFF8A93C0.toInt()
    private val cPanel = 0xFF0C1133.toInt()

    // ---------------------------------------------------------------- state
    private val rnd = Random(System.nanoTime())
    private val world = GameWorld(rnd)
    private val scoreBook = ScoreBook(prefs)
    private var screen = Screen.MENU
    private var running = false
    private var lastFrameNs = 0L
    private var time = 0f

    private var lastResult: SubmitResult? = null
    private var gameOverT = 0f
    private var confettiLeft = 0f
    private var resumeCountdown = 0f
    private var scorePop = 0f
    private var toastText = ""
    private var toastT = 0f
    private var toastSub = ""
    private var bonusMsgT = 0f
    private var livesPop = 0f
    private var lifeFlash = 0f
    private var howScroll = 0f
    private var howVel = 0f
    private var howDragging = false
    private var lastTouchY = 0f
    private var howContentH = 0f
    private var lastWallSound = 0f
    private var hitFlash = 0f

    private var pressed: UiButton? = null
    private var autopilot = false   // debug builds only (CI screenshots)
    private var forceMiss = false   // debug builds only
    private var missOnce = false    // debug builds only
    private var activePointer = -1

    // ---------------------------------------------------------------- layout
    private var w = 1f
    private var h = 1f
    private var safeTop = 0f
    private var safeBottom = 0f
    private var cw = 1f      // content width
    private var cx = 0f
    private var u = 1f       // 1% of content width
    private var top = 0f
    private var bot = 0f
    private val dp = resources.displayMetrics.density

    // ---------------------------------------------------------------- assets
    private val fontTitle: Typeface = loadFont("fonts/tektur_medium.ttf", Typeface.DEFAULT_BOLD)
    private val fontBody: Typeface = loadFont("fonts/outfit_regular.ttf", Typeface.DEFAULT)
    private val fontBold: Typeface = loadFont("fonts/outfit_bold.ttf", Typeface.DEFAULT_BOLD)
    private val logo: Bitmap? = try {
        BitmapFactory.decodeResource(resources, R.drawable.bn_logo)
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- paints
    private val bgPaint = Paint()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; color = 0x1422E6FF }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint().apply { color = 0xB3030510.toInt() }
    private var paddleShader: LinearGradient? = null

    /** Unit-size heart (about 2 x 1.9) centred on 0,0; drawn scaled. */
    private val heartPath = Path().apply {
        moveTo(0f, -0.45f)
        cubicTo(-0.25f, -1.0f, -1.05f, -0.95f, -1.0f, -0.25f)
        cubicTo(-0.95f, 0.25f, -0.35f, 0.55f, 0f, 0.95f)
        cubicTo(0.35f, 0.55f, 0.95f, 0.25f, 1.0f, -0.25f)
        cubicTo(1.05f, -0.95f, 0.25f, -1.0f, 0f, -0.45f)
        close()
    }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-0.35f, -0.45f, 1.7f,
            intArrayOf(0xFFFFD1E0.toInt(), 0xFFFF3D7F.toInt(), 0xFFA0103F.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val heartGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0x99FF3D7F.toInt(), 0x40FF3D7F, 0x00FF3D7F),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val paddleMatrix = Matrix()
    private val tmpRect = RectF()

    // ---------------------------------------------------------------- effects
    private val particles = Particles(320)
    private val trailX = FloatArray(14)
    private val trailY = FloatArray(14)
    private var trailCount = 0
    private var trailHead = 0
    private val starX = FloatArray(60)
    private val starY = FloatArray(60)
    private val starP = FloatArray(60)
    private var gridLines = FloatArray(0)

    // menu background ball
    private var mbX = 0f
    private var mbY = 0f
    private var mbVX = 0f
    private var mbVY = 0f

    // ---------------------------------------------------------------- buttons
    private val btnPlay = UiButton(Action.PLAY, "PLAY", Style.PRIMARY)
    private val btnTop3 = UiButton(Action.TOP3, "TOP 3 HIGH SCORES", Style.SECONDARY)
    private val btnHow = UiButton(Action.HOW_TO, "HOW TO PLAY", Style.SECONDARY)
    private val btnAbout = UiButton(Action.ABOUT, "ABOUT", Style.SECONDARY)
    private val btnSound = UiButton(Action.SOUND, "", Style.SECONDARY)
    private val menuButtons = listOf(btnPlay, btnTop3, btnHow, btnAbout, btnSound)

    private val btnBackHow = UiButton(Action.BACK, "BACK", Style.SECONDARY)
    private val btnBackTop = UiButton(Action.BACK, "BACK", Style.SECONDARY)
    private val btnBackAbout = UiButton(Action.BACK, "BACK", Style.SECONDARY)
    // Email and mobile are display-only (not clickable)
    private val emailRow = RectF()
    private val phoneRow = RectF()
    private val aboutButtons = listOf(btnBackAbout)

    private val btnResume = UiButton(Action.RESUME, "RESUME", Style.PRIMARY)
    private val btnPauseMenu = UiButton(Action.MAIN_MENU, "MAIN MENU", Style.SECONDARY)
    private val pauseButtons = listOf(btnResume, btnPauseMenu)

    private val btnNewGame = UiButton(Action.NEW_GAME, "NEW GAME", Style.PRIMARY)
    private val btnOverMenu = UiButton(Action.MAIN_MENU, "MAIN MENU", Style.SECONDARY)
    private val btnExit = UiButton(Action.EXIT, "EXIT GAME", Style.DANGER)
    private val overButtons = listOf(btnNewGame, btnOverMenu, btnExit)

    private val btnPause = UiButton(Action.PAUSE, "", Style.ICON)
    private val playButtons = listOf(btnPause)

    private val panelRect = RectF()
    private val logoRect = RectF()
    private val lbCards = arrayOf(RectF(), RectF(), RectF())
    private val howCard = RectF()
    /** One laid-out line group of the How to Play content (y is relative to the content top). */
    private class HowRow(val kind: Int, val label: String, val lines: List<String>, val y: Float)
    private var howRows: List<HowRow> = emptyList()
    private var howTextSize = 1f
    private val howDemo = RectF()   // relative to content top

    private val howBasics = listOf(
        "Tap PLAY to start the game.",
        "Move your finger or thumb left and right to control the paddle.",
        "Keep the ball bouncing by hitting it with the paddle.",
        "Every successful bounce increases your score by 1.",
        "The ball becomes faster as time passes and your score increases.",
        "Do not let the ball fall below the paddle."
    )
    private val howBonus = listOf(
        "Every 10 successful bounces, a bonus life appears from the top of the screen.",
        "The bonus life slowly falls toward the bottom.",
        "Move your paddle underneath the falling bonus object to collect it.",
        "Collecting it gives you one extra life.",
        "If you miss the ball, one available life is consumed.",
        "If you have an extra life, a new ball falls from the top and the game continues.",
        "If you have no remaining lives, the game ends."
    )

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    init {
        isFocusable = true
        updateSoundLabel()
    }

    private fun loadFont(path: String, fallback: Typeface): Typeface = try {
        Typeface.createFromAsset(context.assets, path)
    } catch (e: Exception) {
        fallback
    }

    // ================================================================ lifecycle

    fun onHostResume() {
        running = true
        lastFrameNs = 0L
        postInvalidateOnAnimation()
    }

    fun onHostPause() {
        pauseIfPlaying()
        running = false
        pressed = null
    }

    fun onFocusLost() {
        pauseIfPlaying()
    }

    private fun pauseIfPlaying() {
        if (screen == Screen.PLAYING && world.started && !world.over) {
            screen = Screen.PAUSED
            resumeCountdown = 0f
            invalidate()
        }
    }

    /** @return true if the back press was handled inside the game. */
    fun handleBack(): Boolean {
        when (screen) {
            Screen.MENU -> return false
            Screen.HOW_TO, Screen.LEADERBOARD, Screen.ABOUT -> screen = Screen.MENU
            Screen.PLAYING -> if (world.started) screen = Screen.PAUSED else goToMenu()
            Screen.PAUSED -> perform(Action.RESUME)
            Screen.GAME_OVER -> goToMenu()
        }
        sounds.play(SoundManager.Sfx.CLICK)
        invalidate()
        return true
    }

    // ================================================================ layout

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        super.onSizeChanged(nw, nh, ow, oh)
        doLayout()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var t = 0
        var b = 0
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            t = i.top; b = i.bottom
        } else if (Build.VERSION.SDK_INT >= 28) {
            val cut = insets.displayCutout
            if (cut != null) { t = cut.safeInsetTop; b = cut.safeInsetBottom }
        }
        if (t.toFloat() != safeTop || b.toFloat() != safeBottom) {
            safeTop = t.toFloat()
            safeBottom = b.toFloat()
            doLayout()
        }
        return insets
    }

    private fun doLayout() {
        if (width <= 0 || height <= 0) return
        w = width.toFloat()
        h = height.toFloat()
        cw = min(w * 0.88f, h * 0.52f)
        cx = w / 2f
        u = cw / 100f
        top = safeTop + h * 0.015f
        bot = h - safeBottom - h * 0.015f

        world.setup(w, h, safeTop, safeBottom)

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF05060F.toInt(), 0xFF0A0D2A.toInt(), 0xFF170A33.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        // faint neon grid
        val step = w / 9f
        val lines = ArrayList<Float>()
        var x = step / 2f
        while (x < w) { lines.add(x); lines.add(0f); lines.add(x); lines.add(h); x += step }
        var y = step / 2f
        while (y < h) { lines.add(0f); lines.add(y); lines.add(w); lines.add(y); y += step }
        gridLines = lines.toFloatArray()
        for (i in starX.indices) {
            starX[i] = rnd.nextFloat() * w
            starY[i] = rnd.nextFloat() * h
            starP[i] = rnd.nextFloat() * 6.28f
        }

        val r = world.ballR
        glowPaint.shader = RadialGradient(
            0f, 0f, r * 3f,
            intArrayOf(0x9922E6FF.toInt(), 0x403D7BFF, 0x003D7BFF),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        ballPaint.shader = RadialGradient(
            -r * 0.35f, -r * 0.35f, r * 1.6f,
            intArrayOf(cWhite, cCyan, cBlue), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        paddleShader = LinearGradient(
            0f, 0f, 1f, 0f, intArrayOf(cPurple, cCyan, cPurple), null, Shader.TileMode.CLAMP
        )
        paddlePaint.shader = paddleShader

        mbX = w * 0.3f; mbY = h * 0.5f; mbVX = h * 0.18f; mbVY = 0f

        layoutMenu()
        layoutHowTo()
        layoutLeaderboard()
        layoutAbout()
        layoutPause()
        layoutGameOver()
        val pr = u * 5.5f
        btnPause.rect.set(w - u * 4f - pr * 2f, top + u * 2f, w - u * 4f, top + u * 2f + pr * 2f)
    }

    private fun setButton(b: UiButton, centerY: Float, bw: Float, bh: Float) {
        b.rect.set(cx - bw / 2f, centerY - bh / 2f, cx + bw / 2f, centerY + bh / 2f)
        b.shader = if (b.style == Style.PRIMARY) LinearGradient(
            b.rect.left, 0f, b.rect.right, 0f, intArrayOf(cPurple, cBlue, cCyan), null, Shader.TileMode.CLAMP
        ) else null
    }

    private fun stackButtons(buttons: List<UiButton>, startY: Float, bw: Float, bh: Float, gap: Float, firstH: Float = bh) {
        var y = startY
        for ((i, b) in buttons.withIndex()) {
            val hh = if (i == 0) firstH else bh
            setButton(b, y + hh / 2f, bw, hh)
            y += hh + gap
        }
    }

    private fun logoHeightFor(width: Float): Float {
        val lg = logo ?: return width * 0.256f
        return width * lg.height / lg.width
    }

    private fun layoutMenu() {
        val lw = cw * 0.62f
        val lh = logoHeightFor(lw)
        val ly = top + h * 0.035f
        logoRect.set(cx - lw / 2f, ly, cx + lw / 2f, ly + lh)
        val bestY = logoRect.bottom + u * 34f
        val footer = bot - u * 3f
        val blockH = u * 17f + 4 * u * 12.5f + 4 * u * 3.2f
        val areaTop = bestY + u * 6f
        val areaBottom = footer - u * 9f
        val startY = max(areaTop, areaTop + (areaBottom - areaTop - blockH) / 2f)
        stackButtons(menuButtons, startY, cw * 0.80f, u * 12.5f, u * 3.2f, u * 17f)
    }

    private fun layoutHowTo() {
        setButton(btnBackHow, bot - u * 9f, cw * 0.6f, u * 12.5f)
        howCard.set(cx - cw / 2f, top + u * 24f, cx + cw / 2f, btnBackHow.rect.top - u * 5f)
        val size = u * 4.3f
        howTextSize = size
        textPaint.typeface = fontBody
        textPaint.textSize = size
        textPaint.letterSpacing = 0f
        val maxW = howCard.width() - u * 15f - u * 5f
        val lineH = size * 1.32f
        val rows = ArrayList<HowRow>()
        // animated illustration first
        howDemo.set(howCard.left + u * 5f, u * 5f, howCard.right - u * 5f, u * 5f + u * 40f)
        var y = howDemo.bottom + u * 7f      // caption line
        y += u * 9f
        rows.add(HowRow(1, "HOW TO PLAY", emptyList(), y))
        y += u * 7f + size
        howBasics.forEachIndexed { i, t ->
            val lines = wrap(t, textPaint, maxW)
            rows.add(HowRow(0, (i + 1).toString(), lines, y))
            y += lines.size * lineH + u * 2.6f
        }
        y += u * 6f
        rows.add(HowRow(1, "BONUS LIFE", emptyList(), y))
        y += u * 7f + size
        howBonus.forEach { t ->
            val lines = wrap(t, textPaint, maxW)
            rows.add(HowRow(2, "", lines, y))
            y += lines.size * lineH + u * 2.6f
        }
        howRows = rows
        howContentH = y + u * 3f
        howScroll = clampHowScroll(howScroll)
    }

    private fun layoutLeaderboard() {
        setButton(btnBackTop, bot - u * 9f, cw * 0.6f, u * 12.5f)
        val cardH = u * 24f
        val gap = u * 5f
        val total = cardH * 3 + gap * 2
        val areaTop = top + u * 34f
        val areaBottom = btnBackTop.rect.top - u * 6f
        val start = max(areaTop, areaTop + (areaBottom - areaTop - total) / 2f)
        for (i in 0 until 3) {
            val y = start + i * (cardH + gap)
            lbCards[i].set(cx - cw / 2f, y, cx + cw / 2f, y + cardH)
        }
    }

    private fun layoutAbout() {
        setButton(btnBackAbout, bot - u * 9f, cw * 0.6f, u * 12.5f)
        val lw = cw * 0.80f
        val lh = logoHeightFor(lw)
        val ly = top + u * 10f
        logoAboutRect.set(cx - lw / 2f, ly, cx + lw / 2f, ly + lh)
        val linkTop = logoAboutRect.bottom + u * 30f
        emailRow.set(cx - cw / 2f, linkTop, cx + cw / 2f, linkTop + u * 16f)
        phoneRow.set(cx - cw / 2f, linkTop + u * 20f, cx + cw / 2f, linkTop + u * 36f)
    }

    private val logoAboutRect = RectF()

    private fun layoutPause() {
        val ph = u * 94f
        panelRect.set(cx - cw * 0.48f, (h - ph) / 2f, cx + cw * 0.48f, (h + ph) / 2f)
        stackButtons(pauseButtons, panelRect.bottom - u * 8f - u * 13f * 2 - u * 4f, cw * 0.72f, u * 13f, u * 4f)
    }

    private val overPanel = RectF()

    private fun layoutGameOver() {
        val ph = min(u * 132f, bot - top - u * 4f)
        overPanel.set(cx - cw * 0.48f, (h - ph) / 2f, cx + cw * 0.48f, (h + ph) / 2f)
        val bh = u * 12.5f
        val gap = u * 3.2f
        stackButtons(overButtons, overPanel.bottom - u * 7f - bh * 3 - gap * 2, cw * 0.74f, bh, gap)
    }

    // ================================================================ frame loop

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0f else ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNs = now
        if (running) update(dt)
        render(canvas)
        if (running) postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        time += dt
        particles.update(dt, h * 0.9f)
        scorePop = max(0f, scorePop - dt * 4f)
        toastT = max(0f, toastT - dt)
        bonusMsgT = max(0f, bonusMsgT - dt)
        livesPop = max(0f, livesPop - dt * 3f)
        lifeFlash = max(0f, lifeFlash - dt * 2.5f)
        if (screen == Screen.HOW_TO && !howDragging && abs(howVel) > 1f) {
            howScroll = clampHowScroll(howScroll + howVel * dt)
            howVel *= (1f - min(1f, dt * 4f))
        }
        hitFlash = max(0f, hitFlash - dt * 3f)

        when (screen) {
            Screen.MENU, Screen.HOW_TO, Screen.LEADERBOARD, Screen.ABOUT -> updateMenuBall(dt)
            Screen.PLAYING -> updatePlaying(dt)
            Screen.GAME_OVER -> {
                gameOverT += dt
                if (confettiLeft > 0f) {
                    confettiLeft -= dt
                    if (rnd.nextFloat() < 0.6f) spawnConfetti(4)
                }
            }
            Screen.PAUSED -> Unit
        }
    }

    private fun updateMenuBall(dt: Float) {
        val r = world.ballR * 1.3f
        val floor = bot - u * 2f
        val g = h * 1.6f
        mbVY += g * dt
        mbX += mbVX * dt
        mbY += mbVY * dt
        if (mbX - r < 0f) { mbX = r; mbVX = abs(mbVX) }
        if (mbX + r > w) { mbX = w - r; mbVX = -abs(mbVX) }
        if (mbY + r > floor) {
            mbY = floor - r
            mbVY = -kotlin.math.sqrt(2f * g * h * 0.55f)
            spawnSparks(mbX, floor, 6, 0.6f)
        }
        pushTrail(mbX, mbY)
    }

    private fun updatePlaying(dt: Float) {
        if (resumeCountdown > 0f) {
            resumeCountdown -= dt
            return
        }
        if (autopilot) {
            // debug autopilot: catch a low bonus heart while the ball is rising, otherwise follow the ball
            val heart = world.bonuses.maxByOrNull { it.y }
            if (heart != null && world.ballVY < 0f && heart.y > world.ballY) world.movePaddleTo(heart.x, immediate = true)
            else world.movePaddleTo(world.ballX + sin(time * 0.9f) * world.paddleW * 0.3f, immediate = true)
        }
        if (forceMiss) world.movePaddleTo(if (world.ballX < w / 2f) w else 0f, immediate = true)
        val events = world.update(dt)
        for (e in events) {
            when (e.type) {
                EventType.PADDLE_HIT -> {
                    sounds.play(SoundManager.Sfx.BOUNCE)
                    spawnSparks(e.x, e.y, 18, 1f)
                    scorePop = 1f
                    hitFlash = 1f
                }
                EventType.WALL_HIT -> {
                    if (time - lastWallSound > 0.1f) {
                        sounds.play(SoundManager.Sfx.WALL)
                        lastWallSound = time
                    }
                    spawnSparks(e.x, e.y, 5, 0.5f)
                }
                EventType.LEVEL_UP -> {
                    toastText = "SPEED UP!  LEVEL ${world.level}"
                    toastSub = ""
                    toastT = 1.6f
                }
                EventType.BONUS_SPAWNED -> {
                    toastSub = "BONUS LIFE INCOMING \u2013 CATCH THE HEART!"
                    if (toastT <= 0f) { toastText = ""; toastT = 1.6f }
                    spawnSparks(e.x, e.y, 10, 0.4f)
                }
                EventType.BONUS_COLLECTED -> {
                    sounds.play(SoundManager.Sfx.BONUS)
                    bonusMsgT = 1.8f
                    livesPop = 1f
                    val colors = intArrayOf(cPink, cGold, cWhite)
                    for (i in 0 until 30) {
                        val a = (rnd.nextDouble() * Math.PI * 2).toFloat()
                        val sp = (0.2f + rnd.nextFloat() * 0.8f) * h * 0.35f
                        particles.spawn(e.x, e.y, cos(a) * sp, sin(a) * sp - h * 0.15f, 0.6f + rnd.nextFloat() * 0.4f,
                            world.ballR * (0.2f + rnd.nextFloat() * 0.25f), colors[rnd.nextInt(colors.size)], 0.5f)
                    }
                }
                EventType.BONUS_MISSED -> Unit
                EventType.LIFE_LOST -> {
                    if (missOnce) { missOnce = false; forceMiss = false; autopilot = true }
                    sounds.play(SoundManager.Sfx.LIFE_LOST)
                    lifeFlash = 1f
                    trailCount = 0
                    for (i in 0 until 20) {
                        val a = (-Math.PI + rnd.nextDouble() * Math.PI).toFloat()
                        val sp = rnd.nextFloat() * h * 0.45f
                        particles.spawn(e.x, h - safeBottom, cos(a) * sp, sin(a) * sp, 0.7f, world.ballR * 0.3f, cPink, 0.8f)
                    }
                }
                EventType.BALL_RELAUNCHED -> spawnSparks(world.ballX, world.ballY, 8, 0.3f)
                EventType.GAME_OVER -> {
                    onGameOver()
                    return
                }
            }
        }
        if (world.started && world.relaunchTimer <= 0f) pushTrail(world.ballX, world.ballY)
    }

    private fun pushTrail(x: Float, y: Float) {
        trailX[trailHead] = x
        trailY[trailHead] = y
        trailHead = (trailHead + 1) % trailX.size
        if (trailCount < trailX.size) trailCount++
    }

    private fun spawnSparks(x: Float, y: Float, n: Int, power: Float) {
        val colors = intArrayOf(cCyan, cPurple, cBlue, cWhite)
        for (i in 0 until n) {
            val a = (-Math.PI + rnd.nextDouble() * Math.PI).toFloat() // upwards half-circle
            val sp = (0.25f + rnd.nextFloat() * 0.75f) * h * 0.45f * power
            particles.spawn(
                x, y, cos(a) * sp, sin(a) * sp,
                0.35f + rnd.nextFloat() * 0.35f,
                world.ballR * (0.18f + rnd.nextFloat() * 0.22f),
                colors[rnd.nextInt(colors.size)], 0.6f
            )
        }
    }

    private fun spawnConfetti(n: Int) {
        val colors = intArrayOf(cGold, cCyan, cPink, cPurple, cWhite)
        for (i in 0 until n) {
            particles.spawn(
                rnd.nextFloat() * w, overPanel.top - u * 2f,
                (rnd.nextFloat() - 0.5f) * w * 0.3f, -rnd.nextFloat() * h * 0.25f,
                1.2f + rnd.nextFloat() * 0.8f, u * (0.8f + rnd.nextFloat() * 0.9f),
                colors[rnd.nextInt(colors.size)], 0.35f
            )
        }
    }

    // ================================================================ game flow

    private fun startNewGame() {
        world.reset()
        particles.clear()
        trailCount = 0
        resumeCountdown = 0f
        toastT = 0f
        bonusMsgT = 0f
        lifeFlash = 0f
        livesPop = 0f
        lastResult = null
        confettiLeft = 0f
        autopilot = false
        forceMiss = false
        screen = Screen.PLAYING
        keepScreenOn = true
    }

    /** Debug-build hook used by the CI emulator test (never called in release builds). */
    fun debugCommand(cmd: String) {
        when (cmd) {
            "menu" -> goToMenu()
            "howto" -> screen = Screen.HOW_TO
            "top3" -> screen = Screen.LEADERBOARD
            "about" -> screen = Screen.ABOUT
            "autoplay" -> { startNewGame(); world.start(); autopilot = true }
            "pause" -> if (screen == Screen.PLAYING && world.started) screen = Screen.PAUSED
            "miss" -> {
                if (screen == Screen.PAUSED) { screen = Screen.PLAYING; resumeCountdown = 0f }
                autopilot = false; forceMiss = true; missOnce = false
            }
            "missonce" -> {
                if (screen == Screen.PAUSED) { screen = Screen.PLAYING; resumeCountdown = 0f }
                autopilot = false; forceMiss = true; missOnce = true
            }
            "auto" -> {
                if (screen == Screen.PAUSED) { screen = Screen.PLAYING; resumeCountdown = 0f }
                autopilot = true; forceMiss = false
            }
            "bonus" -> {
                if (screen == Screen.PAUSED) { screen = Screen.PLAYING; resumeCountdown = 0f }
                if (screen == Screen.PLAYING && world.started) world.spawnBonus(w * 0.5f)
            }
            "dump" -> for (s in Screen.values()) for (b in buttonsFor(s)) {
                android.util.Log.i("BNUI", "$s ${b.action} ${b.rect.centerX().toInt()} ${b.rect.centerY().toInt()}")
            }
        }
        invalidate()
    }

    private fun onGameOver() {
        val result = scoreBook.submit(world.score, System.currentTimeMillis())
        lastResult = result
        screen = Screen.GAME_OVER
        gameOverT = 0f
        trailCount = 0
        pressed = null
        keepScreenOn = false
        for (i in 0 until 26) {
            val a = (-Math.PI + rnd.nextDouble() * Math.PI).toFloat()
            val sp = rnd.nextFloat() * h * 0.5f
            particles.spawn(world.ballX, h - safeBottom, cos(a) * sp, sin(a) * sp, 0.8f, world.ballR * 0.35f, cPink, 0.8f)
        }
        if (result.newBest) {
            sounds.play(SoundManager.Sfx.HIGH_SCORE)
            confettiLeft = 2.5f
            spawnConfetti(40)
        } else {
            sounds.play(SoundManager.Sfx.GAME_OVER)
        }
    }

    private fun goToMenu() {
        // Leaving a run from the pause menu still records the score reached.
        if (world.started && !world.over && world.score > 0) {
            scoreBook.submit(world.score, System.currentTimeMillis())
        }
        world.reset()
        particles.clear()
        trailCount = 0
        screen = Screen.MENU
        keepScreenOn = false
    }

    private fun perform(action: Action) {
        when (action) {
            Action.PLAY, Action.NEW_GAME -> startNewGame()
            Action.TOP3 -> screen = Screen.LEADERBOARD
            Action.HOW_TO -> { screen = Screen.HOW_TO; howScroll = 0f; howVel = 0f }
            Action.ABOUT -> screen = Screen.ABOUT
            Action.SOUND -> {
                val on = !prefs.soundOn
                prefs.soundOn = on
                sounds.enabled = on
                updateSoundLabel()
            }
            Action.BACK -> screen = Screen.MENU
            Action.RESUME -> {
                screen = Screen.PLAYING
                resumeCountdown = if (world.started) 3f else 0f
                lastFrameNs = 0L
            }
            Action.MAIN_MENU -> goToMenu()
            Action.EXIT -> activity.exitGame()
            Action.PAUSE -> {
                if (world.started && !world.over) screen = Screen.PAUSED
            }
        }
        invalidate()
    }

    private fun updateSoundLabel() {
        btnSound.label = if (prefs.soundOn) "SOUND: ON" else "SOUND: OFF"
    }

    private fun buttonsFor(s: Screen): List<UiButton> = when (s) {
        Screen.MENU -> menuButtons
        Screen.HOW_TO -> listOf(btnBackHow)
        Screen.LEADERBOARD -> listOf(btnBackTop)
        Screen.ABOUT -> aboutButtons
        Screen.PAUSED -> pauseButtons
        Screen.GAME_OVER -> overButtons
        Screen.PLAYING -> playButtons
    }

    private fun overButtonsEnabled() = gameOverT > 0.8f

    // ================================================================ input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = e.getPointerId(0)
                onDown(e.x, e.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (screen == Screen.PLAYING && pressed == null) {
                    val i = e.actionIndex
                    activePointer = e.getPointerId(i)
                    world.movePaddleTo(e.getX(i), immediate = true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activePointer)
                if (i >= 0) onMove(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == activePointer && e.pointerCount > 1) {
                    val ni = if (e.actionIndex == 0) 1 else 0
                    activePointer = e.getPointerId(ni)
                }
            }
            MotionEvent.ACTION_UP -> {
                onUp(e.x, e.y)
                activePointer = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                activePointer = -1
            }
        }
        return true
    }

    private fun hit(b: UiButton, x: Float, y: Float, slop: Float = 0f): Boolean {
        tmpRect.set(b.rect)
        tmpRect.inset(-slop, -slop)
        return tmpRect.contains(x, y)
    }

    private fun onDown(x: Float, y: Float) {
        if (screen == Screen.PLAYING) {
            if (world.started && resumeCountdown <= 0f && hit(btnPause, x, y, u * 3f)) {
                pressed = btnPause
                return
            }
            pressed = null
            world.movePaddleTo(x, immediate = true)
            if (!world.started && resumeCountdown <= 0f) world.start()
            return
        }
        if (screen == Screen.HOW_TO && howCard.contains(x, y)) {
            howDragging = true
            howVel = 0f
            lastTouchY = y
            pressed = null
            return
        }
        if (screen == Screen.GAME_OVER && !overButtonsEnabled()) return
        pressed = buttonsFor(screen).firstOrNull { hit(it, x, y) }
    }

    private fun onMove(x: Float, y: Float) {
        // The paddle sits exactly under the finger: no smoothing, no lag.
        if (screen == Screen.PLAYING && pressed == null) world.movePaddleTo(x, immediate = true)
        if (screen == Screen.HOW_TO && howDragging) {
            val dy = y - lastTouchY
            lastTouchY = y
            howScroll = clampHowScroll(howScroll - dy)
            howVel = -dy * 60f
        }
    }

    private fun clampHowScroll(v: Float): Float =
        v.coerceIn(0f, max(0f, howContentH - (howCard.height() - u * 2f)))

    private fun onUp(x: Float, y: Float) {
        if (howDragging) { howDragging = false; return }
        val b = pressed
        pressed = null
        if (b == null) return
        if (!buttonsFor(screen).contains(b)) return
        if (hit(b, x, y, u * 4f)) {
            if (b.action == Action.SOUND) {
                perform(b.action)
                sounds.play(SoundManager.Sfx.CLICK) // only audible when sound was just turned on
            } else {
                sounds.play(SoundManager.Sfx.CLICK)
                perform(b.action)
            }
        }
    }

    // ================================================================ rendering

    private fun render(c: Canvas) {
        drawBackground(c)
        when (screen) {
            Screen.MENU -> renderMenu(c)
            Screen.HOW_TO -> renderHowTo(c)
            Screen.LEADERBOARD -> renderLeaderboard(c)
            Screen.ABOUT -> renderAbout(c)
            Screen.PLAYING -> renderPlaying(c)
            Screen.PAUSED -> { renderPlaying(c); renderPause(c) }
            Screen.GAME_OVER -> { renderPlaying(c); renderGameOver(c) }
        }
    }

    private fun drawBackground(c: Canvas) {
        c.drawRect(0f, 0f, w, h, bgPaint)
        if (gridLines.isNotEmpty()) c.drawLines(gridLines, gridPaint)
        fillPaint.shader = null
        for (i in starX.indices) {
            val a = 0.25f + 0.35f * (0.5f + 0.5f * sin(time * 1.3f + starP[i]))
            fillPaint.color = withAlpha(if (i % 3 == 0) cCyan else cWhite, a)
            c.drawCircle(starX[i], starY[i], dp * (0.6f + (i % 3) * 0.4f), fillPaint)
        }
    }

    private fun drawBall(c: Canvas, x: Float, y: Float, scale: Float, alpha: Float) {
        c.save()
        c.translate(x, y)
        c.scale(scale, scale)
        glowPaint.alpha = (alpha * 255).toInt()
        c.drawCircle(0f, 0f, world.ballR * 3f, glowPaint)
        ballPaint.alpha = (alpha * 255).toInt()
        c.drawCircle(0f, 0f, world.ballR, ballPaint)
        c.restore()
    }

    private fun drawTrail(c: Canvas, scale: Float, alpha: Float) {
        if (trailCount < 2) return
        fillPaint.shader = null
        val n = trailCount
        for (k in 0 until n) {
            val idx = (trailHead - n + k + trailX.size * 2) % trailX.size
            val t = (k + 1).toFloat() / n
            fillPaint.color = withAlpha(cCyan, 0.28f * t * alpha)
            c.drawCircle(trailX[idx], trailY[idx], world.ballR * scale * (0.3f + 0.6f * t), fillPaint)
        }
    }

    private fun drawPaddle(c: Canvas) {
        val pw = world.paddleW
        val ph = world.paddleH
        val l = world.paddleX - pw / 2f
        val t = world.paddleY
        tmpRect.set(l, t, l + pw, t + ph)
        fillPaint.shader = null
        for (i in 3 downTo 1) {
            fillPaint.color = withAlpha(cPurple, 0.10f + 0.10f * hitFlash)
            val g = i * ph * 0.55f
            c.drawRoundRect(tmpRect.left - g, tmpRect.top - g, tmpRect.right + g, tmpRect.bottom + g, ph / 2f + g, ph / 2f + g, fillPaint)
        }
        paddleMatrix.setScale(pw, 1f)
        paddleMatrix.postTranslate(l, 0f)
        paddleShader?.setLocalMatrix(paddleMatrix)
        c.drawRoundRect(tmpRect, ph / 2f, ph / 2f, paddlePaint)
        fillPaint.color = withAlpha(cWhite, 0.55f)
        c.drawRoundRect(l + ph * 0.6f, t + ph * 0.18f, l + pw - ph * 0.6f, t + ph * 0.36f, ph * 0.1f, ph * 0.1f, fillPaint)
    }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        tf: Typeface = fontBody, align: Paint.Align = Paint.Align.CENTER,
        glow: Int = 0, glowR: Float = 0f, maxWidth: Float = 0f, spacing: Float = 0f
    ) {
        textPaint.typeface = tf
        textPaint.textSize = size
        textPaint.letterSpacing = spacing
        textPaint.color = color
        textPaint.textAlign = align
        if (maxWidth > 0f) {
            val mw = textPaint.measureText(s)
            if (mw > maxWidth) textPaint.textSize = size * maxWidth / mw
        }
        if (glow != 0 && glowR > 0f) textPaint.setShadowLayer(glowR, 0f, 0f, glow) else textPaint.clearShadowLayer()
        c.drawText(s, x, y, textPaint)
        textPaint.clearShadowLayer()
    }

    private fun drawButton(c: Canvas, b: UiButton, enabled: Boolean = true) {
        val r = b.rect
        val a = if (enabled) 1f else 0.4f
        c.save()
        if (pressed === b && enabled) c.scale(0.95f, 0.95f, r.centerX(), r.centerY())
        val rad = r.height() * 0.5f
        fillPaint.shader = null
        when (b.style) {
            Style.PRIMARY -> {
                for (i in 3 downTo 1) {
                    val g = i * dp * 3f
                    fillPaint.color = withAlpha(cPurple, 0.10f * a * (0.8f + 0.2f * sin(time * 3f)))
                    c.drawRoundRect(r.left - g, r.top - g, r.right + g, r.bottom + g, rad + g, rad + g, fillPaint)
                }
                fillPaint.shader = b.shader
                fillPaint.alpha = (255 * a).toInt()
                c.drawRoundRect(r, rad, rad, fillPaint)
                fillPaint.shader = null
                text(c, b.label, r.centerX(), r.centerY() + r.height() * 0.15f, r.height() * 0.40f,
                    withAlpha(cWhite, a), fontTitle, glow = 0x99000000.toInt(), glowR = dp * 3f, maxWidth = r.width() * 0.85f, spacing = 0.08f)
            }
            Style.SECONDARY, Style.DANGER -> {
                val edge = if (b.style == Style.DANGER) cPink else cCyan
                fillPaint.color = withAlpha(if (b.style == Style.DANGER) 0xFF2A0C22.toInt() else 0xFF111845.toInt(), 0.9f * a)
                c.drawRoundRect(r, rad, rad, fillPaint)
                strokePaint.strokeWidth = dp * 1.6f
                strokePaint.color = withAlpha(edge, 0.85f * a)
                c.drawRoundRect(r, rad, rad, strokePaint)
                text(c, b.label, r.centerX(), r.centerY() + r.height() * 0.13f, r.height() * 0.34f,
                    withAlpha(cText, a), fontBold, maxWidth = r.width() * 0.85f, spacing = 0.06f)
            }
            Style.LINK -> {
                val lr = r.height() * 0.28f
                fillPaint.color = withAlpha(0xFF101640.toInt(), 0.95f)
                c.drawRoundRect(r, lr, lr, fillPaint)
                strokePaint.strokeWidth = dp * 1.2f
                strokePaint.color = withAlpha(cBlue, 0.7f)
                c.drawRoundRect(r, lr, lr, strokePaint)
                val pad = r.height() * 0.32f
                text(c, b.label, r.left + pad, r.top + r.height() * 0.38f, r.height() * 0.20f, cMuted,
                    fontBold, Paint.Align.LEFT, spacing = 0.15f)
                text(c, b.sub, r.left + pad, r.top + r.height() * 0.76f, r.height() * 0.30f, cCyan,
                    fontBold, Paint.Align.LEFT, maxWidth = r.width() - pad * 3.2f)
                // underline (clickable)
                textPaint.typeface = fontBold
                val tw = min(textPaint.measureText(b.sub), r.width() - pad * 3.2f)
                fillPaint.color = withAlpha(cCyan, 0.5f)
                c.drawRect(r.left + pad, r.top + r.height() * 0.81f, r.left + pad + tw, r.top + r.height() * 0.81f + dp, fillPaint)
                text(c, "›", r.right - pad, r.centerY() + r.height() * 0.14f, r.height() * 0.5f, cCyan,
                    fontBold, Paint.Align.RIGHT)
            }
            Style.ICON -> Unit
        }
        c.restore()
    }

    private fun drawPanel(c: Canvas, r: RectF, edge: Int, alpha: Float) {
        val rad = u * 6f
        fillPaint.shader = null
        for (i in 3 downTo 1) {
            val g = i * dp * 4f
            fillPaint.color = withAlpha(edge, 0.06f * alpha)
            c.drawRoundRect(r.left - g, r.top - g, r.right + g, r.bottom + g, rad + g, rad + g, fillPaint)
        }
        fillPaint.color = withAlpha(cPanel, 0.94f * alpha)
        c.drawRoundRect(r, rad, rad, fillPaint)
        strokePaint.strokeWidth = dp * 1.4f
        strokePaint.color = withAlpha(edge, 0.6f * alpha)
        c.drawRoundRect(r, rad, rad, strokePaint)
    }

    private fun drawLogo(c: Canvas, r: RectF) {
        val lg = logo ?: run {
            text(c, Brand.COMPANY, r.centerX(), r.centerY(), r.height() * 0.5f, cBlue, fontTitle)
            return
        }
        bitmapPaint.alpha = 255
        c.drawBitmap(lg, null, r, bitmapPaint)
    }

    // ---------------------------------------------------------------- screens

    private fun renderMenu(c: Canvas) {
        drawTrail(c, 1.3f, 0.6f)
        drawBall(c, mbX, mbY, 1.3f, 0.55f)
        particles.draw(c, particlePaint)

        drawLogo(c, logoRect)
        val titleY = logoRect.bottom + u * 17f
        text(c, Brand.GAME, cx, titleY, u * 13f, cText, fontTitle, glow = cCyan, glowR = u * 2.5f, maxWidth = cw, spacing = 0.04f)
        text(c, "NEON ARCADE", cx, titleY + u * 6.5f, u * 3.4f, cPurple, fontBold, spacing = 0.5f)
        val best = scoreBook.best()
        text(c, "BEST  $best", cx, logoRect.bottom + u * 34f, u * 4.6f, cGold, fontBold, glow = 0x66FFD54A, glowR = u * 1.5f, spacing = 0.12f)

        for (b in menuButtons) drawButton(c, b)
        text(c, Brand.DEVELOPED_BY, cx, bot - u * 3f, u * 3.4f, cMuted, fontBody, spacing = 0.05f)
    }

    private fun renderTitle(c: Canvas, title: String, sub: String?, color: Int) {
        text(c, title, cx, top + u * 15f, u * 10f, cText, fontTitle, glow = color, glowR = u * 2.5f, maxWidth = cw)
        if (sub != null) text(c, sub, cx, top + u * 22f, u * 4f, color, fontBold, spacing = 0.35f)
    }

    private fun renderHowTo(c: Canvas) {
        drawBall(c, mbX, mbY, 1.3f, 0.25f)
        renderTitle(c, "HOW TO PLAY", null, cCyan)
        drawPanel(c, howCard, cCyan, 1f)
        val lineH = howTextSize * 1.32f
        val badgeR = u * 3.4f
        c.save()
        c.clipRect(howCard.left + dp, howCard.top + dp * 2f, howCard.right - dp, howCard.bottom - dp * 2f)
        c.translate(0f, howCard.top - howScroll)
        drawHowDemo(c, howDemo)
        text(c, "Slide to move  \u2022  Keep it bouncing  \u2022  Catch the heart",
            cx, howDemo.bottom + u * 7f, u * 3.3f, cMuted, fontBody, maxWidth = howCard.width() - u * 8f)
        val textX = howCard.left + u * 15f
        val bx = howCard.left + u * 8f
        for (row in howRows) {
            when (row.kind) {
                1 -> {
                    val col = if (row.label == "BONUS LIFE") cPink else cCyan
                    text(c, row.label, howCard.left + u * 6f, row.y, u * 5.4f, cText, fontTitle, Paint.Align.LEFT,
                        glow = col, glowR = u * 1.6f, spacing = 0.06f)
                    if (row.label == "BONUS LIFE") drawHeart(c, howCard.left + u * 6f + u * 37f, row.y - u * 1.8f, u * 2.4f, 1f, true)
                    fillPaint.shader = null
                    fillPaint.color = withAlpha(col, 0.5f)
                    c.drawRect(howCard.left + u * 6f, row.y + u * 2.4f, howCard.right - u * 6f, row.y + u * 2.4f + dp, fillPaint)
                }
                0 -> {
                    fillPaint.shader = null
                    fillPaint.color = cPurple
                    c.drawCircle(bx, row.y - howTextSize * 0.35f, badgeR, fillPaint)
                    text(c, row.label, bx, row.y - howTextSize * 0.35f + badgeR * 0.42f, badgeR * 1.2f, cWhite, fontBold)
                    var ly = row.y
                    for (line in row.lines) {
                        text(c, line, textX, ly, howTextSize, cText, fontBody, Paint.Align.LEFT); ly += lineH
                    }
                }
                else -> {
                    drawHeart(c, bx, row.y - howTextSize * 0.35f, u * 2.2f, 1f, false)
                    var ly = row.y
                    for (line in row.lines) {
                        text(c, line, textX, ly, howTextSize, 0xFFFFD6E3.toInt(), fontBody, Paint.Align.LEFT); ly += lineH
                    }
                }
            }
        }
        c.restore()
        // scroll bar + hint
        val view = howCard.height() - u * 2f
        if (howContentH > view) {
            val track = howCard.height() - u * 12f
            val thumb = max(u * 8f, track * view / howContentH)
            val maxScroll = howContentH - view
            val ty = howCard.top + u * 6f + (track - thumb) * (howScroll / maxScroll)
            fillPaint.shader = null
            fillPaint.color = withAlpha(cCyan, 0.55f)
            c.drawRoundRect(howCard.right - u * 2.6f, ty, howCard.right - u * 1.4f, ty + thumb, u, u, fillPaint)
            if (howScroll < maxScroll - u) {
                // fade the text out behind the hint so they never overlap
                val fadeTop = howCard.bottom - u * 11f
                fillPaint.shader = LinearGradient(0f, fadeTop, 0f, howCard.bottom - u * 1.5f,
                    0x000C1133, 0xF50C1133.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(howCard.left + dp * 2f, fadeTop, howCard.right - u * 3f, howCard.bottom - dp * 2f, fillPaint)
                fillPaint.shader = null
                val p = 0.55f + 0.45f * sin(time * 4f)
                text(c, "SCROLL FOR MORE \u25BE", cx, howCard.bottom - u * 2.5f, u * 3f, withAlpha(cCyan, p), fontBold, spacing = 0.12f)
            }
        }
        drawButton(c, btnBackHow)
    }

    /**
     * Small looping illustration: the paddle slides (with a finger under it), the ball bounces on it
     * and a bonus heart falls and is caught (+1).
     */
    private fun drawHowDemo(c: Canvas, r: RectF) {
        val rad = u * 4f
        fillPaint.shader = null
        fillPaint.color = withAlpha(0xFF070A22.toInt(), 0.9f)
        c.drawRoundRect(r, rad, rad, fillPaint)
        strokePaint.strokeWidth = dp
        strokePaint.color = withAlpha(cPurple, 0.5f)
        c.drawRoundRect(r, rad, rad, strokePaint)

        val cycle = 3.2f
        val n = (time / cycle).toInt()
        val p = (time % cycle) / cycle
        val leftX = r.left + r.width() * 0.25f
        val rightX = r.left + r.width() * 0.75f
        val fromX = if (n % 2 == 0) leftX else rightX
        val toX = if (n % 2 == 0) rightX else leftX
        val moveP = (p / 0.8f).coerceIn(0f, 1f)
        val ease = moveP * moveP * (3f - 2f * moveP)
        val px = fromX + (toX - fromX) * ease
        val padW = r.width() * 0.28f
        val padH = u * 1.8f
        val padY = r.bottom - u * 12f
        // paddle
        fillPaint.color = withAlpha(cPurple, 0.25f)
        c.drawRoundRect(px - padW / 2f - u, padY - u, px + padW / 2f + u, padY + padH + u, padH, padH, fillPaint)
        fillPaint.color = cCyan
        c.drawRoundRect(px - padW / 2f, padY, px + padW / 2f, padY + padH, padH / 2f, padH / 2f, fillPaint)
        // finger / thumb under the paddle with arrows
        fillPaint.color = withAlpha(cWhite, 0.30f)
        c.drawCircle(px, padY + u * 7f, u * 3f, fillPaint)
        fillPaint.color = withAlpha(cWhite, 0.55f)
        c.drawCircle(px, padY + u * 7f, u * 1.4f, fillPaint)
        text(c, "\u2039        \u203A", px, padY + u * 8.6f, u * 5f, withAlpha(cCyan, 0.8f), fontBold)
        // ball bouncing on the paddle
        val br = u * 2.3f
        val bounce = abs(sin(time * Math.PI.toFloat() / 0.55f))
        val by = padY - br - bounce * (r.height() * 0.42f)
        c.save(); c.translate(px, by)
        fillPaint.color = withAlpha(cCyan, 0.25f); c.drawCircle(0f, 0f, br * 2f, fillPaint)
        fillPaint.color = cWhite; c.drawCircle(0f, 0f, br, fillPaint)
        fillPaint.color = withAlpha(cCyan, 0.8f); c.drawCircle(br * 0.15f, br * 0.15f, br * 0.75f, fillPaint)
        c.restore()
        // falling bonus heart, caught at the end of the slide
        val hr = u * 2.6f
        if (p < 0.8f) {
            val hy = r.top + u * 5f + (padY - hr - (r.top + u * 5f)) * (p / 0.8f)
            drawHeart(c, toX, hy, hr * (1f + 0.1f * sin(time * 8f)), 1f, true)
        } else {
            val q = (p - 0.8f) / 0.2f
            text(c, "+1", toX, padY - u * 4f - q * u * 6f, u * 6f, withAlpha(cGold, 1f - q), fontTitle,
                glow = withAlpha(cPink, 1f - q), glowR = u * 2f)
        }
    }

    private fun renderLeaderboard(c: Canvas) {
        drawBall(c, mbX, mbY, 1.3f, 0.2f)
        text(c, "TOP 3", cx, top + u * 17f, u * 14f, cGold, fontTitle, glow = cGold, glowR = u * 3f)
        text(c, "HIGH SCORES", cx, top + u * 25f, u * 4.2f, cText, fontBold, spacing = 0.4f)
        val entries: List<ScoreEntry> = scoreBook.top()
        val medal = intArrayOf(cGold, cSilver, cBronze)
        val names = arrayOf("1ST", "2ND", "3RD")
        for (i in 0 until 3) {
            val r = lbCards[i]
            val e = entries.getOrNull(i)
            val col = medal[i]
            val pulse = if (e != null) 0.55f + 0.45f * (0.5f + 0.5f * sin(time * 2.5f + i)) else 0.25f
            drawPanel(c, r, col, if (e != null) 1f else 0.6f)
            strokePaint.strokeWidth = dp * 2f
            strokePaint.color = withAlpha(col, pulse)
            c.drawRoundRect(r, u * 6f, u * 6f, strokePaint)
            // medal
            val mr = r.height() * 0.30f
            val mx = r.left + r.height() * 0.5f
            val my = r.centerY()
            fillPaint.shader = RadialGradient(mx - mr * 0.3f, my - mr * 0.3f, mr * 1.4f,
                intArrayOf(cWhite, col, darker(col)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            fillPaint.alpha = if (e != null) 255 else 90
            c.drawCircle(mx, my, mr, fillPaint)
            fillPaint.shader = null
            text(c, (i + 1).toString(), mx, my + mr * 0.42f, mr * 1.2f, if (e != null) 0xFF2A1A00.toInt() else 0x88FFFFFF.toInt(), fontTitle)
            val tx = mx + mr + u * 5f
            text(c, names[i], tx, r.top + r.height() * 0.44f, r.height() * 0.2f, withAlpha(col, if (e != null) 1f else 0.5f), fontTitle, Paint.Align.LEFT, spacing = 0.1f)
            if (e != null) {
                text(c, dateFormat.format(Date(e.timeMillis)), tx, r.top + r.height() * 0.72f, r.height() * 0.13f, cMuted, fontBody, Paint.Align.LEFT)
                text(c, e.score.toString(), r.right - u * 6f, r.centerY() + r.height() * 0.16f, r.height() * 0.42f, cWhite, fontTitle, Paint.Align.RIGHT, glow = col, glowR = u * 2f)
            } else {
                text(c, "Empty - play to claim it", tx, r.top + r.height() * 0.72f, r.height() * 0.13f, withAlpha(cMuted, 0.7f), fontBody, Paint.Align.LEFT)
                text(c, "---", r.right - u * 6f, r.centerY() + r.height() * 0.12f, r.height() * 0.3f, withAlpha(cMuted, 0.6f), fontTitle, Paint.Align.RIGHT)
            }
        }
        drawButton(c, btnBackTop)
    }

    private fun renderAbout(c: Canvas) {
        drawLogo(c, logoAboutRect)
        var y = logoAboutRect.bottom + u * 11f
        text(c, Brand.FULL_TITLE, cx, y, u * 5.6f, cText, fontTitle, glow = cCyan, glowR = u * 1.5f, maxWidth = cw)
        y += u * 7f
        text(c, Brand.DEVELOPED_BY, cx, y, u * 4.2f, cMuted, fontBody)
        y += u * 5f
        fillPaint.shader = LinearGradient(cx - cw * 0.35f, 0f, cx + cw * 0.35f, 0f,
            intArrayOf(0x003D7BFF, cCyan, 0x003D7BFF), null, Shader.TileMode.CLAMP)
        c.drawRect(cx - cw * 0.35f, y, cx + cw * 0.35f, y + dp, fillPaint)
        fillPaint.shader = null

        drawInfoRow(c, emailRow, "EMAIL", Brand.EMAIL)
        drawInfoRow(c, phoneRow, "MOBILE", Brand.PHONE)
        val info = phoneRow.bottom + u * 6f
        text(c, "Version ${BuildConfig.VERSION_NAME}", cx, info + u * 2f, u * 3.8f, cText, fontBold, spacing = 0.08f)
        text(c, Brand.COPYRIGHT, cx, info + u * 8f, u * 3.2f, cMuted, fontBody, maxWidth = cw)
        drawButton(c, btnBackAbout)
    }

    /** Contact details shown as plain information - tapping does nothing. */
    private fun drawInfoRow(c: Canvas, r: RectF, label: String, value: String) {
        val lr = r.height() * 0.28f
        fillPaint.shader = null
        fillPaint.color = withAlpha(0xFF101640.toInt(), 0.95f)
        c.drawRoundRect(r, lr, lr, fillPaint)
        strokePaint.strokeWidth = dp * 1.2f
        strokePaint.color = withAlpha(cBlue, 0.6f)
        c.drawRoundRect(r, lr, lr, strokePaint)
        val pad = r.height() * 0.32f
        text(c, label, r.left + pad, r.top + r.height() * 0.38f, r.height() * 0.20f, cMuted, fontBold, Paint.Align.LEFT, spacing = 0.15f)
        text(c, value, r.left + pad, r.top + r.height() * 0.76f, r.height() * 0.30f, cCyan, fontBold, Paint.Align.LEFT, maxWidth = r.width() - pad * 2f)
    }

    private val tmpPanelRect = RectF()
    private fun tmpPanel(r: RectF, pad: Float): RectF {
        tmpPanelRect.set(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
        return tmpPanelRect
    }

    private fun renderPlaying(c: Canvas) {
        val showBall = screen != Screen.GAME_OVER
        drawPaddle(c)
        if (showBall) {
            drawTrail(c, 1f, 1f)
            drawBall(c, world.ballX, world.ballY, 1f, 1f)
        }
        if (screen != Screen.GAME_OVER) drawBonuses(c)
        particles.draw(c, particlePaint)
        if (lifeFlash > 0f) {
            fillPaint.shader = null
            fillPaint.color = withAlpha(cPink, 0.28f * lifeFlash)
            c.drawRect(0f, 0f, w, h, fillPaint)
        }

        // HUD
        val s = 1f + 0.3f * scorePop
        c.save()
        c.scale(s, s, cx, top + u * 11f)
        text(c, world.score.toString(), cx, top + u * 16f, u * 15f, cWhite, fontTitle, glow = cCyan, glowR = u * 3f)
        c.restore()
        text(c, "BEST ${max(scoreBook.best(), world.score)}", u * 5f, top + u * 7f, u * 3.6f, cGold, fontBold, Paint.Align.LEFT, spacing = 0.1f)
        text(c, "LEVEL ${world.level}", u * 5f, top + u * 12.5f, u * 3.6f, cCyan, fontBold, Paint.Align.LEFT, spacing = 0.1f)
        text(c, "x" + String.format(Locale.US, "%.2f", world.speedMul), u * 5f, top + u * 18f, u * 3f, cMuted, fontBody, Paint.Align.LEFT)
        drawLives(c)

        if (screen == Screen.PLAYING && world.started && !world.over) drawPauseIcon(c)

        if (screen == Screen.PLAYING && !world.started) {
            val p = 0.6f + 0.4f * (0.5f + 0.5f * sin(time * 4f))
            text(c, "TOUCH TO START", cx, h * 0.52f, u * 7f, withAlpha(cText, p), fontTitle, glow = cPurple, glowR = u * 2f, maxWidth = cw)
            text(c, "Slide your finger to move the paddle", cx, h * 0.52f + u * 8f, u * 3.8f, cMuted, fontBody, maxWidth = cw)
            text(c, "BONUS LIFE EVERY 10 HITS \u2013 CATCH THE HEART!", cx, h * 0.52f + u * 15f, u * 3.8f, cPink, fontBold, spacing = 0.06f, maxWidth = cw)
        }
        if (toastT > 0f && screen == Screen.PLAYING) {
            val a = min(1f, toastT / 0.4f)
            val sc = 1f + 0.15f * (1f - min(1f, (1.6f - toastT) / 0.25f))
            c.save()
            c.scale(sc, sc, cx, h * 0.36f)
            if (toastText.isNotEmpty()) text(c, toastText, cx, h * 0.36f, u * 6.5f, withAlpha(cWhite, a), fontTitle, glow = withAlpha(cPurple, a), glowR = u * 3f, maxWidth = cw)
            c.restore()
            if (toastSub.isNotEmpty()) text(c, toastSub, cx, h * 0.36f + u * 7f, u * 3.8f, withAlpha(0xFFFFB3CB.toInt(), a), fontBold, glow = withAlpha(cPink, a), glowR = u * 1.5f, maxWidth = cw)
        }
        if (bonusMsgT > 0f && screen == Screen.PLAYING) {
            val a = min(1f, bonusMsgT / 0.5f)
            val rise = (1.8f - bonusMsgT) * u * 6f
            val sc = 1f + 0.25f * max(0f, 1f - (1.8f - bonusMsgT) / 0.3f)
            c.save()
            c.scale(sc, sc, cx, h * 0.46f - rise)
            text(c, "BONUS LIFE +1", cx, h * 0.46f - rise, u * 8f, withAlpha(cGold, a), fontTitle, glow = withAlpha(cPink, a), glowR = u * 3f, maxWidth = cw)
            c.restore()
        }
        if (screen == Screen.PLAYING && world.relaunchTimer > 0f) {
            val left = world.lives
            text(c, "LIFE LOST", cx, h * 0.50f, u * 8f, cPink, fontTitle, glow = cPink, glowR = u * 3f, maxWidth = cw)
            text(c, if (left == 1) "1 LIFE LEFT \u2013 GET READY" else "$left LIVES LEFT \u2013 GET READY", cx, h * 0.50f + u * 8f, u * 4.2f, cText, fontBold, spacing = 0.1f, maxWidth = cw)
        }
        if (resumeCountdown > 0f && screen == Screen.PLAYING) {
            val n = ceil(resumeCountdown).toInt()
            val frac = resumeCountdown - (n - 1)
            text(c, n.toString(), cx, h * 0.48f, u * (22f + 10f * frac), withAlpha(cWhite, frac.coerceIn(0.3f, 1f)), fontTitle, glow = cCyan, glowR = u * 4f)
            text(c, "GET READY", cx, h * 0.48f + u * 10f, u * 4.5f, cCyan, fontBold, spacing = 0.3f)
        }
    }

    private fun drawHeart(c: Canvas, x: Float, y: Float, r: Float, alpha: Float, glow: Boolean) {
        c.save()
        c.translate(x, y)
        c.scale(r, r)
        if (glow) {
            heartGlowPaint.alpha = (alpha * 255).toInt()
            c.drawCircle(0f, 0f, 2.6f, heartGlowPaint)
        }
        heartPaint.alpha = (alpha * 255).toInt()
        c.drawPath(heartPath, heartPaint)
        c.restore()
    }

    /** Falling bonus-life hearts: glow, pulse, orbiting sparkles and a "+1" tag. */
    private fun drawBonuses(c: Canvas) {
        val br = world.bonusR
        for (b in world.bonuses) {
            val pulse = 1f + 0.12f * sin(b.age * 7f)
            // falling streak
            fillPaint.shader = null
            for (k in 1..5) {
                fillPaint.color = withAlpha(cPink, 0.10f * (6 - k) / 5f)
                c.drawCircle(b.x, b.y - k * br * 0.55f, br * (0.75f - k * 0.1f), fillPaint)
            }
            drawHeart(c, b.x, b.y, br * pulse, 1f, true)
            for (k in 0 until 6) {
                val a = b.age * 2.4f + k * 1.047f
                fillPaint.color = withAlpha(if (k % 2 == 0) cGold else cWhite, 0.85f)
                c.drawCircle(b.x + cos(a) * br * 1.9f, b.y + sin(a) * br * 1.9f, br * 0.12f, fillPaint)
            }
            text(c, "+1", b.x + br * 1.6f, b.y - br * 1.2f, br * 0.9f, cGold, fontTitle, Paint.Align.LEFT, glow = cPink, glowR = br * 0.4f)
        }
    }

    /** Life indicator under the score: one heart per remaining life. */
    private fun drawLives(c: Canvas) {
        val n = world.lives
        if (n <= 0) return
        val hr = u * 2.6f
        val y = top + u * 24f
        val pop = 1f + 0.35f * livesPop
        if (n <= 6) {
            val gap = hr * 2.7f
            var x = cx - (n - 1) * gap / 2f
            for (i in 0 until n) {
                val sc = if (i == n - 1) pop else 1f
                drawHeart(c, x, y, hr * sc, 1f, i == n - 1 && livesPop > 0f)
                x += gap
            }
        } else {
            drawHeart(c, cx - hr * 1.6f, y, hr * pop, 1f, livesPop > 0f)
            text(c, "x $n", cx - hr * 0.2f, y + hr * 0.6f, hr * 1.7f, cText, fontTitle, Paint.Align.LEFT)
        }
    }

    private fun drawPauseIcon(c: Canvas) {
        val r = btnPause.rect
        val pr = r.width() / 2f
        fillPaint.shader = null
        fillPaint.color = withAlpha(0xFF111845.toInt(), if (pressed === btnPause) 1f else 0.75f)
        c.drawCircle(r.centerX(), r.centerY(), pr, fillPaint)
        strokePaint.strokeWidth = dp * 1.5f
        strokePaint.color = withAlpha(cCyan, 0.8f)
        c.drawCircle(r.centerX(), r.centerY(), pr, strokePaint)
        fillPaint.color = cText
        val bw = pr * 0.2f
        val bh = pr * 0.8f
        c.drawRoundRect(r.centerX() - bw * 1.7f, r.centerY() - bh / 2f, r.centerX() - bw * 0.7f, r.centerY() + bh / 2f, bw / 3f, bw / 3f, fillPaint)
        c.drawRoundRect(r.centerX() + bw * 0.7f, r.centerY() - bh / 2f, r.centerX() + bw * 1.7f, r.centerY() + bh / 2f, bw / 3f, bw / 3f, fillPaint)
    }

    private fun renderPause(c: Canvas) {
        c.drawRect(0f, 0f, w, h, dimPaint)
        drawPanel(c, panelRect, cCyan, 1f)
        text(c, "PAUSED", cx, panelRect.top + u * 17f, u * 11f, cText, fontTitle, glow = cCyan, glowR = u * 2.5f)
        text(c, "SCORE  ${world.score}", cx, panelRect.top + u * 28f, u * 5f, cGold, fontBold, spacing = 0.1f)
        text(c, "LIVES  ${world.lives}", cx, panelRect.top + u * 36f, u * 4.2f, cPink, fontBold, spacing = 0.1f)
        text(c, "A 3-second countdown follows Resume.", cx, panelRect.top + u * 42.5f, u * 3.6f, cMuted, fontBody)
        for (b in pauseButtons) drawButton(c, b)
    }

    private fun renderGameOver(c: Canvas) {
        val res = lastResult ?: return
        val a = min(1f, gameOverT / 0.3f)
        // red flash right after the miss
        if (gameOverT < 0.4f) {
            fillPaint.shader = null
            fillPaint.color = withAlpha(cPink, 0.35f * (1f - gameOverT / 0.4f))
            c.drawRect(0f, 0f, w, h, fillPaint)
        }
        dimPaint.alpha = (0xB3 * a).toInt()
        c.drawRect(0f, 0f, w, h, dimPaint)
        dimPaint.alpha = 0xB3

        val slide = (1f - a) * u * 10f
        c.save()
        c.translate(0f, slide)
        val edge = if (res.newBest) cGold else cPink
        drawPanel(c, overPanel, edge, a)
        val p = overPanel
        var y = p.top + u * 16f
        text(c, "GAME OVER", cx, y, u * 11f, withAlpha(cText, a), fontTitle, glow = withAlpha(cPink, a), glowR = u * 3f, maxWidth = p.width() * 0.9f)
        y += u * 10f
        text(c, "FINAL SCORE", cx, y, u * 3.6f, withAlpha(cMuted, a), fontBold, spacing = 0.35f)
        y += u * 19f
        text(c, res.score.toString(), cx, y, u * 20f, withAlpha(cWhite, a), fontTitle, glow = withAlpha(cCyan, a), glowR = u * 3f)
        y += u * 9f
        text(c, "PERSONAL BEST  ${res.best}", cx, y, u * 4.4f, withAlpha(cGold, a), fontBold, spacing = 0.1f)
        y += u * 10f
        if (res.newBest) {
            val pulse = 1f + 0.07f * sin(time * 7f)
            c.save()
            c.scale(pulse, pulse, cx, y - u * 2f)
            text(c, "NEW HIGH SCORE!", cx, y, u * 6.6f, withAlpha(cGold, a), fontTitle, glow = withAlpha(cGold, a), glowR = u * 3f, maxWidth = p.width() * 0.88f)
            c.restore()
            text(c, "#1 on your TOP 3 leaderboard", cx, y + u * 6.5f, u * 3.6f, withAlpha(cText, a), fontBody)
        } else if (res.rank in 1..3) {
            val col = when (res.rank) { 1 -> cGold; 2 -> cSilver; else -> cBronze }
            val label = when (res.rank) { 1 -> "1ST"; 2 -> "2ND"; else -> "3RD" }
            text(c, "TOP 3 ACHIEVED – $label PLACE!", cx, y, u * 5f, withAlpha(col, a), fontTitle, glow = withAlpha(col, a), glowR = u * 2f, maxWidth = p.width() * 0.88f)
            text(c, "Your score is saved on the leaderboard", cx, y + u * 6.5f, u * 3.6f, withAlpha(cText, a), fontBody)
        } else {
            val need = scoreBook.top().let { if (it.size < 3) 1 else it[2].score + 1 }
            text(c, "Score $need to enter the TOP 3", cx, y, u * 4f, withAlpha(cMuted, a), fontBody)
        }
        val enabled = overButtonsEnabled()
        for (b in overButtons) drawButton(c, b, enabled)
        c.restore()
    }

    // ================================================================ helpers

    private fun withAlpha(color: Int, a: Float): Int =
        (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * ((color ushr 24) and 0xFF)).toInt() shl 24)

    private fun darker(color: Int): Int {
        val r = ((color shr 16) and 0xFF) * 6 / 10
        val g = ((color shr 8) and 0xFF) * 6 / 10
        val b = (color and 0xFF) * 6 / 10
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun wrap(s: String, paint: Paint, maxW: Float): List<String> {
        val out = ArrayList<String>()
        var cur = ""
        for (word in s.split(' ')) {
            val t = if (cur.isEmpty()) word else "$cur $word"
            if (cur.isEmpty() || paint.measureText(t) <= maxW) cur = t else {
                out.add(cur); cur = word
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }
}
