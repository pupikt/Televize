package cz.pupik.televize.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pupik.televize.BuildConfig
import cz.pupik.televize.data.Place
import cz.pupik.televize.data.WeatherApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AllAppsScreen(ui: Ui) {
    val first = remember { FocusRequester() }
    LaunchedEffect(ui.apps.size) { delay(60); runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 40.dp)) {
        BasicText("Všechny aplikace", style = ts(40.sp, 800))
        Spacer(Modifier.height(6.dp))
        BasicText("Podrž OK = přidat na hlavní obrazovku nebo odebrat (★).", style = ts(17.sp, 500, C.dim))
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            GridCells.Fixed(5),
            contentPadding = PaddingValues(top = 18.dp, bottom = 48.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(ui.apps, key = { _, a -> a.pkg }) { i, app ->
                Column(Modifier.enter(60 + (i % 5) * 40 + (i / 5) * 50), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppTile(
                        app, 152.dp, 86.dp, ui,
                        onClick = { ui.open(app) },
                        onLong = { ui.toggleFav(app) },
                        mod = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        star = app.pkg in ui.favPkgs, scale = 1.12f,
                    )
                    Spacer(Modifier.height(9.dp))
                    BasicText(app.label, style = ts(14.sp, 600, C.dim), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.width(152.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(ui: Ui) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var places by remember { mutableStateOf<List<Place>?>(null) }
    var psk by remember { mutableStateOf(ui.prefs.psk) }
    var msg by remember { mutableStateOf("") }

    fun search() {
        val q = query
        if (q.isBlank()) return
        scope.launch {
            places = runCatching { withContext(Dispatchers.IO) { WeatherApi.search(q) } }.getOrElse { emptyList() }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 40.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BasicText("Nastavení", style = ts(40.sp, 800))
            Spacer(Modifier.height(10.dp))
        }
        item {
            Section("Služba Televize", if (ui.accOn) "Zapnutá – ztlumení, modré tlačítko, usínání i tlačítko Domů fungují." else "Vypnutá. Zapni ji: Usnadnění → Televize → Zapnout.") {
                PillButton("gear", if (ui.accOn) "Usnadnění" else "Zapnout v Usnadnění", !ui.accOn, Modifier.focusRequester(first)) { ui.openAccessibility() }
            }
        }
        item {
            Section("Tlačítko Domů", if (ui.takeHome) "Otevře tuhle obrazovku místo obrazovky Sony." else "Otevře původní obrazovku Sony.") {
                PillButton("grid", if (ui.takeHome) "Vypnout" else "Zapnout") { ui.takeHome = !ui.takeHome; ui.prefs.takeHome = ui.takeHome }
            }
        }
        item {
            Section("Pohyblivé pozadí", if (ui.aurora) "Polární záře se pomalu vlní." else "Pozadí stojí (šetří výkon).") {
                PillButton("moon", if (ui.aurora) "Zastavit" else "Rozpohybovat") { ui.aurora = !ui.aurora; ui.prefs.aurora = ui.aurora }
            }
        }
        item {
            Section("Počasí", "Teď: ${ui.city}. Napiš obec a zmáčkni potvrdit na klávesnici.") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(query, { query = it }, "Název obce", Modifier.width(320.dp)) { search() }
                    PillButton("down", "Hledat") { search() }
                }
                places?.let { list ->
                    Spacer(Modifier.height(12.dp))
                    if (list.isEmpty()) BasicText("Nic jsem nenašel.", style = ts(15.sp, 500, C.dim))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.take(4).forEach { p ->
                            Chip("${p.name}${if (p.region.isNotBlank()) " · " + p.region.removeSuffix(" kraj") else ""}", false) {
                                ui.setPlace(p); places = null; query = ""
                            }
                        }
                    }
                }
            }
        }
        item {
            Section(
                "Vypínání při usínání",
                "Telka se vypne sama přes službu Televize. Kdyby nezhasla, jde to záložně přes „IP control“: " +
                    "Nastavení telky → Síť → Nastavení domácí sítě → IP control → Ověřování: Normální a sdílený klíč, a sem napiš stejný Sdílený klíč.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(psk, { psk = it }, "Sdílený klíč (nepovinné)", Modifier.width(320.dp)) { ui.prefs.psk = psk; msg = "Uloženo." }
                    PillButton("down", "Uložit") { ui.prefs.psk = psk; msg = "Uloženo." }
                    PillButton("power", "Vyzkoušet vypnutí") { ui.prefs.psk = psk; ui.powerOff() }
                }
                if (msg.isNotEmpty()) { Spacer(Modifier.height(8.dp)); BasicText(msg, style = ts(15.sp, 600, C.cyan)) }
            }
        }
        item {
            val running = cz.pupik.televize.night.NightService.instance != null
            Section("Záznam služby", if (running) "Služba běží." else "Služba teď neběží.") {
                val lines = ui.prefs.log.takeLast(12).reversed()
                if (lines.isEmpty()) BasicText("Zatím nic.", style = ts(14.sp, 500, C.faint))
                lines.forEach { BasicText(it, style = ts(13.sp, 500, C.dim)) }
            }
        }
        item {
            Section("Telka Sony", "Obraz, zvuk, síť a další původní nastavení.") {
                PillButton("gear", "Otevřít nastavení telky") { ui.openTvSettings() }
            }
        }
        item {
            Section("Verze ${BuildConfig.VERSION_NAME}", if (ui.update != null) "Je k dispozici nová verze." else "Aktualizace se hledají samy při každém otevření.") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ui.update != null) PillButton("down", if (ui.updating != null) "Stahuji ${Math.round(ui.updating!! * 100)} %" else "Nainstalovat novou verzi", true) { ui.installUpdate() }
                    else PillButton("down", "Zkontrolovat") { ui.checkUpdate(true) }
                }
                if (ui.updateMsg.isNotEmpty()) { Spacer(Modifier.height(8.dp)); BasicText(ui.updateMsg, style = ts(15.sp, 600, C.cyan)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, text: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(rounded(28.dp)).padding(horizontal = 28.dp, vertical = 22.dp)) {
        BasicText(title, style = ts(21.sp, 800))
        Spacer(Modifier.height(4.dp))
        BasicText(text, style = ts(16.sp, 500, C.dim))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, mod: Modifier, onDone: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    BasicTextField(
        value, onChange,
        singleLine = true,
        textStyle = ts(18.sp, 600),
        cursorBrush = SolidColor(C.cyan),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = mod.onFocusChanged { f = it.isFocused },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().glass(pill(), f).padding(horizontal = 22.dp, vertical = 13.dp)) {
                if (value.isEmpty()) BasicText(hint, style = ts(18.sp, 500, C.faint))
                inner()
            }
        },
    )
}

