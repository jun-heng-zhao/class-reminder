package com.balance.classreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.app.AlarmManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.DAY_NAMES
import com.balance.classreminder.data.dayName
import com.balance.classreminder.domain.WeekCalc

private val PALETTE = listOf(
    Color(0xFFBBDEFB), Color(0xFFC8E6C9), Color(0xFFFFE0B2), Color(0xFFF8BBD0),
    Color(0xFFD1C4E9), Color(0xFFB2EBF2), Color(0xFFFFF9C4), Color(0xFFD7CCC8),
)

@Composable
fun ScheduleScreen(
    courses: List<Course>,
    settings: AppSettings,
    week: Int,
    currentWeek: Int,
    onWeekOffset: (Int) -> Unit,
    onBackToNow: () -> Unit,
    onAddAt: (Int, Int) -> Unit,
    onEdit: (Course) -> Unit,
) {
    val periods = settings.periods
    val context = LocalContext.current
    var listMode by remember { mutableStateOf(false) }
    val notificationsOff = !NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAlarmOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !(context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        if (notificationsOff || exactAlarmOff) {
            Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("提醒现在收不到", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (notificationsOff) Text("· 通知权限没开，去「设置」页打开", fontSize = 12.sp)
                    if (exactAlarmOff) Text("· 精确闹钟没允许，去「设置」页打开", fontSize = 12.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onWeekOffset(-1) }) { Text("◀ 上一周") }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (week >= 1) "第 $week 周" else "第 ? 周",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onWeekOffset(1) }) { Text("下一周 ▶") }
        }

        if (settings.termStartDate.isBlank()) {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("还没设置第一周的周一", fontWeight = FontWeight.Bold)
                    Text("去「设置」里填上开学第一周的日期，才能算出今天是第几周。", fontSize = 13.sp)
                }
            }
        } else if (week != currentWeek) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("正在看第 $week 周（本周是第 $currentWeek 周）", fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBackToNow) { Text("回到本周") }
            }
        }

        Row(Modifier.padding(top = 4.dp)) {
            FilterChip(
                selected = !listMode,
                onClick = { listMode = false },
                label = { Text("表格") },
                modifier = Modifier.padding(end = 6.dp),
            )
            FilterChip(
                selected = listMode,
                onClick = { listMode = true },
                label = { Text("列表") },
            )
        }

        if (!listMode) {
        // 星期表头
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Spacer(Modifier.width(26.dp))
            DAY_NAMES.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 课表网格
        for (period in periods.indices) {
            val periodNo = period + 1
            Row(Modifier.fillMaxWidth().height(58.dp)) {
                Column(Modifier.width(26.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Text("$periodNo", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(periods[period].startLabel, fontSize = 8.sp, color = Color.Gray)
                }
                for (day in 1..7) {
                    val course = courses.firstOrNull {
                        it.dayOfWeek == day &&
                            periodNo >= it.startPeriod && periodNo <= it.endPeriod &&
                            WeekCalc.weekMatches(it, week)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.5.dp, Color(0xFFBDBDBD))
                            .background(
                                if (course != null) PALETTE[course.colorIndex.mod(PALETTE.size)]
                                else Color.Transparent
                            )
                            .clickable {
                                if (course != null) onEdit(course) else onAddAt(day, periodNo)
                            }
                    ) {
                        if (course != null && periodNo == course.startPeriod) {
                            Column(Modifier.padding(1.dp)) {
                                Text(
                                    text = course.name,
                                    fontSize = 10.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (course.location.isNotBlank()) {
                                    Text(
                                        text = "@" + course.location,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color(0xFF37474F),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        } else {
            // 列表模式：按星期分组，一眼看清"几点、去哪、上什么"
            var anyThisWeek = false
            (1..7).forEach { day ->
                val dayCourses = courses
                    .filter { it.dayOfWeek == day && WeekCalc.weekMatches(it, week) }
                    .sortedBy { it.startPeriod }
                if (dayCourses.isEmpty()) return@forEach
                anyThisWeek = true
                Text(
                    dayName(day) + "（${dayCourses.size} 门）",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                dayCourses.forEach { course ->
                    val start = periods.getOrNull(course.startPeriod - 1)?.startLabel.orEmpty()
                    val end = periods.getOrNull(course.endPeriod - 1)?.endLabel.orEmpty()
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onEdit(course) }
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .width(6.dp)
                                    .height(38.dp)
                                    .background(PALETTE[course.colorIndex.mod(PALETTE.size)])
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(course.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(
                                    "$start-$end · 第 ${course.startPeriod}" +
                                        (if (course.endPeriod != course.startPeriod) "-${course.endPeriod}" else "") +
                                        " 节",
                                    fontSize = 12.sp,
                                )
                                Text(courseSubtitle(course), fontSize = 11.sp)
                            }
                            Text("修改", fontSize = 12.sp)
                        }
                    }
                }
            }
            if (!anyThisWeek) {
                Text("第 $week 周没有课", fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }

        val countThisWeek = courses.count { WeekCalc.weekMatches(it, week) }
        Text(
            text = "第 $week 周共 $countThisWeek 门课 · 点格子可加课，点课可改",
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = { onAddAt(1, 1) }) { Text("＋ 新增一节其他时间的课") }

    }
}
