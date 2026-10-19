package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.PaymentEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate

/**
 * Data structure representing aggregated daily financial metrics for visual line charts.
 */
data class DailyFinancialPoint(
    val dateIso: String,
    val jalaliDate: JalaliDateHelper.JalaliDate,
    val totalExpense: Long,
    val totalSalaryPaid: Long
)

/**
 * Visual reporting component featuring an interactive dual line chart:
 * - Line 1: مجموع هزینه‌های روزانه (Daily Total Expenses)
 * - Line 2: حقوق پرداخت شده روزانه (Daily Salaries Paid)
 * Inspired by Recharts area-line chart with interactive scrubber, gradient fills, and summary cards.
 */
@Composable
fun VisualReportsChartComponent(
    expenses: List<ExpenseEntity>,
    payments: List<PaymentEntity>,
    startDateIso: String,
    endDateIso: String,
    onPeriodSelected: (startIso: String, endIso: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPreset by remember { mutableStateOf("14days") }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var isDailyListExpanded by remember { mutableStateOf(false) }

    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }

    // Aggregate daily data in the current date range
    val chartPoints = remember(expenses, payments, startDateIso, endDateIso) {
        val points = mutableListOf<DailyFinancialPoint>()
        var curr = startDateIso
        val safeEnd = if (endDateIso < startDateIso) startDateIso else endDateIso
        var iterations = 0

        while (curr <= safeEnd && iterations < 90) {
            val dateExpenses = expenses.filter { it.date == curr && !it.deleted }
            val expSum = dateExpenses.sumOf { it.amount }

            val datePayments = payments.filter {
                it.date == curr && !it.deleted && (it.targetType == "person" || it.handedToPersonId != null)
            }
            val salSum = datePayments.sumOf { it.amount }

            val jDate = try {
                JalaliDateHelper.fromIsoDate(curr)
            } catch (_: Exception) {
                JalaliDateHelper.today()
            }

            points.add(
                DailyFinancialPoint(
                    dateIso = curr,
                    jalaliDate = jDate,
                    totalExpense = expSum,
                    totalSalaryPaid = salSum
                )
            )

            curr = JalaliDateHelper.addDays(curr, 1)
            iterations++
        }
        points
    }

    // Default selected point to last day if not chosen
    LaunchedEffect(chartPoints.size) {
        if (chartPoints.isNotEmpty()) {
            selectedPointIndex = chartPoints.lastIndex
        }
    }

    val totalExpensesInRange = remember(chartPoints) { chartPoints.sumOf { it.totalExpense } }
    val totalSalariesInRange = remember(chartPoints) { chartPoints.sumOf { it.totalSalaryPaid } }
    val maxExpensePoint = remember(chartPoints) { chartPoints.maxByOrNull { it.totalExpense } }
    val maxSalaryPoint = remember(chartPoints) { chartPoints.maxByOrNull { it.totalSalaryPaid } }
    val daysCount = chartPoints.size.coerceAtLeast(1)
    val avgDailyExpense = totalExpensesInRange / daysCount
    val avgDailySalary = totalSalariesInRange / daysCount

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("visual_reports_chart_component"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Chart Header with Title and Range Presets
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "نمودار خطی هزینه‌ها و حقوق",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${NumberFormatHelper.toPersianDigits(chartPoints.size)} روز",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Quick Period Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedPreset == "7days",
                            onClick = {
                                selectedPreset = "7days"
                                onPeriodSelected(JalaliDateHelper.addDays(todayIso, -6), todayIso)
                            },
                            label = { Text("۷ روز اخیر") },
                            modifier = Modifier.testTag("chart_preset_7d")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedPreset == "14days",
                            onClick = {
                                selectedPreset = "14days"
                                onPeriodSelected(JalaliDateHelper.addDays(todayIso, -13), todayIso)
                            },
                            label = { Text("۱۴ روز اخیر") },
                            modifier = Modifier.testTag("chart_preset_14d")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedPreset == "30days",
                            onClick = {
                                selectedPreset = "30days"
                                onPeriodSelected(JalaliDateHelper.addDays(todayIso, -29), todayIso)
                            },
                            label = { Text("۳۰ روز اخیر") },
                            modifier = Modifier.testTag("chart_preset_30d")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedPreset == "curMonth",
                            onClick = {
                                selectedPreset = "curMonth"
                                val jToday = JalaliDateHelper.today()
                                val gFirst = JalaliDateHelper.toGregorian(jToday.year, jToday.month, 1)
                                onPeriodSelected(JalaliDateHelper.toIsoDate(gFirst), todayIso)
                            },
                            label = { Text("ماه جاری") },
                            modifier = Modifier.testTag("chart_preset_cur_month")
                        )
                    }
                }

                // Chart Legend (Recharts style)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    // Expenses series indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(ColorWarningOrange)
                        )
                        Text(
                            text = "مجموع هزینه‌ها",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Salaries series indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(ColorSuccessGreen)
                        )
                        Text(
                            text = "حقوق پرداخت‌شده",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // The Dual Line Chart Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    DualLineChartCanvas(
                        points = chartPoints,
                        selectedPointIndex = selectedPointIndex,
                        onSelectPoint = { selectedPointIndex = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Selected Point Tooltip / Detail Card
                val activePoint = selectedPointIndex?.let { chartPoints.getOrNull(it) }
                if (activePoint != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chart_tooltip_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = activePoint.jalaliDate.toDisplayFull(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = activePoint.dateIso,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = "هزینه این روز",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = NumberFormatHelper.formatTomanFull(activePoint.totalExpense),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorWarningOrange
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "حقوق پرداخت‌شده",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = NumberFormatHelper.formatTomanFull(activePoint.totalSalaryPaid),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorSuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Summary KPI Metric Cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "کل هزینه‌های بازه",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = NumberFormatHelper.formatTomanShort(totalExpensesInRange),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarningOrange
                    )
                    Text(
                        text = "میانگین روزانه: ${NumberFormatHelper.formatTomanShort(avgDailyExpense)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "کل حقوق پرداختی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = NumberFormatHelper.formatTomanShort(totalSalariesInRange),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorSuccessGreen
                    )
                    Text(
                        text = "میانگین روزانه: ${NumberFormatHelper.formatTomanShort(avgDailySalary)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Peak Day Info if available
        if (maxExpensePoint != null && maxExpensePoint.totalExpense > 0) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = ColorWarningOrange)
                        Column {
                            Text(
                                text = "روز اوج هزینه‌ها:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = maxExpensePoint.jalaliDate.toDisplayShort(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = NumberFormatHelper.formatTomanFull(maxExpensePoint.totalExpense),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarningOrange
                    )
                }
            }
        }

        // 3. Expandable Daily Breakdown Table
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDailyListExpanded = !isDailyListExpanded }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "ریز تفکیک روزانه (جدول داده‌ها)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = { isDailyListExpanded = !isDailyListExpanded }) {
                        Icon(
                            if (isDailyListExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isDailyListExpanded) "بستن" else "باز کردن"
                        )
                    }
                }

                AnimatedVisibility(visible = isDailyListExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        val activePoints = chartPoints.filter { it.totalExpense > 0 || it.totalSalaryPaid > 0 }
                        if (activePoints.isEmpty()) {
                            Text(
                                text = "در این بازه، تراکنش هزینه یا حقوق ثبت نشده است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            activePoints.reversed().forEach { point ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (selectedPointIndex != null && chartPoints.getOrNull(selectedPointIndex!!)?.dateIso == point.dateIso)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedPointIndex = chartPoints.indexOfFirst { it.dateIso == point.dateIso }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 6.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = point.jalaliDate.toDisplayShort(),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = point.jalaliDate.dayOfWeekName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (point.totalExpense > 0) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        text = "هزینه: ${NumberFormatHelper.formatTomanShort(point.totalExpense)}",
                                                        color = ColorWarningOrange,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            )
                                        }
                                        if (point.totalSalaryPaid > 0) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        text = "حقوق: ${NumberFormatHelper.formatTomanShort(point.totalSalaryPaid)}",
                                                        color = ColorSuccessGreen,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Canvas drawing a high-performance, smooth interactive dual line chart.
 */
@Composable
private fun DualLineChartCanvas(
    points: List<DailyFinancialPoint>,
    selectedPointIndex: Int?,
    onSelectPoint: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "chartAnim"
    )

    val maxVal = remember(points) {
        val maxE = points.maxOfOrNull { it.totalExpense } ?: 0L
        val maxS = points.maxOfOrNull { it.totalSalaryPaid } ?: 0L
        val peak = maxOf(maxE, maxS)
        if (peak <= 0L) 1_000_000L else peak
    }

    val expenseColor = ColorWarningOrange
    val salaryColor = ColorSuccessGreen
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val textLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val totalW = size.width
                    val padL = 40f
                    val padR = 40f
                    val chartW = totalW - padL - padR
                    if (chartW > 0 && points.size > 1) {
                        val relX = (offset.x - padL).coerceIn(0f, chartW)
                        val stepX = chartW / (points.size - 1)
                        val idx = (relX / stepX).toInt().coerceIn(0, points.size - 1)
                        onSelectPoint(idx)
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        val padLeft = 20f
        val padRight = 50f
        val padTop = 20f
        val padBottom = 35f

        val chartWidth = width - padLeft - padRight
        val chartHeight = height - padTop - padBottom

        if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

        // 1. Draw horizontal grid lines (4 lines)
        val gridLevels = 4
        for (i in 0..gridLevels) {
            val y = padTop + (chartHeight * (i.toFloat() / gridLevels))
            drawLine(
                color = gridColor,
                start = Offset(padLeft, y),
                end = Offset(width - padRight, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
        }

        val stepX = if (points.size > 1) chartWidth / (points.size - 1) else chartWidth

        // Helper to convert index and value to offset
        fun pointOffset(index: Int, value: Long): Offset {
            val x = padLeft + (index * stepX)
            val fraction = (value.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
            val y = padTop + chartHeight - (chartHeight * fraction * animatedProgress)
            return Offset(x, y)
        }

        // 2. Paths for Expenses Line & Gradient Fill
        val expensePath = Path()
        val expenseFillPath = Path()

        val salaryPath = Path()
        val salaryFillPath = Path()

        points.forEachIndexed { i, pt ->
            val eOffset = pointOffset(i, pt.totalExpense)
            val sOffset = pointOffset(i, pt.totalSalaryPaid)

            if (i == 0) {
                expensePath.moveTo(eOffset.x, eOffset.y)
                expenseFillPath.moveTo(eOffset.x, padTop + chartHeight)
                expenseFillPath.lineTo(eOffset.x, eOffset.y)

                salaryPath.moveTo(sOffset.x, sOffset.y)
                salaryFillPath.moveTo(sOffset.x, padTop + chartHeight)
                salaryFillPath.lineTo(sOffset.x, sOffset.y)
            } else {
                val prevEOffset = pointOffset(i - 1, points[i - 1].totalExpense)
                val prevSOffset = pointOffset(i - 1, points[i - 1].totalSalaryPaid)

                // Smooth cubic Bezier curves
                val cX = (prevEOffset.x + eOffset.x) / 2
                expensePath.cubicTo(cX, prevEOffset.y, cX, eOffset.y, eOffset.x, eOffset.y)
                expenseFillPath.cubicTo(cX, prevEOffset.y, cX, eOffset.y, eOffset.x, eOffset.y)

                val cXs = (prevSOffset.x + sOffset.x) / 2
                salaryPath.cubicTo(cXs, prevSOffset.y, cXs, sOffset.y, sOffset.x, sOffset.y)
                salaryFillPath.cubicTo(cXs, prevSOffset.y, cXs, sOffset.y, sOffset.x, sOffset.y)
            }

            if (i == points.lastIndex) {
                expenseFillPath.lineTo(eOffset.x, padTop + chartHeight)
                expenseFillPath.close()

                salaryFillPath.lineTo(sOffset.x, padTop + chartHeight)
                salaryFillPath.close()
            }
        }

        // Draw Expense Gradient Fill
        drawPath(
            path = expenseFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(expenseColor.copy(alpha = 0.22f), expenseColor.copy(alpha = 0.02f)),
                startY = padTop,
                endY = padTop + chartHeight
            )
        )

        // Draw Salary Gradient Fill
        drawPath(
            path = salaryFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(salaryColor.copy(alpha = 0.20f), salaryColor.copy(alpha = 0.02f)),
                startY = padTop,
                endY = padTop + chartHeight
            )
        )

        // Draw Line strokes
        drawPath(
            path = expensePath,
            color = expenseColor,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawPath(
            path = salaryPath,
            color = salaryColor,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 3. Draw Data Points
        points.forEachIndexed { i, pt ->
            val eOffset = pointOffset(i, pt.totalExpense)
            val sOffset = pointOffset(i, pt.totalSalaryPaid)

            // Draw circular dots
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = eOffset
            )
            drawCircle(
                color = expenseColor,
                radius = 3.dp.toPx(),
                center = eOffset
            )

            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = sOffset
            )
            drawCircle(
                color = salaryColor,
                radius = 3.dp.toPx(),
                center = sOffset
            )
        }

        // 4. Highlight Selected Index
        if (selectedPointIndex != null && selectedPointIndex in points.indices) {
            val selIdx = selectedPointIndex
            val pt = points[selIdx]
            val selEOffset = pointOffset(selIdx, pt.totalExpense)
            val selSOffset = pointOffset(selIdx, pt.totalSalaryPaid)

            // Draw vertical scrubber guide line
            drawLine(
                color = textLabelColor,
                start = Offset(selEOffset.x, padTop),
                end = Offset(selEOffset.x, padTop + chartHeight),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )

            // Outer highlight halo on Expense point
            drawCircle(
                color = expenseColor.copy(alpha = 0.35f),
                radius = 9.dp.toPx(),
                center = selEOffset
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = selEOffset
            )
            drawCircle(
                color = expenseColor,
                radius = 4.dp.toPx(),
                center = selEOffset
            )

            // Outer highlight halo on Salary point
            drawCircle(
                color = salaryColor.copy(alpha = 0.35f),
                radius = 9.dp.toPx(),
                center = selSOffset
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = selSOffset
            )
            drawCircle(
                color = salaryColor,
                radius = 4.dp.toPx(),
                center = selSOffset
            )
        }
    }
}
