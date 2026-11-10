package cz.pupik.televize.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class TvApp(
    val pkg: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

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
                out += TvApp(pkg, label, banner, icon)
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
