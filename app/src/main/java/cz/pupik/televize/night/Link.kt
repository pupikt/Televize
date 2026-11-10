package cz.pupik.televize.night

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import cz.pupik.televize.BuildConfig
import cz.pupik.televize.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/** Stav propojení s Domácností (pro Nastavení). */
data class LinkState(val paired: Boolean = false, val code: String? = null, val ok: Boolean = false, val error: String? = null)

/**
 * Propojení s Domácností (Tomova aplikace, https://domacnost.dilna.workers.dev).
 * Telka je za routerem → sama se každé ~3 s ptá serveru: pošle stav, dostane příkazy.
 */
object Link {
    const val SERVER = "https://domacnost.dilna.workers.dev"
    val state = MutableStateFlow(LinkState())

    private var thread: HandlerThread? = null
    private var h: Handler? = null
    private var svc: NightService? = null
    private lateinit var prefs: Prefs

    fun start(s: NightService) {
        svc = s
        prefs = Prefs(s)
        state.value = LinkState(paired = prefs.linkPaired, code = prefs.linkCode)
        if (thread != null) return
        thread = HandlerThread("domacnost").apply { start() }
        h = Handler(thread!!.looper)
        h!!.post(tick)
    }

    fun stop() {
        h?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null; h = null; svc = null
    }

    /** Nový kód pro spárování – ukáže se v Nastavení, Tom ho napíše v Domácnosti. */
    fun startPairing(ctx: Context) {
        val p = Prefs(ctx)
        val rnd = SecureRandom()
        val token = ByteArray(32).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val code = (100000 + rnd.nextInt(900000)).toString()
        p.linkToken = token; p.linkCode = code; p.linkPaired = false
        state.value = LinkState(paired = false, code = code)
        Thread {
            val r = runCatching { post("/api/tv/hello", JSONObject().put("code", code).put("h", sha(token)).toString(), null) }
            if (r.isFailure || r.getOrNull()?.first != 200) state.value = state.value.copy(error = "Server Domácnosti teď neodpovídá – zkus to znovu.")
        }.start()
        h?.removeCallbacks(tick); h?.post(tick)
    }

    fun unpair(ctx: Context) {
        val p = Prefs(ctx)
        p.linkToken = ""; p.linkCode = null; p.linkPaired = false
        state.value = LinkState()
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            var next = 3000L
            runCatching { next = sync() }
            h?.postDelayed(this, next)
        }
    }

    /** Jedno kolo: pošle stav, provede příkazy. Vrací, za kolik ms znovu. */
    private fun sync(): Long {
        val s = svc ?: return 10_000
        val token = prefs.linkToken
        if (token.isBlank()) return 10_000
        val pm = s.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return 15_000 // telka zhasnutá – nic neposílat
        val (code, body) = runCatching { post("/api/tv/sync", s.snapshot().toString(), token) }.getOrElse {
            state.value = state.value.copy(ok = false, error = "Bez spojení s Domácností")
            return 10_000
        }
        if (code == 401) {
            // ještě nespárováno (čeká se na kód), nebo Tom telku v Domácnosti odpojil
            if (prefs.linkPaired) { prefs.linkPaired = false; prefs.linkToken = ""; state.value = LinkState(error = "Domácnost telku odpojila.") }
            return 4000
        }
        if (code != 200) { state.value = state.value.copy(ok = false, error = "Domácnost hlásí chybu $code"); return 10_000 }
        if (!prefs.linkPaired) { prefs.linkPaired = true; prefs.linkCode = null; s.log("propojeno s Domácností") }
        state.value = LinkState(paired = true, ok = true)
        val cmds = runCatching { JSONObject(body).optJSONArray("cmds") }.getOrNull() ?: JSONArray()
        for (i in 0 until cmds.length()) {
            val c = cmds.optJSONObject(i) ?: continue
            s.runCommand(c)
        }
        return if (cmds.length() > 0) 1500 else 3000
    }

    private fun post(path: String, json: String, token: String?): Pair<Int, String> {
        val c = URL(SERVER + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 10_000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "Televize/${BuildConfig.VERSION_CODE}")
        if (token != null) c.setRequestProperty("X-TV-Key", token)
        c.outputStream.use { it.write(json.toByteArray()) }
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to body
    }

    private fun sha(t: String) = MessageDigest.getInstance("SHA-256").digest(t.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Hlasitost v % (Sony: STREAM_MUSIC = reproduktory telky). */
    fun volume(ctx: Context): Pair<Int, Boolean> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val muted = runCatching { am.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false)
        return Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max) to muted
    }
}
