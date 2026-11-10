package cz.pupik.televize.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pupik.televize.data.Weather
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================
// 3) Živé počasí v pozadí – podle aktuálního kódu počasí (WMO) a dne/noci
// =====================================================================
private class Drop(val x: Float, val y: Float, val speed: Float, val len: Float, val size: Float, val sway: Float)

@Composable
fun WeatherFx(w: Weather?, animate: Boolean) {
    val code = w?.code ?: return
    val kind = when (code) {
        in 95..99 -> "storm"
        in 71..77, 85, 86 -> "snow"
        in 61..67, in 80..82 -> "rain"
        in 51..57 -> "drizzle"
        45, 48 -> "fog"
        2, 3 -> "cloud"
        0, 1 -> if (w.day) "sun" else "clear"
        else -> ""
    }
    if (kind == "" || kind == "clear") return // za jasné noci stačí hvězdy z pozadí
    val drops = remember(kind) {
        val r = Random(7)
        val n = when (kind) { "storm" -> 90; "rain" -> 70; "drizzle" -> 40; "snow" -> 70; else -> 0 }
        List(n) { Drop(r.nextFloat(), r.nextFloat(), 0.7f + r.nextFloat() * 0.6f, 0.6f + r.nextFloat() * 0.8f, 0.6f + r.nextFloat(), r.nextFloat() * 6.28f) }
    }
    // jeden společný čas (0..1 za 6 s) – telka má slabý procesor, žádné další smyčky
    val t = if (animate) {
        val tr = rememberInfiniteTransition(label = "wx")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t")
        v
    } else 0.3f
    // blesk: občas krátce zbělá obloha
    var flash by remember { mutableStateOf(0f) }
    if (kind == "storm" && animate) LaunchedEffect(Unit) {
        val r = Random(System.currentTimeMillis())
        while (true) {
            delay(6000L + r.nextLong(9000))
            flash = 1f; delay(90); flash = 0.2f; delay(70); flash = 0.8f; delay(110); flash = 0f
        }
    }
    val fl by animateFloatAsState(flash, tween(80), label = "flash")
    Canvas(Modifier.fillMaxSize()) {
        val W = size.width; val H = size.height
        when (kind) {
            "rain", "drizzle", "storm" -> {
                val k = if (kind == "drizzle") 0.55f else 1f
                for (d in drops) {
                    val p = (d.y + t * 3f * d.speed) % 1f
                    val x = (d.x + 0.05f * p) * W
                    val y = p * (H + 80f) - 40f
                    val l = (if (kind == "drizzle") 14f else 26f) * d.len * density
                    drawLine(Color(0xFFBFD8FF).copy(alpha = 0.16f * k + 0.1f * d.size * k), Offset(x, y), Offset(x - l * 0.12f, y + l), strokeWidth = 1.1f * density, cap = StrokeCap.Round)
                }
                if (kind == "storm" && fl > 0f) drawRect(Color(0xFFDDE6FF).copy(alpha = 0.22f * fl))
            }
            "snow" -> for (d in drops) {
                val p = (d.y + t * 0.7f * d.speed) % 1f
                val x = (d.x + 0.012f * sin(t * 2 * PI.toFloat() * 2 + d.sway)) * W
                val y = p * (H + 40f) - 20f
                drawCircle(Color.White.copy(alpha = 0.22f + 0.25f * (d.size - 0.6f)), (1.2f + 1.8f * d.size) * density, Offset(x, y))
            }
            "sun" -> {
                // teplá záře shora vpravo (za kartou počasí), pomalu dýchá
                val c = Offset(W * 0.82f, -H * 0.08f)
                val r = W * (0.42f + 0.02f * sin(t * 2 * PI.toFloat()))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFDE68A).copy(alpha = 0.16f), Color(0xFFFDE68A).copy(alpha = 0.05f), Color.Transparent), c, r), r, c)
            }
            "cloud" -> {
                // měkké mraky pomalu plují zleva doprava
                for (i in 0 until 4) {
                    val x = ((i * 0.31f + t * 0.5f) % 1.3f - 0.15f) * W
                    val y = H * (0.10f + 0.07f * i)
                    val r = W * (0.14f + 0.03f * i)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFC7D2FE).copy(alpha = 0.07f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
                }
            }
            "fog" -> for (i in 0 until 3) {
                val y = H * (0.35f + 0.18f * i)
                val dx = sin(t * 2 * PI.toFloat() + i) * W * 0.04f
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFDDE3F0).copy(alpha = 0.07f), Color.Transparent), y - 60 * density, y + 60 * density),
                    Offset(dx - W * 0.1f, y - 60 * density), Size(W * 1.2f, 120 * density),
                )
            }
        }
    }
}

// =====================================================================
// 5) Večerní nálada – po západu slunce teplejší a tmavší, ráno zpátky
// =====================================================================
/** 0 = den, 1 = noc; přechod hodinu po západu a půl hodiny před východem. */
fun eveningFactor(w: Weather?, now: Long = System.currentTimeMillis()): Float {
    val cal = Calendar.getInstance()
    val sunset = w?.sunset?.let { sameDay(it, now) } ?: cal.apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 17); set(Calendar.MINUTE, 0) }.timeInMillis
    val sunrise = w?.sunrise?.let { sameDay(it, now) } ?: cal.apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 7); set(Calendar.MINUTE, 0) }.timeInMillis
    return when {
        now >= sunset -> ((now - sunset) / 3_600_000f).coerceIn(0f, 1f)
        now < sunrise -> ((sunrise - now) / 1_800_000f).coerceIn(0f, 1f)
        else -> 0f
    }
}
// východ/západ z předpovědi posunutý na dnešní datum (cache může být ze včerejška)
private fun sameDay(t: Long, now: Long): Long {
    val a = Calendar.getInstance().apply { timeInMillis = t }
    return Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, a.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, a.get(Calendar.MINUTE)); set(Calendar.SECOND, 0)
    }.timeInMillis
}

/** Teplý tmavý závoj přes celou obrazovku (bez blend módů – na staré telce spolehlivé). */
@Composable
fun EveningTint(w: Weather?) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val f by animateFloatAsState(eveningFactor(w, now), tween(4000), label = "eve")
    if (f < 0.01f) return
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF2B1206).copy(alpha = 0.20f * f))
        // vršek ještě o kus tmavší – jako když večer zhasneš velké světlo
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f * f), Color.Transparent), 0f, size.height * 0.6f))
    }
}

// =====================================================================
// 4) Domeček – řez domem se spotřebiči (místo pruhu „Doma“)
// =====================================================================
private val STOPS = listOf(15f to Color(0xFF60A5FA), 18.5f to Color(0xFF38BDF8), 21f to Color(0xFF818CF8), 23f to Color(0xFFC084FC), 25f to Color(0xFFF472B6))
fun tempColor(t: Double?): Color {
    if (t == null) return Color(0xFF646E8C)
    val v = t.toFloat()
    if (v <= STOPS.first().first) return STOPS.first().second
    for (i in 1 until STOPS.size) {
        val (t1, c1) = STOPS[i]; val (t0, c0) = STOPS[i - 1]
        if (v <= t1) { val k = (v - t0) / (t1 - t0); return Color(c0.red + (c1.red - c0.red) * k, c0.green + (c1.green - c0.green) * k, c0.blue + (c1.blue - c0.blue) * k) }
    }
    return STOPS.last().second
}

@Composable
fun HouseMini(h: House, animate: Boolean) {
    val d = h.dev
    val busy = d != null && (d.burner || d.klimaOn || d.wm == "run" || d.vac)
    val t = if (animate && busy) {
        val tr = rememberInfiniteTransition(label = "house")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "t")
        v
    } else 0f
    val top = h.rooms.firstOrNull { it.name.contains("ložnic", true) } ?: h.rooms.getOrNull(0)
    val bottom = h.rooms.firstOrNull { it !== top }
    Column(Modifier.width(232.dp)) {
        // střecha s komínem (z komína pára, když hoří hořák)
        Canvas(Modifier.width(232.dp).height(28.dp)) {
            val W = size.width; val H = size.height
            val ch = Offset(W * 0.72f, H * 0.18f)
            drawRoundRect(Color(0xFF20263F), ch, Size(10 * density, H * 0.62f), CornerRadius(2 * density))
            if (d?.burner == true) for (i in 0 until 3) {
                val p = (t * 2f + i / 3f) % 1f
                drawCircle(Color(0xFFE2E8F0).copy(alpha = 0.45f * (1f - p)), (2.5f + 4f * p) * density, Offset(ch.x + 5 * density + p * 6 * density, ch.y - p * 18 * density))
            }
            val roof = Path().apply { moveTo(W * 0.04f, H); lineTo(W * 0.5f, 0f); lineTo(W * 0.96f, H); close() }
            drawPath(roof, Brush.verticalGradient(listOf(Color(0xFF2A3150).copy(alpha = 0.9f), Color(0xFF171C30).copy(alpha = 0.9f))))
            drawPath(roof, Color.White.copy(alpha = 0.16f), style = Stroke(1.2f * density))
        }
        Floor(top, "Ložnice") { DevTop(d, t) }
        Spacer(Modifier.height(3.dp))
        Floor(bottom, "Obývák") { DevBottom(d, t) }
    }
}

@Composable
private fun Floor(r: Room?, fallback: String, devices: @Composable () -> Unit) {
    val c = tempColor(r?.temp)
    Row(
        Modifier
            .padding(horizontal = 8.dp).width(216.dp).height(46.dp)
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.40f), c.copy(alpha = 0.10f))), rounded(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), rounded(12.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            BasicText(r?.name ?: fallback, style = ts(10.sp, 700, C.dim), maxLines = 1)
            BasicText(r?.temp?.let { "%.1f°".format(Locale("cs"), it) } ?: "–", style = ts(19.sp, 800, spacing = (-0.5).sp))
        }
        Box(Modifier.weight(1f).height(40.dp)) { devices() }
        r?.hum?.let { hum ->
            val warn = hum >= 65 || hum <= 35
            BasicText("${Math.round(hum)} %", style = ts(12.sp, 700, if (warn) Color(0xFFFCA5A5) else Color(0xFFBAE6FD)))
        }
    }
}

/** Ložnice: klimatizace (proudění podle režimu) a telka, na kterou se právě díváš. */
@Composable
private fun DevTop(d: HouseDev?, t: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val s = density * 0.72f
        // klimatizace – bílá „věž“ s výdechem
        val on = d?.klimaOn == true
        val mode = d?.klimaMode ?: ""
        val ax = 2 * s; val ay = 10 * s
        val a = if (on) 1f else 0.55f
        drawRoundRect(Color(0xFFE8ECF6).copy(alpha = a), Offset(ax, ay), Size(20 * s, 34 * s), CornerRadius(5 * s))
        drawRoundRect(Color(0xFF9AA4BF).copy(alpha = a), Offset(ax + 3 * s, ay + 3 * s), Size(14 * s, 4 * s), CornerRadius(2 * s))
        drawRect(Color(0xFF1E2440).copy(alpha = a), Offset(ax, ay + 13 * s), Size(20 * s, 5 * s))
        val led = when { !on -> Color(0xFF4B5370); mode == "heat" -> C.pink; mode == "dry" -> Color(0xFF5EEAD4); mode == "fan" -> Color(0xFFF1F5F9); else -> C.cyan }
        drawCircle(led, 1.4f * s, Offset(ax + 15 * s, ay + 15.5f * s))
        if (on) airflow(mode, Offset(ax + 22 * s, ay + 2 * s), t)
        // telka – svítí, protože se na ni zrovna díváš
        val tx = size.width - 46 * s; val ty = 10 * s
        drawCircle(Brush.radialGradient(listOf(C.indigo.copy(alpha = 0.35f), Color.Transparent), Offset(tx + 22 * s, ty + 14 * s), 34 * s), 34 * s, Offset(tx + 22 * s, ty + 14 * s))
        drawRoundRect(Color(0xFF0B1020), Offset(tx, ty), Size(44 * s, 27 * s), CornerRadius(3 * s))
        drawRoundRect(Brush.linearGradient(listOf(C.indigo, C.cyan), Offset(tx, ty), Offset(tx + 44 * s, ty + 27 * s)), Offset(tx + 2 * s, ty + 2 * s), Size(40 * s, 23 * s), CornerRadius(2 * s), alpha = 0.55f + 0.1f * sin(t * 2 * PI.toFloat() * 3))
        drawLine(Color(0xFF6B7393), Offset(tx + 22 * s, ty + 27 * s), Offset(tx + 22 * s, ty + 33 * s), 2 * s)
        drawLine(Color(0xFF6B7393), Offset(tx + 14 * s, ty + 33 * s), Offset(tx + 30 * s, ty + 33 * s), 2 * s, StrokeCap.Round)
    }
}

private fun DrawScope.airflow(mode: String, o: Offset, t: Float) {
    val s = density * 0.72f
    for (i in 0 until 3) {
        val p = (t * 2f + i / 3f) % 1f
        val fade = (if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f).coerceIn(0f, 1f)
        when (mode) {
            "heat" -> { // teplé vlnky stoupají
                val x = o.x + 2 * s + i * 7 * s; val y = o.y + 8 * s - p * 18 * s
                val path = Path().apply { moveTo(x, y); quadraticTo(x + 3 * s, y - 3 * s, x, y - 6 * s); quadraticTo(x - 3 * s, y - 9 * s, x, y - 12 * s) }
                drawPath(path, Color(0xFFF9A8D4).copy(alpha = fade), style = Stroke(1.6f * s, cap = StrokeCap.Round))
            }
            "fan" -> { // vítr do strany
                val y = o.y + 4 * s + i * 6 * s; val x = o.x + p * 26 * s
                drawLine(Color(0xFFE2E8F0).copy(alpha = fade), Offset(x, y), Offset(x + 12 * s, y), 1.5f * s, StrokeCap.Round)
            }
            "dry" -> { // kapky padají
                val x = o.x + 6 * s + i * 8 * s; val y = o.y + p * 30 * s
                drawCircle(Color(0xFF5EEAD4).copy(alpha = fade), 2.4f * s, Offset(x, y))
            }
            else -> { // chlazení – vločky padají
                val c = Offset(o.x + 6 * s + i * 8 * s + p * 6 * s, o.y + p * 32 * s)
                val col = Color(0xFFBAE6FD).copy(alpha = fade)
                for (k in 0 until 3) {
                    val ang = k * PI.toFloat() / 3
                    val dx = 3.2f * s * kotlin.math.cos(ang); val dy = 3.2f * s * sin(ang)
                    drawLine(col, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), 1.2f * s, StrokeCap.Round)
                }
            }
        }
    }
}

/** Obývák: vysavač (parkuje vlevo, při úklidu jezdí k pračce) a pračka (pere = poskakuje, buben se točí). */
@Composable
private fun DevBottom(d: HouseDev?, t: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val s = density * 0.72f
        val floorY = size.height - 6 * s
        // pračka vpravo
        val wm = d?.wm ?: "off"
        val run = wm == "run"
        val hop = if (run) sin(t * 2 * PI.toFloat() * 4) else 0f
        val wx = size.width - 40 * s + hop * 1.8f * s
        val wy = floorY - 34 * s - abs(hop) * 1.2f * s
        val a = if (wm == "off") 0.55f else 1f
        drawRoundRect(Color(0xFFE8ECF6).copy(alpha = a), Offset(wx, wy), Size(32 * s, 34 * s), CornerRadius(4 * s))
        drawRoundRect(Color(0xFFCFD5E6).copy(alpha = a), Offset(wx, wy), Size(32 * s, 7 * s), CornerRadius(4 * s))
        val dc = Offset(wx + 16 * s, wy + 21 * s)
        drawCircle(Color(0xFF1E2440).copy(alpha = a), 9.5f * s, dc)
        val drum = when (wm) { "run", "pause" -> Color(0xFF7DD3FC); "done" -> Color(0xFF4ADE80); else -> Color(0xFF4B5370) }
        if (wm == "done") drawCircle(drum, 6.5f * s, dc, style = Stroke(1.6f * s))
        else for (k in 0 until 6) { // přerušovaný buben, při praní se točí
            val start = k * 60f + if (run) t * 360f * 3 else 0f
            drawArc(drum, start, 34f, false, Offset(dc.x - 6.5f * s, dc.y - 6.5f * s), Size(13 * s, 13 * s), style = Stroke(1.6f * s))
        }
        // vysavač – zaparkovaný vlevo, při úklidu jezdí sem a tam
        val k = if (d?.vac == true) (0.5f - 0.5f * kotlin.math.cos(t * 2 * PI.toFloat())) else 0f
        val vx = 10 * s + k * (size.width - 100 * s)
        val vc = Offset(vx + 10 * s, floorY - 8 * s)
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(vc.x - 10 * s, floorY - 2 * s), Size(20 * s, 4 * s))
        drawCircle(Color(0xFF1E2440), 9 * s, vc)
        drawCircle(Color(0xFFC4B5FD), 9 * s, vc, style = Stroke(1.2f * s))
        val blink = if (d?.vac == true) 0.5f + 0.5f * sin(t * 2 * PI.toFloat() * 6) else 0f
        drawCircle(if (d?.vac == true) C.cyan.copy(alpha = 0.5f + 0.5f * blink) else Color(0xFF4B5370), 3 * s, Offset(vc.x, vc.y - 3 * s))
    }
}
