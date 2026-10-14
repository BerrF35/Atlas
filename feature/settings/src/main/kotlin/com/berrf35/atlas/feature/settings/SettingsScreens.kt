package com.berrf35.atlas.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.designsystem.component.AtlasButton
import com.berrf35.atlas.core.designsystem.component.AtlasTopBar
import com.berrf35.atlas.core.designsystem.component.ButtonVariant
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.component.SectionHeader
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.model.AtlasSettings
import com.berrf35.atlas.core.model.BatteryProfile
import kotlinx.coroutines.launch

/**
 * S17: Settings Main Screen.
 */
@Composable
fun SettingsMainScreen(
    onOpenTracking: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenData: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepo = remember { AtlasDataLocator.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val colors = AtlasTheme.colors
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "SETTINGS",
            subtitle = "Instrument Configuration"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Demo data mode switch
            SectionHeader(title = "DATA ENGINE MODE")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (settings.isDemoMode) colors.ink else colors.rule, RoundedCornerShape(8.dp))
                    .background(if (settings.isDemoMode) colors.quiet else colors.surface, RoundedCornerShape(8.dp))
                    .clickable {
                        scope.launch { settingsRepo.setDemoMode(!settings.isDemoMode) }
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Demo Mode",
                        style = AtlasTheme.typography.title,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (settings.isDemoMode) "Loaded with Bangalore multimodal test movements" else "Using empty local database for real tracking",
                        style = AtlasTheme.typography.label,
                        color = colors.muted
                    )
                }
                Text(
                    text = if (settings.isDemoMode) "ENABLED" else "DISABLED",
                    style = AtlasTheme.typography.label,
                    color = if (settings.isDemoMode) colors.ink else colors.muted
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            // Subsections
            SectionHeader(title = "SUBSYSTEMS")

            SettingsRow(
                title = "Tracking & Sensors",
                subtitle = "Battery profile: ${settings.batteryProfile.label} · State: ${if (settings.trackingEnabled) "Active" else "Paused"}",
                onClick = onOpenTracking
            )

            SettingsRow(
                title = "Map & Tiles",
                subtitle = "Tiles: OpenFreeMap · Ambient cache: ${settings.ambientCacheMb} MB",
                onClick = onOpenMap
            )

            SettingsRow(
                title = "Data & Exports",
                subtitle = "Export GPX, GeoJSON, CSV, or purge database",
                onClick = onOpenData
            )

            SettingsRow(
                title = "Privacy & Security",
                subtitle = "App lock: ${if (settings.appLockEnabled) "Enabled" else "Off"} · Reverse geocoding: ${if (settings.reverseGeocodingEnabled) "Online" else "Offline"}",
                onClick = onOpenPrivacy
            )

            SettingsRow(
                title = "Appearance",
                subtitle = "Theme: ${settings.theme} · Motion: ${if (settings.reduceMotion) "Reduced" else "Standard"}",
                onClick = onOpenAppearance
            )

            SettingsRow(
                title = "Diagnostics & Interruption Log",
                subtitle = "Health logs, GPS tunnel events, OxygenOS throttling status",
                onClick = onOpenDiagnostics
            )

            SettingsRow(
                title = "About Atlas",
                subtitle = "Version 1.0.0-provisional · OnePlus Nord 5 target",
                onClick = onOpenAbout
            )

            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

/**
 * S18: Tracking Settings Screen.
 */
@Composable
fun TrackingSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepo = remember { AtlasDataLocator.settingsRepository }
    val trackingRepo = remember { AtlasDataLocator.trackingRepository }
    val settings by settingsRepo.settings.collectAsState()
    val colors = AtlasTheme.colors
    val scope = rememberCoroutineScope()

    val profiles = listOf(BatteryProfile.PRECISE, BatteryProfile.BALANCED, BatteryProfile.SAVER)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "TRACKING PROFILES",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionHeader(title = "BATTERY PROFILE")

            profiles.forEach { profile ->
                val isSelected = settings.batteryProfile == profile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isSelected) colors.ink else colors.rule, RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.quiet else colors.surface, RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch { settingsRepo.setBatteryProfile(profile) }
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.label,
                            style = AtlasTheme.typography.title,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile.description,
                            style = AtlasTheme.typography.label,
                            color = colors.muted
                        )
                    }
                    Text(
                        text = if (isSelected) "SELECTED" else "SELECT",
                        style = AtlasTheme.typography.label,
                        color = if (isSelected) colors.ink else colors.muted
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            HairlineRule()

            SectionHeader(title = "PAUSE RECORDING")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AtlasButton(
                    text = "Pause 1 Hour",
                    onClick = { scope.launch { trackingRepo.pause(60) } },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AtlasButton(
                    text = "Resume",
                    onClick = { scope.launch { trackingRepo.resume() } },
                    variant = ButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * S19: Map Settings Screen.
 */
@Composable
fun MapSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepo = remember { AtlasDataLocator.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val colors = AtlasTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "MAP & TILES",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionHeader(title = "TILE ENDPOINT")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(text = "Tile Server URL", style = AtlasTheme.typography.title, color = colors.ink)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = settings.tileServerUrl, style = AtlasTheme.typography.numeric.copy(fontSize = 13.sp), color = colors.muted)
            }

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            SectionHeader(title = "AMBIENT CACHE")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(text = "Max Cache Limit: ${settings.ambientCacheMb} MB", style = AtlasTheme.typography.title, color = colors.ink)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Vector tiles are stored offline for recently viewed bounding areas.", style = AtlasTheme.typography.label, color = colors.muted)
            }
        }
    }
}

/**
 * S20: Data & Export Screen.
 */
@Composable
fun DataExportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exportRepo = remember { AtlasDataLocator.exportRepository }
    val colors = AtlasTheme.colors
    val scope = rememberCoroutineScope()

    var selectedFormat by remember { mutableStateOf("GPX") }
    var exportStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "DATA & EXPORTS",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionHeader(title = "EXPORT FORMAT")

            listOf("GPX", "GeoJSON", "CSV", "SQLite Archive").forEach { fmt ->
                val isSelected = selectedFormat == fmt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isSelected) colors.ink else colors.rule, RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.quiet else colors.surface, RoundedCornerShape(8.dp))
                        .clickable { selectedFormat = fmt }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = fmt, style = AtlasTheme.typography.title, color = colors.ink)
                    Text(
                        text = if (isSelected) "SELECTED" else "SELECT",
                        style = AtlasTheme.typography.label,
                        color = if (isSelected) colors.ink else colors.muted
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            AtlasButton(
                text = "Export Records as $selectedFormat",
                onClick = {
                    scope.launch {
                        val res = exportRepo.exportData(selectedFormat.lowercase(), null)
                        exportStatus = res.getOrNull()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (exportStatus != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = exportStatus!!,
                    style = AtlasTheme.typography.label,
                    color = colors.ink
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HairlineRule()

            SectionHeader(title = "PURGE DATABASE")
            AtlasButton(
                text = "Clear All Local Movement Data",
                onClick = {
                    scope.launch {
                        exportRepo.clearAllData()
                        exportStatus = "Local movement database purged."
                    }
                },
                variant = ButtonVariant.DESTRUCTIVE,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * S21: Privacy & Security Screen.
 */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepo = remember { AtlasDataLocator.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val colors = AtlasTheme.colors
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "PRIVACY & SECURITY",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // App Lock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { settingsRepo.setAppLockEnabled(!settings.appLockEnabled) } }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "App Lock (Biometric / PIN)", style = AtlasTheme.typography.title, color = colors.ink)
                    Text(text = "Require biometric verification on launch", style = AtlasTheme.typography.label, color = colors.muted)
                }
                Text(text = if (settings.appLockEnabled) "ENABLED" else "DISABLED", style = AtlasTheme.typography.label, color = colors.ink)
            }

            HairlineRule()

            // Reverse Geocoding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { settingsRepo.setReverseGeocodingEnabled(!settings.reverseGeocodingEnabled) } }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Online Reverse Geocoding", style = AtlasTheme.typography.title, color = colors.ink)
                    Text(text = "Query OpenStreetMap Nominatim for street names instead of coordinates only", style = AtlasTheme.typography.label, color = colors.muted)
                }
                Text(text = if (settings.reverseGeocodingEnabled) "ENABLED" else "DISABLED", style = AtlasTheme.typography.label, color = colors.ink)
            }
        }
    }
}

/**
 * S22: Appearance Screen.
 */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepo = remember { AtlasDataLocator.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val colors = AtlasTheme.colors
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "APPEARANCE",
            navigationIcon = {
                Text(
                    text = "Back",
                    style = AtlasTheme.typography.label,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionHeader(title = "THEME")

            listOf("SYSTEM", "LIGHT", "DARK").forEach { thm ->
                val isSelected = settings.theme == thm
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isSelected) colors.ink else colors.rule, RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.quiet else colors.surface, RoundedCornerShape(8.dp))
                        .clickable { scope.launch { settingsRepo.setTheme(thm) } }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = thm, style = AtlasTheme.typography.title, color = colors.ink)
                    Text(
                        text = if (isSelected) "ACTIVE" else "SET",
                        style = AtlasTheme.typography.label,
                        color = if (isSelected) colors.ink else colors.muted
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            // Reduce motion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { settingsRepo.setReduceMotion(!settings.reduceMotion) } }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Reduce Motion", style = AtlasTheme.typography.title, color = colors.ink)
                    Text(text = "Disable route playback animations and transitions", style = AtlasTheme.typography.label, color = colors.muted)
                }
                Text(text = if (settings.reduceMotion) "ON" else "OFF", style = AtlasTheme.typography.label, color = colors.ink)
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = AtlasTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AtlasTheme.typography.title, color = colors.ink)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = AtlasTheme.typography.label, color = colors.muted)
        }
        Text(text = "›", style = AtlasTheme.typography.title.copy(fontSize = 18.sp), color = colors.muted)
    }
    HairlineRule()
}
