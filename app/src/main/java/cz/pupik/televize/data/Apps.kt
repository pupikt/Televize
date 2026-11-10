package cz.pupik.televize.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import cz.pupik.televize.R
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class TvApp(
    val pkg: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    /** Barva, kterou aplikace „svítí“ do pozadí, když je vybraná. */
    val tint: Color = Color(0xFF818CF8),
)

/** Vlastní dlaždice z originálních log (tools/tiles.py). */
private class Brand(val key: String, val art: Int, val tint: Color)

private val BRANDS = listOf(
    Brand("youtube", R.drawable.tile_youtube, Color(0xFFFF2D55)),
    Brand("netflix", R.drawable.tile_netflix, Color(0xFFE50914)),
    Brand("disney", R.drawable.tile_disney, Color(0xFF22B8D6)),
    Brand("oneplay", R.drawable.tile_oneplay, Color(0xFF5B5BF0)),
)

/** Průměrná (trochu sytější) barva obrázku – pro záři v pozadí. */
private fun average(img: ImageBitmap?): Color {
    if (img == null) return Color(0xFF818CF8)
    return runCatching {
        val b = Bitmap.createScaledBitmap(img.asAndroidBitmap(), 8, 8, true)
        var r = 0f; var g = 0f; var bl = 0f; var n = 0f
        for (y in 0 until 8) for (x in 0 until 8) {
            val c = b.getPixel(x, y)
            val hsv = FloatArray(3); AColor.colorToHSV(c, hsv)
            val w = 0.15f + hsv[1] * hsv[2]            // syté a světlé body váží víc
            r += AColor.red(c) * w; g += AColor.green(c) * w; bl += AColor.blue(c) * w; n += w
        }
        val hsv = FloatArray(3)
        AColor.RGBToHSV((r / n).toInt(), (g / n).toInt(), (bl / n).toInt(), hsv)
        if (hsv[1] < 0.18f) return Color(0xFF818CF8)   // šedivé logo → výchozí indigo
        hsv[1] = (hsv[1] * 1.3f).coerceIn(0.45f, 0.9f); hsv[2] = hsv[2].coerceIn(0.7f, 1f)
        Color(AColor.HSVToColor(hsv))
    }.getOrDefault(Color(0xFF818CF8))
}

object Apps {
    /** Výchozí oblíbené: YouTube, Netflix, Disney+, Oneplay (hledá se podle názvu i balíčku). */
    private val DEFAULT = listOf("youtube", "netflix", "disney", "oneplay")

    fun load(ctx: Context): List<TvApp> {
        val pm = ctx.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<TvApp>()
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        )
        for (it in intents) {
            for (ri in pm.queryIntentActivities(it, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || !seen.add(pkg)) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val banner = runCatching {
                    (ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm))
                        ?.let { d -> d.toBitmap(320, 180).asImageBitmap() }
                }.getOrNull()
                val icon = runCatching { ri.loadIcon(pm)?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
                val key = (label + " " + pkg).lowercase()
                val brand = BRANDS.firstOrNull { b ->
                    key.contains(b.key) && !(b.key == "youtube" && pkg != "com.google.android.youtube.tv" && !label.equals("YouTube", true))
                }
                if (brand != null) {
                    val art = runCatching { BitmapFactory.decodeResource(ctx.resources, brand.art).asImageBitmap() }.getOrNull()
                    out += TvApp(pkg, label, art ?: banner, icon, brand.tint)
                } else {
                    out += TvApp(pkg, label, banner, icon, average(banner ?: icon))
                }
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun defaultFavs(apps: List<TvApp>): List<String> {
        val res = ArrayList<String>()
        for (k in DEFAULT) {
            val hit = when (k) {
                "youtube" -> apps.firstOrNull { it.pkg == "com.google.android.youtube.tv" }
                    ?: apps.firstOrNull { it.label.equals("YouTube", true) }
                else -> null
            } ?: apps.firstOrNull { it.label.lowercase().contains(k) || it.pkg.lowercase().contains(k) }
            if (hit != null && hit.pkg !in res) res += hit.pkg
        }
        return res
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm: PackageManager = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(i) }.isSuccess
    }
}
