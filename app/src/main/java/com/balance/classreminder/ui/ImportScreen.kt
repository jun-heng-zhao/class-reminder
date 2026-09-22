package com.balance.classreminder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.PeriodTime
import com.balance.classreminder.ocr.CalendarParser
import com.balance.classreminder.ocr.GridParser
import com.balance.classreminder.ocr.ParseResult
import com.balance.classreminder.ocr.ParsedCourse
import com.balance.classreminder.ocr.PeriodParser
import com.balance.classreminder.ocr.TimetableOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** 三种图都能导入：课表、作息时间表、教学校历。 */
private enum class ImportKind(val label: String, val hint: String) {
    TIMETABLE("课表图片", "教务系统的课表截图，或者手写课表的照片"),
    PERIODS("作息时间图片", "学校作息时间表，用来填每节课几点上下课"),
    CALENDAR("校历图片", "教学校历，用来定「第一周」是哪天、一学期多少教学周"),
}

@Composable
fun ImportScreen(
    settings: AppSettings,
    onImportCourses: (List<Course>) -> Unit,
    onChangeSettings: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(ImportKind.TIMETABLE) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var rawDump by remember { mutableStateOf("") }

    var drafts by remember { mutableStateOf<List<Course>>(emptyList()) }
    var raws by remember { mutableStateOf<List<String>>(emptyList()) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var parsedPeriods by remember { mutableStateOf<List<PeriodTime?>>(emptyList()) }
    var calendarResult by remember { mutableStateOf<CalendarParser.Result?>(null) }

    fun resetResults() {
        drafts = emptyList()
        raws = emptyList()
        warnings = emptyList()
        parsedPeriods = emptyList()
        calendarResult = null
    }

    val recognize: (Uri) -> Unit = { uri ->
        busy = true
        status = "识别中，请稍等…"
        scope.launch {
            runCatching {
                val boxes = TimetableOcr.recognize(context, uri)
                rawDump = boxes.sortedWith(compareBy({ it.top }, { it.left }))
                    .joinToString("\n") { "x=${it.centerX} y=${it.centerY} | ${it.text}" }
                runCatching { File(context.cacheDir, "ocr_dump.txt").writeText(rawDump) }
                withContext(Dispatchers.Default) {
                    when (kind) {
                        ImportKind.TIMETABLE -> GridParser.parse(boxes, settings.totalWeeks)
                        ImportKind.PERIODS -> PeriodParser.parse(boxes)
                        ImportKind.CALENDAR -> CalendarParser.parse(boxes, LocalDate.now().year)
                    }
                }
            }.onSuccess { result ->
                resetResults()
                when (result) {
                    is ParseResult -> {
                        drafts = result.courses.map { it.toCourse() }
                        raws = result.courses.map { it.rawText }
                        warnings = result.warnings
                        status = if (drafts.isEmpty()) "没识别出课程，换张更清晰的图，或手动添加"
                        else "识别出 ${drafts.size} 门课，核对后导入"
                    }
                    is CalendarParser.Result -> {
                        calendarResult = result
                        status = if (result.firstWeekMonday != null) {
                            "读到第一周周一：${result.firstWeekMonday}"
                        } else {
                            "没读到开学日期，看下面的线索在设置页手动填"
                        }
                    }
                    is List<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        parsedPeriods = result as List<PeriodTime?>
                        val filled = parsedPeriods.count { it != null }
                        status = if (filled >= 4) "读到 $filled 节时间，核对后应用"
                        else "没读到完整的作息时间，换张更清晰的图，或在设置页手动填"
                    }
                    else -> status = "识别完成"
                }
                busy = false
            }.onFailure { e ->
                status = "识别失败：${e.message ?: e::class.simpleName}"
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) recognize(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) recognize(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("图片只在本机识别，不联网。识别完先核对，再决定要不要应用。", fontSize = 12.sp)

        Row(Modifier.padding(vertical = 6.dp)) {
            ImportKind.entries.forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = {
                        kind = k
                        resetResults()
                        status = ""
                    },
                    label = { Text(k.label) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        Text(kind.hint, fontSize = 11.sp)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !busy,
            ) { Text("相册选图") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("image/*")) },
                enabled = !busy,
            ) { Text("从文件选择") }
            if (busy) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.width(22.dp).height(22.dp))
            }
        }

        Text(status, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))

        if (warnings.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("识别提示", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    warnings.forEach { Text("· $it", fontSize = 12.sp) }
                }
            }
        }

        if (parsedPeriods.isNotEmpty()) {
            parsedPeriods.forEachIndexed { index, period ->
                Text(
                    "第 ${index + 1} 节  " + (period?.let { "${it.startLabel}-${it.endLabel}" } ?: "（没读到，保留原值）"),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
            Button(
                onClick = {
                    val merged = settings.periods.toMutableList()
                    parsedPeriods.forEachIndexed { index, period ->
                        if (period != null) {
                            if (index < merged.size) merged[index] = period else merged += period
                        }
                    }
                    onChangeSettings(settings.copy(periods = merged))
                    status = "作息时间已应用，去设置页可以再改"
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("应用这份作息时间") }
        }

        calendarResult?.let { result ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("从校历里读到的", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("第一周周一：${result.firstWeekMonday ?: "没读到"}", fontSize = 13.sp)
                    Text("教学周数：${result.totalWeeks?.toString() ?: "没读到"}", fontSize = 13.sp)
                    result.hints.forEach { Text("· $it", fontSize = 11.sp) }
                }
            }
            Button(
                onClick = {
                    onChangeSettings(
                        settings.copy(
                            termStartDate = result.firstWeekMonday ?: settings.termStartDate,
                            totalWeeks = result.totalWeeks ?: settings.totalWeeks,
                        )
                    )
                    status = "已应用到设置"
                },
                enabled = result.firstWeekMonday != null || result.totalWeeks != null,
                modifier = Modifier.padding(bottom = 8.dp),
            ) { Text("应用到设置") }
        }

        drafts.forEachIndexed { index, course ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(course.name, fontWeight = FontWeight.Bold)
                    Text(courseSubtitle(course), fontSize = 13.sp)
                    if (raws.getOrNull(index)?.isNotBlank() == true) {
                        Text("原图识别：" + raws[index].replace("\n", " / "), fontSize = 10.sp)
                    }
                    Row {
                        TextButton(onClick = { editingIndex = index }) { Text("修改") }
                        TextButton(onClick = {
                            drafts = drafts.filterIndexed { i, _ -> i != index }
                            raws = raws.filterIndexed { i, _ -> i != index }
                        }) { Text("不要这条") }
                    }
                }
            }
        }

        if (drafts.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = { onImportCourses(drafts) }) { Text("导入这 ${drafts.size} 门课") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    resetResults()
                    status = "已清空识别结果"
                }) { Text("清空") }
            }
        }

        if (rawDump.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("识别原文", rawDump))
                    Toast.makeText(context, "识别原文已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("复制识别原文（想让我帮你调识别时用）") }
        }

        Text(
            "课表识别不准很正常：表格越规整越准。识别完在这里改，导入后也能在课表页点格子改。",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        )
    }

    editingIndex?.let { index ->
        val course = drafts.getOrNull(index)
        if (course != null) {
            EditCourseDialog(
                course = course,
                title = "修改识别结果",
                onDismiss = { editingIndex = null },
                onSave = { updated ->
                    drafts = drafts.toMutableList().also { it[index] = updated }
                    editingIndex = null
                },
                onDelete = {
                    drafts = drafts.filterIndexed { i, _ -> i != index }
                    raws = raws.filterIndexed { i, _ -> i != index }
                    editingIndex = null
                },
            )
        }
    }
}

private fun ParsedCourse.toCourse(): Course = Course(
    id = UUID.randomUUID().toString(),
    name = name,
    location = location,
    teacher = teacher,
    dayOfWeek = dayOfWeek,
    startPeriod = period,
    endPeriod = endPeriod,
    startWeek = startWeek,
    endWeek = endWeek,
    parity = parity,
    reminderMinutes = -1,
    colorIndex = (dayOfWeek + period).mod(8),
    weekSpec = weekSpec,
)
