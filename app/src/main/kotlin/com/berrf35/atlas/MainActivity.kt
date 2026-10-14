package com.berrf35.atlas

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.PlaceId
import com.berrf35.atlas.feature.day.DayMapScreen
import com.berrf35.atlas.feature.day.TodayScreen
import com.berrf35.atlas.feature.diagnostics.AboutScreen
import com.berrf35.atlas.feature.diagnostics.DiagnosticsScreen
import com.berrf35.atlas.feature.diagnostics.GapDetailScreen
import com.berrf35.atlas.feature.history.HistoryScreen
import com.berrf35.atlas.feature.insights.HeatmapScreen
import com.berrf35.atlas.feature.insights.InsightsOverviewScreen
import com.berrf35.atlas.feature.insights.RecordsScreen
import com.berrf35.atlas.feature.journey.JourneyDetailScreen
import com.berrf35.atlas.feature.journey.JourneyEditScreen
import com.berrf35.atlas.feature.journey.PlaybackScreen
import com.berrf35.atlas.feature.onboarding.OnboardingOnePlusScreen
import com.berrf35.atlas.feature.onboarding.OnboardingPermissionsScreen
import com.berrf35.atlas.feature.onboarding.OnboardingWelcomeScreen
import com.berrf35.atlas.feature.places.PlaceDetailScreen
import com.berrf35.atlas.feature.places.PlacesListScreen
import com.berrf35.atlas.feature.settings.AppearanceSettingsScreen
import com.berrf35.atlas.feature.settings.DataExportScreen
import com.berrf35.atlas.feature.settings.MapSettingsScreen
import com.berrf35.atlas.feature.settings.PrivacySettingsScreen
import com.berrf35.atlas.feature.settings.SettingsMainScreen
import com.berrf35.atlas.feature.settings.TrackingSettingsScreen
import kotlinx.coroutines.launch

sealed interface AppDestination {
    data object OnboardingWelcome : AppDestination
    data object OnboardingPermissions : AppDestination
    data object OnboardingOnePlus : AppDestination

    data object Today : AppDestination
    data object DayMap : AppDestination

    data object History : AppDestination
    data class JourneyDetail(val journeyId: JourneyId) : AppDestination
    data class Playback(val journeyId: JourneyId) : AppDestination
    data class JourneyEdit(val journeyId: JourneyId) : AppDestination

    data object Insights : AppDestination
    data object Records : AppDestination
    data object Heatmap : AppDestination

    data object Places : AppDestination
    data class PlaceDetail(val placeId: PlaceId) : AppDestination

    data object Settings : AppDestination
    data object TrackingSettings : AppDestination
    data object MapSettings : AppDestination
    data object DataExport : AppDestination
    data object PrivacySettings : AppDestination
    data object AppearanceSettings : AppDestination

    data object Diagnostics : AppDestination
    data class GapDetail(val gapId: String) : AppDestination
    data object About : AppDestination
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AtlasDataLocator.init(applicationContext)

        setContent {
            val settingsRepo = remember { AtlasDataLocator.settingsRepository }
            val settings by settingsRepo.settings.collectAsState()

            val isDark = when (settings.theme) {
                "DARK" -> true
                "LIGHT" -> false
                else -> false // Minimal light default
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
            }

            AtlasTheme(darkTheme = isDark) {
                AtlasApp()
            }
        }
    }
}

@Composable
fun AtlasApp() {
    val settingsRepo = remember { AtlasDataLocator.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var currentDestination by remember(settings.isOnboardingCompleted) {
        mutableStateOf<AppDestination>(
            if (settings.isOnboardingCompleted) AppDestination.Today
            else AppDestination.OnboardingWelcome
        )
    }
    val backStack = remember { mutableStateListOf<AppDestination>() }

    fun navigateTo(destination: AppDestination) {
        backStack.add(currentDestination)
        currentDestination = destination
    }

    fun popBack() {
        if (backStack.isNotEmpty()) {
            currentDestination = backStack.removeAt(backStack.size - 1)
        }
    }

    BackHandler(enabled = backStack.isNotEmpty()) {
        popBack()
    }

    val isTopLevel = currentDestination in listOf(
        AppDestination.Today,
        AppDestination.History,
        AppDestination.Insights,
        AppDestination.Places,
        AppDestination.Settings
    )

    val colors = AtlasTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (val dest = currentDestination) {
                    is AppDestination.OnboardingWelcome -> OnboardingWelcomeScreen(
                        onSetupTracking = { navigateTo(AppDestination.OnboardingPermissions) },
                        onExploreSampleData = {
                            scope.launch {
                                settingsRepo.setDemoMode(true)
                                settingsRepo.setTrackingEnabled(false)
                                settingsRepo.setOnboardingCompleted(true)
                                backStack.clear()
                                currentDestination = AppDestination.Today
                            }
                        }
                    )
                    is AppDestination.OnboardingPermissions -> OnboardingPermissionsScreen(
                        onBack = { popBack() },
                        onContinue = { navigateTo(AppDestination.OnboardingOnePlus) },
                        onSkip = {
                            scope.launch {
                                settingsRepo.setOnboardingCompleted(true)
                                backStack.clear()
                                currentDestination = AppDestination.Today
                            }
                        }
                    )
                    is AppDestination.OnboardingOnePlus -> OnboardingOnePlusScreen(
                        onBack = { popBack() },
                        onFinish = {
                            scope.launch {
                                settingsRepo.setDemoMode(false)
                                settingsRepo.setTrackingEnabled(true)
                                settingsRepo.setOnboardingCompleted(true)
                                AtlasDataLocator.trackingRepository.start()
                                backStack.clear()
                                currentDestination = AppDestination.Today
                            }
                        }
                    )

                    is AppDestination.Today -> TodayScreen(
                        onOpenJourney = { id -> navigateTo(AppDestination.JourneyDetail(id)) },
                        onOpenMap = { navigateTo(AppDestination.DayMap) },
                        onOpenDiagnostics = { navigateTo(AppDestination.Diagnostics) }
                    )
                    is AppDestination.DayMap -> DayMapScreen(
                        onBack = { popBack() }
                    )

                    is AppDestination.History -> HistoryScreen(
                        onOpenJourney = { id -> navigateTo(AppDestination.JourneyDetail(id)) }
                    )
                    is AppDestination.JourneyDetail -> JourneyDetailScreen(
                        journeyId = dest.journeyId,
                        onBack = { popBack() },
                        onPlay = { id -> navigateTo(AppDestination.Playback(id)) },
                        onEdit = { id -> navigateTo(AppDestination.JourneyEdit(id)) }
                    )
                    is AppDestination.Playback -> PlaybackScreen(
                        journeyId = dest.journeyId,
                        onBack = { popBack() }
                    )
                    is AppDestination.JourneyEdit -> JourneyEditScreen(
                        journeyId = dest.journeyId,
                        onBack = { popBack() }
                    )

                    is AppDestination.Insights -> InsightsOverviewScreen(
                        onOpenRecords = { navigateTo(AppDestination.Records) },
                        onOpenHeatmap = { navigateTo(AppDestination.Heatmap) }
                    )
                    is AppDestination.Records -> RecordsScreen(
                        onBack = { popBack() }
                    )
                    is AppDestination.Heatmap -> HeatmapScreen(
                        onBack = { popBack() }
                    )

                    is AppDestination.Places -> PlacesListScreen(
                        onOpenPlace = { id -> navigateTo(AppDestination.PlaceDetail(id)) }
                    )
                    is AppDestination.PlaceDetail -> PlaceDetailScreen(
                        placeId = dest.placeId,
                        onBack = { popBack() }
                    )

                    is AppDestination.Settings -> SettingsMainScreen(
                        onOpenTracking = { navigateTo(AppDestination.TrackingSettings) },
                        onOpenMap = { navigateTo(AppDestination.MapSettings) },
                        onOpenData = { navigateTo(AppDestination.DataExport) },
                        onOpenPrivacy = { navigateTo(AppDestination.PrivacySettings) },
                        onOpenAppearance = { navigateTo(AppDestination.AppearanceSettings) },
                        onOpenDiagnostics = { navigateTo(AppDestination.Diagnostics) },
                        onOpenAbout = { navigateTo(AppDestination.About) }
                    )
                    is AppDestination.TrackingSettings -> TrackingSettingsScreen(
                        onBack = { popBack() }
                    )
                    is AppDestination.MapSettings -> MapSettingsScreen(
                        onBack = { popBack() }
                    )
                    is AppDestination.DataExport -> DataExportScreen(
                        onBack = { popBack() }
                    )
                    is AppDestination.PrivacySettings -> PrivacySettingsScreen(
                        onBack = { popBack() }
                    )
                    is AppDestination.AppearanceSettings -> AppearanceSettingsScreen(
                        onBack = { popBack() }
                    )

                    is AppDestination.Diagnostics -> DiagnosticsScreen(
                        onBack = { popBack() },
                        onOpenGap = { gapId -> navigateTo(AppDestination.GapDetail(gapId)) }
                    )
                    is AppDestination.GapDetail -> GapDetailScreen(
                        gapId = dest.gapId,
                        onBack = { popBack() }
                    )
                    is AppDestination.About -> AboutScreen(
                        onBack = { popBack() }
                    )
                }
            }

            // Minimalist Bottom Navigation Bar
            if (isTopLevel) {
                HairlineRule()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(colors.surface),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Pair("TODAY", AppDestination.Today),
                        Pair("HISTORY", AppDestination.History),
                        Pair("INSIGHTS", AppDestination.Insights),
                        Pair("PLACES", AppDestination.Places),
                        Pair("SETTINGS", AppDestination.Settings)
                    )

                    tabs.forEach { (label, dest) ->
                        val isSelected = currentDestination == dest
                        Column(
                            modifier = Modifier
                                .clickable {
                                    if (currentDestination != dest) {
                                        backStack.clear()
                                        currentDestination = dest
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                style = AtlasTheme.typography.label.copy(fontSize = 11.sp),
                                color = if (isSelected) colors.ink else colors.muted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .width(18.dp)
                                    .background(if (isSelected) colors.ink else androidx.compose.ui.graphics.Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}
