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
}
