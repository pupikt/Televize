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

    /** Pošle telce stisk tlačítka přes IP control. Vrací HTTP kód (200 = OK, 403 = špatný klíč, -1 = nejde). */
    fun key(psk: String, k: String): Int = runCatching {
        val code = KEYS[k] ?: return -1
        val c = URL("http://127.0.0.1/sony/IRCC").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 2000; c.readTimeout = 3000
        c.setRequestProperty("Content-Type", "text/xml; charset=UTF-8")
        c.setRequestProperty("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
        if (psk.isNotBlank()) c.setRequestProperty("X-Auth-PSK", psk)
        val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$code</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
        c.outputStream.use { it.write(body.toByteArray()) }
        val r = c.responseCode
        runCatching { (if (r < 400) c.inputStream else c.errorStream)?.close() }
        r
    }.getOrDefault(-1)
}
