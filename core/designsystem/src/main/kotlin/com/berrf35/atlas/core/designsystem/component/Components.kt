package com.berrf35.atlas.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berrf35.atlas.core.designsystem.theme.AtlasTheme
import com.berrf35.atlas.core.designsystem.theme.ModeVisuals
import com.berrf35.atlas.core.model.TrackingHealth
import com.berrf35.atlas.core.model.TransportMode

/**
 * Hairline rule separator matching UI_UX_SPEC §1.1.
 */
@Composable
fun HairlineRule(
    modifier: Modifier = Modifier,
    color: Color = AtlasTheme.colors.rule
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/**
 * Top bar with minimal baseline: back/close at left, contextual title, up to two actions.
 */
@Composable
fun AtlasTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AtlasTheme.colors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = AtlasTheme.typography.title,
                    color = AtlasTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = AtlasTheme.typography.label,
                        color = AtlasTheme.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (actions != null) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        }
        HairlineRule()
    }
}

/**
 * Primary numerical metric display (large mono value with 12sp label below).
 */
@Composable
fun Metric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    isLarge: Boolean = false,
    annotation: String? = null
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = if (isLarge) AtlasTheme.typography.numericLarge else AtlasTheme.typography.numeric,
                color = AtlasTheme.colors.ink,
                maxLines = 1,
                softWrap = false
            )
            if (unit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = AtlasTheme.typography.label,
                    color = AtlasTheme.colors.muted,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (annotation != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = annotation,
                    style = AtlasTheme.typography.label,
                    color = AtlasTheme.colors.muted,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = AtlasTheme.typography.label,
            color = AtlasTheme.colors.muted,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Section Header: heading with optional quiet action aligned baseline. No container card.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = AtlasTheme.typography.heading,
            color = AtlasTheme.colors.ink
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = AtlasTheme.typography.label,
                color = AtlasTheme.colors.muted,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(4.dp)
            )
        }
    }
}

/**
 * 48dp Button matching visual rules: Primary (filled ink), Secondary (outline), Tertiary (text), Destructive.
 */
enum class ButtonVariant {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    DESTRUCTIVE
}

@Composable
fun AtlasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(8.dp)
    val colors = AtlasTheme.colors

    val (bg, contentColor, border) = when (variant) {
        ButtonVariant.PRIMARY -> Triple(
            if (enabled) colors.ink else colors.quiet,
            if (enabled) colors.surface else colors.muted,
            null
        )
        ButtonVariant.SECONDARY -> Triple(
            Color.Transparent,
            if (enabled) colors.ink else colors.muted,
            if (enabled) colors.rule else colors.quiet
        )
        ButtonVariant.TERTIARY -> Triple(
            Color.Transparent,
            if (enabled) colors.ink else colors.muted,
            null
        )
        ButtonVariant.DESTRUCTIVE -> Triple(
            Color.Transparent,
            if (enabled) colors.danger else colors.muted,
            if (enabled) colors.danger else colors.quiet
        )
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .then(
                if (border != null) Modifier.border(1.dp, border, shape)
                else Modifier
            )
            .background(bg, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AtlasTheme.typography.label.copy(fontSize = 13.sp),
            color = contentColor
        )
    }
}

/**
 * Mode pattern mark (32x16 dp swatch + label).
 */
@Composable
fun ModeMark(
    mode: TransportMode,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val visual = ModeVisuals.get(mode)
    val color = if (AtlasTheme.colors.isDark) visual.darkTint else visual.lightTint

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 14.dp)
                .background(AtlasTheme.colors.quiet, RoundedCornerShape(2.dp))
                .drawBehind {
                    val stroke = visual.strokeWidthDp.dp.toPx()
                    val pathEffect = when {
                        visual.dashIntervals != null -> PathEffect.dashPathEffect(visual.dashIntervals, 0f)
                        visual.dotIntervals != null -> PathEffect.dashPathEffect(visual.dotIntervals, 0f)
                        else -> null
                    }
                    drawLine(
                        color = color,
                        start = Offset(4f, size.height / 2),
                        end = Offset(size.width - 4f, size.height / 2),
                        strokeWidth = stroke,
                        pathEffect = pathEffect
                    )
                }
        )
        if (showLabel) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = visual.label,
                style = AtlasTheme.typography.label,
                color = AtlasTheme.colors.ink,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Tracking health chip (plain text chip with state indicator).
 */
@Composable
fun HealthChip(
    health: TrackingHealth,
    modifier: Modifier = Modifier,
    detailText: String? = null,
    overrideLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = AtlasTheme.colors
    val (statusColor, bg) = if (overrideLabel != null) {
        Pair(colors.muted, colors.surfaceRaised)
    } else when (health) {
        TrackingHealth.RECORDING -> Pair(colors.ink, colors.quiet)
        TrackingHealth.RESTING -> Pair(colors.muted, colors.surfaceRaised)
        TrackingHealth.DEGRADED -> Pair(colors.muted, colors.quiet)
        TrackingHealth.INTERRUPTED -> Pair(colors.danger, colors.quiet)
        TrackingHealth.BLOCKED -> Pair(colors.danger, colors.quiet)
        TrackingHealth.PAUSED -> Pair(colors.muted, colors.quiet)
        TrackingHealth.OFF -> Pair(colors.muted, colors.surfaceRaised)
    }

    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(statusColor, RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = overrideLabel ?: health.label,
            style = AtlasTheme.typography.label,
            color = colors.ink,
            maxLines = 1,
            softWrap = false
        )
        if (detailText != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "· $detailText",
                style = AtlasTheme.typography.label,
                color = colors.muted,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Timeline row: 56dp time column, vertical rule with dot, title, metadata, chevron.
 */
@Composable
fun TimelineRow(
    timeDisplay: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    mode: TransportMode? = null,
    isGap: Boolean = false,
    durationDisplay: String? = null,
    distanceDisplay: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time column (56 dp mono)
        Text(
            text = timeDisplay,
            style = AtlasTheme.typography.numeric.copy(fontSize = 13.sp),
            color = AtlasTheme.colors.muted,
            modifier = Modifier.width(56.dp)
        )

        // Vertical indicator rule + marker
        Box(
            modifier = Modifier
                .width(16.dp)
                .padding(top = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .size(if (isGap) 6.dp else 8.dp)
                    .background(
                        if (isGap) AtlasTheme.colors.danger else AtlasTheme.colors.ink,
                        RoundedCornerShape(4.dp)
                    )
            )
        }

        // Details column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = AtlasTheme.typography.title.copy(fontSize = 14.sp),
                    color = if (isGap) AtlasTheme.colors.danger else AtlasTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (distanceDisplay != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = distanceDisplay,
                        style = AtlasTheme.typography.numeric.copy(fontSize = 13.sp),
                        color = AtlasTheme.colors.ink,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mode != null) {
                        ModeMark(mode = mode, showLabel = false)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = subtitle,
                        style = AtlasTheme.typography.body.copy(fontSize = 12.sp),
                        color = AtlasTheme.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (durationDisplay != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = durationDisplay,
                        style = AtlasTheme.typography.label,
                        color = AtlasTheme.colors.muted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * FilterBar: Horizontally scrollable text items with underline selected state.
 */
@Composable
fun FilterBar(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    resultCount: Int? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier
                    .clickable { onItemSelected(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = AtlasTheme.typography.label,
                    color = if (isSelected) AtlasTheme.colors.ink else AtlasTheme.colors.muted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(24.dp)
                        .background(if (isSelected) AtlasTheme.colors.ink else Color.Transparent)
                )
            }
        }

        if (resultCount != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$resultCount items",
                style = AtlasTheme.typography.label,
                color = AtlasTheme.colors.muted,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}

/**
 * Empty state: Explains what is expected and names the next useful action.
 */
@Composable
fun EmptyStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = AtlasTheme.typography.heading,
            color = AtlasTheme.colors.ink,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = AtlasTheme.typography.body,
            color = AtlasTheme.colors.muted,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            AtlasButton(
                text = actionLabel,
                onClick = onActionClick,
                variant = ButtonVariant.SECONDARY
            )
        }
    }
}

/**
 * Error state with diagnosis and retry.
 */
@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = AtlasTheme.typography.body,
            color = AtlasTheme.colors.danger,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        AtlasButton(
            text = "Retry",
            onClick = onRetry,
            variant = ButtonVariant.SECONDARY
        )
    }
}
