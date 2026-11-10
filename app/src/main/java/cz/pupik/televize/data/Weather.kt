package cz.pupik.televize.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Hour(val time: String, val temp: Int, val code: Int, val day: Boolean)
class Weather(
    val temp: Int,
    val code: Int,
    val day: Boolean,
    val max: Int,
    val min: Int,
    val hours: List<Hour>,
    val at: Long,
)

class Place(val name: String, val region: String, val lat: Double, val lon: Double)

/** Počasí z Open-Meteo (zdarma, bez klíče). */
object WeatherApi {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Televize")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    fun fetch(lat: Double, lon: Double): String = get(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2"
    )

    fun parse(json: String, at: Long): Weather {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val curTime = cur.getString("time") // "2026-11-10T21:45"
        val h = o.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code")
        val days = h.getJSONArray("is_day")
        val hours = ArrayList<Hour>()
        val curHour = curTime.substring(0, 13)
        var start = -1
        for (i in 0 until times.length()) if (times.getString(i).substring(0, 13) > curHour) { start = i; break }
        if (start >= 0) {
            var i = start
            while (i < times.length() && hours.size < 4) {
                hours += Hour(times.getString(i).substring(11, 16), Math.round(temps.getDouble(i)).toInt(), codes.getInt(i), days.getInt(i) == 1)
                i += 3
            }
        }
        val d = o.getJSONObject("daily")
        return Weather(
            temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
            code = cur.getInt("weather_code"),
            day = cur.getInt("is_day") == 1,
            max = Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            min = Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            hours = hours,
            at = at,
        )
    }

    fun search(name: String): List<Place> {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=cs&countryCode=CZ"))
        val arr = o.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map {
            val r = arr.getJSONObject(it)
            Place(r.getString("name"), r.optString("admin1", ""), r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Jasno"
        1 -> "Skoro jasno"
        2 -> "Polojasno"
        3 -> "Zataženo"
        45, 48 -> "Mlha"
        51, 53, 55 -> "Mrholení"
        56, 57, 66, 67 -> "Mrznoucí déšť"
        61 -> "Slabý déšť"
        63 -> "Déšť"
        65 -> "Silný déšť"
        71 -> "Slabé sněžení"
        73, 77 -> "Sněžení"
        75 -> "Husté sněžení"
        80, 81 -> "Přeháňky"
        82 -> "Prudké přeháňky"
        85, 86 -> "Sněhové přeháňky"
        95, 96, 99 -> "Bouřka"
        else -> ""
    }
}
