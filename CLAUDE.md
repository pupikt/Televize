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
  - **tlačítko Domů**: když se objeví launcher Googlu/Sony (`com.google.android.tvlauncher` …), otevře Televizi (pojistka max 4× za 10 s).
  - Nic z toho zatím **neověřeno na skutečné telce** (emulátor tu nejede – chybí KVM).
- **Aktualizace**: appka při otevření kouká na `releases/latest` v `pupikt/televize` (tag `vN` > versionCode) → tlačítko „Nová verze“ stáhne `televize.apk` a otevře instalaci. **Repo musí být veřejné**, jinak se APK nestáhne.
- **Sestavení**: push na `main` → GitHub Actions (`.github/workflows/build.yml`) → release `vN` (N = číslo běhu = versionCode) s `televize.apk`. Podpisový klíč `app/televize.jks` je v repu schválně (heslo `televize`) – aktualizace musí být podepsané stejně; nic tajného to nechrání.
- Lokálně: Android SDK do `/root/android-sdk` (cmdline-tools → `platforms;android-35`, `build-tools;35.0.0`), `local.properties` se `sdk.dir`, `./gradlew assembleRelease`. Vzhled se kontroluje přes Paparazzi v dočasné kopii projektu (obrazovka 1920×1080, xhdpi = 960×540 dp) – do repa se nedává.

## Instalace u Toma
1. Na telce z Obchodu Play appka **Downloader** (AFTVnews).
2. Povolit jí instalaci neznámých aplikací (zeptá se sama).
3. Do Downloaderu adresa `github.com/pupikt/televize/releases/latest/download/televize.apk` → Instalovat.
4. Otevřít Televizi → Večer u telky → Otevřít Usnadnění → Televize → Zapnout.
