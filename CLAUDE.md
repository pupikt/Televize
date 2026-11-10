# Televize

Paměť projektu. Přečti celé na začátku práce a po větší změně sem připiš, co a proč.

## Kdo a jak
- Tomáš Pupík – úplný začátečník, neprogramuje. Piš **česky, hovorově, krátce**, návody **po jednom malém kroku**, přesně kam kliknout (na ovladači).
- Samostatný projekt – nemíchat s Domácností (`pupikt/Domacnost2`), Dílnou ani webem. Jen vzhled je ve stejném duchu (noční modrá, polární záře, Liquid Glass – Tom je fanda Applu).
- Tajné klíče a hesla nikdy nechtít do chatu.

## Telka
- **Sony KD-49XD8077** (2016), Android TV **9** (BRAVIA_ATV2_EU), slabý procesor → nic těžkého na pozadí. Ve stejné síti má IP 192.168.1.105.
- Tom používá hlavně **YouTube, Netflix, Disney+, Oneplay**; chtěl hodiny, počasí a hlavně **ztlumení jasu večer, když u telky usínají** (Sony to v menu rychle neumí).

## Co to je (10. 11. 2026)
- Vlastní **hlavní obrazovka (launcher)** – Kotlin + Jetpack Compose, `app/`, balíček `cz.pupik.televize`, minSdk 26, targetSdk 28, ~1 MB.
- `MainActivity` (HOME + LEANBACK_LAUNCHER), stav v `ui/Ui.kt`, obrazovky `ui/Home.kt` (hodiny, počasí, oblíbené aplikace, tlačítka), `ui/Screens.kt` (Všechny aplikace, Nastavení), `ui/Kit.kt` (barvy, písmo Manrope, `Modifier.glass`, `Focusable` = ovládání ovladačem: OK = klik, podržet OK = dlouhý stisk), `ui/Aurora.kt` (pozadí), `ui/Icons.kt` (kreslené ikony počasí a tlačítek).
- **Oblíbené**: výchozí se najdou podle názvu (youtube/netflix/disney/oneplay), ve Všech aplikacích podržet OK = přidat/odebrat (★), na úvodu podržet OK = posunout doleva. Ukládá `Prefs.favs`.
- **Počasí**: Open-Meteo (bez klíče), výchozí Olomouc (Tomova adresa není známá – nehádat), obec jde změnit v Nastavení. Cache 20 min.
- **Služba usnadnění `night/NightService`** (Tom ji musí jednou zapnout: Nastavení → Usnadnění → Televize):
  - **ztlumení** = černá průsvitná vrstva `TYPE_ACCESSIBILITY_OVERLAY` přes všechno (i YouTube/Netflix); úrovně 100/70/50/35/20/10 %; ráno (po 6 h) se jas vrátí sám;
  - **modré tlačítko** na ovladači (KEYCODE_PROG_BLUE) cykluje jas + ukazatel vpravo nahoře; v živém vysílání (`com.sony.dtv.tvx`) se nechytá (teletext);
  - **usínání** 15–90 min: 40 % času beze změny, pak pomalu do černa a vypnout: `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` (Android 9 uspí zařízení), když telka nezhasne a je nastavený klíč → `Bravia.powerOff` (IP control na 127.0.0.1, hlavička X-Auth-PSK);
  - **tlačítko Domů**: služba chytá přímo KEYCODE_HOME (filtr kláves) a otevře Televizi; záložně když se objeví jakákoli jiná domovská appka (seznam z `queryIntentActivities(CATEGORY_HOME)` + známé `com.google.android.tvlauncher` …), přesměruje (pojistka max 4× za 10 s). Tom 10. 11.: po zapnutí v Usnadnění to zprvu nešlo, pak „už to jede“ – přímé chytání Domů přidáno kvůli spolehlivosti, zatím neověřené. Pak Tom: Domů z videa v YouTube → zůstal na staré obrazovce Sony, pomohlo až vypnout/zapnout telku (měl ještě verzi bez chytání klávesy; nejspíš se přesměrování přetahovalo s menu Sony a vypnula ho pojistka). Přesměrování teď čeká 250 ms, pojistka 6× za 15 s, a služba zapisuje **Záznam služby** (Nastavení, `Prefs.log`) – při potížích chtít od Toma fotku toho záznamu.
  - Nic z toho zatím **neověřeno na skutečné telce** (emulátor tu nejede – chybí KVM).
- **Aktualizace**: appka při otevření kouká na `releases/latest` v `pupikt/televize` (tag `vN` > versionCode) → tlačítko „Nová verze“ stáhne `televize.apk` a otevře instalaci. **Repo musí být veřejné**, jinak se APK nestáhne.
- **Sestavení**: push na `main` → GitHub Actions (`.github/workflows/build.yml`) → release `vN` (N = číslo běhu = versionCode) s `televize.apk`. Podpisový klíč `app/televize.jks` je v repu schválně (heslo `televize`) – aktualizace musí být podepsané stejně; nic tajného to nechrání.
- Lokálně: Android SDK do `/root/android-sdk` (cmdline-tools → `platforms;android-35`, `build-tools;35.0.0`), `local.properties` se `sdk.dir`, `./gradlew assembleRelease`. Vzhled se kontroluje přes Paparazzi v dočasné kopii projektu (obrazovka 1920×1080, xhdpi = 960×540 dp) – do repa se nedává.

## Vzhled v2 (10. 11. 2026, Tom: „originál loga, topová grafika, udělej maximum“)
- **Dlaždice z originálních log**, která Tom poslal (`tools/logos/`): `python3 tools/tiles.py` → `res/drawable-nodpi/tile_{youtube,netflix,disney,oneplay}.png` (640×360; YouTube = červená ikona + bílý nápis na tmavé, Netflix na černé s červenou září, Disney+ i Oneplay vyříznuté a na tmavém pozadí se září jako YouTube a Netflix – Tom 10. 11. chtěl jednotně). Přiřazení podle názvu/balíčku v `BRANDS` (`data/Apps.kt`), každá značka má barvu `tint`; ostatní aplikace mají svůj banner a `tint` = průměrná barva.
- **Pozadí** `ui/Aurora.kt`: hvězdy, dva předrozmazané závoje polární záře (`python3 tools/aurora.py` → `aurora_a/b.png`, jen se pomalu posouvají – šetří výkon) a zespodu **záře v barvě vybrané aplikace** (plynule se přelévá).
- **Sklo je průsvitné** (`Modifier.glass`, `solid = true` jen pro panel Večer). Vybrané tlačítko = **bílá pilulka s tmavým textem** (na telce vidět na dálku).
- **Dlaždice** `AppTile`: při najetí se pružně zvětší, dostane světlou hranu, záři v barvě aplikace a přejede po ní odlesk; název vybrané aplikace je pod řadou (jinak pozdrav). Spuštění = dlaždice se „nafoukne“ a obrazovka ztmavne (230 ms).
- **Lišta dole** (`Dock`): Večer (ukazuje „Jas 50 %“ / „Usne za N min“), Aplikace, Nastavení, případně Nová verze.
- Nájezd prvků při otevření (`Modifier.enter`), přetáčecí číslice hodin a teploty (`Roll`), **klidový režim** po 2 min bez ovladače (zůstanou hodiny, počasí a záře; první stisk jen probudí).
- Náhledy: `previewMode` / `previewFocus` v `Kit.kt` (Paparazzi neumí zaostřit ani dohrát animace).

## Propojení s Domácností (10. 11. 2026)
- Tom chtěl telku ovládat z Domácnosti (`pupikt/Domacnost2`, https://domacnost.dilna.workers.dev) – tím se výjimečně propojuje; podrobnosti serveru v CLAUDE.md Domácnosti (`worker/tv.ts`).
- `night/Link.kt`: vlákno ve službě, když je telka zapnutá, každé ~3 s `POST /api/tv/sync` s hlavičkou `X-TV-Key` (náhodný token v `Prefs.linkToken`; server zná jen SHA-256). Pošle `NightService.snapshot()`, příkazy provede `NightService.runCommand` (vypnout, Domů, spustit appku, jas, usínání, hlasitost přes `AudioManager.STREAM_MUSIC`, ztlumit, pauza = mediální klávesa).
- Spárování: Nastavení → Domácnost → „Propojit s Domácností“ → `POST /api/tv/hello {code, h}` → 6místný kód na obrazovce → Tom ho napíše v Domácnosti → další sync vrátí 200 = propojeno. 401 po spárování = Domácnost telku odpojila.
- Funguje jen se zapnutou službou usnadnění. Zapnout telku z mobilu nejde.
- **Ovladač z mobilu** (10. 11. 2026): příkaz `key` – `back` = `GLOBAL_ACTION_BACK`, `home` = `goHome()`, ostatní (šipky, OK, menu, play…) = `Bravia.key` → IRCC `http://127.0.0.1/sony/IRCC` (když nic, zkusí vlastní IPv4 adresy telky; fungující si pamatuje; přesná chyba do stavu `irccInfo` a do Záznamu služby) (SOAP, hlavička `X-Auth-PSK` = `Prefs.psk`). Klíč jde nastavit v Nastavení Televize nebo poslat z Domácnosti (příkaz `psk`). Výsledek posledního tlačítka ve stavu `ircc` (ok / nopsk / fail). Tlačítka se provádějí rovnou ve vlákně Link (síť), ostatní příkazy na hlavním vlákně.
- Ve stavu se posílá i `ip` = adresa telky v domácí síti (pro zapínání přes Zkratky v iPhonu – viz CLAUDE.md Domácnosti).
- **Spárování ovladače přes PIN** (10. 11. 2026): IRCC s předsdíleným klíčem vracel u Toma 403 (klíč přitom sedí – getPowerStatus prošel), proto `Bravia.register`: `/sony/accessControl` actRegister (clientid `televize-domacnost:1`, level private) bez PINu → telka ukáže PIN → příkaz `irccPin` → znovu s `Authorization: Basic :PIN` → cookie `auth=…` (`Prefs.irccCookie`), posílá se v IRCC jako `Cookie`. Při 403 se cookie zkusí obnovit bez PINu, jinak se smaže. Stav `irccPair`: pin / badpin / ok / fail:N. **Ověřeno 10. 11. 2026 – po spárování PINem ovladač z Domácnosti funguje.**
- **Zapínání** (10. 11. 2026): předsdílený klíč Tomova telka nebere vůbec (setPowerStatus i po restartu 403, getPowerStatus prošel jen proto, že je veřejný – „klíč sedí“ byl omyl). Proto telka posílá ve stavu `cookie` ze spárování PINem; Domácnost ji dává zkratce v iPhonu (viz CLAUDE.md Domácnosti). Cookie se obnovuje každé 3 dny ve `snapshot()` přes `Bravia.register(null, cookie)` (že obnova bez PINu funguje, je neověřené).
- **Dalších 4 nápady (10. 11. 2026 večer, Tom vybral 1, 2, 3, 5)**:
  - **Oznámení na telce**: Domácnost (`notifyTick` → `tvToast`) pošle příkaz `toast {kind, title, body}`, jen když telka běží; `NightService.toast` = skleněná karta vpravo nahoře (emoji podle druhu: 🧺 pračka, 🤖 vysavač, 💧 klima, 🔥 kotel, ❄️ zima), 8 s, víc najednou za sebou. Čidla a propojení se na telku neposílají.
  - **Dobrou noc** (jen v ovladači v Domácnosti, ne jinde): příkaz `goodnight` → jas 20 % + usínání 60 min + ukazatel „☾ Dobrou noc“.
  - **Pruh „Doma“** na hlavní obrazovce pod počasím (`HouseStrip`): teploty 2 místností + co běží (bez telky), `Link.house` → `POST /api/tv/dum` (X-TV-Key) = stejný souhrn jako widget (mezipaměť 4 min), každé 2 min, jen když je telka propojená. Dlaždice posunuté níž (top 92 dp).
  - (5 = telka ve widgetu – jen na straně Domácnosti.)
- **Rychlý režim**: když sync vrátí `fast: true` (v mobilu je otevřená stránka Televize), další dotaz za 250 ms, jinak 3 s.

## Instalace u Toma
1. Na telce z Obchodu Play appka **Downloader** (AFTVnews).
2. Povolit jí instalaci neznámých aplikací (zeptá se sama).
3. Do Downloaderu adresa `github.com/pupikt/televize/releases/latest/download/televize.apk` → Instalovat.
4. Otevřít Televizi → Večer u telky → Otevřít Usnadnění → Televize → Zapnout.
