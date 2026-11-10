package cz.pupik.televize

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import kotlinx.coroutines.delay
import cz.pupik.televize.ui.C
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import cz.pupik.televize.night.NightService
import cz.pupik.televize.ui.AllAppsScreen
import cz.pupik.televize.ui.Aurora
import cz.pupik.televize.ui.HomeScreen
import cz.pupik.televize.ui.NightSheet
import cz.pupik.televize.ui.Screen
import cz.pupik.televize.ui.SettingsScreen
import cz.pupik.televize.ui.Ui
import cz.pupik.televize.ui.WeatherFx
import cz.pupik.televize.ui.EveningTint

class MainActivity : ComponentActivity() {
    private lateinit var ui: Ui

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this, lifecycleScope)
        setContent {
            LaunchedEffect(Unit) { NightService.state.collect { ui.night = it } }
            Box(Modifier.fillMaxSize()) {
                val tint = when {
                    ui.nightOpen || ui.screen != Screen.Home -> C.indigo
                    else -> ui.focused?.tint ?: C.indigo
                }
                Aurora(ui.aurora, tint, calm = if (ui.screen != Screen.Home || ui.nightOpen) 1f else 0f)
                // živé počasí v pozadí (déšť, sníh, bouřka, slunce, mraky, mlha)
                WeatherFx(ui.weather, ui.aurora)
                // klidový režim po 2 minutách bez ovladače
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(5000)
                        ui.idle = ui.screen == Screen.Home && !ui.nightOpen && System.currentTimeMillis() - ui.lastKey > 120_000
                    }
                }
                AnimatedContent(
                    ui.screen,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.97f)) togetherWith
                            (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 1.02f))
                    },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(ui)
                        Screen.All -> AllAppsScreen(ui)
                        Screen.Settings -> SettingsScreen(ui)
                    }
                }
                NightSheet(ui)
                // večerní nálada: po západu slunce teplejší a tmavší obrazovka
                EveningTint(ui.weather)
            }
            BackHandler(ui.nightOpen || ui.screen != Screen.Home) {
                if (ui.nightOpen) ui.nightOpen = false else ui.screen = Screen.Home
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        ui.lastKey = System.currentTimeMillis()
        if (ui.idle) {
            // první stisk jen probudí obrazovku
            if (event.action == KeyEvent.ACTION_UP) ui.idle = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        ui.lastKey = System.currentTimeMillis(); ui.idle = false; ui.launching = null
        super.onResume()
        ui.accOn = NightService.isEnabled(this)
        ui.reloadApps()
        ui.loadWeather()
        ui.checkUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Tlačítko Domů znovu → zpátky na hlavní obrazovku.
        ui.nightOpen = false
        ui.screen = Screen.Home
        ui.homeFocusTick++
    }
}
