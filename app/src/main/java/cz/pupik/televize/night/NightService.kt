package cz.pupik.televize.night

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import cz.pupik.televize.MainActivity
import cz.pupik.televize.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.thread
import kotlin.math.pow

/** Stav večerního režimu pro obrazovku. */
data class Night(val level: Float = 1f, val sleepEnd: Long = 0L, val sleepMin: Int = 0)

/**
 * Služba usnadnění:
 *  - černá průsvitná vrstva přes celý obraz = ztlumení (funguje i v YouTube / Netflixu),
 *  - modré tlačítko na ovladači mění jas,
 *  - časovač usínání: obraz pomalu tmavne a nakonec se telka vypne,
 *  - tlačítko Domů: když se ukáže obrazovka Sony/Google, otevře se Televize.
 */
class NightService : AccessibilityService() {

    companion object {
        val LEVELS = floatArrayOf(1f, 0.7f, 0.5f, 0.35f, 0.2f, 0.1f)
        val state = MutableStateFlow(Night())
        @Volatile var instance: NightService? = null

        /** Co služba naposledy viděla (pro Nastavení – ladění na dálku). */
        @Volatile var lastSeen = ""
        @Volatile var lastHomeKey = 0L
        private val KNOWN_HOME = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.apps.tv.launcherx",
        )
        private const val LIVE_TV = "com.sony.dtv.tvx"

        fun isEnabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, NightService::class.java).flattenToString()
            return list.split(':').any { it.equals(me, true) || it.equals(me.replace("cz.pupik.televize/cz.pupik.televize.", "cz.pupik.televize/."), true) }
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private var veil: View? = null
    private var badge: TextView? = null
    private var level = 1f
    private var sleepEnd = 0L
    private var sleepStart = 0L
    private var sleepFrom = 1f
    private var sleepMin = 0
    private var foreground = ""
    private var blueDown = false
    private val redirects = ArrayList<Long>()

    /** Všechny domovské obrazovky v telce kromě naší (Sony jich může mít víc). */
    private val stockHome: Set<String> by lazy {
        val pm = packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val found = pm.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toMutableSet()
        found += KNOWN_HOME
        found -= packageName
        found -= setOf("com.android.settings", "com.android.tv.settings", "com.android.provision")
        found
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    override fun onServiceConnected() {
        instance = this
        Prefs(this).log("služba zapnutá")
        // chytat i tlačítko Domů přímo (spolehlivější než čekat na menu Sony)
        serviceInfo = serviceInfo?.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // Ztlumení z minula vydrží jen do rána (6 h).
        level = if (System.currentTimeMillis() - prefs.levelAt < 6 * 3600_000L) prefs.level else 1f
        addVeil()
        apply()
        Link.start(this)
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
    }

    override fun onDestroy() {
        instance = null
        runCatching { prefs.log("služba skončila") }
        Link.stop()
        runCatching { unregisterReceiver(screen) }
        h.removeCallbacksAndMessages(null)
        runCatching { veil?.let { wm.removeView(it) } }
        runCatching { badge?.let { wm.removeView(it) } }
        state.value = Night()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    // ---------- vrstva ----------
    private fun overlayParams(w: Int, hgt: Int) = WindowManager.LayoutParams(
        w, hgt,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )

    private fun addVeil() {
        val v = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0f; visibility = View.GONE }
        runCatching { wm.addView(v, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)) }
            .onSuccess { veil = v }
    }

    private fun apply() {
        val v = veil
        if (v != null) {
            val a = (1f - level).coerceIn(0f, 1f)
            v.animate().cancel()
            if (a <= 0.001f) {
                v.animate().alpha(0f).setDuration(350).withEndAction { v.visibility = View.GONE }.start()
            } else {
                v.visibility = View.VISIBLE
                v.animate().alpha(a).setDuration(350).start()
            }
        }
        state.value = Night(level, sleepEnd, sleepMin)
    }

    fun setLevel(l: Float, save: Boolean = true) {
        level = l.coerceIn(0f, 1f)
        if (save) prefs.level = level
        if (sleepEnd > 0 && save) { sleepFrom = level; sleepStart = SystemClock.elapsedRealtime(); }
        apply()
    }

    // ---------- usínání ----------
    fun startSleep(minutes: Int) {
        h.removeCallbacks(sleepTick)
        if (minutes <= 0) {
            sleepEnd = 0; sleepMin = 0
            level = prefs.level.takeIf { System.currentTimeMillis() - prefs.levelAt < 6 * 3600_000L } ?: 1f
            apply(); return
        }
        sleepMin = minutes
        sleepFrom = level
        sleepStart = SystemClock.elapsedRealtime()
        sleepEnd = System.currentTimeMillis() + minutes * 60_000L
        apply()
        h.postDelayed(sleepTick, 2000)
    }

    private val sleepTick = object : Runnable {
        override fun run() {
            if (sleepEnd == 0L) return
            val total = sleepMin * 60_000f
            val p = ((SystemClock.elapsedRealtime() - sleepStart) / total).coerceIn(0f, 1f)
            // Prvních 40 % času beze změny, pak pomalu do černa.
            val q = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            level = sleepFrom * (1f - q).pow(1.6f)
            if (p >= 1f) {
                level = 0f
                apply()
                sleepEnd = 0; sleepMin = 0
                powerOff()
                return
            }
            apply()
            h.postDelayed(this, 2000)
        }
    }

    /** Vypne telku: uspání přes službu usnadnění, záložně „IP control“. */
    fun powerOff() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val ok = runCatching { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }.getOrDefault(false)
        h.postDelayed({
            if (pm.isInteractive) {
                val psk = prefs.psk
                if (psk.isNotBlank()) thread { Bravia.powerOff(psk) }
            }
        }, if (ok) 4000 else 0)
    }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Telka zhasla → po zapnutí ať je obraz zase normální.
                    h.removeCallbacks(sleepTick)
                    sleepEnd = 0; sleepMin = 0
                    if (level < 0.05f) { level = 1f; prefs.level = 1f }
                    apply()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (System.currentTimeMillis() - prefs.levelAt > 6 * 3600_000L && level < 1f) {
                        level = 1f; apply()
                    }
                }
            }
        }
    }

    // ---------- ukazatel jasu ----------
    private fun showBadge(text: String) {
        val b = badge ?: TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            val px = (resources.displayMetrics.density)
            setPadding((26 * px).toInt(), (12 * px).toInt(), (26 * px).toInt(), (12 * px).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 40 * px
                setColor(Color.argb(200, 18, 22, 40))
                setStroke((1.5f * px).toInt(), Color.argb(110, 255, 255, 255))
            }
            alpha = 0f
            val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                x = (40 * px).toInt(); y = (32 * px).toInt()
            }
            runCatching { wm.addView(this, lp) }
            badge = this
        }
        b.text = text
        b.animate().cancel()
        b.animate().alpha(1f).setDuration(180).start()
        h.removeCallbacks(hideBadge)
        h.postDelayed(hideBadge, 1800)
    }

    private val hideBadge = Runnable { badge?.animate()?.alpha(0f)?.setDuration(400)?.start() }

    // ---------- Domácnost ----------
    fun log(msg: String) = h.post { prefs.log(msg) }

    private fun labelOf(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Co telka zrovna dělá – posílá se do Domácnosti (volá se z vlákna Link). */
    fun snapshot(): JSONObject {
        val (vol, muted) = Link.volume(this)
        val apps = JSONArray()
        (prefs.favs ?: emptyList()).forEach { p -> apps.put(JSONObject().put("pkg", p).put("label", labelOf(p))) }
        val fg = foreground
        return JSONObject()
            .put("screen", true)
            .put("pkg", fg)
            .put("label", if (fg.isBlank()) "" else labelOf(fg))
            .put("level", Math.round(level * 100) / 100.0)
            .put("sleepEnd", sleepEnd)
            .put("sleepMin", sleepMin)
            .put("vol", vol)
            .put("muted", muted)
            .put("apps", apps)
            .put("ver", cz.pupik.televize.BuildConfig.VERSION_CODE)
            .put("psk", prefs.psk.isNotBlank())
            .put("irccInfo", irccInfo ?: JSONObject.NULL)
            .put("irccWhy", irccWhy ?: JSONObject.NULL)
            .put("irccPair", irccPair ?: if (prefs.irccCookie.isNotBlank()) "ok" else JSONObject.NULL)
            .put("ip", Bravia.hosts().firstOrNull { it != "127.0.0.1" } ?: JSONObject.NULL)
            .put("ircc", ircc ?: if (prefs.psk.isBlank()) "nopsk" else JSONObject.NULL)
    }

    /** Jak dopadlo poslední tlačítko přes IP control: ok / nopsk / fail. */
    @Volatile var ircc: String? = null
    @Volatile var irccInfo: String? = null
    @Volatile var irccWhy: String? = null
    @Volatile var irccPair: String? = null

    /** Příkaz z Domácnosti. Volá se z vlákna Link: tlačítka jdou rovnou odsud (síť), ostatní na hlavní vlákno. */
    fun runCommand(c: JSONObject) {
        when (c.optString("t")) {
            "irccPair" -> {
                // telka ukáže na obrazovce PIN
                val (rc, ck) = Bravia.register(null, null)
                if (rc == 200 && ck != null) { prefs.irccCookie = ck; irccPair = "ok"; ircc = null; irccWhy = null }
                else irccPair = if (rc == 401) "pin" else "fail:$rc"
                log("párování ovladače: $rc"); return
            }
            "irccPin" -> {
                val (rc, ck) = Bravia.register(c.optString("v").trim(), null)
                if (rc == 200 && ck != null) { prefs.irccCookie = ck; irccPair = "ok"; ircc = null; irccWhy = null; irccInfo = null }
                else irccPair = if (rc == 401) "badpin" else "fail:$rc"
                log("PIN ovladače: $rc"); return
            }
            "psk" -> { prefs.psk = c.optString("v"); ircc = null; irccInfo = null; irccWhy = null; log("klíč IP control uložen z Domácnosti"); return }
            "key" -> {
                val k = c.optString("k")
                if (k == "back") { h.post { performGlobalAction(GLOBAL_ACTION_BACK) }; return }
                if (k == "home") { h.post { goHome() }; return }
                val psk = prefs.psk
                if (psk.isBlank() && prefs.irccCookie.isBlank()) { ircc = "nopsk"; return }
                var r = Bravia.key(psk, k, prefs.irccCookie)
                if (r.code == 403 && prefs.irccCookie.isNotBlank()) {
                    // cookie vypršela (platí cca 2 týdny) → obnovit bez PINu a zkusit znovu
                    val (rc, ck) = Bravia.register(null, prefs.irccCookie)
                    if (rc == 200 && ck != null) { prefs.irccCookie = ck; r = Bravia.key(psk, k, ck) }
                    else { prefs.irccCookie = ""; irccPair = null }
                }
                ircc = if (r.code == 200) "ok" else "fail"
                irccInfo = if (r.code == 200) null else r.detail
                irccWhy = if (r.code == 200) null else r.why
                if (r.code != 200) log("tlačítko $k: ${r.detail}")
                return
            }
        }
        h.post { runOnMain(c) }
    }

    private fun runOnMain(c: JSONObject) {
        val t = c.optString("t")
        prefs.log("z Domácnosti: $t")
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        runCatching {
            when (t) {
                "off" -> powerOff()
                "home" -> goHome()
                "open" -> {
                    val pkg = c.optString("pkg")
                    if (!cz.pupik.televize.data.Apps.launch(this, pkg)) prefs.log("aplikace $pkg nejde spustit")
                }
                "level" -> { startSleep(0); setLevel(c.optDouble("v", 1.0).toFloat()) }
                "sleep" -> startSleep(c.optInt("min", 0))
                "vol" -> {
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(c.optDouble("v", 0.0) / 100.0 * max).toInt(), AudioManager.FLAG_SHOW_UI)
                }
                "volStep" -> am.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (c.optInt("d") > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI,
                )
                "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                "playpause" -> {
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                }
                else -> {}
            }
        }.onFailure { prefs.log("chyba příkazu $t: ${it.message}") }
    }

    // ---------- tlačítka a Domů ----------
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_HOME && prefs.takeHome) {
            lastHomeKey = System.currentTimeMillis()
            if (e.action == KeyEvent.ACTION_UP) { prefs.log("Domů (z $foreground)"); goHome() }
            return true
        }
        if (e.keyCode != KeyEvent.KEYCODE_PROG_BLUE) return false
        if (foreground == LIVE_TV) return false // v živém vysílání patří barevná tlačítka teletextu
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) blueDown = true
            return true
        }
        if (e.action == KeyEvent.ACTION_UP && blueDown) {
            blueDown = false
            val i = LEVELS.indexOfFirst { kotlin.math.abs(it - level) < 0.03f }
            val next = if (i < 0) LEVELS[1] else LEVELS[(i + 1) % LEVELS.size]
            setLevel(next)
            showBadge(if (next >= 0.999f) "☀  Plný jas" else "☾  Jas ${Math.round(next * 100)} %")
            return true
        }
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return
        foreground = pkg
        lastSeen = pkg + " / " + (e.className ?: "")
        if (pkg in stockHome && prefs.takeHome) {
            // chvilku počkat, ať se nepřetahujeme s menu Sony, které se teprve otevírá
            h.removeCallbacks(redirect)
            h.postDelayed(redirect, 250)
        }
    }

    private val redirect = Runnable {
        if (foreground !in stockHome) return@Runnable
        val now = SystemClock.elapsedRealtime()
        redirects.removeAll { now - it > 15_000 }
        if (redirects.size >= 6) { prefs.log("pojistka: $foreground se pořád vrací – nechávám ho"); return@Runnable }
        redirects += now
        prefs.log("přesměrování z $foreground")
        goHome()
    }
}
