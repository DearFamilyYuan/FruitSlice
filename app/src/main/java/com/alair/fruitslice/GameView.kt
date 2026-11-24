package com.alair.fruitslice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fruit slicing game.
 *
 * Design goals for kids under 5:
 *  - no bombs, no lives, no losing: nothing can "go wrong"
 *  - huge fruits, slicing happens on any drag OR a simple tap on a fruit
 *  - every hit gives juice + sound + vibration, so random mashing still feels good
 *  - a 10-minute session timer ends play on its own; MainActivity loops a
 *    gentle voice reminder afterward (screen-time management for parents)
 */
class GameView(context: Context) : View(context), Choreographer.FrameCallback {

    private class FruitType(
        val body: Int, val flesh: Int, val rind: Int, val leaf: Int,
        val sx: Float = 1f,          // horizontal stretch of the silhouette
        val sy: Float = 1f,          // vertical stretch of the silhouette
        val sizeMul: Float = 1f,     // radius multiplier (cherries small, melons big)
        val bodySeeds: Int = 0,      // seed dots drawn on the skin
        val fleshSeeds: Int = 0,     // seed dots drawn on the cut flesh
        val pattern: Int = 0,        // 1 = pineapple cross-hatch
        val shape: Int = 0,          // 0 = oval (stretched circle), 1 = banana crescent
        val pit: Boolean = false     // brown pit drawn on one cut half (avocado)
    )

    private class Fruit(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val r: Float, val type: FruitType,
        var rot: Float, var rotSpeed: Float
    )

    private class Half(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val r: Float, val type: FruitType,
        var rot: Float, var rotSpeed: Float,
        val top: Boolean, var life: Float
    )

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val size: Float, val color: Int, var life: Float, val maxLife: Float
    )

    private class Trail(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        var life: Float, val maxLife: Float
    )

    private val density = resources.displayMetrics.density

    private val types = listOf(
        // apple
        FruitType(0xFFE53935.toInt(), 0xFFFFEBEE.toInt(), 0xFFB71C1C.toInt(), 0xFF43A047.toInt()),
        // orange
        FruitType(0xFFFF9800.toInt(), 0xFFFFF3E0.toInt(), 0xFFE65100.toInt(), 0xFF43A047.toInt()),
        // watermelon: big, green skin, red flesh, black seeds
        FruitType(0xFF43A047.toInt(), 0xFFEF5350.toInt(), 0xFF1B5E20.toInt(), 0xFF2E7D32.toInt(),
            sizeMul = 1.15f, fleshSeeds = 7),
        // lemon: oval
        FruitType(0xFFFDD835.toInt(), 0xFFFFFDE7.toInt(), 0xFFF9A825.toInt(), 0xFF43A047.toInt(),
            sx = 1.25f, sy = 0.95f),
        // plum: small
        FruitType(0xFF8E24AA.toInt(), 0xFFF3E5F5.toInt(), 0xFF4A148C.toInt(), 0xFF43A047.toInt(),
            sizeMul = 0.85f),
        // strawberry: slightly tall, yellow seeds on skin
        FruitType(0xFFF44336.toInt(), 0xFFFFCDD2.toInt(), 0xFFC62828.toInt(), 0xFF43A047.toInt(),
            sx = 0.95f, sy = 1.15f, sizeMul = 0.9f, bodySeeds = 10),
        // grape: small round
        FruitType(0xFF7B1FA2.toInt(), 0xFFE1BEE7.toInt(), 0xFF4A148C.toInt(), 0xFF43A047.toInt(),
            sizeMul = 0.7f),
        // peach
        FruitType(0xFFFF8A65.toInt(), 0xFFFFE0B2.toInt(), 0xFFE64A19.toInt(), 0xFF43A047.toInt()),
        // pear: tall
        FruitType(0xFFC0CA33.toInt(), 0xFFF1F8E9.toInt(), 0xFF9E9D24.toInt(), 0xFF43A047.toInt(),
            sx = 0.95f, sy = 1.22f),
        // mango: oval
        FruitType(0xFFFFB300.toInt(), 0xFFFFE082.toInt(), 0xFFF57F17.toInt(), 0xFF43A047.toInt(),
            sx = 1.22f, sy = 0.92f),
        // kiwi: brown skin, green flesh with seeds
        FruitType(0xFF8D6E63.toInt(), 0xFFAED581.toInt(), 0xFF5D4037.toInt(), 0xFF43A047.toInt(),
            sizeMul = 0.85f, fleshSeeds = 9),
        // cherry: very small
        FruitType(0xFFD32F2F.toInt(), 0xFFFFCDD2.toInt(), 0xFFB71C1C.toInt(), 0xFF43A047.toInt(),
            sizeMul = 0.6f),
        // blueberry: tiny
        FruitType(0xFF3949AB.toInt(), 0xFFC5CAE9.toInt(), 0xFF1A237E.toInt(), 0xFF43A047.toInt(),
            sizeMul = 0.55f),
        // pineapple: tall, cross-hatch skin
        FruitType(0xFFFDD835.toInt(), 0xFFFFF59D.toInt(), 0xFFF9A825.toInt(), 0xFF2E7D32.toInt(),
            sx = 0.95f, sy = 1.25f, sizeMul = 1.1f, pattern = 1),
        // banana: crescent silhouette
        FruitType(0xFFFFD54F.toInt(), 0xFFFFF8E1.toInt(), 0xFFF9A825.toInt(), 0xFF43A047.toInt(),
            sizeMul = 1.1f, shape = 1),
        // avocado: dark green skin, light green flesh, brown pit on one half
        FruitType(0xFF558B2F.toInt(), 0xFFCCFF90.toInt(), 0xFF33691E.toInt(), 0xFF43A047.toInt(),
            sx = 1.05f, sy = 1.2f, sizeMul = 1.05f, pit = true),
        // passion fruit: dark purple skin, yellow flesh packed with seeds
        FruitType(0xFF6A1B9A.toInt(), 0xFFFFD54F.toInt(), 0xFF4A148C.toInt(), 0xFF43A047.toInt(),
            sizeMul = 0.9f, fleshSeeds = 14),
        // pomelo: big pale green skin, pink flesh
        FruitType(0xFFE6EE9C.toInt(), 0xFFFF8A80.toInt(), 0xFFC0CA33.toInt(), 0xFF43A047.toInt(),
            sizeMul = 1.3f, fleshSeeds = 8)
    )

    private val fruits = mutableListOf<Fruit>()
    private val halves = mutableListOf<Half>()
    private val particles = mutableListOf<Particle>()
    private val trails = mutableListOf<Trail>()
    private val pointerLast = HashMap<Int, FloatArray>()

    private var score = 0
    private var spawnTimer = 0f
    private var running = false
    private var paused = false
    private var lastFrame = 0L

    // play-time budget for this launch; only ticks while actually playing
    private var remainingSec = TOTAL_PLAY_SEC
    private var timeUp = false

    /** Invoked exactly once when the 10 minutes run out (main thread). */
    var onTimeUp: (() -> Unit)? = null

    /** True once the play budget is exhausted (screen is frozen on the notice). */
    val isTimeUp: Boolean
        get() = timeUp

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0x44000000
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f * density
        setShadowLayer(6f * density, 0f, 3f * density, 0x66000000)
    }
    private val timerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B5E20.toInt()
        textSize = 64f * density
        textAlign = Paint.Align.RIGHT
        setShadowLayer(6f * density, 0f, 3f * density, 0x44000000)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDDFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 44f * density
        setShadowLayer(4f * density, 0f, 2f * density, 0x66000000)
    }
    private var bgPaint = Paint()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private var popSoundId = 0
    private var cheerSoundId = 0
    private var soundLoaded = false

    init {
        // Long-press must be swallowed: no context menu, no text selection popups.
        isLongClickable = true
        setOnLongClickListener { true }
        // Tell the framework we handle touch so taps never fall through.
        isClickable = true
        popSoundId = soundPool.load(context, R.raw.pop, 1)
        cheerSoundId = soundPool.load(context, R.raw.cheer, 1)
        soundPool.setOnLoadCompleteListener { _, _, status -> soundLoaded = status == 0 }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    fun onPauseGame() {
        paused = true
    }

    fun onResumeGame() {
        if (!isAttachedToWindow) return
        paused = false
        lastFrame = 0L
    }

    // ---------------------------------------------------------------- game loop

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastFrame == 0L) lastFrame = frameTimeNanos
        var dt = (frameTimeNanos - lastFrame) / 1_000_000_000f
        lastFrame = frameTimeNanos
        if (dt > 0.05f) dt = 0.05f // clamp so a stall never teleports fruits
        if (dt < 0f) dt = 0f
        if (!paused && !timeUp) update(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun update(dt: Float) {
        // count down first: stops exactly at 0, then flips into the time-up state
        remainingSec -= dt
        if (remainingSec <= 0f) {
            remainingSec = 0f
            if (!timeUp) {
                timeUp = true
                post { onTimeUp?.invoke() }
            }
            return
        }

        val h = height.toFloat()
        val w = width.toFloat()
        if (w <= 0f || h <= 0f) return
        val gravity = h * 3f

        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnBatch(w, h, gravity)
            spawnTimer = 0.7f + Random.nextFloat() * 0.6f
        }

        val it = fruits.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.vy += gravity * dt
            f.x += f.vx * dt
            f.y += f.vy * dt
            f.rot += f.rotSpeed * dt
            if (f.y - f.r > h && f.vy > 0f) it.remove()
        }

        val hit = halves.iterator()
        while (hit.hasNext()) {
            val m = hit.next()
            m.vy += gravity * dt
            m.x += m.vx * dt
            m.y += m.vy * dt
            m.rot += m.rotSpeed * dt
            m.life -= dt
            if (m.life <= 0f || m.y - m.r > h * 1.3f) hit.remove()
        }

        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.vy += gravity * 1.4f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            if (p.life <= 0f) pit.remove()
        }

        val tit = trails.iterator()
        while (tit.hasNext()) {
            val t = tit.next()
            t.life -= dt
            if (t.life <= 0f) tit.remove()
        }

        // Fruit drifting into a still-visible slice trail gets cut too.
        for (t in trails) {
            sliceSegment(t.x1, t.y1, t.x2, t.y2)
        }
    }

    private fun spawnBatch(w: Float, h: Float, gravity: Float) {
        val count = 1 + Random.nextInt(3)
        repeat(count) {
            if (fruits.size > 8) return@repeat
            val base = minOf(w, h)
            val type = types[Random.nextInt(types.size)]
            val r = base * (0.11f + Random.nextFloat() * 0.04f) * type.sizeMul
            val x = w * (0.15f + Random.nextFloat() * 0.7f)
            val peak = h * (0.45f + Random.nextFloat() * 0.28f)
            val vy = -sqrt(2f * gravity * peak)
            val vx = (Random.nextFloat() - 0.5f) * w * 0.45f
            fruits += Fruit(
                x = x, y = h + r * 1.5f, vx = vx, vy = vy, r = r,
                type = type,
                rot = Random.nextFloat() * 360f,
                rotSpeed = (Random.nextFloat() - 0.5f) * 240f
            )
        }
    }

    // ---------------------------------------------------------------- slicing

    private fun sliceSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
        val it = fruits.iterator()
        while (it.hasNext()) {
            val f = it.next()
            // stretched silhouettes (lemon / pear / mango) get a generous round hit box;
            // the banana body reaches out to ~1.35r (thick end), so use a bigger box
            val hitR = if (f.type.shape == 1) f.r * 1.3f
            else f.r * ((f.type.sx + f.type.sy) * 0.5f)
            if (segCircle(x1, y1, x2, y2, f.x, f.y, hitR)) {
                it.remove()
                sliceFruit(f)
            }
        }
    }

    private fun sliceFruit(f: Fruit) {
        score++
        // oval fruit: flip the lower half 180° so the pieces read as top/bottom.
        // banana: keep the same rotation, each piece draws its own arc range.
        val lowerFlip = if (f.type.shape == 1) 0f else 180f
        halves += Half(
            f.x, f.y - f.r * 0.1f, f.vx - 90f * density, f.vy - 40f * density,
            f.r, f.type, f.rot, f.rotSpeed * 0.7f, top = true, life = 1.6f
        )
        halves += Half(
            f.x, f.y + f.r * 0.1f, f.vx + 90f * density, f.vy + 20f * density,
            f.r, f.type, f.rot + lowerFlip, -f.rotSpeed * 0.7f, top = false, life = 1.6f
        )
        // juice splash
        repeat(16) {
            val ang = Random.nextFloat() * (Math.PI * 2).toFloat()
            val spd = (0.6f + Random.nextFloat() * 1.4f) * f.r * 4f
            particles += Particle(
                x = f.x, y = f.y,
                vx = kotlin.math.cos(ang) * spd,
                vy = kotlin.math.sin(ang) * spd,
                size = f.r * (0.07f + Random.nextFloat() * 0.10f),
                color = f.type.flesh,
                life = 0.7f + Random.nextFloat() * 0.5f,
                maxLife = 1.2f
            )
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (soundLoaded) {
            soundPool.play(popSoundId, 0.9f, 0.9f, 1, 0, 0.9f + Random.nextFloat() * 0.3f)
        }
        if (score % 10 == 0) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            if (soundLoaded) soundPool.play(cheerSoundId, 0.8f, 0.8f, 1, 0, 1f)
        }    }

    private fun segCircle(
        x1: Float, y1: Float, x2: Float, y2: Float,
        cx: Float, cy: Float, r: Float
    ): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        var t = if (len2 <= 0f) 0f else ((cx - x1) * dx + (cy - y1) * dy) / len2
        if (t < 0f) t = 0f else if (t > 1f) t = 1f
        val px = x1 + t * dx
        val py = y1 + t * dy
        return hypot(cx - px, cy - py) <= r
    }

    // ---------------------------------------------------------------- touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val x = event.getX(i)
                val y = event.getY(i)
                pointerLast[id] = floatArrayOf(x, y)
                // A bare tap on a fruit counts as a slice (easiest for small kids).
                sliceSegment(x, y, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val nx = event.getX(i)
                    val ny = event.getY(i)
                    val last = pointerLast[id]
                    if (last == null) {
                        pointerLast[id] = floatArrayOf(nx, ny)
                        continue
                    }
                    val moved = hypot(nx - last[0], ny - last[1])
                    if (moved > 2f * density) {
                        trails += Trail(last[0], last[1], nx, ny, TRAIL_LIFE, TRAIL_LIFE)
                        sliceSegment(last[0], last[1], nx, ny)
                        last[0] = nx
                        last[1] = ny
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerLast.remove(event.getPointerId(event.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> pointerLast.clear()
        }
        return true
    }

    // ---------------------------------------------------------------- rendering

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        drawBackground(canvas, w, h)

        for (f in fruits) drawFruit(canvas, f)
        for (m in halves) drawHalf(canvas, m)
        drawParticles(canvas)
        drawTrails(canvas)
        drawScore(canvas, w)
        drawTimer(canvas, w)

        if (paused) {
            canvas.drawColor(0x55000000)
        }
        if (timeUp) {
            canvas.drawColor(0x99000000)
            canvas.drawText("Time's Up!", w / 2f, h / 2f, hintPaint)
        }
    }

    /** Countdown in the top-right corner; turns red and blinks near the end. */
    private fun drawTimer(canvas: Canvas, w: Int) {
        val total = remainingSec.coerceAtLeast(0f).toInt()
        val text = String.format(Locale.US, "%d:%02d", total / 60, total % 60)
        timerPaint.color = when {
            remainingSec <= 60f ->
                if ((remainingSec * 2).toInt() % 2 == 0) 0xFFD32F2F.toInt() else 0x33D32F2F
            remainingSec <= 300f -> 0xFFF57F17.toInt()
            else -> 0xFF1B5E20.toInt()
        }
        val pad = 24f * density
        canvas.drawText(text, w - pad, pad + timerPaint.textSize, timerPaint)
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int) {
        val shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            resources.getColor(R.color.sky_top), resources.getColor(R.color.sky_bottom),
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        bgPaint.shader = null
    }

    private fun drawFruit(canvas: Canvas, f: Fruit) {
        val t = f.type
        canvas.save()
        canvas.translate(f.x, f.y)
        canvas.rotate(f.rot)
        canvas.scale(t.sx, t.sy)

        if (t.shape == 1) {
            // banana: tapered silhouette, thick stalk end + pointed tail
            drawBananaShape(canvas, f.r, BANANA_STALK_A, BANANA_A1, t, showFlesh = false)
            canvas.restore()
            return
        }

        fillPaint.shader = android.graphics.RadialGradient(
            -f.r * 0.3f, -f.r * 0.35f, f.r * 1.35f,
            lighten(t.body), t.body, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, f.r, fillPaint)
        fillPaint.shader = null

        if (t.pattern == 1) {
            // clip to the skin so the hatch never spills outside the fruit
            canvas.save()
            val clipPath = Path()
            clipPath.addCircle(0f, 0f, f.r * 0.97f, Path.Direction.CW)
            canvas.clipPath(clipPath)
            drawCrossHatch(canvas, f.r, t.rind)
            canvas.restore()
        }

        // highlight
        fillPaint.color = 0x55FFFFFF
        canvas.drawOval(
            RectF(-f.r * 0.55f, -f.r * 0.65f, -f.r * 0.05f, -f.r * 0.30f), fillPaint
        )
        // seeds on the skin (strawberry)
        if (t.bodySeeds > 0) {
            fillPaint.color = 0xFFF7E26B.toInt()
            val rad = f.r * 0.78f
            for (i in 0 until t.bodySeeds) {
                val ang = i * 2f * Math.PI.toFloat() / t.bodySeeds
                canvas.drawCircle(
                    kotlin.math.cos(ang) * rad, kotlin.math.sin(ang) * rad,
                    f.r * 0.055f, fillPaint
                )
            }
        }
        // leaf
        fillPaint.color = t.leaf
        canvas.save()
        canvas.rotate(-30f, 0f, -f.r)
        canvas.drawOval(
            RectF(-f.r * 0.10f, -f.r * 1.20f, f.r * 0.45f, -f.r * 0.92f), fillPaint
        )
        canvas.restore()
        // stem
        fillPaint.color = 0xFF6D4C41.toInt()
        canvas.drawRect(
            -f.r * 0.05f, -f.r * 1.10f, f.r * 0.05f, -f.r * 0.75f, fillPaint
        )
        canvas.drawCircle(0f, 0f, f.r, strokePaint)
        canvas.restore()
    }

    /**
     * Banana silhouette built as a filled path instead of a uniform stroke,
     * so the shape tapers: the head end is thick, the tail end narrows to a tip.
     * The stalk is part of the same path - it continues the arc with a thin
     * wedge and a blunt cut, instead of a separate straight stick.
     */
    private fun bananaWidthAt(angle: Float): Float {
        // returns half width in units of r for a given arc angle
        return if (angle >= BANANA_A0) {
            val t = (angle - BANANA_A0) / (BANANA_A1 - BANANA_A0)
            0.055f + 0.30f * Math.pow((1f - t).toDouble(), 0.6).toFloat()
        } else {
            // stalk: 0 at the cut end, 1 where it meets the shoulder (width matches body)
            val u = (angle - BANANA_STALK_A) / (BANANA_A0 - BANANA_STALK_A)
            0.11f + 0.245f * u * u
        }
    }

    private fun drawBananaShape(
        canvas: Canvas, r: Float, from: Float, to: Float,
        type: FruitType, showFlesh: Boolean
    ) {
        val steps = 20
        val path = Path()
        fun point(angle: Float, radius: Float): Pair<Float, Float> {
            val rad = Math.toRadians(angle.toDouble())
            return (Math.cos(rad) * radius).toFloat() to (Math.sin(rad) * radius).toFloat()
        }
        // outer edge, start -> end
        for (i in 0..steps) {
            val a = from + (to - from) * i / steps
            val (px, py) = point(a, r + bananaWidthAt(a) * r)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        // inner edge, end -> start
        for (i in steps downTo 0) {
            val a = from + (to - from) * i / steps
            val (px, py) = point(a, r - bananaWidthAt(a) * r)
            path.lineTo(px, py)
        }
        path.close()

        fillPaint.shader = null
        fillPaint.color = type.body
        canvas.drawPath(path, fillPaint)
        arcPaint.strokeWidth = 3f * density
        arcPaint.color = darken(type.rind)
        canvas.drawPath(path, arcPaint)

        // stalk region (only present when the drawn range starts before the shoulder)
        if (from < BANANA_A0 - 0.5f) {
            val stalkTo = minOf(to, BANANA_A0)
            val stalkPath = Path()
            val seg = 6
            for (i in 0..seg) {
                val a = from + (stalkTo - from) * i / seg
                val (px, py) = point(a, r + bananaWidthAt(a) * r)
                if (i == 0) stalkPath.moveTo(px, py) else stalkPath.lineTo(px, py)
            }
            for (i in seg downTo 0) {
                val a = from + (stalkTo - from) * i / seg
                val (px, py) = point(a, r - bananaWidthAt(a) * r)
                stalkPath.lineTo(px, py)
            }
            stalkPath.close()
            fillPaint.color = 0xFF795548.toInt()
            canvas.drawPath(stalkPath, fillPaint)
            arcPaint.strokeWidth = 3f * density
            arcPaint.color = darken(0xFF795548.toInt())
            canvas.drawPath(stalkPath, arcPaint)
        }

        // flesh ridge visible on the cut pieces
        if (showFlesh && to > BANANA_A0 + 0.5f) {
            arcPaint.strokeWidth = r * 0.10f
            arcPaint.color = type.flesh
            canvas.drawArc(
                RectF(-r, -r, r, r),
                maxOf(from, BANANA_A0), to - maxOf(from, BANANA_A0), false, arcPaint
            )
        }
    }

    private fun drawCrossHatch(canvas: Canvas, r: Float, color: Int) {        fillPaint.color = color
        fillPaint.alpha = 0x77
        for (angle in floatArrayOf(45f, -45f)) {
            canvas.save()
            canvas.rotate(angle)
            var d = -r * 2f
            while (d < r * 2f) {
                canvas.drawRect(d, -r * 2f, d + r * 0.07f, r * 2f, fillPaint)
                d += r * 0.42f
            }
            canvas.restore()
        }
        fillPaint.alpha = 255
    }

    private fun drawHalf(canvas: Canvas, m: Half) {
        val t = m.type
        canvas.save()
        canvas.translate(m.x, m.y)
        canvas.rotate(m.rot)
        canvas.scale(t.sx, t.sy)

        if (t.shape == 1) {
            // banana piece: keeps its taper, cut face shows the flesh ridge
            if (m.top) drawBananaShape(canvas, m.r, BANANA_STALK_A, 0f, t, showFlesh = true)
            else drawBananaShape(canvas, m.r, 0f, BANANA_A1, t, showFlesh = true)
            canvas.restore()
            return
        }

        val rect = RectF(-m.r, -m.r, m.r, m.r)
        val path = Path()
        path.moveTo(0f, 0f)
        path.arcTo(rect, if (m.top) 180f else 0f, 180f, false)
        path.close()
        fillPaint.shader = null
        fillPaint.color = t.rind
        canvas.drawPath(path, fillPaint)

        val inner = m.r * 0.86f
        val rectIn = RectF(-inner, -inner, inner, inner)
        val pathIn = Path()
        pathIn.moveTo(0f, 0f)
        pathIn.arcTo(rectIn, if (m.top) 180f else 0f, 180f, false)
        pathIn.close()
        fillPaint.color = t.flesh
        canvas.drawPath(pathIn, fillPaint)

        // avocado pit: sits on one cut half only
        if (t.pit && m.top) {
            fillPaint.color = 0xFF8D6E63.toInt()
            canvas.drawOval(
                RectF(-inner * 0.45f, -inner * 0.85f, inner * 0.45f, -inner * 0.15f),
                fillPaint
            )
            fillPaint.color = 0x44FFFFFF
            canvas.drawOval(
                RectF(-inner * 0.30f, -inner * 0.78f, -inner * 0.05f, -inner * 0.55f),
                fillPaint
            )
        }

        // seeds on the cut flesh (watermelon / kiwi)
        if (t.fleshSeeds > 0) {
            fillPaint.color = 0xFF3E2723.toInt()
            val rad = inner * 0.55f
            val base = if (m.top) -90f else 90f
            for (i in 0 until t.fleshSeeds) {
                val f = (i.toFloat() / (t.fleshSeeds - 1)) - 0.5f
                val px = f * rad * 1.4f
                // alternate two rows so dense flesh (passion fruit) fills the cut face
                val row = if (i % 2 == 0) 1f else 0.52f
                val py = (if (m.top) -1f else 1f) * inner * 0.42f * row * (1f - f * f * 1.5f)
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(base + f * 40f)
                canvas.drawOval(
                    RectF(-m.r * 0.045f, -m.r * 0.075f, m.r * 0.045f, m.r * 0.075f), fillPaint
                )
                canvas.restore()
            }
        }

        canvas.drawPath(path, strokePaint)
        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            fillPaint.color = p.color
            fillPaint.alpha = (a * 255).toInt()
            canvas.drawCircle(p.x, p.y, p.size * (0.5f + a * 0.5f), fillPaint)
        }
        fillPaint.alpha = 255
    }

    private fun drawTrails(canvas: Canvas) {
        for (t in trails) {
            val a = (t.life / t.maxLife).coerceIn(0f, 1f)
            trailPaint.alpha = (a * 200).toInt()
            canvas.drawLine(t.x1, t.y1, t.x2, t.y2, trailPaint)
        }
        trailPaint.alpha = 255
    }

    private fun drawScore(canvas: Canvas, w: Int) {
        val pad = 24f * density
        canvas.drawText(score.toString(), pad, pad + textPaint.textSize, textPaint)
    }

    private fun lighten(color: Int): Int {
        val r = (Color.red(color) + 70).coerceAtMost(255)
        val g = (Color.green(color) + 70).coerceAtMost(255)
        val b = (Color.blue(color) + 70).coerceAtMost(255)
        return Color.rgb(r, g, b)
    }

    private fun darken(color: Int): Int {
        val r = (Color.red(color) - 70).coerceAtLeast(0)
        val g = (Color.green(color) - 70).coerceAtLeast(0)
        val b = (Color.blue(color) - 70).coerceAtLeast(0)
        return Color.rgb(r, g, b)
    }

    private companion object {
        const val TRAIL_LIFE = 0.16f

        /** Play budget per launch: 10 minutes. */
        const val TOTAL_PLAY_SEC = 10f * 60f

        // banana arc geometry: angular span of head -> tail
        const val BANANA_A0 = -75f
        const val BANANA_A1 = 75f
        const val BANANA_STALK_A = BANANA_A0 - 30f
    }
}
