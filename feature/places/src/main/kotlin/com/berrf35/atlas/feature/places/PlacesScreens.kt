package com.berrf35.atlas.feature.places

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.berrf35.atlas.core.designsystem.component.EmptyStateView
import com.berrf35.atlas.core.designsystem.component.HairlineRule
import com.berrf35.atlas.core.designsystem.component.ModeMark
import com.berrf35.atlas.core.designsystem.component.SectionHeader
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.map.AtlasMap
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.PlaceId
import com.berrf35.atlas.core.model.PlaceView
import com.berrf35.atlas.core.model.StayId
import com.berrf35.atlas.core.model.StayKind
import com.berrf35.atlas.core.model.StayView
import com.berrf35.atlas.core.model.TimeRange
import kotlinx.coroutines.launch

/**
 * S15: Places List Screen.
 */
@Composable
fun PlacesListScreen(
    onOpenPlace: (PlaceId) -> Unit,
    modifier: Modifier = Modifier
) {
    val placesRepo = remember { AtlasDataLocator.placesRepository }
    val places by placesRepo.getPlaces().collectAsState(initial = emptyList())
    val clusters by placesRepo.getRouteClusters().collectAsState(initial = emptyList())
    val colors = AtlasTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = "PLACES",
            subtitle = "${places.size} Identified Locations"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionHeader(
                title = "FREQUENT LOCATIONS",
                actionLabel = "${places.size} PLACES"
            )

            if (places.isEmpty()) {
                EmptyStateView(
                    title = "No Places Identified Yet",
                    message = "When you stay stationary at a location for more than 15 minutes, Atlas identifies and clusters the visit here."
                )
            } else {
                places.forEach { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                            .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
                            .clickable { onOpenPlace(place.id) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = place.name,
                                    style = AtlasTheme.typography.title,
                                    color = colors.ink
                                )
                                if (place.isPrivacyZone) {
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = "PRIVACY",
                                        style = AtlasTheme.typography.label.copy(fontSize = 10.sp),
                                        color = colors.muted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${place.visitCount} visits · Radius ${place.radiusMeters}m",
                                style = AtlasTheme.typography.label,
                                color = colors.muted
                            )
                        }
                        Text(
                            text = "Edit",
                            style = AtlasTheme.typography.label,
                            color = colors.ink
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HairlineRule()

            // Recurring route corridors section
            SectionHeader(
                title = "RECURRING CORRIDORS",
                actionLabel = "${clusters.size} CLUSTERS"
            )

            clusters.forEach { cluster ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                        .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = cluster.name,
                        style = AtlasTheme.typography.title,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModeMark(mode = cluster.primaryMode)
                        Text(
                            text = "${cluster.memberCount} completed journeys",
                            style = AtlasTheme.typography.label,
                            color = colors.muted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/**
 * S16: Place Detail / Edit Screen.
 */
@Composable
fun PlaceDetailScreen(
    placeId: PlaceId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placesRepo = remember { AtlasDataLocator.placesRepository }
    val place by placesRepo.getPlace(placeId).collectAsState(initial = null)
    val colors = AtlasTheme.colors
    val scope = rememberCoroutineScope()

    if (place == null) {
        Box(
            modifier = modifier.fillMaxSize().background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Loading place...", style = AtlasTheme.typography.body, color = colors.muted)
        }
        return
    }

    val currentPlace = place!!
    var radiusFloat by remember(currentPlace) { mutableFloatStateOf(currentPlace.radiusMeters.toFloat()) }
    var isPrivacyZone by remember(currentPlace) { mutableStateOf(currentPlace.isPrivacyZone) }

    val mockStay = remember(currentPlace) {
        listOf(
            StayView(
                id = StayId("stay-${currentPlace.id}"),
                range = TimeRange(1791964800000L, 1791968400000L),
                kind = StayKind.VISIT,
                location = currentPlace.center,
                placeId = currentPlace.id,
                placeName = currentPlace.name,
                radiusMeters = radiusFloat.toInt()
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        AtlasTopBar(
            title = currentPlace.name,
            subtitle = "${currentPlace.visitCount} visits",
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
        ) {
            // Map header showing geofence center
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AtlasMap(
                    stops = mockStay,
                    interactive = true
                )
            }

            HairlineRule()

            Column(modifier = Modifier.padding(20.dp)) {
                // Radius control
                SectionHeader(title = "DETECTION RADIUS: ${radiusFloat.toInt()} METERS")
                Slider(
                    value = radiusFloat,
                    onValueChange = { radiusFloat = it },
                    valueRange = 25f..500f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Arrivals within this geofence are aggregated into this place.",
                    style = AtlasTheme.typography.label,
                    color = colors.muted
                )

                Spacer(modifier = Modifier.height(24.dp))
                HairlineRule()

                // Privacy Zone Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPrivacyZone = !isPrivacyZone }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Zone",
                            style = AtlasTheme.typography.title,
                            color = colors.ink
                        )
                        Text(
                            text = "Fuzz coordinates in external exports and hide exact address on shared maps.",
                            style = AtlasTheme.typography.label,
                            color = colors.muted
                        )
                    }
                    Text(
                        text = if (isPrivacyZone) "ON" else "OFF",
                        style = AtlasTheme.typography.label,
                        color = if (isPrivacyZone) colors.ink else colors.muted
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                HairlineRule()
                Spacer(modifier = Modifier.height(24.dp))

                // Save button
                AtlasButton(
                    text = "Save Place Changes",
                    onClick = {
                        scope.launch {
                            placesRepo.updatePlace(
                                currentPlace.copy(
                                    radiusMeters = radiusFloat.toInt(),
                                    isPrivacyZone = isPrivacyZone
                                )
                            )
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Delete place button
                AtlasButton(
                    text = "Delete Place",
                    onClick = {
                        scope.launch {
                            placesRepo.deletePlace(currentPlace.id)
                            onBack()
                        }
                    },
                    variant = ButtonVariant.DESTRUCTIVE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
