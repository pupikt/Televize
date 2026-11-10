package cz.pupik.televize.night

import java.net.HttpURLConnection
import java.net.URL

/** Záložní vypnutí přes „IP control“ telky Sony (volá sama sebe na 127.0.0.1). */
object Bravia {
    fun powerOff(psk: String): Boolean = runCatching {
        if (psk.isBlank()) return false
        val c = URL("http://127.0.0.1/sony/system").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 4000; c.readTimeout = 6000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("X-Auth-PSK", psk)
        c.outputStream.use {
            it.write("""{"method":"setPowerStatus","id":55,"params":[{"status":false}],"version":"1.0"}""".toByteArray())
        }
        val body = (if (c.responseCode < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.responseCode == 200 && !body.contains("\"error\"")
    }.getOrDefault(false)

    /** Kódy tlačítek ovladače Sony (IRCC). */
    val KEYS = mapOf(
        "up" to "AAAAAQAAAAEAAAB0Aw==",
        "down" to "AAAAAQAAAAEAAAB1Aw==",
        "left" to "AAAAAQAAAAEAAAA0Aw==",
        "right" to "AAAAAQAAAAEAAAAzAw==",
        "ok" to "AAAAAQAAAAEAAABlAw==",
        "back" to "AAAAAgAAAJcAAAAjAw==",
        "menu" to "AAAAAgAAAJcAAAA2Aw==",
        "play" to "AAAAAgAAAJcAAAAaAw==",
        "pause" to "AAAAAgAAAJcAAAAZAw==",
        "rew" to "AAAAAgAAAJcAAAAbAw==",
        "ff" to "AAAAAgAAAJcAAAAcAw==",
    )

    /** Adresy, na kterých telka může poslouchat: sama sebe, pak vlastní adresa v domácí síti. */
    fun hosts(): List<String> {
        val out = mutableListOf("127.0.0.1")
        runCatching {
            for (ni in java.net.NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) if (a is java.net.Inet4Address && !a.isLoopbackAddress) out += a.hostAddress
            }
        }
        return out.distinct()
    }

    @Volatile private var goodHost: String? = null

    /** Výsledek posledního tlačítka: HTTP kód (200 = OK, 401/403 = klíč), -1 = telka neodpovídá; text = podrobnosti. */
    class Result(val code: Int, val detail: String)

    /** Pošle telce stisk tlačítka přes IP control. */
    fun key(psk: String, k: String): Result {
        val code = KEYS[k] ?: return Result(-1, "neznámé tlačítko")
        val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$code</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
        val tries = listOfNotNull(goodHost) + hosts().filter { it != goodHost }
        var last = Result(-1, "nic neodpovídá")
        for (host in tries) {
            val r = runCatching {
                val c = URL("http://$host/sony/IRCC").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 1500; c.readTimeout = 3000
                c.setRequestProperty("Content-Type", "text/xml; charset=UTF-8")
                c.setRequestProperty("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
                if (psk.isNotBlank()) c.setRequestProperty("X-Auth-PSK", psk)
                c.outputStream.use { it.write(body.toByteArray()) }
                val rc = c.responseCode
                val txt = runCatching { (if (rc < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                Result(rc, "$host: HTTP $rc ${txt.take(120).replace(Regex("\\s+"), " ")}")
            }.getOrElse { Result(-1, "$host: ${it.javaClass.simpleName} ${it.message ?: ""}".take(160)) }
            last = r
            if (r.code == 200) { goodHost = host; return r }
            if (r.code in 400..499) return r // telka odpověděla – jen nechce (klíč / nastavení)
        }
        return last
    }
}
