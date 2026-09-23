package dev.auxdesign.voz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auxdesign.voz.AppGraph
import kotlinx.coroutines.launch

enum class Screen { ONBOARDING, HOME, SETTINGS, LOG, ABOUT }

@Composable
fun VozRoot(graph: AppGraph) {
    val settings by graph.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: return
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(if (loaded.onboardingDone) Screen.HOME else Screen.ONBOARDING) }
    var setupStep by rememberSaveable { mutableIntStateOf(0) }
    // Where "Back" from the setup steps returns to (Home or Settings).
    var setupReturn by rememberSaveable { mutableStateOf(Screen.HOME) }

    fun openSetup(step: Int, from: Screen) {
        setupStep = step
        setupReturn = from
        screen = Screen.ONBOARDING
    }

    VozTheme(highContrast = loaded.highContrast) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.safeDrawingPadding()) {
                BackHandler(enabled = screen == Screen.SETTINGS || screen == Screen.LOG || screen == Screen.ABOUT) {
                    screen = if (screen == Screen.ABOUT) Screen.SETTINGS else Screen.HOME
                }
                when (screen) {
                    Screen.ONBOARDING -> OnboardingScreen(
                        startStep = setupStep,
                        canLeave = loaded.onboardingDone,
                        onFinish = {
                            scope.launch { graph.settingsStore.update { it.copy(onboardingDone = true) } }
                            screen = if (loaded.onboardingDone) setupReturn else Screen.HOME
                        },
                        onLeave = { screen = setupReturn },
                    )
                    Screen.HOME -> HomeScreen(
                        graph = graph,
                        onSettings = { screen = Screen.SETTINGS },
                        onHistory = { screen = Screen.LOG },
                        onSetup = { step -> openSetup(step, Screen.HOME) },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        graph = graph,
                        settings = loaded,
                        onBack = { screen = Screen.HOME },
                        onSetup = { openSetup(SetupStep.MIC, Screen.SETTINGS) },
                        onAbout = { screen = Screen.ABOUT },
                    )
                    Screen.LOG -> ActionLogScreen(graph) { screen = Screen.HOME }
                    Screen.ABOUT -> AboutScreen(cloudBuilt = graph.engines.cloudBuilt) { screen = Screen.SETTINGS }
                }
            }
        }
    }
}
