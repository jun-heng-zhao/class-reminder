// 设置草稿、教学周校准与本地备份管理。
package com.balance.classreminder.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.PeriodTime
import com.balance.classreminder.data.Store
import com.balance.classreminder.domain.WeekCalc
import java.time.LocalDate

/**
 * 设置：一级是分组菜单，点进去才是具体设置项。
 * 分类内容共享外层草稿，返回键或「返回设置」回到菜单。
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    courses: List<Course>,
    onChange: (AppSettings) -> Unit,
    onTestNotification: () -> Unit,
    onRestore: (() -> Unit)? = null,
    active: Boolean = true,
    onImportCourses: (List<Course>) -> Unit = {},
) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf("首页") } // 分类切换不重建外层草稿
    val initialCalibrationText = remember(settings.manualWeek, settings.manualWeekMonday, settings.termStartDate) {
        if (settings.manualWeek > 0) WeekCalc.teachingWeek(settings, LocalDate.now()).toString() else ""
    } // 显示当前周，不能把历史锚点周当作今天的周数
    var termStartText by remember(settings.termStartDate) { mutableStateOf(settings.termStartDate) } // 日期原文
    var totalWeeksText by remember(settings.totalWeeks) { mutableStateOf(settings.totalWeeks.toString()) } // 总周数原文
    var reminderText by remember(settings.defaultReminderMinutes) { mutableStateOf(settings.defaultReminderMinutes.toString()) } // 提前量原文
    var calibrationText by remember(initialCalibrationText, settings.manualWeek, settings.manualWeekMonday) { mutableStateOf(initialCalibrationText) } // 当前周原文
    var calibrationEnabled by remember(settings.manualWeek, settings.manualWeekMonday) { mutableStateOf(settings.manualWeek > 0) } // 显式恢复才能停用校准
    var strongReminderDraft by remember(settings.strongReminder) { mutableStateOf(settings.strongReminder) } // 开关也只修改草稿
    var periodsDraft by remember(settings.periods) {
        mutableStateOf(settings.periods.map { it.startLabel to it.endLabel })
    }
    var dataMessage by remember { mutableStateOf("") }
    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAlarmOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    } else true

    BackHandler(enabled = active && section != "首页") { section = "首页" }
    // 只重建分类内容和滚动状态，所有表单草稿始终由外层持有。
    key(section) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (section != "首页") {
                TextButton(onClick = { section = "首页" }) { Text("返回设置") }
            }
            if (section == "图片导入") {
                ImportScreen(
                    settings = settings,
                    onImportCourses = { imported ->
                        section = "首页"
                        onImportCourses(imported)
                    },
                    onChangeSettings = { updated ->
                        section = "首页"
                        onChange(updated)
                    },
                )
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(if (section == "首页") "设置" else section, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (section == "首页") {
                        Text(
                            "已有 ${courses.size} 门课 · 当前第 ${WeekCalc.teachingWeek(settings, LocalDate.now())} 周 / 共 ${settings.totalWeeks} 周",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        listOf(
                            "学期与作息" to "开学日期、教学周校准、总周数与上课节次",
                            "提醒与权限" to "提前提醒、响铃、通知与精确闹钟权限",
                            "图片导入" to "识别课表、作息时间表与教学校历",
                            "备份与恢复" to "导出课表或从本地备份恢复",
                        ).forEach { (title, subtitle) ->
                            Card(
                                onClick = { section = title; dataMessage = "" },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (section == "学期与作息" || section == "提醒与权限") {
                        Text(
                            "本页设置先保存在草稿中，点击「保存设置」后生效；点击「取消」仅撤销本页修改。切换分类不会丢失草稿。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        )
                    }
                    if (section == "学期与作息") {
                        Text("学期设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = termStartText,
                            onValueChange = { termStartText = it },
                            label = { Text("第一周的周一（yyyy-MM-dd）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row {
                            TextButton(onClick = {
                                termStartText = WeekCalc.mondayOf(LocalDate.now()).toString()
                            }) { Text("设为本周一") }
                            TextButton(onClick = {
                                termStartText = WeekCalc.mondayOf(LocalDate.now()).plusWeeks(1).toString()
                            }) { Text("设为下周一") }
                        }
                        val nowWeek = WeekCalc.weekOf(WeekCalc.parseDate(termStartText), LocalDate.now()) // 开学日推算预览
                        Text(
                            if (termStartText.isBlank()) "开学日期未知？可以只校准当前教学周喵"
                            else "按这个日期算，今天是第 $nowWeek 周",
                            fontSize = 12.sp,
                        )
                        OutlinedTextField(
                            value = calibrationText,
                            onValueChange = { calibrationText = it; calibrationEnabled = true },
                            label = { Text("当前教学周校准（1～总周数）") },
                            supportingText = { Text(if (calibrationEnabled) "校准后每周一递增；恢复按开学日请点下方按钮" else "当前按开学日计算；输入周数即可校准") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row {
                            TextButton(onClick = {
                                calibrationText = WeekCalc.teachingWeek(settings, LocalDate.now()).coerceAtLeast(1).toString()
                                calibrationEnabled = true
                            }) { Text("校准为本周") }
                            TextButton(onClick = {
                                calibrationText = ""
                                calibrationEnabled = false
                            }) { Text("恢复按开学日") }
                        }
                        Text("以上修改和恢复操作均需点击「保存设置」才生效。", fontSize = 12.sp)
                        OutlinedTextField(
                            value = totalWeeksText,
                            onValueChange = { totalWeeksText = it },
                            label = { Text("总周数") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("上课节次时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("共 ${settings.periods.size} 节。时间填成 8:00 这种格式。", fontSize = 12.sp)
                        periodsDraft.forEachIndexed { index, period ->
                            PeriodRow(
                                index = index,
                                start = period.first,
                                end = period.second,
                                onChange = { start, end ->
                                    periodsDraft = periodsDraft.toMutableList().also { it[index] = start to end }
                                },
                            )
                        }
                    }
                    if (section == "提醒与权限") {
                        OutlinedTextField(
                            value = reminderText,
                            onValueChange = { reminderText = it },
                            label = { Text("默认提前提醒(分钟)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("响铃提醒（默认关，只需要通知就别开）", fontSize = 14.sp)
                                Text("打开后会额外放系统闹钟铃声；只想要通知就不用打开", fontSize = 11.sp)
                            }
                            Switch(checked = strongReminderDraft, onCheckedChange = { strongReminderDraft = it })
                        }
                    }

                    if (section == "学期与作息" || section == "提醒与权限") {
                        Row(Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = {
                                val termSection = section == "学期与作息" // 仅校验和保存当前页字段
                                val calibrationChanged = calibrationEnabled &&
                                    (settings.manualWeek <= 0 || calibrationText.trim() != initialCalibrationText)
                                val error = if (termSection) validateSettingsDraft(
                                    termStartText, totalWeeksText, "0", calibrationText, calibrationEnabled, periodsDraft,
                                    preservedCalibrationWeek = initialCalibrationText.toIntOrNull().takeIf { !calibrationChanged },
                                ) else validateReminderDraft(reminderText)
                                if (error != null) {
                                    dataMessage = error
                                    return@OutlinedButton
                                }
                                val manualWeek = when {
                                    !termSection -> settings.manualWeek
                                    !calibrationEnabled -> 0
                                    calibrationChanged -> calibrationText.trim().toInt()
                                    else -> settings.manualWeek
                                } // 未修改校准时连同历史锚点一并保留，避免跨周倒退
                                val next = if (termSection) settings.copy(
                                    termStartDate = termStartText.trim(),
                                    totalWeeks = totalWeeksText.trim().toInt(),
                                    periods = periodsDraft.map { PeriodTime(requireNotNull(parseStrictMinute(it.first)), requireNotNull(parseStrictMinute(it.second))) },
                                    manualWeek = manualWeek,
                                    manualWeekMonday = when {
                                        manualWeek == 0 -> ""
                                        calibrationChanged -> WeekCalc.mondayOf(LocalDate.now()).toString()
                                        else -> settings.manualWeekMonday
                                    },
                                ) else settings.copy(
                                    defaultReminderMinutes = reminderText.trim().toInt(),
                                    strongReminder = strongReminderDraft,
                                )
                                onChange(next)
                                dataMessage = "设置保存好了喵"
                                Toast.makeText(context, "设置保存好了，周次继续往前走喵", Toast.LENGTH_SHORT).show()
                            }) { Text("保存设置") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                if (section == "学期与作息") {
                                    termStartText = settings.termStartDate
                                    totalWeeksText = settings.totalWeeks.toString()
                                    calibrationText = initialCalibrationText
                                    calibrationEnabled = settings.manualWeek > 0
                                    periodsDraft = settings.periods.map { it.startLabel to it.endLabel }
                                } else {
                                    reminderText = settings.defaultReminderMinutes.toString()
                                    strongReminderDraft = settings.strongReminder
                                }
                                dataMessage = "已取消本页修改"
                            }) { Text("取消") }
                        }
                    }

                    if (section == "备份与恢复") {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("课表数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("换手机、重装应用前先导出；内部数据空了会从这份备份自动恢复。", fontSize = 12.sp)
                        Row(Modifier.padding(top = 4.dp)) {
                            OutlinedButton(onClick = {
                                val path = Store.get(context).exportToExternal()
                                dataMessage = path?.let { "已导出到：$it" } ?: "导出失败（外部存储不可用）"
                            }) { Text("导出课表") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                if (Store.get(context).importFromExternal()) {
                                    onRestore?.invoke()
                                    dataMessage = if (onRestore != null) "已从备份恢复，课程和设置已同步"
                                    else "备份已恢复，请重新打开应用刷新课程和设置"
                                    Toast.makeText(context, "备份回来啦喵", Toast.LENGTH_SHORT).show()
                                } else dataMessage = "外部目录里没有备份文件"
                            }) { Text("从备份恢复") }
                        }
                        Text("备份路径：Android/data/com.balance.classreminder/files/schedule.txt", fontSize = 10.sp)
                    }
                    if (section == "提醒与权限") {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("提醒权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "通知权限：" + if (notificationsOn) "已开启" else "未开启（收不到提醒）",
                            fontSize = 13.sp,
                        )
                        Text(
                            "精确闹钟：" + if (exactAlarmOn) "已允许" else "未允许（提醒可能晚几分钟）",
                            fontSize = 13.sp,
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
                    if (dataMessage.isNotBlank()) {
                        Text(dataMessage, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text(
                        "已有 ${courses.size} 门课。数据只存在手机本地，不上传。",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodRow(index: Int, start: String, end: String, onChange: (String, String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("第 ${index + 1} 节", Modifier.width(58.dp), fontSize = 12.sp)
        OutlinedTextField(
            value = start,
            onValueChange = { onChange(it, end) },
            label = { Text("开始") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = end,
            onValueChange = { onChange(start, it) },
            label = { Text("结束") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun validateSettingsDraft(
    termStart: String,
    totalWeeks: String,
    reminder: String,
    calibration: String,
    calibrationEnabled: Boolean,
    periods: List<Pair<String, String>>,
    preservedCalibrationWeek: Int? = null,
): String? {
    val total = parseStrictInteger(totalWeeks) // 保留原文，拒绝空白、符号和混入的字符
    val calibrationWeek = parseStrictInteger(calibration)
    if (!(calibrationEnabled && termStart.isBlank()) &&
        (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(termStart.trim()) || WeekCalc.parseDate(termStart.trim()) == null)) {
        return "第一周日期必须是有效的 yyyy-MM-dd"
    }
    if (total == null) return "总周数必须填写数字"
    if (total !in 1..60) return "总周数必须在 1～60 之间"
    validateReminderDraft(reminder)?.let { return it }
    if (calibrationEnabled) {
        if (calibrationWeek == null) return "校准周不能为空且必须填写数字；取消校准请点恢复按开学日"
        if (calibrationWeek !in 1..total && calibrationWeek != preservedCalibrationWeek) {
            return "校准周必须在 1～总周数之间"
        }
    }
    periods.forEachIndexed { index, (start, end) ->
        val startMinute = parseStrictMinute(start) ?: return "第 ${index + 1} 节开始时间无效"
        val endMinute = parseStrictMinute(end) ?: return "第 ${index + 1} 节结束时间无效"
        if (endMinute <= startMinute) return "第 ${index + 1} 节必须满足结束时间晚于开始时间"
    }
    return null
}

private fun validateReminderDraft(reminder: String): String? {
    val reminderMinutes = parseStrictInteger(reminder) // 提醒页不依赖学期草稿或开学日期
    if (reminderMinutes == null) return "默认提前提醒必须填写数字"
    if (reminderMinutes !in 0..1440) return "默认提前提醒必须在 0～1440 分钟之间"
    return null
}

private fun parseStrictInteger(text: String): Int? =
    text.trim().takeIf { Regex("[0-9]+").matches(it) }?.toIntOrNull()

private fun parseStrictMinute(text: String): Int? {
    val match = Regex("^(\\d{1,2})[:：](\\d{2})$").matchEntire(text.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
