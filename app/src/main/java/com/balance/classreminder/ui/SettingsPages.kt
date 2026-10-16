package com.balance.classreminder.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.PeriodTime
import com.balance.classreminder.data.Store
import com.balance.classreminder.data.parseMinute
import com.balance.classreminder.domain.WeekCalc
import java.time.LocalDate

/** 学期与提醒：第一周、总周数、默认提前量、响铃开关。 */
@Composable
fun TermSettingsPage(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    // 日期改成「先输完再点确定」：以前边输边存，输到一半的日期也算数
    var dateText by remember(settings.termStartDate) { mutableStateOf(settings.termStartDate) }
    var dateError by remember { mutableStateOf("") }

    fun commitTermStart(text: String) {
        val raw = text.trim()
        if (raw.isEmpty()) {
            dateError = ""
            onChange(settings.copy(termStartDate = ""))
            return
        }
        val parsed = WeekCalc.parseDate(raw)
        if (parsed == null) {
            dateError = "日期格式不对，要写成 2026-09-07 这样"
            return
        }
        // 填哪天都行，一律对齐到那周的周一 —— 周次计算只认周一
        val monday = WeekCalc.mondayOf(parsed).toString()
        dateError = ""
        dateText = monday
        onChange(settings.copy(termStartDate = monday))
    }

    Text("第一周的周一", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    OutlinedTextField(
        value = dateText,
        onValueChange = {
            dateText = it
            if (dateError.isNotBlank()) dateError = ""
        },
        label = { Text("yyyy-MM-dd") },
        singleLine = true,
        isError = dateError.isNotBlank(),
        supportingText = { if (dateError.isNotBlank()) Text(dateError, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commitTermStart(dateText) }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { commitTermStart(dateText) }) { Text("确定") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = {
            val monday = WeekCalc.mondayOf(LocalDate.now()).toString()
            dateError = ""
            dateText = monday
            onChange(settings.copy(termStartDate = monday))
        }) { Text("设为本周一") }
        TextButton(onClick = {
            val monday = WeekCalc.mondayOf(LocalDate.now()).plusWeeks(1).toString()
            dateError = ""
            dateText = monday
            onChange(settings.copy(termStartDate = monday))
        }) { Text("设为下周一") }
    }

    val nowWeek = WeekCalc.weekOf(WeekCalc.parseDate(settings.termStartDate), LocalDate.now())
    Text(
        if (settings.termStartDate.isBlank()) "还没设置第一周，课表页算不出周次"
        else "按这个日期算，今天是第 $nowWeek 周",
        fontSize = 12.sp,
    )

    Row(Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = settings.totalWeeks.toString(),
            onValueChange = { onChange(settings.copy(totalWeeks = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 1)) },
            label = { Text("总周数") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = settings.defaultReminderMinutes.toString(),
            onValueChange = {
                onChange(settings.copy(defaultReminderMinutes = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0))
            },
            label = { Text("默认提前提醒(分钟)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("响铃提醒（默认关，只需要通知就别开）", fontSize = 14.sp)
            Text("打开后会额外放系统闹钟铃声；只想要通知就不用打开", fontSize = 11.sp)
        }
        Switch(
            checked = settings.strongReminder,
            onCheckedChange = { onChange(settings.copy(strongReminder = it)) },
        )
    }
}

/** 上课节次时间：逐节改上下课时刻。 */
@Composable
fun PeriodsPage(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    Text("共 ${settings.periods.size} 节。时间填成 8:00 这种格式。", fontSize = 12.sp)
    settings.periods.forEachIndexed { index, period ->
        PeriodRow(
            index = index,
            start = period.startLabel,
            end = period.endLabel,
            onCommit = { s, e ->
                val next = settings.periods.toMutableList()
                if (index < next.size) {
                    next[index] = PeriodTime(parseMinute(s), parseMinute(e))
                    onChange(settings.copy(periods = next))
                }
            },
        )
    }
}

/** 课表数据：导出 / 恢复。 */
@Composable
fun DataPage(courses: List<Course>) {
    val context = LocalContext.current
    var dataMessage by remember { mutableStateOf("") }

    Text("换手机、重装应用前先导出；内部数据空了会从这份备份自动恢复。", fontSize = 12.sp)
    Row(Modifier.padding(top = 4.dp)) {
        OutlinedButton(onClick = {
            val path = Store.get(context).exportToExternal()
            dataMessage = path?.let { "已导出到：$it" } ?: "导出失败（外部存储不可用）"
        }) { Text("导出课表") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = {
            dataMessage = if (Store.get(context).importFromExternal()) "已从备份恢复"
            else "外部目录里没有备份文件"
        }) { Text("从备份恢复") }
    }
    if (dataMessage.isNotBlank()) Text(dataMessage, fontSize = 11.sp)
    Text("备份路径：Android/data/com.balance.classreminder/files/schedule.txt", fontSize = 10.sp)
    Text("已有 ${courses.size} 门课。数据只存在手机本地，不上传。", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
}

/** 提醒权限：通知 / 精确闹钟状态、跳转设置、测试通知、HyperOS 提示。 */
@Composable
fun PermissionsPage(settings: AppSettings, onTestNotification: () -> Unit) {
    val context = LocalContext.current
    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAlarmOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    } else true

    Text(
        "通知权限：" + if (notificationsOn) "已开启" else "未开启（收不到提醒）",
        fontSize = 13.sp,
    )
    Text(
        "精确闹钟：" + if (exactAlarmOn) "已允许" else "未允许（提醒可能晚几分钟）",
        fontSize = 13.sp,
    )
    Text(
        "当前默认提前 " + settings.defaultReminderMinutes + " 分钟；" +
            if (settings.strongReminder) "响铃提醒已开" else "响铃提醒已关",
        fontSize = 12.sp,
    )
    Row(Modifier.padding(top = 4.dp)) {
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }) { Text("通知设置") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                }
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                )
            }
        }) { Text("精确闹钟设置") }
    }
    TextButton(onClick = onTestNotification) { Text("发一条测试通知") }

    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text("小米 / HyperOS 额外要做的事", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "1. 设置 → 应用设置 → 课表提醒 → 允许「自启动」，省电策略改成「无限制」。\n" +
                    "2. 设置 → 应用设置 → 课表提醒 → 通知管理 → 允许通知、允许横幅。\n" +
                    "3. 如果长时间没收到提醒，进最近任务把本应用加锁。",
                fontSize = 12.sp,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                },
                modifier = Modifier.padding(top = 6.dp),
            ) { Text("打开本应用系统设置") }
        }
    }
}

@Composable
private fun PeriodRow(index: Int, start: String, end: String, onCommit: (String, String) -> Unit) {
    var startText by remember(index) { mutableStateOf(start) }
    var endText by remember(index) { mutableStateOf(end) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("第 ${index + 1} 节", Modifier.width(58.dp), fontSize = 12.sp)
        OutlinedTextField(
            value = startText,
            onValueChange = { startText = it; onCommit(startText, endText) },
            label = { Text("开始") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = endText,
            onValueChange = { endText = it; onCommit(startText, endText) },
            label = { Text("结束") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}
