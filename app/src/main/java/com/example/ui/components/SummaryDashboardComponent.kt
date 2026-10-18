package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate

/**
 * Summary dashboard component providing an overview of farm operations:
 * - Number of farms & plots
 * - Total workers, foremen & crews
 * - Active, today, and overdue tasks
 * With animated chart visualization and quick navigation.
 */
@Composable
fun SummaryDashboardComponent(
    uiState: MainUiState,
    onNavigateToFarms: () -> Unit,
    onNavigateToWorkforce: () -> Unit,
    onNavigateToTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    val todayIso = remember {
        val now = LocalDate.now()
        val m = now.monthValue.toString().padStart(2, '0')
        val d = now.dayOfMonth.toString().padStart(2, '0')
        "${now.year}-$m-$d"
    }

    // Farms & Plots count
    val farmsCount = uiState.farms.size
    val plotsCount = uiState.plots.size

    // Workforce calculations
    val allPersons = uiState.persons.filter { it.active }
    val totalPersonsCount = allPersons.size
    val crewsCount = uiState.crews.size
    val activeAttendancesToday = uiState.attendances.count { it.date == todayIso }

    // Tasks calculations
    val openTasks = uiState.tasks.filter { it.status == "open" }
    val activeTasksCount = openTasks.size
    val overdueTasksCount = openTasks.count { it.endDate < todayIso }
    val doneTasksCount = uiState.tasks.count { it.status == "done" }
    val totalTasksCount = uiState.tasks.size
    val completionRate = if (totalTasksCount > 0) (doneTasksCount.toFloat() / totalTasksCount.toFloat()) else 0f

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("summary_dashboard_component")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title + Toggle button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "نمای کلی مزرعه",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "خلاصه وضعیت مزارع، نیروی کار و کارها",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("summary_dashboard_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "بستن خلاصه" else "نمایش خلاصه",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    // Row of 3 KPI Cards
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        KpiSummaryItem(
                            title = "مزارع",
                            mainValue = farmsCount,
                            subLabel = "${NumberFormatHelper.toPersianDigits(plotsCount)} قطعه",
                            icon = Icons.Default.Landscape,
                            color = ColorSuccessGreen,
                            onClick = onNavigateToFarms,
                            modifier = Modifier.weight(1f)
                        )

                        KpiSummaryItem(
                            title = "نیروی کار",
                            mainValue = totalPersonsCount,
                            subLabel = "${NumberFormatHelper.toPersianDigits(crewsCount)} دسته",
                            icon = Icons.Default.Groups,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = onNavigateToWorkforce,
                            modifier = Modifier.weight(1f)
                        )

                        KpiSummaryItem(
                            title = "کارهای فعال",
                            mainValue = activeTasksCount,
                            subLabel = if (overdueTasksCount > 0) "${NumberFormatHelper.toPersianDigits(overdueTasksCount)} عقب‌افتاده" else "در جریان",
                            icon = Icons.Default.Assignment,
                            color = if (overdueTasksCount > 0) ColorDangerRed else ColorWarningOrange,
                            onClick = onNavigateToTasks,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Visual Chart Section: Custom Bar & Donut Visualization
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "نمودار مقایسه‌ای وضعیت عملیات",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Interactive Bar Chart drawn with Jetpack Compose Canvas
                            val barData = listOf(
                                ChartBarItem("مزارع", farmsCount, ColorSuccessGreen),
                                ChartBarItem("نیروی کار", totalPersonsCount, MaterialTheme.colorScheme.primary),
                                ChartBarItem("کارهای باز", activeTasksCount, ColorWarningOrange),
                                ChartBarItem("انجام‌شده", doneTasksCount, Color(0xFF00897B))
                            )

                            OperationsBarChart(
                                items = barData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                            )

                            // Quick progress meter if there are tasks
                            if (totalTasksCount > 0) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val percentInt = (completionRate * 100).toInt()
                                    val percentFa = NumberFormatHelper.toPersianDigits(percentInt)
                                    Text(
                                        text = "پیشرفت کارهای کل سامانه: $percentFa٪",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )

                                    val doneFa = NumberFormatHelper.toPersianDigits(doneTasksCount)
                                    val totalFa = NumberFormatHelper.toPersianDigits(totalTasksCount)
                                    Text(
                                        text = "$doneFa از $totalFa کار",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { completionRate },
                                    color = ColorSuccessGreen,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ChartBarItem(
    val label: String,
    val value: Int,
    val color: Color
)

@Composable
private fun KpiSummaryItem(
    title: String,
    mainValue: Int,
    subLabel: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = NumberFormatHelper.toPersianDigits(mainValue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun OperationsBarChart(
    items: List<ChartBarItem>,
    modifier: Modifier = Modifier
) {
    val maxValue = remember(items) {
        val max = items.maxOfOrNull { it.value } ?: 1
        if (max <= 0) 1 else max
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        items.forEach { item ->
            val fraction = (item.value.toFloat() / maxValue.toFloat()).coerceIn(0.08f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(durationMillis = 650),
                label = "bar_anim_${item.label}"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            ) {
                // Value text on top of bar
                Text(
                    text = NumberFormatHelper.toPersianDigits(item.value),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = item.color
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Bar canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .weight(1f)
                ) {
                    val barWidth = size.width
                    val totalHeight = size.height
                    val barHeight = totalHeight * animatedFraction
                    val topY = totalHeight - barHeight

                    // Subtle background track
                    drawRoundRect(
                        color = item.color.copy(alpha = 0.12f),
                        topLeft = Offset(0f, 0f),
                        size = Size(barWidth, totalHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                    )

                    // Active filled bar
                    drawRoundRect(
                        color = item.color,
                        topLeft = Offset(0f, topY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Label below bar
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
