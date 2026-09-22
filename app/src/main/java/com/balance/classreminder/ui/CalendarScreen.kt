package com.balance.classreminder.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.BuiltInHolidays
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.DAY_NAMES
import com.balance.classreminder.data.DayKind
import com.balance.classreminder.data.DayOverride
import com.balance.classreminder.data.DeviceCalendar
import com.balance.classreminder.data.dayName
import com.balance.classreminder.domain.WeekCalc
import java.time.LocalDate
import java.time.YearMonth

private val COLOR_CLASS = Color(0xFF90CAF9)
private val COLOR_HOLIDAY = Color(0xFFEF9A9A)
private val COLOR_SWAP = Color(0xFFFFCC80)
private val COLOR_VACATION = Color(0xFFCFD8DC)

/**
 * 教学日历：看整月的上课情况，调整放假/调休/假期。
 *
 * 法定节假日走系统日历接口（CalendarContract）读取，读不到再退回内置的 2026 安排；
 * 学期区间（第一周周一到最后一周末）之外的日期按学生假期显示（寒暑假）。
 */
@Composable
fun CalendarScreen(
    settings: AppSettings,
    courses: List<Course>,
    onChange: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var holidayRows by remember { mutableStateOf<List<HolidayRow>?>(null) }
    var importMessage by remember { mutableStateOf("") }
    var pendingFromPhone by remember { mutableStateOf<List<DeviceCalendar.HolidayEvent>>(emptyList()) }

    val termStartDate = WeekCalc.parseDate(settings.termStartDate)
    val termEndDate = termStartDate?.plusDays(settings.totalWeeks * 7L - 1)

    fun applyHolidays(events: List<DeviceCalendar.HolidayEvent>) {
        if (events.isEmpty()) {
            importMessage = "手机日历里没有节假日事件。系统日历要先订阅「中国法定节假日」（日历 App → 设置 → 节假日）；" +
                "不想订阅就用「内置 2026 安排」，或者直接点日期手动标。"
            return
        }
        holidayRows = events.map { event ->
            HolidayRow(
                event = event,
                selected = true,
                kind = if (event.isWorkday) DayKind.SWAP else DayKind.HOLIDAY,
                // 标「班」的日子先不定上哪天的课，之后由用户安排
                swapToDayOfWeek = if (event.isWorkday) 0 else event.date.dayOfWeek.value,
                swapToWeek = if (event.isWorkday) -1
                else WeekCalc.weekOf(termStartDate, event.date),
            )
        }
    }

    fun queryPhoneCalendar() {
        val from = termStartDate?.minusDays(14) ?: today.minusDays(30)
        val to = termStartDate?.plusDays(settings.totalWeeks * 7L + 28) ?: today.plusDays(210)
        applyHolidays(DeviceCalendar.query(context, from, to))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) queryPhoneCalendar()
        else importMessage = "没给日历权限，读不到系统日历里的节假日；也可以手动标或用内置安排。"
    }

    fun ensurePermissionThenQuery() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) queryPhoneCalendar() else permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
    }

    // 走系统日历接口：进页面查一次，有没标过的节假日就提示一键应用
    LaunchedEffect(settings.termStartDate, settings.totalWeeks) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            return@LaunchedEffect
        }
        val from = termStartDate?.minusDays(14) ?: today.minusDays(30)
        val to = termStartDate?.plusDays(settings.totalWeeks * 7L + 28) ?: today.plusDays(210)
        pendingFromPhone = DeviceCalendar.query(context, from, to)
            .filter { event -> settings.overrides.none { it.date == event.date.toString() } }
    }

    fun putOverride(date: LocalDate, kind: DayKind, swapTo: Int, note: String, swapWeek: Int) {
        val next = settings.overrides.filterNot { it.date == date.toString() }.toMutableList()
        if (kind != DayKind.NORMAL) {
            next += DayOverride(date.toString(), kind, swapTo, note, swapWeek)
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

        val leading = month.atDay(1).dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()
        val rows = (leading + daysInMonth + 6) / 7

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
                    val classes = WeekCalc.coursesOn(settings, courses, date)
                    val inTerm = termStartDate != null && termEndDate != null &&
                        !date.isBefore(termStartDate) && !date.isAfter(termEndDate)
                    val vacation = termStartDate != null && !inTerm

                    val bg = when {
                        override?.kind == DayKind.HOLIDAY -> COLOR_HOLIDAY
                        override?.kind == DayKind.SWAP -> COLOR_SWAP
                        vacation -> COLOR_VACATION
                        classes.isNotEmpty() -> COLOR_CLASS
                        else -> Color.White
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            .padding(1.dp)
                            .background(bg, RoundedCornerShape(4.dp))
                            .border(
                                if (date == today) 2.5.dp else 0.5.dp,
                                if (date == today) Color(0xFF0D47A1) else Color(0xFF9E9E9E),
                                RoundedCornerShape(4.dp),
                            )
                            .clickable { editingDate = date }
                            .padding(3.dp)
                    ) {
                        Text("$dayNumber", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        when {
                            override?.kind == DayKind.HOLIDAY -> Text("放假", fontSize = 10.sp, color = Color(0xFF7F0000))
                            override?.kind == DayKind.SWAP && override.swapToDayOfWeek !in 1..7 ->
                                Text("待安排", fontSize = 9.sp, color = Color(0xFF7A3E00))
                            override?.kind == DayKind.SWAP -> Text(
                                "上" + dayName(override.swapToDayOfWeek) + "课",
                                fontSize = 9.sp,
                                color = Color(0xFF7A3E00),
                            )
                            classes.isNotEmpty() -> Text(
                                "${classes.size} 节 · " + classes.first().start.toLocalTime()
                                    .let { "%02d:%02d".format(it.hour, it.minute) },
                                fontSize = 9.sp,
                                color = Color(0xFF0D47A1),
                            )
                            vacation -> Text(vacationLabel(date), fontSize = 9.sp, color = Color(0xFF37474F))
                        }
                    }
                }
            }
        }

        if (pendingFromPhone.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "手机日历里有 ${pendingFromPhone.size} 天节假日（${pendingFromPhone.first().calendarName}）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("放假的日子标红；标「班」的日子先留空，之后你自己安排上哪天的课。", fontSize = 11.sp)
                    Button(
                        onClick = { applyHolidays(pendingFromPhone) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text("应用这些节假日") }
                }
            }
        }

        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { ensurePermissionThenQuery() }) { Text("从手机日历导入") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val entries = BuiltInHolidays.entriesForTerm(termStartDate, settings.totalWeeks)
                if (entries.isEmpty()) {
                    importMessage = "这个学期范围内没有内置的法定节假日"
                } else {
                    holidayRows = entries.map { entry ->
                        HolidayRow(
                            event = DeviceCalendar.HolidayEvent(
                                date = LocalDate.parse(entry.date),
                                title = entry.note,
                                calendarName = "内置：2026 年国务院放假安排",
                                isWorkday = entry.kind == DayKind.SWAP,
                            ),
                            selected = true,
                            kind = entry.kind,
                            swapToDayOfWeek = entry.swapToDayOfWeek,
                            swapToWeek = WeekCalc.weekOf(termStartDate, LocalDate.parse(entry.date)),
                        )
                    }
                    importMessage = ""
                }
            }) { Text("内置 2026 安排") }
        }
        if (importMessage.isNotBlank()) {
            Text(importMessage, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Text(
            "颜色：蓝=有课，红=放假，橙=调休（上班日先「待安排」，点进去选上哪天的课），灰=学期外（寒暑假），深蓝框=今天。",
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
                                        DayKind.SWAP -> {
                                            if (o.swapToDayOfWeek !in 1..7) {
                                                append(" 调休（待安排上哪天的课）")
                                            } else {
                                                append(" 上")
                                                if (o.swapToWeek > 0) append("第").append(o.swapToWeek).append("周")
                                                append(dayName(o.swapToDayOfWeek)).append("的课")
                                            }
                                        }
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
            "寒暑假不用手标：第一周之前、最后一周之后的日期自动按学生假期显示（灰）。",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }

    editingDate?.let { date ->
        val existing = WeekCalc.overrideOf(settings, date)
        val week = WeekCalc.weekOf(termStartDate, date)
        DayEditDialog(
            date = date,
            currentWeek = week,
            existing = existing,
            onDismiss = { editingDate = null },
            onSave = { kind, swapTo, swapWeek, note ->
                putOverride(date, kind, swapTo, note, swapWeek)
                editingDate = null
            },
        )
    }

    holidayRows?.let { rows ->
        HolidayImportDialog(
            rows = rows,
            onChangeRows = { holidayRows = it },
            onDismiss = { holidayRows = null },
            onApply = { chosen ->
                val next = settings.overrides.filterNot { o -> chosen.any { it.event.date.toString() == o.date } }
                    .toMutableList()
                chosen.forEach { row ->
                    next += DayOverride(
                        date = row.event.date.toString(),
                        kind = row.kind,
                        swapToDayOfWeek = row.swapToDayOfWeek,
                        note = row.event.title,
                        swapToWeek = row.swapToWeek,
                    )
                }
                onChange(settings.copy(overrides = next.sortedBy { it.date }))
                importMessage = "已导入 ${chosen.size} 天"
                holidayRows = null
            },
        )
    }
}

/** 学期之外的日期是学生假期，按月份给个说法。 */
private fun vacationLabel(date: LocalDate): String = when (date.monthValue) {
    1, 2 -> "寒假"
    7, 8 -> "暑假"
    else -> "假期"
}

private data class HolidayRow(
    val event: DeviceCalendar.HolidayEvent,
    val selected: Boolean,
    val kind: DayKind,
    val swapToDayOfWeek: Int,
    val swapToWeek: Int,
)

@Composable
private fun HolidayImportDialog(
    rows: List<HolidayRow>,
    onChangeRows: (List<HolidayRow>) -> Unit,
    onDismiss: () -> Unit,
    onApply: (List<HolidayRow>) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节假日（${rows.size} 条）") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("勾选要应用的。标「班」的日子默认「待安排」，之后在日历页点那天再定上哪天的课。", fontSize = 11.sp)
                rows.forEachIndexed { index, row ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.selected,
                                onCheckedChange = { checked ->
                                    onChangeRows(rows.toMutableList().also { it[index] = row.copy(selected = checked) })
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${row.event.date} ${row.event.title}", fontSize = 13.sp)
                                Text(row.event.calendarName, fontSize = 10.sp)
                            }
                            FilterChip(
                                selected = row.kind == DayKind.HOLIDAY,
                                onClick = {
                                    onChangeRows(rows.toMutableList().also { it[index] = row.copy(kind = DayKind.HOLIDAY) })
                                },
                                label = { Text("放假") },
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = row.kind == DayKind.SWAP,
                                onClick = {
                                    onChangeRows(rows.toMutableList().also { it[index] = row.copy(kind = DayKind.SWAP) })
                                },
                                label = { Text("上课") },
                            )
                        }
                        if (row.kind == DayKind.SWAP) {
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                FilterChip(
                                    selected = row.swapToDayOfWeek !in 1..7,
                                    onClick = {
                                        onChangeRows(rows.toMutableList().also { it[index] = row.copy(swapToDayOfWeek = 0) })
                                    },
                                    label = { Text("待安排") },
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                (1..7).forEach { d ->
                                    FilterChip(
                                        selected = row.swapToDayOfWeek == d,
                                        onClick = {
                                            onChangeRows(rows.toMutableList().also { it[index] = row.copy(swapToDayOfWeek = d) })
                                        },
                                        label = { Text(dayName(d).removePrefix("周")) },
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                                OutlinedTextField(
                                    value = if (row.swapToWeek > 0) row.swapToWeek.toString() else "",
                                    onValueChange = { text ->
                                        val w = text.filter { it.isDigit() }.toIntOrNull() ?: -1
                                        onChangeRows(rows.toMutableList().also { it[index] = row.copy(swapToWeek = w) })
                                    },
                                    label = { Text("第几周") },
                                    singleLine = true,
                                    modifier = Modifier.width(110.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(rows.filter { it.selected }) }) { Text("应用选中的") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DayEditDialog(
    date: LocalDate,
    currentWeek: Int,
    existing: DayOverride?,
    onDismiss: () -> Unit,
    onSave: (DayKind, Int, Int, String) -> Unit,
) {
    var kind by remember(date) { mutableStateOf(existing?.kind ?: DayKind.NORMAL) }
    var swapTo by remember(date) { mutableStateOf(existing?.swapToDayOfWeek ?: 0) }
    var swapWeek by remember(date) { mutableStateOf(existing?.swapToWeek ?: currentWeek) }
    var note by remember(date) { mutableStateOf(existing?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$date（${dayName(date.dayOfWeek.value)}，第 $currentWeek 周）") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
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
                        label = { Text("调休（按别的星期上）") },
                    )
                }

                if (kind == DayKind.SWAP) {
                    Text(
                        "按星期几的课表上（不确定就先「待安排」，之后再来定）",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = swapTo !in 1..7,
                            onClick = { swapTo = 0 },
                            label = { Text("待安排") },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        (1..7).forEach { d ->
                            FilterChip(
                                selected = swapTo == d,
                                onClick = { swapTo = d },
                                label = { Text(dayName(d)) },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                    if (swapTo in 1..7) {
                        Text("按第几周的课表上", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChip(
                                selected = swapWeek <= 0,
                                onClick = { swapWeek = -1 },
                                label = { Text("这天所在的周（第 $currentWeek 周）") },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            (1..6).forEach { w ->
                                val value = currentWeek - 1 + w
                                if (value in 1..30) {
                                    FilterChip(
                                        selected = swapWeek == value,
                                        onClick = { swapWeek = value },
                                        label = { Text("第 $value 周") },
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            }
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
        confirmButton = {
            TextButton(onClick = { onSave(kind, swapTo, swapWeek, note) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
