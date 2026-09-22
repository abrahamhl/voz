package dev.auxdesign.voz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auxdesign.voz.AppGraph
import kotlinx.coroutines.launch

enum class Screen { ONBOARDING, HOME, SETTINGS, LOG }

@Composable
fun VozRoot(graph: AppGraph) {
    val settings by graph.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: return
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(if (loaded.onboardingDone) Screen.HOME else Screen.ONBOARDING) }

    VozTheme(highContrast = loaded.highContrast) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.safeDrawingPadding()) {
                BackHandler(enabled = screen == Screen.SETTINGS || screen == Screen.LOG) { screen = Screen.HOME }
                when (screen) {
                    Screen.ONBOARDING -> OnboardingScreen(graph) {
                        scope.launch { graph.settingsStore.update { it.copy(onboardingDone = true) } }
                        screen = Screen.HOME
                    }
                    Screen.HOME -> HomeScreen(
                        graph = graph,
                        onSettings = { screen = Screen.SETTINGS },
                        onLog = { screen = Screen.LOG },
                        onSetup = { screen = Screen.ONBOARDING },
                    )
                    Screen.SETTINGS -> SettingsScreen(graph, loaded) { screen = Screen.HOME }
                    Screen.LOG -> ActionLogScreen(graph) { screen = Screen.HOME }
                }
            }
        }
    }
}
