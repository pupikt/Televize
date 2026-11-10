package cz.pupik.televize.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pupik.televize.data.TvApp
import cz.pupik.televize.data.WeatherApi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CS = Locale("cs", "CZ")

@Composable
fun HomeScreen(ui: Ui) {
    val first = remember { FocusRequester() }
    LaunchedEffect(ui.favApps.size, ui.homeFocusTick) {
        delay(60); runCatching { first.requestFocus() }
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(60_000 - System.currentTimeMillis() % 60_000 + 50) }
    }
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)

    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 34.dp, bottom = 30.dp)) {
        // ---- hodiny + počasí ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column {
                BasicText(SimpleDateFormat("H:mm", CS).format(now), style = ts(84.sp, 250, spacing = (-2).sp))
                BasicText(
                    SimpleDateFormat("EEEE d. MMMM", CS).format(now).replaceFirstChar { it.uppercase() },
                    style = ts(20.sp, 500, C.dim),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            WeatherCard(ui)
        }

        Spacer(Modifier.weight(1f))

        BasicText(greeting(hour), style = ts(22.sp, 700), modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))

        // ---- oblíbené aplikace ----
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.favApps.isEmpty()) item {
                Focusable(onClick = { ui.screen = Screen.All }, focusMod = Modifier.focusRequester(first)) { f ->
                    Box(Modifier.size(192.dp, 108.dp).glass(rounded(22.dp), f), contentAlignment = Alignment.Center) {
                        BasicText("Vybrat aplikace", style = ts(18.sp, 700))
                    }
                }
            }
            itemsIndexed(ui.favApps, key = { _, a -> a.pkg }) { i, app ->
                AppCard(
                    app, big = true,
                    onClick = { ui.open(app) },
                    onLong = { ui.moveFav(app, -1) },
                    mod = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }

        Spacer(Modifier.weight(0.6f))

        // ---- večer + ostatní ----
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val n = ui.night
            val dimLabel = when {
                n.sleepEnd > 0 -> "Usne za ${((n.sleepEnd - now.time).coerceAtLeast(0) / 60_000 + 1)} min"
                n.level < 0.999f -> "Ztlumeno · ${Math.round(n.level * 100)} %"
                else -> "Večer u telky"
            }
            PillButton("moon", dimLabel, highlight = n.level < 0.999f || n.sleepEnd > 0) { ui.nightOpen = true }
            PillButton("grid", "Všechny aplikace") { ui.screen = Screen.All }
            PillButton("gear", "Nastavení") { ui.screen = Screen.Settings }
            if (ui.update != null) PillButton("down", if (ui.updating != null) "Stahuji ${Math.round(ui.updating!! * 100)} %" else "Nová verze", highlight = true) { ui.installUpdate() }
        }
        if (!ui.accOn) {
            Spacer(Modifier.height(14.dp))
            BasicText(
                "Ztlumení a tlačítko Domů zatím nefungují – zapni službu v Nastavení → Usnadnění.",
                style = ts(15.sp, 500, C.faint),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun greeting(h: Int) = when (h) {
    in 5..9 -> "Dobré ráno"
    in 10..17 -> "Dobrý den"
    in 18..21 -> "Dobrý večer"
    else -> "Dobrou noc"
}

@Composable
fun AppCard(app: TvApp, big: Boolean, onClick: () -> Unit, onLong: (() -> Unit)? = null, mod: Modifier = Modifier, star: Boolean = false) {
    val w = if (big) 192.dp else 152.dp
    val h = if (big) 108.dp else 86.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(onClick = onClick, onLong = onLong, scale = 1.12f, focusMod = mod) { f ->
            Box(Modifier.size(w, h).glass(rounded(22.dp), f, tint = if (f) C.cyan else Color.White)) {
                val b = app.banner
                if (b != null) {
                    Image(b, app.label, Modifier.fillMaxSize().padding(3.dp).clip(rounded(19.dp)), contentScale = ContentScale.Crop)
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        app.icon?.let { Image(it, null, Modifier.size(h * 0.48f)) }
                        Spacer(Modifier.height(6.dp))
                        BasicText(app.label, style = ts(13.sp, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // skleněný lesk přes logo
                Box(
                    Modifier.fillMaxSize().clip(rounded(22.dp)).background(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = if (f) 0.20f else 0.08f), Color.Transparent, Color.Transparent))
                    )
                )
                if (star) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Color.Black.copy(alpha = .55f), pill()), contentAlignment = Alignment.Center) {
                    LineIcon("star", 16.dp, Color(0xFFFDE68A))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BasicText(app.label, style = ts(14.sp, 600, C.dim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(w))
    }
}

@Composable
fun PillButton(icon: String, label: String, highlight: Boolean = false, mod: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, scale = 1.07f, focusMod = mod) { f ->
        Row(
            Modifier.glass(pill(), f, tint = if (highlight) C.indigo else Color.White, strong = highlight).padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(icon, 20.dp, if (highlight) Color(0xFFC7D2FE) else C.text)
            Spacer(Modifier.width(10.dp))
            BasicText(label, style = ts(17.sp, 650))
        }
    }
}

@Composable
private fun WeatherCard(ui: Ui) {
    val w = ui.weather
    Row(Modifier.glass(rounded(28.dp)).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (w == null) {
            BasicText("Načítám počasí…", style = ts(16.sp, 500, C.dim))
            return@Row
        }
        WeatherIcon(w.code, w.day, 60.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            BasicText("${w.temp}°", style = ts(44.sp, 300, spacing = (-1).sp))
            BasicText("${WeatherApi.describe(w.code)} · ${ui.city}", style = ts(14.sp, 600, C.dim))
            BasicText("↑ ${w.max}°   ↓ ${w.min}°", style = ts(13.sp, 600, C.faint))
        }
        if (w.hours.isNotEmpty()) {
            Spacer(Modifier.width(22.dp))
            Box(Modifier.width(1.dp).height(70.dp).background(Color.White.copy(alpha = .14f)))
            Spacer(Modifier.width(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                w.hours.forEach { hr ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(hr.time.removePrefix("0"), style = ts(13.sp, 600, C.faint))
                        Spacer(Modifier.height(2.dp))
                        WeatherIcon(hr.code, hr.day, 30.dp)
                        Spacer(Modifier.height(2.dp))
                        BasicText("${hr.temp}°", style = ts(15.sp, 700))
                    }
                }
            }
        }
    }
}

/** Panel „Večer u telky“: jas a usínání. */
@Composable
fun NightSheet(ui: Ui) {
    AnimatedVisibility(
        ui.nightOpen,
        enter = fadeIn(tween(250)) + slideInVertically(tween(380)) { it / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 3 },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Black.copy(alpha = .75f)))), contentAlignment = Alignment.BottomCenter) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
            Column(Modifier.padding(bottom = 48.dp).width(780.dp).glass(rounded(34.dp), strong = true).padding(horizontal = 36.dp, vertical = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon("moon", 26.dp, Color(0xFFC7D2FE))
                    Spacer(Modifier.width(12.dp))
                    BasicText("Večer u telky", style = ts(26.sp, 800))
                }
                if (!ui.accOn) {
                    Spacer(Modifier.height(16.dp))
                    BasicText(
                        "Aby šlo ztlumit obraz i v YouTube a Netflixu, zapni jednou službu Televize: Nastavení → Usnadnění → Televize → Zapnout.",
                        style = ts(17.sp, 500, C.dim),
                    )
                    Spacer(Modifier.height(18.dp))
                    PillButton("gear", "Otevřít Usnadnění", true, Modifier.focusRequester(first)) { ui.openAccessibility() }
                    return@Column
                }
                val n = ui.night
                Spacer(Modifier.height(22.dp))
                BasicText("Jas obrazu", style = ts(16.sp, 700, C.dim))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(1f, .7f, .5f, .35f, .2f, .1f).forEachIndexed { i, l ->
                        val sel = n.sleepEnd == 0L && kotlin.math.abs(n.level - l) < .03f
                        Chip(if (l >= .999f) "Plný" else "${Math.round(l * 100)} %", sel, if (i == 0) Modifier.focusRequester(first) else Modifier) { ui.setLevel(l) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                BasicText("Usínání", style = ts(16.sp, 700, C.dim))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(0, 15, 30, 45, 60, 90).forEach { m ->
                        val sel = if (m == 0) n.sleepEnd == 0L else n.sleepMin == m && n.sleepEnd > 0
                        Chip(if (m == 0) "Ne" else "$m min", sel) { ui.sleep(m) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                val txt = when {
                    n.sleepEnd > 0 -> "Obraz bude pomalu tmavnout a za ${n.sleepMin} min se telka sama vypne. Dobrou noc."
                    n.level < .999f -> "Obraz je ztlumený všude – i v YouTube a Netflixu. Ráno se jas vrátí sám."
                    else -> "Tip: modré tlačítko na ovladači mění jas kdykoli, i uprostřed filmu."
                }
                BasicText(txt, style = ts(16.sp, 500, C.dim))
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PillButton("power", "Vypnout telku teď") { ui.powerOff() }
                    PillButton("check", "Hotovo") { ui.nightOpen = false }
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, mod: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, scale = 1.1f, focusMod = mod) { f ->
        Box(
            Modifier
                .glass(pill(), f, tint = if (selected) C.indigo else Color.White, strong = selected)
                .then(if (selected) Modifier.background(Brush.verticalGradient(listOf(C.indigo.copy(alpha = .55f), C.cyan.copy(alpha = .30f))), pill()) else Modifier)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = ts(17.sp, if (selected) 800 else 600), modifier = Modifier.alpha(if (selected || f) 1f else .85f))
        }
    }
}
