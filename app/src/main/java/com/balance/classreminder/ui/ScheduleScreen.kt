package com.balance.classreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.app.AlarmManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.DayKind
import com.balance.classreminder.data.dayName
import com.balance.classreminder.domain.WeekCalc
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    today: LocalDate = LocalDate.now(),
) {
    val periods = settings.periods
    val context = LocalContext.current
    var showToday by rememberSaveable { mutableStateOf(true) } // 默认打开今日，周课表单独浏览
    val todayScroll = rememberScrollState() // 两种课表分别保留滚动位置
    val weekScroll = rememberScrollState()
    val notificationsOff = !NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAlarmOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !(context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false)
    val todayCourses = remember(settings, courses, today) { WeekCalc.coursesOn(settings, courses, today) } // 不随浏览周切换
    val configured = WeekCalc.termMonday(settings) != null // 有效校准无需先填开学日
    val todayOverride = remember(settings, today) { WeekCalc.overrideOf(settings, today) } // 放假与待安排分别提示
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") } // 时间固定两位，便于扫读
    val weekCourses = remember(courses, week) { courses.filter { WeekCalc.weekMatches(it, week) } } // 仅相关输入变化时过滤

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showToday, onClick = { showToday = true }, label = { Text("今日课表") }, modifier = Modifier.weight(1f))
            FilterChip(selected = !showToday, onClick = { showToday = false }, label = { Text("周课表") }, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(if (showToday) todayScroll else weekScroll)) {
        if (showToday) {
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${today.monthValue}月${today.dayOfMonth}日 · ${dayName(today.dayOfWeek.value)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (configured) "第 $currentWeek 周 · 今日 ${todayCourses.size} 门课，出发吧～" else "今日任务板，等待课表就位～",
                    style = MaterialTheme.typography.bodySmall,
                )
                when {
                    !configured -> Text("尚未配置教学周，请到「设置」填写开学日期或校准当前周。")
                    todayOverride?.kind == DayKind.HOLIDAY -> Text("今天放假，安心补充能量～" + todayOverride.note.takeIf { it.isNotBlank() }?.let { "\n备注：$it" }.orEmpty())
                    WeekCalc.needsArrangement(settings, today) -> Text("今天是调休日，课程待安排。请到「日历」指定上哪天的课；安排前不会提醒。")
                    todayCourses.isEmpty() -> Text(
                        if (courses.isEmpty()) "还没有课程，去「导入」添加课表，或切换「周课表」点击「加课」。"
                        else "今天没有课程，自由时间解锁～"
                    )
                    else -> Text("按时间排好啦，点课程即可查看或修改～", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        todayCourses.forEach { occurrence ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onEdit(occurrence.course) }) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(62.dp)) {
                        Text(occurrence.start.format(timeFormat), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(occurrence.end.format(timeFormat), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(occurrence.course.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(occurrence.course.location.ifBlank { "地点未填写，点此补充" }, style = MaterialTheme.typography.bodyMedium)
                        Text("第 ${occurrence.course.startPeriod}–${occurrence.course.endPeriod} 节 · 点此编辑", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        }
        if (notificationsOff || exactAlarmOff) {
            Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("提醒权限待开启", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (notificationsOff) Text("· 通知权限没开，去「设置」页打开", fontSize = 12.sp)
                    if (exactAlarmOff) Text("· 精确闹钟没允许，去「设置」页打开", fontSize = 12.sp)
                }
            }
        }
        if (!showToday) {
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

        if (!configured) {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("还没配置教学周", fontWeight = FontWeight.Bold)
                    Text("去「设置」填写开学日期或校准当前周，课表与提醒会使用同一基准。", fontSize = 13.sp)
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
            Text("本周课程", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onAddAt(1, 1) }) { Text("＋ 加课") }
        }

            // 列表模式：按星期分组，一眼看清"几点、去哪、上什么"
            var anyThisWeek = false
            (1..7).forEach { day ->
                val dayCourses = weekCourses
                    .filter { it.dayOfWeek == day }
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
                    // 每一节自己的上下课时间分别列出来，中间的课间不算进课程时间
                    val slots = (course.startPeriod..course.endPeriod).mapNotNull { p ->
                        periods.getOrNull(p - 1)?.let { "${it.startLabel}-${it.endLabel}" }
                    }
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
                                    slots.joinToString("  ") + " · 第 ${course.startPeriod}" +
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
        val countThisWeek = weekCourses.size
        Text(
            text = "第 $week 周共 $countThisWeek 门课 · 点课程可查看或修改",
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = { onAddAt(1, 1) }) { Text("＋ 新增一节其他时间的课") }
        }
        }
    }
}
