package com.balance.classreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.DAY_NAMES
import com.balance.classreminder.data.DayKind
import com.balance.classreminder.data.DayOverride
import com.balance.classreminder.data.dayName
import com.balance.classreminder.domain.WeekCalc
import java.time.LocalDate
import java.time.YearMonth

/**
 * 教学日历：看整月的上课情况，并逐日调整放假/调休。
 * 国家法定调休（周六上周三的课）靠这里手动标，标完提醒和课表都会跟着变。
 */
@Composable
fun CalendarScreen(
    settings: AppSettings,
    courses: List<Course>,
    onChange: (AppSettings) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()

    fun overrides() = settings.overrides

    fun putOverride(date: LocalDate, kind: DayKind, swapTo: Int, note: String) {
        val next = overrides().filterNot { it.date == date.toString() }.toMutableList()
        if (kind != DayKind.NORMAL) {
            next += DayOverride(date.toString(), kind, swapTo, note)
        }
        onChange(settings.copy(overrides = next.sortedBy { it.date }))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("◀") }
            Spacer(Modifier.weight(1f))
            Text(
                "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("▶") }
        }

        Row(Modifier.fillMaxWidth()) {
            DAY_NAMES.forEach { name ->
                Text(
                    name.removePrefix("周"),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        val firstOfMonth = month.atDay(1)
        val leading = firstOfMonth.dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()
        val cells = leading + daysInMonth
        val rows = (cells + 6) / 7

        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = row * 7 + col - leading + 1
                    if (dayNumber < 1 || dayNumber > daysInMonth) {
                        Box(Modifier.weight(1f).aspectRatio(0.85f))
                        continue
                    }
                    val date = month.atDay(dayNumber)
                    val override = WeekCalc.overrideOf(settings, date)
                    val effectiveDay = WeekCalc.effectiveDayOfWeek(settings, date)
                    val classes = if (effectiveDay == null) emptyList()
                    else WeekCalc.coursesOn(settings, courses, date)

                    val bg = when {
                        override?.kind == DayKind.HOLIDAY -> Color(0xFFFFCDD2)
                        override?.kind == DayKind.SWAP -> Color(0xFFFFE0B2)
                        classes.isNotEmpty() -> Color(0xFFE3F2FD)
                        else -> Color.Transparent
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            .padding(1.dp)
                            .background(bg, RoundedCornerShape(4.dp))
                            .border(
                                if (date == today) 2.dp else 0.5.dp,
                                if (date == today) Color(0xFF1976D2) else Color(0xFFBDBDBD),
                                RoundedCornerShape(4.dp),
                            )
                            .clickable { editingDate = date }
                            .padding(2.dp)
                    ) {
                        Text("$dayNumber", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        when {
                            override?.kind == DayKind.HOLIDAY -> Text("放假", fontSize = 9.sp, color = Color(0xFFB71C1C))
                            override?.kind == DayKind.SWAP -> Text(
                                "上" + dayName(override.swapToDayOfWeek), fontSize = 9.sp, color = Color(0xFFE65100)
                            )
                            classes.isNotEmpty() -> Text("${classes.size} 节", fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        Text(
            "蓝色=有课，红色=放假，橙色=调休（按别的星期上课）。点任意一天可以改。",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (settings.overrides.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("已设置的调休（${settings.overrides.size} 天）", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    settings.overrides.forEach { o ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildString {
                                    append(o.date)
                                    when (o.kind) {
                                        DayKind.HOLIDAY -> append(" 放假")
                                        DayKind.SWAP -> append(" 上").append(dayName(o.swapToDayOfWeek)).append("的课")
                                        DayKind.NORMAL -> append(" 正常")
                                    }
                                    if (o.note.isNotBlank()) append("（").append(o.note).append("）")
                                },
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onChange(settings.copy(overrides = settings.overrides.filterNot { it.date == o.date }))
                            }) { Text("删除", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }

        Text(
            "调休常用法：放假那天标「放假」；被调成工作日的周末标「上X的课」，比如 10 月 11 日周六上星期三的课。",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }

    editingDate?.let { date ->
        val existing = WeekCalc.overrideOf(settings, date)
        DayEditDialog(
            date = date,
            existing = existing,
            onDismiss = { editingDate = null },
            onSave = { kind, swapTo, note ->
                putOverride(date, kind, swapTo, note)
                editingDate = null
            },
        )
    }
}

@Composable
private fun DayEditDialog(
    date: LocalDate,
    existing: DayOverride?,
    onDismiss: () -> Unit,
    onSave: (DayKind, Int, String) -> Unit,
) {
    var kind by remember(date) { mutableStateOf(existing?.kind ?: DayKind.NORMAL) }
    var swapTo by remember(date) { mutableStateOf(existing?.swapToDayOfWeek ?: date.dayOfWeek.value) }
    var note by remember(date) { mutableStateOf(existing?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$date（${dayName(date.dayOfWeek.value)}）") },
        text = {
            Column {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = kind == DayKind.NORMAL,
                        onClick = { kind = DayKind.NORMAL },
                        label = { Text("正常上课") },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    FilterChip(
                        selected = kind == DayKind.HOLIDAY,
                        onClick = { kind = DayKind.HOLIDAY },
                        label = { Text("放假") },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    FilterChip(
                        selected = kind == DayKind.SWAP,
                        onClick = { kind = DayKind.SWAP },
                        label = { Text("按别的星期上") },
                    )
                }

                if (kind == DayKind.SWAP) {
                    Text("这天按星期几的课表上课", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        (1..7).forEach { d ->
                            FilterChip(
                                selected = swapTo == d,
                                onClick = { swapTo = d },
                                label = { Text(dayName(d)) },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(kind, swapTo, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
