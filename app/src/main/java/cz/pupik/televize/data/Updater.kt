package cz.pupik.televize.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cz.pupik.televize.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Release(val code: Int, val url: String)

/** Aktualizace z GitHubu (repo pupikt/televize, release s tagem vN a souborem televize.apk). */
object Updater {
    const val REPO = "pupikt/televize"

    fun check(): Release? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Televize")
        if (c.responseCode != 200) return null
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val code = o.optString("tag_name").removePrefix("v").toIntOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return if (code > BuildConfig.VERSION_CODE) Release(code, a.getString("browser_download_url")) else null
            }
        }
        return null
    }

    /** Stáhne APK; vrátí soubor. */
    fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val f = File(dir, "televize.apk")
        var url = r.url
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "Televize")
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) { url = c.getHeaderField("Location"); continue }
            break
        }
        val total = c.contentLengthLong.coerceAtLeast(1)
        c.inputStream.use { inp ->
            f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = inp.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        return f
    }

    fun install(ctx: Context, f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
        }
        val uri = FileProvider.getUriForFile(ctx, "cz.pupik.televize.files", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
