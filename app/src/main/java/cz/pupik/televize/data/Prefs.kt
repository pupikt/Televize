package cz.pupik.televize.data

import android.content.Context
import android.content.SharedPreferences

/** Všechno, co si Televize pamatuje (jen v telce). */
class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.applicationContext.getSharedPreferences("tv", Context.MODE_PRIVATE)

    /** Oblíbené aplikace na hlavní obrazovce (balíčky v pořadí); null = ještě nevybráno → výchozí. */
    var favs: List<String>?
        get() = p.getString("favs", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) = p.edit().putString("favs", v?.joinToString(",")).apply()

    var city: String
        get() = p.getString("city", "Olomouc")!!
        set(v) = p.edit().putString("city", v).apply()
    var lat: Double
        get() = p.getFloat("lat", 49.5938f).toDouble()
        set(v) = p.edit().putFloat("lat", v.toFloat()).apply()
    var lon: Double
        get() = p.getFloat("lon", 17.2509f).toDouble()
        set(v) = p.edit().putFloat("lon", v.toFloat()).apply()

    /** Klíč „IP control“ telky Sony – záložní způsob vypnutí. */
    var psk: String
        get() = p.getString("psk", "")!!
        set(v) = p.edit().putString("psk", v.trim()).apply()

    /** Tlačítko Domů otevře Televizi místo obrazovky Sony. */
    var takeHome: Boolean
        get() = p.getBoolean("home", true)
        set(v) = p.edit().putBoolean("home", v).apply()

    /** Pohyblivá polární záře v pozadí. */
    var aurora: Boolean
        get() = p.getBoolean("aurora", true)
        set(v) = p.edit().putBoolean("aurora", v).apply()

    /** Jas 0..1 (1 = neztlumeno) a kdy se nastavil. */
    var level: Float
        get() = p.getFloat("level", 1f)
        set(v) = p.edit().putFloat("level", v).putLong("levelAt", System.currentTimeMillis()).apply()
    val levelAt: Long get() = p.getLong("levelAt", 0)

    var weatherCache: String?
        get() = p.getString("wcache", null)
        set(v) = p.edit().putString("wcache", v).apply()
}
