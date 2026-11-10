package cz.pupik.televize.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
private val INK = Color(0xFF0B1020)

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
    // teploty a co běží doma – každé 2 minuty (jen když je telka propojená s Domácností)
    val link by cz.pupik.televize.night.Link.state.collectAsState()
    LaunchedEffect(link.paired) {
        if (!link.paired && !previewMode) return@LaunchedEffect
        while (true) { runCatching { ui.loadHouse() }; delay(120_000) }
    }

    // klidový režim: po chvíli nečinnosti zůstanou jen hodiny, počasí a záře
    val awake by animateFloatAsState(if (ui.idle) 0f else 1f, tween(1400, easing = FastOutSlowInEasing), label = "idle")
    // spuštění aplikace: obrazovka se „propadne“ do dlaždice
    val launch by animateFloatAsState(if (ui.launching != null) 0f else 1f, tween(260), label = "launch")

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.25f + 0.75f * launch }) {
        // ---- hodiny + počasí ----
        Row(Modifier.fillMaxWidth().padding(start = 58.dp, end = 54.dp, top = 34.dp), verticalAlignment = Alignment.Top) {
            Column(
                Modifier.enter(0).graphicsLayer {
                    val k = 1f + 0.30f * (1f - awake)
                    scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f)
                }
            ) {
                Row {
                    val hm = SimpleDateFormat("H:mm", CS).format(now)
                    Roll(hm, ts(88.sp, 220, spacing = (-2.5).sp))
                }
                BasicText(
                    SimpleDateFormat("EEEE d. MMMM", CS).format(now).replaceFirstChar { it.uppercase() },
                    style = ts(19.sp, 500, C.dim),
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Box(Modifier.enter(90)) { WeatherCard(ui) }
                ui.house?.let { hs ->
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.enter(160)) { HouseStrip(hs) }
                }
            }
        }

        // ---- oblíbené aplikace ----
        Column(Modifier.align(Alignment.CenterStart).padding(top = 92.dp).graphicsLayer { alpha = awake }) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(horizontal = 52.dp, vertical = 28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.favApps.isEmpty()) item {
                    Focusable(onClick = { ui.screen = Screen.All }, focusMod = Modifier.focusRequester(first)) { f ->
                        Box(Modifier.size(196.dp, 110.dp).glass(rounded(24.dp), f), contentAlignment = Alignment.Center) {
                            BasicText("Vybrat aplikace", style = ts(18.sp, 700))
                        }
                    }
                }
                itemsIndexed(ui.favApps, key = { _, a -> a.pkg }) { i, app ->
                    Box(Modifier.enter(170 + i * 70, 40f)) {
                        AppTile(
                            app, 196.dp, 110.dp, ui,
                            onClick = { ui.open(app) },
                            onLong = { ui.moveFav(app, -1) },
                            mod = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
            // název vybrané aplikace (nebo pozdrav)
            val title = ui.focused?.label ?: greeting(hour)
            AnimatedContent(
                title,
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith fadeOut(tween(120)) },
                label = "title",
                modifier = Modifier.padding(start = 58.dp).enter(420),
            ) { t ->
                BasicText(t, style = ts(26.sp, 750, spacing = (-0.3).sp))
            }
        }

        // ---- lišta ----
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp).enter(520, 40f).graphicsLayer { alpha = awake },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!ui.accOn) {
                BasicText(
                    "Ztlumení a tlačítko Domů zatím spí – zapni je ve „Večer“.",
                    style = ts(14.sp, 500, C.faint),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Dock(ui, now)
        }
    }
}

private fun greeting(h: Int) = when (h) {
    in 5..9 -> "Dobré ráno"
    in 10..17 -> "Dobrý den"
    in 18..21 -> "Dobrý večer"
    else -> "Dobrou noc"
}

/** Dlaždice aplikace: logo pod sklem, při najetí se zvedne, rozzáří barvou aplikace a přejede po ní odlesk. */
@Composable
fun AppTile(
    app: TvApp, w: Dp, h: Dp, ui: Ui,
    onClick: () -> Unit, onLong: (() -> Unit)? = null,
    mod: Modifier = Modifier, star: Boolean = false, scale: Float = 1.14f,
) {
    val shape = rounded(if (w > 170.dp) 24.dp else 20.dp)
    val launching = ui.launching == app.pkg
    val boom by animateFloatAsState(if (launching) 1.22f else 1f, tween(260, easing = FastOutSlowInEasing), label = "boom")
    Focusable(
        Modifier.graphicsLayer { scaleX = boom; scaleY = boom },
        onClick = onClick, onLong = onLong, scale = scale, focusMod = mod,
        onFocus = { ui.focused = app }, id = app.pkg,
    ) { f ->
        val elev by animateDpAsState(if (f) 30.dp else 6.dp, tween(260), label = "e")
        val sweep = remember { Animatable(0f) }
        LaunchedEffect(f) {
            if (f) { sweep.snapTo(0f); sweep.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) } else sweep.snapTo(0f)
        }
        val halo by animateFloatAsState(if (f) 1f else 0f, tween(350), label = "halo")
        Box(
            Modifier
                .size(w, h)
                .drawBehind {
                    // záře v barvě aplikace kolem vybrané dlaždice
                    if (halo > 0.01f) {
                        // zploštělá, aby se vešla do řady (LazyRow ořezává)
                        val r = size.width * 0.72f
                        val ky = (size.height / 2 + 24.dp.toPx()) / r
                        scale(1f, ky, center) {
                            drawCircle(
                                Brush.radialGradient(listOf(app.tint.copy(alpha = 0.60f * halo), app.tint.copy(alpha = 0.18f * halo), Color.Transparent), center, r),
                                r, center,
                            )
                        }
                    }
                }
                .shadow(elev, shape, ambientColor = if (f) app.tint else Color.Black, spotColor = if (f) app.tint else Color.Black)
                .clip(shape)
                .background(Color(0xFF12162A))
                .drawWithContent {
                    drawContent()
                    // stálý lesk skla
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = if (f) 0.16f else 0.07f), Color.Transparent), Offset.Zero, Offset(size.width * 0.6f, size.height)))
                    // odlesk, který přejede při najetí
                    val p = sweep.value
                    if (p > 0f && p < 1f) {
                        val x = -size.width * 0.6f + p * size.width * 2.2f
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                                Offset(x, 0f), Offset(x + size.width * 0.45f, size.height),
                            )
                        )
                    }
                    if (!f) drawRect(Color.Black.copy(alpha = 0.18f)) // nevybrané lehce ustoupí
                }
                .border(
                    if (f) 2.dp else 1.dp,
                    Brush.verticalGradient(
                        if (f) listOf(Color.White, Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.8f))
                        else listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.14f))
                    ),
                    shape,
                ),
        ) {
            val b = app.banner
            if (b != null) {
                Image(b, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(app.tint.copy(alpha = 0.45f), Color(0xFF12162A)))),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    app.icon?.let { Image(it, null, Modifier.size(h * 0.46f)) }
                    Spacer(Modifier.height(6.dp))
                    BasicText(app.label, style = ts(13.sp, 700), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
            if (star) Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp).background(Color.Black.copy(alpha = .6f), pill()),
                contentAlignment = Alignment.Center,
            ) { LineIcon("star", 14.dp, Color(0xFFFDE68A)) }
        }
    }
}

/** Plovoucí skleněná lišta dole – jako v iOS. */
@Composable
private fun Dock(ui: Ui, now: Date) {
    val n = ui.night
    val active = n.level < 0.999f || n.sleepEnd > 0
    val nightLabel = when {
        n.sleepEnd > 0 -> "Usne za ${((n.sleepEnd - now.time).coerceAtLeast(0) / 60_000 + 1)} min"
        n.level < 0.999f -> "Jas ${Math.round(n.level * 100)} %"
        else -> "Večer"
    }
    Row(Modifier.glass(pill(), strong = true).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        DockItem("moon", nightLabel, ui, active, "dock-night") { ui.nightOpen = true }
        DockItem("grid", "Aplikace", ui, id = "dock-apps") { ui.screen = Screen.All }
        DockItem("gear", "Nastavení", ui, id = "dock-set") { ui.screen = Screen.Settings }
        if (ui.update != null) DockItem("down", if (ui.updating != null) "${Math.round(ui.updating!! * 100)} %" else "Nová verze", ui, true, "dock-up") { ui.installUpdate() }
    }
}

@Composable
private fun DockItem(icon: String, label: String, ui: Ui, active: Boolean = false, id: String? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, scale = 1.06f, onFocus = { ui.focused = null }, id = id) { f ->
        Row(
            Modifier
                .then(if (f) Modifier.shadow(18.dp, pill(), ambientColor = Color.White, spotColor = Color.White).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE3FF))), pill()) else Modifier)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = if (f) INK else if (active) Color(0xFFC7D2FE) else C.text
            LineIcon(icon, 19.dp, c)
            Spacer(Modifier.width(10.dp))
            BasicText(label, style = ts(16.sp, if (f) 800 else 650, c))
        }
    }
}

/** Tlačítko-pilulka: vybrané je bílé s tmavým textem (na telce je to vidět na dálku). */
@Composable
fun PillButton(icon: String, label: String, highlight: Boolean = false, mod: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, scale = 1.06f, focusMod = mod) { f ->
        Row(
            Modifier
                .then(
                    if (f) Modifier.shadow(16.dp, pill(), ambientColor = Color.White, spotColor = Color.White).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE3FF))), pill())
                    else Modifier.glass(pill(), tint = if (highlight) C.indigo else Color.White, strong = highlight)
                )
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = if (f) INK else if (highlight) Color(0xFFC7D2FE) else C.text
            LineIcon(icon, 19.dp, c)
            Spacer(Modifier.width(10.dp))
            BasicText(label, style = ts(16.sp, if (f) 800 else 650, c))
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, mod: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, scale = 1.1f, focusMod = mod) { f ->
        Box(
            Modifier
                .then(
                    when {
                        f -> Modifier.shadow(16.dp, pill(), ambientColor = Color.White, spotColor = Color.White).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE3FF))), pill())
                        selected -> Modifier.background(Brush.horizontalGradient(listOf(C.indigo, C.cyan)), pill()).border(1.dp, Color.White.copy(alpha = .5f), pill())
                        else -> Modifier.glass(pill())
                    }
                )
                .padding(horizontal = 20.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = ts(16.sp, if (selected || f) 800 else 600, if (f) INK else C.text))
        }
    }
}

/** Pruh „Doma“ pod počasím: teploty místností a co zrovna běží (z Domácnosti). */
@Composable
private fun HouseStrip(h: House) {
    Row(Modifier.glass(pill()).padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        LineIcon("home", 18.dp, Color(0xFF7DD3FC))
        h.rooms.forEach { r ->
            Spacer(Modifier.width(14.dp))
            BasicText(r.name, style = ts(14.sp, 600, C.dim))
            Spacer(Modifier.width(6.dp))
            BasicText(r.temp?.let { "%.1f°".format(Locale("cs"), it) } ?: "–", style = ts(16.sp, 800))
        }
        h.running.forEach { t ->
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(6.dp).background(Color(0xFF4ADE80), pill()))
            Spacer(Modifier.width(7.dp))
            BasicText(t, style = ts(14.sp, 700, Color(0xFFBAE6FD)), maxLines = 1)
        }
    }
}

@Composable
private fun WeatherCard(ui: Ui) {
    val w = ui.weather
    Row(Modifier.glass(rounded(30.dp)).padding(horizontal = 24.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        if (w == null) {
            BasicText("Načítám počasí…", style = ts(16.sp, 500, C.dim))
            return@Row
        }
        WeatherIcon(w.code, w.day, 58.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Roll("${w.temp}°", ts(42.sp, 300, spacing = (-1).sp))
            BasicText("${WeatherApi.describe(w.code)} · ${ui.city}", style = ts(14.sp, 600, C.dim))
            BasicText("↑ ${w.max}°   ↓ ${w.min}°", style = ts(13.sp, 600, C.faint))
        }
        if (w.hours.isNotEmpty()) {
            Spacer(Modifier.width(22.dp))
            Box(Modifier.width(1.dp).height(68.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .22f), Color.Transparent))))
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
        enter = fadeIn(tween(250)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 220f)) { it / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 3 },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .30f), Color.Black.copy(alpha = .72f)))), contentAlignment = Alignment.BottomCenter) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
            Column(Modifier.padding(bottom = 44.dp).width(790.dp).glass(rounded(36.dp), strong = true, solid = true).padding(horizontal = 36.dp, vertical = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon("moon", 26.dp, Color(0xFFC7D2FE))
                    Spacer(Modifier.width(12.dp))
                    BasicText("Večer u telky", style = ts(26.sp, 800))
                }
                if (!ui.accOn) {
                    Spacer(Modifier.height(16.dp))
                    BasicText(
                        "Aby šlo ztlumit obraz i v YouTube a Netflixu, zapni jednou službu Televize: Usnadnění → Televize → Zapnout.",
                        style = ts(17.sp, 500, C.dim),
                    )
                    Spacer(Modifier.height(18.dp))
                    PillButton("gear", "Otevřít Usnadnění", true, Modifier.focusRequester(first)) { ui.openAccessibility() }
                    return@Column
                }
                val n = ui.night
                Spacer(Modifier.height(22.dp))
                BasicText("Jas obrazu", style = ts(15.sp, 700, C.dim))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(1f, .7f, .5f, .35f, .2f, .1f).forEachIndexed { i, l ->
                        val sel = n.sleepEnd == 0L && kotlin.math.abs(n.level - l) < .03f
                        Chip(if (l >= .999f) "Plný" else "${Math.round(l * 100)} %", sel, if (i == 0) Modifier.focusRequester(first) else Modifier) { ui.setLevel(l) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                BasicText("Usínání", style = ts(15.sp, 700, C.dim))
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
