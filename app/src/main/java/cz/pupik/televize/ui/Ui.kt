package cz.pupik.televize.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.pupik.televize.data.Apps
import cz.pupik.televize.data.Place
import cz.pupik.televize.data.Prefs
import cz.pupik.televize.data.Release
import cz.pupik.televize.data.TvApp
import cz.pupik.televize.data.Updater
import cz.pupik.televize.data.Weather
import cz.pupik.televize.data.WeatherApi
import cz.pupik.televize.night.Night
import cz.pupik.televize.night.NightService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Home, All, Settings }

/** Stav celé obrazovky. */
class Ui(private val ctx: Context, private val scope: CoroutineScope) {
    val prefs = Prefs(ctx)

    var screen by mutableStateOf(Screen.Home)
    var nightOpen by mutableStateOf(false)
    var homeFocusTick by mutableStateOf(0)

    /** Aplikace, na které je zrovna ovladač (podle ní svítí pozadí). */
    var focused by mutableStateOf<TvApp?>(null)
    /** Balíček, který se právě spouští (animace). */
    var launching by mutableStateOf<String?>(null)
    /** Klidový režim – po chvíli nečinnosti zůstanou jen hodiny. */
    var idle by mutableStateOf(false)
    var lastKey = System.currentTimeMillis()

    var apps by mutableStateOf<List<TvApp>>(emptyList())
    var favPkgs by mutableStateOf<List<String>>(emptyList())
    val favApps: List<TvApp> get() = favPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }

    var weather by mutableStateOf<Weather?>(null)
    var city by mutableStateOf(prefs.city)

    var night by mutableStateOf(Night())
    var accOn by mutableStateOf(false)
    var takeHome by mutableStateOf(prefs.takeHome)
    var aurora by mutableStateOf(prefs.aurora)

    var update by mutableStateOf<Release?>(null)
    var updating by mutableStateOf<Float?>(null)
    var updateMsg by mutableStateOf("")

    // ---------- aplikace ----------
    fun reloadApps() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { Apps.load(ctx) }
            apps = list
            val stored = prefs.favs
            favPkgs = (stored ?: Apps.defaultFavs(list)).filter { p -> list.any { it.pkg == p } }
            if (stored == null && favPkgs.isNotEmpty()) prefs.favs = favPkgs
        }
    }

    fun open(app: TvApp) {
        if (launching != null) return
        launching = app.pkg
        scope.launch {
            delay(230)
            Apps.launch(ctx, app.pkg)
            delay(900)
            launching = null
        }
    }

    fun toggleFav(app: TvApp) {
        favPkgs = if (app.pkg in favPkgs) favPkgs - app.pkg else favPkgs + app.pkg
        prefs.favs = favPkgs
    }

    /** Podržení OK na hlavní obrazovce posune aplikaci o místo doleva (první jde na konec). */
    fun moveFav(app: TvApp, dir: Int) {
        val l = favPkgs.toMutableList()
        val i = l.indexOf(app.pkg); if (i < 0) return
        val j = (i + dir + l.size) % l.size
        l.removeAt(i); l.add(j, app.pkg)
        favPkgs = l; prefs.favs = l
    }

    // ---------- počasí ----------
    fun loadWeather(force: Boolean = false) {
        val cached = prefs.weatherCache
        if (weather == null && cached != null) runCatching {
            val at = cached.substringBefore('|').toLong()
            weather = WeatherApi.parse(cached.substringAfter('|'), at)
        }
        val w = weather
        if (!force && w != null && System.currentTimeMillis() - w.at < 20 * 60_000) return
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { WeatherApi.fetch(prefs.lat, prefs.lon) }
                val now = System.currentTimeMillis()
                weather = WeatherApi.parse(json, now)
                prefs.weatherCache = "$now|$json"
            }
        }
    }

    fun setPlace(p: Place) {
        prefs.city = p.name; prefs.lat = p.lat; prefs.lon = p.lon
        city = p.name
        prefs.weatherCache = null; weather = null
        loadWeather(true)
    }

    // ---------- večer ----------
    fun setLevel(l: Float) { NightService.instance?.let { it.startSleep(0); it.setLevel(l) } }
    fun sleep(min: Int) { NightService.instance?.startSleep(min) }
    fun powerOff() { NightService.instance?.powerOff() ?: run { cz.pupik.televize.night.Bravia.let { b -> scope.launch(Dispatchers.IO) { b.powerOff(prefs.psk) } } } }

    fun openAccessibility() {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openTvSettings() {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ---------- aktualizace ----------
    fun checkUpdate(manual: Boolean = false) {
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { Updater.check() } }
            update = r.getOrNull()
            if (manual) updateMsg = when {
                r.isFailure -> "Nepodařilo se spojit s GitHubem."
                update == null -> "Máš nejnovější verzi."
                else -> ""
            }
        }
    }

    fun installUpdate() {
        val r = update ?: return
        if (updating != null) return
        updating = 0f
        scope.launch {
            val f = runCatching { withContext(Dispatchers.IO) { Updater.download(ctx, r) { p -> updating = p } } }.getOrNull()
            updating = null
            if (f == null) { updateMsg = "Stažení se nepovedlo."; return@launch }
            runCatching { Updater.install(ctx, f) }.onFailure { updateMsg = "Instalace se nespustila." }
        }
    }
}
