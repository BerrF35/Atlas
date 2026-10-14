package com.berrf35.atlas.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.berrf35.atlas.core.designsystem.component.AtlasButton
import com.berrf35.atlas.core.designsystem.component.AtlasTopBar
import com.berrf35.atlas.core.designsystem.component.ButtonVariant
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme

/**
 * S01: Onboarding Welcome Screen.
 * Concise, natural language introduction to Atlas with local-first privacy commitments.
 * Provides explicit choices: "Set up automatic tracking" or "Explore with sample journeys".
 */
@Composable
fun OnboardingWelcomeScreen(
    onSetupTracking: () -> Unit,
    onExploreSampleData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AtlasTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text(
                text = "ATLAS",
                style = AtlasTheme.typography.display,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Personal movement recorder",
                style = AtlasTheme.typography.title,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(32.dp))
            HairlineRule()
            Spacer(modifier = Modifier.height(24.dp))

            GuaranteeItem(
                title = "Local-first storage",
                description = "Raw GPS coordinates and reconstructed routes are stored exclusively on this device in an encrypted SQLite database."
            )
            Spacer(modifier = Modifier.height(20.dp))
            GuaranteeItem(
                title = "Zero external telemetry",
                description = "No remote accounts, no analytics SDKs, and no tracking pings. Your movement history never leaves your phone."
            )
            Spacer(modifier = Modifier.height(20.dp))
            GuaranteeItem(
                title = "Multimodal classification",
                description = "Distinguishes walking, bicycle, motorcycle, car, metro, auto-rickshaw, and train automatically using lightweight on-device inference."
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            HairlineRule()
            Spacer(modifier = Modifier.height(16.dp))

            AtlasButton(
                text = "Set Up Automatic Tracking",
                onClick = onSetupTracking,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            AtlasButton(
                text = "Explore With Sample Journeys",
                onClick = onExploreSampleData,
                variant = ButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sample mode explores demonstration routes in Bangalore without accessing sensors. You can enable real tracking anytime in Settings.",
                style = AtlasTheme.typography.label.copy(fontSize = 11.sp),
                color = colors.muted
            )
        }
    }
}

/**
 * S02: Onboarding Permissions Screen.
 * Guided, stepwise permission flow requesting foreground location, physical activity recognition,
 * notifications, and background location with graceful fallbacks.
 */
@Composable
fun OnboardingPermissionsScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = AtlasTheme.colors

    fun hasPermission(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    var locGranted by remember {
        mutableStateOf(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
    }
    var bgGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else true
        )
    }
    var activityGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
            } else true
        )
    }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )
    }

    // Refresh actual permissions on resume from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                bgGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else true
                activityGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                } else true
                notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        locGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        activityGranted = granted
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "Permissions",
            subtitle = "Step 1 of 2 · Required Capabilities",
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
                .padding(24.dp)
        ) {
            Text(
                text = "Continuous tracking requires Android permissions to record coordinates and run motion detection in the background:",
                style = AtlasTheme.typography.body,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(20.dp))

            PermissionCard(
                title = "Precise Location",
                description = "GNSS fixes while in transit to accurately reconstruct street geometry and stops.",
                granted = locGranted,
                actionLabel = if (locGranted) "GRANTED" else "ALLOW",
                onAction = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            PermissionCard(
                title = "Background Location",
                description = "Enables coordinate logging while your phone is locked in your pocket. On Android 11+, select 'Allow all the time' in Settings.",
                granted = bgGranted,
                actionLabel = if (bgGranted) "GRANTED" else "CONFIGURE",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && locGranted) {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback
                        }
                    } else {
                        // Request foreground first
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            PermissionCard(
                title = "Physical Activity Recognition",
                description = "Powers stationary sleep mode so GPS powers down when you stop walking or driving.",
                granted = activityGranted,
                actionLabel = if (activityGranted) "GRANTED" else "ALLOW",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    } else {
                        activityGranted = true
                    }
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(14.dp))
                PermissionCard(
                    title = "Notifications",
                    description = "Required for the ongoing tracking status indicator in the system notification shade.",
                    granted = notifGranted,
                    actionLabel = if (notifGranted) "GRANTED" else "ALLOW",
                    onAction = {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            HairlineRule()
            Spacer(modifier = Modifier.height(16.dp))

            AtlasButton(
                text = "Proceed to Battery Setup",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            AtlasButton(
                text = "Skip / Limited Mode",
                onClick = onSkip,
                variant = ButtonVariant.TERTIARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * S03: Onboarding OnePlus / OxygenOS Battery Configuration Screen.
 */
@Composable
fun OnboardingOnePlusScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AtlasTheme.colors
    var step1Checked by remember { mutableStateOf(false) }
    var step2Checked by remember { mutableStateOf(false) }
    var step3Checked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "OxygenOS Configuration",
            subtitle = "Step 2 of 2 · OnePlus Nord 5",
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
                .padding(24.dp)
        ) {
            Text(
                text = "OnePlus OxygenOS 16 enforces aggressive background process freezing. To avoid route gaps and service interruptions:",
                style = AtlasTheme.typography.body,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(20.dp))

            OnePlusCheckItem(
                step = "1",
                title = "Battery Usage: Don't Optimize",
                detail = "Go to App info → Battery usage → Set to 'Unrestricted' / 'Don't optimize'.",
                checked = step1Checked,
                onToggle = {
                    step1Checked = !step1Checked
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            OnePlusCheckItem(
                step = "2",
                title = "Auto-launch & Background Running",
                detail = "Allow Atlas to auto-launch on system boot and continue running in the background.",
                checked = step2Checked,
                onToggle = { step2Checked = !step2Checked }
            )

            Spacer(modifier = Modifier.height(14.dp))

            OnePlusCheckItem(
                step = "3",
                title = "Lock in Recent Tasks",
                detail = "In the recent tasks carousel, tap the three dots on Atlas and select 'Lock'.",
                checked = step3Checked,
                onToggle = { step3Checked = !step3Checked }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            HairlineRule()
            Spacer(modifier = Modifier.height(16.dp))
            AtlasButton(
                text = "Complete Setup & Begin Tracking",
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GuaranteeItem(title: String, description: String) {
    val colors = AtlasTheme.colors
    Column {
        Text(text = title, style = AtlasTheme.typography.heading, color = colors.ink)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, style = AtlasTheme.typography.body, color = colors.muted)
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val colors = AtlasTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (granted) colors.ink else colors.rule, RoundedCornerShape(8.dp))
            .background(if (granted) colors.quiet else colors.surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onAction)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AtlasTheme.typography.title, color = colors.ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = AtlasTheme.typography.label, color = colors.muted)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = actionLabel,
            style = AtlasTheme.typography.label,
            color = if (granted) colors.ink else colors.muted
        )
    }
}

@Composable
private fun OnePlusCheckItem(
    step: String,
    title: String,
    detail: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = AtlasTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (checked) colors.ink else colors.rule, RoundedCornerShape(8.dp))
            .background(if (checked) colors.quiet else colors.surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(if (checked) colors.ink else colors.quiet, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = AtlasTheme.typography.numeric.copy(fontSize = 12.sp),
                color = if (checked) colors.surface else colors.ink
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AtlasTheme.typography.title, color = colors.ink)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = detail, style = AtlasTheme.typography.label, color = colors.muted)
        }
    }
}
