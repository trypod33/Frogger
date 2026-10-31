package com.trypod33.frogger

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    companion object {
        const val CELL = 16f
        const val W = 224f
        const val ROWS = 14
        const val TOP = 32f
        const val H = TOP + ROWS * CELL + 16f
        const val R_HOMES = 1
        const val R_WATER0 = 2
        const val R_WATER1 = 6
        const val R_MEDIAN = 7
        const val R_ROAD0 = 8
        const val R_ROAD1 = 12
        const val R_START = 13
        val BAY_COLS = intArrayOf(1, 4, 7, 10, 13)
        const val HOP_DUR = 0.13f

        const val TYPE_LOG = 0
        const val TYPE_TURTLE = 1
        const val TYPE_TRUCK = 2
        const val TYPE_RACER = 3
        const val TYPE_CAR = 4
        const val TYPE_DOZER = 5
        const val TYPE_BEETLE = 6

        const val STATE_ATTRACT = 0
        const val STATE_PLAY = 1
        const val STATE_DYING = 2
        const val STATE_CLEAR = 3
        const val STATE_OVER = 4

        const val DEATH_DROWN = 0
        const val DEATH_SKULL = 1
    }

    private class Lane(val row: Int, val dir: Int, val speed: Float, val count: Int,
                       val lenPx: Float, val type: Int, val divers: IntArray?) {
        var phase = (row * 37f) % (W + lenPx)
        val period = W + lenPx
        val spacing = period / count

        fun update(dt: Float, mult: Float) { phase += dir * speed * mult * dt }

        fun itemX(i: Int): Float {
            var x = (phase + i * spacing) % period
            if (x < 0) x += period
            return x - lenPx
        }

        // 0..7 dive cycle per item: up, sink flash, submerged, resurface
        fun diveP(i: Int, t: Float): Float {
            if (divers == null || !divers.contains(i)) return -1f
            return (t + i * 2.3f + row * 0.9f) % 7f
        }
        fun submerged(i: Int, t: Float): Boolean {
            val p = diveP(i, t)
            return p >= 5.2f && p < 6.4f
        }
        fun alpha(i: Int, t: Float): Int {
            val p = diveP(i, t)
            if (p < 0f) return 255
            return when {
                p < 4.2f -> 255
                p < 5.2f -> (255 - (p - 4.2f) * 175f).toInt()
                p < 6.4f -> 0
                else -> (80 + (p - 6.4f) * 175f).toInt()
            }.coerceIn(0, 255)
        }
    }

    private val holder = getHolder()
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var crash: String? = null

    private val prefs: SharedPreferences = context.getSharedPreferences("frogger", Context.MODE_PRIVATE)
    private var hiScore = prefs.getInt("hi", 0)

    private var state = STATE_ATTRACT
    private var gameT = 0f
    private var score = 0
    private var hiShown = hiScore
    private var lives = 3
    private var level = 1
    private var mult = 1f
    private var timerMax = 30f
    private var extraAwarded = false
    private val bays = BooleanArray(5)
    private var flyBay = -1
    private var flyTimer = 6f
    private var flyShow = 0f

    private var frogX = 104f
    private var frogRow = R_START
    private var frogDir = 0
    private var hopping = false
    private var hopT = 0f
    private var hopFromX = 0f
    private var hopToX = 0f
    private var hopFromRow = 0
    private var hopToRow = 0
    private var bestRow = R_START

    private var deathT = 0f
    private var deathType = DEATH_SKULL
    private var deathX = 0f
    private var deathRow = 0
    private var clearT = 0f

    private var lanes: List<Lane> = ArrayList()

    // sprites
    private var frogSit = arrayOf<Bitmap>()
    private var frogHop = arrayOf<Bitmap>()
    private var sprTurtle: Bitmap? = null
    private var sprRacer: Bitmap? = null
    private var sprCar: Bitmap? = null
    private var sprDozer: Bitmap? = null
    private var sprBeetle: Bitmap? = null
    private var sprFly: Bitmap? = null
    private var sprSkull: Bitmap? = null
    private var sprTruck: Bitmap? = null
    private var sprTruckFlip: Bitmap? = null
    private var log3: Bitmap? = null
    private var log5: Bitmap? = null
    private var hedgeBmp: Bitmap? = null
    private var purpleBmp: Bitmap? = null

    private val spritePaint = Paint().apply { isFilterBitmap = false }
    private val plain = Paint()
    private val textBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; isFakeBoldText = true; textSize = 34f; textAlign = Paint.Align.CENTER }
    private val textMed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; isFakeBoldText = true; textSize = 13f }
    private val textMedC = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; isFakeBoldText = true; textSize = 13f; textAlign = Paint.Align.CENTER }
    private val textSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; isFakeBoldText = true; textSize = 11f; textAlign = Paint.Align.CENTER }
    private val barPaint = Paint()
    private val dst = android.graphics.RectF()

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        buildSprites()
        resetLanes()
    }

    // ---------- sprites ----------

    private fun bmpFromMap(map: Array<String>, pal: Map<Char, Int>): Bitmap {
        val w = map[0].length
        val h = map.size
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            val c = pal[map[y][x]]
            if (c != null) b.setPixel(x, y, c)
        }
        return b
    }

    private fun rot(b: Bitmap, deg: Float): Bitmap =
        Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true)

    private fun flip(b: Bitmap): Bitmap =
        Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postScale(-1f, 1f) }, true)

    private fun buildSprites() {
        val frogPal = mapOf('D' to 0xFF007800.toInt(), 'G' to 0xFF00D000.toInt(),
            'Y' to 0xFFF8D800.toInt(), 'W' to 0xFFFFFFFF.toInt(), 'K' to 0xFF000000.toInt())
        val sit = bmpFromMap(FROG_MAP, frogPal)
        val hop = bmpFromMap(FROG_HOP_MAP, frogPal)
        frogSit = arrayOf(sit, rot(sit, 90f), rot(sit, 180f), rot(sit, 270f))
        frogHop = arrayOf(hop, rot(hop, 90f), rot(hop, 180f), rot(hop, 270f))

        val turtlePal = mapOf('R' to 0xFFE04848.toInt(), 'O' to 0xFFF8A058.toInt(),
            'G' to 0xFF00B000.toInt(), 'K' to 0xFF7A1818.toInt())
        sprTurtle = bmpFromMap(TURTLE_MAP, turtlePal)

        val racerPal = mapOf('W' to 0xFFF8F8F8.toInt(), 'R' to 0xFFE02020.toInt(), 'K' to 0xFF000000.toInt())
        sprRacer = bmpFromMap(RACER_MAP, racerPal)

        val carPal = mapOf('P' to 0xFFC848E8.toInt(), 'C' to 0xFF40E0F0.toInt(), 'K' to 0xFF000000.toInt())
        sprCar = bmpFromMap(CAR_MAP, carPal)

        val dozerPal = mapOf('G' to 0xFF30C030.toInt(), 'D' to 0xFF206020.toInt(), 'K' to 0xFF000000.toInt())
        sprDozer = bmpFromMap(DOZER_MAP, dozerPal)

        val beetlePal = mapOf('Y' to 0xFFF8D020.toInt(), 'R' to 0xFFE03030.toInt(), 'K' to 0xFF000000.toInt())
        sprBeetle = bmpFromMap(BEETLE_MAP, beetlePal)

        val flyPal = mapOf('R' to 0xFFE83030.toInt(), 'O' to 0xFF60D060.toInt(), 'W' to 0xFFF0F0F0.toInt())
        sprFly = bmpFromMap(FLY_MAP, flyPal)

        val skullPal = mapOf('W' to 0xFFE8E8E8.toInt(), 'K' to 0xFF282828.toInt())
        sprSkull = bmpFromMap(SKULL_MAP, skullPal)

        sprTruck = makeTruck()
        sprTruckFlip = flip(sprTruck!!)
        log3 = makeLog(48)
        log5 = makeLog(80)

        hedgeBmp = makeSpeckle(0xFF00A000.toInt(), 0xFF006800.toInt())
        purpleBmp = makeSpeckle(0xFF9848C8.toInt(), 0xFF70289A.toInt())
    }

    private fun makeSpeckle(base: Int, dark: Int): Bitmap {
        val b = Bitmap.createBitmap(224, 16, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        plain.color = base
        c.drawRect(0f, 0f, 224f, 16f, plain)
        plain.color = dark
        var seed = 12345
        var i = 0
        while (i < 260) {
            seed = seed * 1103515245 + 12345
            val x = ((seed ushr 16) % 224).toFloat()
            seed = seed * 1103515245 + 12345
            val y = ((seed ushr 16) % 16).toFloat()
            c.drawRect(x, y, x + 2f, y + 2f, plain)
            i++
        }
        return b
    }

    private fun makeLog(lenPx: Int): Bitmap {
        val b = Bitmap.createBitmap(lenPx, 10, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = plain
        p.color = 0xFFC87838.toInt()
        c.drawRoundRect(android.graphics.RectF(0f, 0f, lenPx.toFloat(), 10f), 5f, 5f, p)
        p.color = 0xFFE8B878.toInt()
        c.drawRoundRect(android.graphics.RectF(2f, 1f, lenPx - 2f, 5f), 4f, 4f, p)
        p.color = 0xFF8A4820.toInt()
        c.drawCircle(4f, 5f, 3.5f, p)
        c.drawCircle(lenPx - 4f, 5f, 3.5f, p)
        p.color = 0xFFB06028.toInt()
        c.drawRect(8f, 3f, 12f, 7f, p)
        c.drawRect(lenPx / 2f, 3f, lenPx / 2f + 4f, 7f, p)
        c.drawRect(lenPx - 16f, 3f, lenPx - 12f, 7f, p)
        return b
    }

    private fun makeTruck(): Bitmap {
        val b = Bitmap.createBitmap(32, 11, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = plain
        // trailer (left, facing right)
        p.color = 0xFFF8F8F8.toInt()
        c.drawRect(1f, 1f, 22f, 9f, p)
        p.color = 0xFFD0D0D0.toInt()
        c.drawRect(1f, 5f, 22f, 7f, p)
        // cab (right)
        p.color = 0xFFE02020.toInt()
        c.drawRect(23f, 2f, 31f, 9f, p)
        p.color = 0xFF40E0F0.toInt()
        c.drawRect(27f, 3f, 30f, 5f, p)
        p.color = 0xFF000000.toInt()
        c.drawRect(4f, 9f, 8f, 11f, p)
        c.drawRect(14f, 9f, 18f, 11f, p)
        c.drawRect(25f, 9f, 29f, 11f, p)
        p.color = 0xFF000000.toInt()
        c.drawRect(1f, 1f, 22f, 2f, p)
        return b
    }

    private fun resetLanes() {
        lanes = listOf(
            Lane(2, 1, 22f, 3, 48f, TYPE_LOG, null),
            Lane(3, -1, 26f, 4, 38f, TYPE_TURTLE, intArrayOf(1, 3)),
            Lane(4, 1, 40f, 2, 80f, TYPE_LOG, null),
            Lane(5, -1, 26f, 3, 38f, TYPE_TURTLE, intArrayOf(1)),
            Lane(8, -1, 26f, 2, 32f, TYPE_TRUCK, null),
            Lane(9, 1, 62f, 3, 12f, TYPE_RACER, null),
            Lane(10, -1, 30f, 4, 12f, TYPE_CAR, null),
            Lane(11, 1, 22f, 3, 12f, TYPE_DOZER, null),
            Lane(12, -1, 26f, 4, 12f, TYPE_BEETLE, null)
        )
    }

    // ---------- game control ----------

    private fun isWater(r: Int) = r in R_WATER0..R_WATER1

    private fun startGame() {
        score = 0; lives = 3; level = 1; mult = 1f; extraAwarded = false
        timerMax = 30f
        for (i in 0 until 5) bays[i] = false
        flyBay = -1; flyTimer = 6f
        resetLanes()
        respawnFrog()
        state = STATE_PLAY
    }

    private fun respawnFrog() {
        frogX = 104f; frogRow = R_START; frogDir = 0
        hopping = false; hopT = 0f
        bestRow = R_START
        timeLeft = timerMax
    }

    var timeLeft = 30f

    private fun hop(dir: Int) {
        if (hopping || state != STATE_PLAY) return
        val dRow = if (dir == 0) -1 else if (dir == 2) 1 else 0
        val dX = if (dir == 1) 1 else if (dir == 3) -1 else 0
        frogDir = dir
        val tx = frogX + dX * CELL
        val tr = frogRow + dRow
        if (dX != 0 && (tx < 0f || tx > W - 12f)) return
        if (tr < R_HOMES || tr > R_START) return
        hopping = true; hopT = 0f
        hopFromX = frogX; hopToX = tx
        hopFromRow = frogRow; hopToRow = tr
        if (dRow == -1 && tr < bestRow) {
            bestRow = tr
            score += 10
            checkExtra()
        }
    }

    private fun checkExtra() {
        if (!extraAwarded && score >= 10000) { lives++; extraAwarded = true }
        if (score > hiShown) hiShown = score
    }

    private fun die(type: Int) {
        state = STATE_DYING
        deathT = 0f
        deathType = type
        deathX = frogX
        deathRow = if (hopping) hopToRow else frogRow
        hopping = false
    }

    private fun resolveHome() {
        val cx = frogX + 6f
        for (b in 0 until 5) {
            val bayCx = BAY_COLS[b] * CELL + 8f
            if (Math.abs(cx - bayCx) <= 7f) {
                if (bays[b]) { die(DEATH_SKULL); return }
                bays[b] = true
                var pts = 50
                if (flyBay == b) { pts += 200; flyBay = -1; flyTimer = 9f }
                score += pts
                checkExtra()
                if (bays.all { it }) {
                    score += 1000
                    checkExtra()
                    state = STATE_CLEAR
                    clearT = 0f
                } else {
                    respawnFrog()
                }
                return
            }
        }
        die(DEATH_SKULL)
    }

    private fun nextLevel() {
        level++
        mult = (1f + 0.13f * (level - 1)).coerceAtMost(2.2f)
        timerMax = (30f - 2f * (level - 1)).coerceAtLeast(18f)
        for (i in 0 until 5) bays[i] = false
        flyBay = -1; flyTimer = 6f
        resetLanes()
        respawnFrog()
        state = STATE_PLAY
    }

    private fun respawnAfterDeath() {
        lives--
        if (lives < 0) {
            if (score > hiScore) { hiScore = score; prefs.edit().putInt("hi", hiScore).apply() }
            state = STATE_OVER
        } else {
            respawnFrog()
            state = STATE_PLAY
        }
    }

    // ---------- loop ----------

    private fun update(dt: Float) {
        gameT += dt
        val m = if (state == STATE_ATTRACT || state == STATE_OVER) 1f else mult
        for (l in lanes) l.update(dt, m)

        when (state) {
            STATE_PLAY -> {
                flyTimer -= dt
                if (flyBay >= 0) {
                    flyShow -= dt
                    if (flyShow <= 0f) { flyBay = -1; flyTimer = 9f }
                } else if (flyTimer <= 0f) {
                    val empties = (0..4).filter { !bays[it] }
                    if (empties.isNotEmpty()) {
                        flyBay = empties[(Math.random() * empties.size).toInt()]
                        flyShow = 4f
                    }
                    flyTimer = 9f
                }
                if (!hopping) {
                    timeLeft -= dt
                    if (timeLeft <= 0f) { die(DEATH_SKULL); return }
                }
                updateFrog(dt)
            }
            STATE_DYING -> {
                deathT += dt
                if (deathT >= 1.5f) respawnAfterDeath()
            }
            STATE_CLEAR -> {
                clearT += dt
                if (clearT >= 2.2f) nextLevel()
            }
        }
    }

    private fun platformAt(row: Int, cx: Float): Lane? {
        for (lane in lanes) {
            if (lane.row != row) continue
            if (lane.type != TYPE_LOG && lane.type != TYPE_TURTLE) continue
            for (i in 0 until lane.count) {
                val x = lane.itemX(i)
                if (cx >= x && cx <= x + lane.lenPx && !lane.submerged(i, gameT)) return lane
            }
        }
        return null
    }

    private fun updateFrog(dt: Float) {
        if (hopping) {
            hopT += dt / HOP_DUR
            if (hopT >= 1f) {
                hopping = false
                hopT = 0f
                frogX = hopToX
                val r = hopToRow
                if (r == R_HOMES) { resolveHome(); return }
                frogRow = r
                if (isWater(r) && platformAt(r, frogX + 6f) == null) { die(DEATH_DROWN); return }
            } else {
                val e = hopT * hopT * (3f - 2f * hopT)
                frogX = hopFromX + (hopToX - hopFromX) * e
            }
        }
        if (state != STATE_PLAY || hopping) return

        if (isWater(frogRow)) {
            val lane = platformAt(frogRow, frogX + 6f)
            if (lane == null) { die(DEATH_DROWN); return }
            // recheck the exact item for submersion
            var idx = -1
            for (i in 0 until lane.count) {
                val x = lane.itemX(i)
                if (frogX + 6f >= x && frogX + 6f <= x + lane.lenPx && !lane.submerged(i, gameT)) { idx = i; break }
            }
            if (idx < 0) { die(DEATH_DROWN); return }
            frogX += lane.dir * lane.speed * mult * dt
            if (frogX + 6f < 2f || frogX + 6f > W - 2f) { die(DEATH_DROWN); return }
        } else if (frogRow in R_ROAD0..R_ROAD1) {
            for (lane in lanes) {
                if (lane.row != frogRow) continue
                for (i in 0 until lane.count) {
                    val x = lane.itemX(i)
                    if (frogX + 10f > x && frogX + 2f < x + lane.lenPx) { die(DEATH_SKULL); return }
                }
            }
        }
    }

    // ---------- drawing ----------

    private fun drawFrame(c: Canvas) {
        c.drawColor(Color.BLACK)
        val vw = width.toFloat(); val vh = height.toFloat()
        val s = minOf(vw / W, vh / H)
        c.save()
        c.translate((vw - W * s) / 2f, (vh - H * s) / 2f)
        c.scale(s, s)

        // score bar
        textMed.color = 0xFFFF5040.toInt()
        c.drawText("1-UP", 24f, 13f, textMed)
        textMedC.color = 0xFFFF5040.toInt()
        c.drawText("HI-SCORE", 160f, 13f, textMedC)
        textMed.color = 0xFFE82828.toInt()
        c.drawText(String.format(java.util.Locale.US, "%05d", score), 24f, 27f, textMed)
        c.drawText(String.format(java.util.Locale.US, "%05d", hiShown), 160f, 27f, textMedC)

        // backgrounds
        val hedge = hedgeBmp!!
        val purple = purpleBmp!!
        c.drawBitmap(hedge, 0f, TOP, plain)
        c.drawBitmap(hedge, 0f, TOP + R_HOMES * CELL, plain)
        plain.color = 0xFF20208C.toInt()
        c.drawRect(0f, TOP + R_WATER0 * CELL, W, TOP + (R_WATER1 + 1) * CELL, plain)
        c.drawBitmap(purple, 0f, TOP + R_MEDIAN * CELL, plain)
        // road: black (canvas is black already)
        c.drawBitmap(purple, 0f, TOP + R_START * CELL, plain)

        // lanes
        for (lane in lanes) {
            val y = TOP + lane.row * CELL
            for (i in 0 until lane.count) {
                val x = lane.itemX(i)
                when (lane.type) {
                    TYPE_LOG -> {
                        val b = if (lane.lenPx > 60f) log5 else log3
                        dst.set(x, y + 3f, x + lane.lenPx, y + 13f)
                        c.drawBitmap(b!!, null, dst, spritePaint)
                    }
                    TYPE_TURTLE -> {
                        val a = lane.alpha(i, gameT)
                        if (a > 0) {
                            spritePaint.alpha = a
                            var j = 0
                            while (j < 3) {
                                dst.set(x + j * 13f, y + 4f, x + j * 13f + 12f, y + 12f)
                                c.drawBitmap(sprTurtle!!, null, dst, spritePaint)
                                j++
                            }
                            spritePaint.alpha = 255
                        }
                    }
                    TYPE_TRUCK -> {
                        val b = if (lane.dir > 0) sprTruckFlip else sprTruck
                        dst.set(x, y + 2f, x + 32f, y + 13f)
                        c.drawBitmap(b!!, null, dst, spritePaint)
                    }
                    else -> {
                        val src = when (lane.type) {
                            TYPE_RACER -> sprRacer
                            TYPE_CAR -> sprCar
                            TYPE_DOZER -> sprDozer
                            else -> sprBeetle
                        }!!
                        val b = if (lane.dir > 0) flipCache(src) else src
                        dst.set(x, y + 5f, x + 12f, y + 11f)
                        c.drawBitmap(b, null, dst, spritePaint)
                    }
                }
            }
        }

        // homes row: bays
        for (b in 0 until 5) {
            val col = BAY_COLS[b]
            val bx = col * CELL + 2f
            val by = TOP + R_HOMES * CELL + 2f
            plain.color = 0xFF080858.toInt()
            c.drawRect(bx, by, bx + 12f, by + 12f, plain)
            if (bays[b]) {
                dst.set(bx, by - 1f, bx + 12f, by + 12f)
                c.drawBitmap(frogSit[0], null, dst, spritePaint)
            } else if (flyBay == b) {
                dst.set(bx + 2f, by + 3f, bx + 10f, by + 9f)
                c.drawBitmap(sprFly!!, null, dst, spritePaint)
            }
        }

        if (state == STATE_ATTRACT) {
            drawAttract(c)
        } else if (state == STATE_PLAY || state == STATE_CLEAR) {
            // frog
            val cx: Float; val cy: Float
            if (hopping) {
                val e = hopT * hopT * (3f - 2f * hopT)
                cx = hopFromX + (hopToX - hopFromX) * e + 6f
                cy = TOP + (hopFromRow + (hopToRow - hopFromRow) * e) * CELL + 8f
            } else {
                cx = frogX + 6f
                cy = TOP + frogRow * CELL + 8f
            }
            val pose = if (hopping) frogHop else frogSit
            dst.set(cx - 7f, cy - 7f, cx + 7f, cy + 7f)
            c.drawBitmap(pose[frogDir], null, dst, spritePaint)
        } else if (state == STATE_DYING) {
            if (deathType == DEATH_DROWN) {
                val cxx = deathX + 6f
                val cyy = TOP + deathRow * CELL + 8f
                barPaint.style = Paint.Style.STROKE
                barPaint.strokeWidth = 1.5f
                for (k in 0 until 3) {
                    val r = (deathT - k * 0.18f) * 16f
                    if (r > 0f) {
                        barPaint.color = if (k == 0) 0xFFB0B0FF.toInt() else 0xFF6060C8.toInt()
                        barPaint.alpha = ((1f - deathT / 1.5f) * 255f).toInt().coerceIn(0, 255)
                        c.drawCircle(cxx, cyy, r.coerceAtMost(12f), barPaint)
                    }
                }
                barPaint.style = Paint.Style.FILL
            } else if ((deathT * 6f).toInt() % 2 == 0) {
                val cxx = deathX + 6f
                val cyy = TOP + deathRow * CELL + 8f
                dst.set(cxx - 7f, cyy - 7f, cxx + 7f, cyy + 7f)
                c.drawBitmap(sprSkull!!, null, dst, spritePaint)
            }
        } else if (state == STATE_OVER) {
            drawGameOver(c)
        }

        // bottom bar: lives + time
        var li = 0
        while (li < lives && li < 5) {
            dst.set(4f + li * 15f, TOP + ROWS * CELL + 2f, 4f + li * 15f + 11f, TOP + ROWS * CELL + 14f)
            c.drawBitmap(frogSit[0], null, dst, spritePaint)
            li++
        }
        textMed.color = 0xFFF8F8F8.toInt()
        c.drawText("TIME", 128f, TOP + ROWS * CELL + 13f, textMed)
        barPaint.color = 0xFF00D000.toInt()
        val frac = (timeLeft / timerMax).coerceIn(0f, 1f)
        c.drawRect(160f, TOP + ROWS * CELL + 6f, 160f + 60f * frac, TOP + ROWS * CELL + 11f, barPaint)
        if (state == STATE_CLEAR) {
            textMedC.color = 0xFFF8F8F8.toInt()
            c.drawText("COURSE CLEAR!", 112f, TOP + 120f, textMedC)
        }
        c.restore()
    }

    private val flipCacheMap = HashMap<Bitmap, Bitmap>()
    private fun flipCache(b: Bitmap): Bitmap = flipCacheMap.getOrPut(b) { flip(b) }

    private fun drawAttract(c: Canvas) {
        textBig.color = 0xFFF8D800.toInt()
        c.drawText("FROGGER", 115f, 108f, textBig)
        textBig.color = 0xFFFF3838.toInt()
        c.drawText("FROGGER", 112f, 105f, textBig)
        if ((gameT % 1f) < 0.65f) {
            textSmall.color = 0xFFFFFFFF.toInt()
            c.drawText("TAP TO START", 112f, 140f, textSmall)
        }
        textSmall.color = 0xFFE82828.toInt()
        c.drawText("HI-SCORE " + String.format(java.util.Locale.US, "%05d", hiScore), 112f, 168f, textSmall)
    }

    private fun drawGameOver(c: Canvas) {
        textMedC.color = 0xFFFF3838.toInt()
        c.drawText("GAME OVER", 112f, TOP + 100f, textMedC)
        if ((gameT % 1f) < 0.65f) {
            textSmall.color = 0xFFFFFFFF.toInt()
            c.drawText("TAP TO PLAY", 112f, TOP + 126f, textSmall)
        }
    }

    // ---------- surface / input ----------

    override fun run() {
        var last = System.currentTimeMillis()
        while (running) {
            val now = System.currentTimeMillis()
            var dt = (now - last) / 1000f
            if (dt > 0.05f) dt = 0.05f
            last = now
            try {
                update(dt)
                val c = holder.lockCanvas()
                if (c != null) {
                    drawFrame(c)
                    holder.unlockCanvasAndPost(c)
                }
            } catch (t: Throwable) {
                crash = t.stackTraceToString()
                try {
                    val c = holder.lockCanvas()
                    if (c != null) {
                        c.drawColor(Color.BLACK)
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = Typeface.MONOSPACE; textSize = 7f; color = 0xFFFFFF00.toInt() }
                        var y = 12f
                        for (line in crash!!.split("\n").take(48)) { c.drawText(line, 4f, y, p); y += 9f }
                        holder.unlockCanvasAndPost(c)
                    }
                } catch (t2: Throwable) {}
                try { Thread.sleep(200) } catch (ie: InterruptedException) {}
            }
        }
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        running = true
        thread = Thread(this).also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        var retry = true
        while (retry) {
            try { thread?.join(); retry = false } catch (e: InterruptedException) {}
        }
        thread = null
    }

    private var touchX = 0f
    private var touchY = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { touchX = e.x; touchY = e.y }
            MotionEvent.ACTION_UP -> {
                val dx = e.x - touchX
                val dy = e.y - touchY
                if (state == STATE_ATTRACT || state == STATE_OVER) { startGame(); return true }
                if (Math.abs(dx) < 24f && Math.abs(dy) < 24f) { hop(0); return true }
                hop(if (Math.abs(dx) > Math.abs(dy)) (if (dx > 0) 1 else 3) else (if (dy > 0) 2 else 0))
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> hop(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> hop(2)
            KeyEvent.KEYCODE_DPAD_LEFT -> hop(3)
            KeyEvent.KEYCODE_DPAD_RIGHT -> hop(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (state != STATE_PLAY) startGame()
            else -> return super.onKeyDown(keyCode, e)
        }
        return true
    }

    // ---------- pixel maps ----------

    private val FROG_MAP = arrayOf(
        ".DD......DD.",
        ".DGD....DGD.",
        ".DWK....KWD.",
        ".DGD....DGD.",
        "..DDDDDDDD..",
        ".DGGGGGGGGD.",
        "DDGYDGGGYGDD",
        "DGGGGGGGGGGD",
        ".DGYYGGYYGD.",
        ".DGGGGGGGGD.",
        "..DDGGGGDD..",
        ".DG..GG..GD.",
        ".DD..GG..DD.")

    private val FROG_HOP_MAP = arrayOf(
        ".DD......DD.",
        ".DGD....DGD.",
        ".DWK....KWD.",
        ".DGD....DGD.",
        "..DDDDDDDD..",
        ".DGGGGGGGGD.",
        "DDGYDGGGYGDD",
        "DGGGGGGGGGGD",
        ".DGYYGGYYGD.",
        ".DGGGGGGGGD.",
        "DGDGGGGGGDGD",
        "D....GG....D",
        "............")

    private val TURTLE_MAP = arrayOf(
        "............",
        "G..RRRRRR...",
        "GGRRORORRR..",
        "GGRROKORRR..",
        "GGRRORORRR..",
        "G.RRRRRRR...",
        "....RRRR....",
        "..G......G..")

    private val RACER_MAP = arrayOf(
        "............",
        "...WWWWWW...",
        "..WWWWWWWW..",
        ".WWWRRRRWWW.",
        "KWWWWWWWWWWK",
        "K..WWWWWW..K")

    private val CAR_MAP = arrayOf(
        "............",
        "...PPPPPP...",
        "..PPPPPPPP..",
        ".PPCCPPCCPP.",
        "KPPPPPPPPPPK",
        "K..PPPPPP..K")

    private val DOZER_MAP = arrayOf(
        "............",
        "...GGGGGG...",
        "..GGGGGGGG..",
        ".GGGGGGGGGG.",
        "DDDDDDDDDDDD",
        "DKDKDKDKDKDK")

    private val BEETLE_MAP = arrayOf(
        "............",
        "...YYYYYY...",
        "..YYYYYYYY..",
        ".YYRRYYRRYY.",
        "KYYYYYYYYYYK",
        "K..YYYYYY..K")

    private val FLY_MAP = arrayOf(
        "W......W",
        ".W.RR.W.",
        "..RRRR..",
        ".RROORR.",
        "..RRRR..",
        "...RR...")

    private val SKULL_MAP = arrayOf(
        "..WWWWWWWW..",
        ".WWWWWWWWWW.",
        "WWKKWWWWKKWW",
        "WWKKWWWWKKWW",
        "WWWWKWWKWWWW",
        ".WWKKKKKKWW.",
        ".WWKWKWKWWW.",
        ".WWWWWWWWWW.",
        "..WWWWWWWW..",
        "............")
}
