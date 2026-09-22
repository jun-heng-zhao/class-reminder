package com.balance.classreminder.ui

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
import com.balance.classreminder.data.dayName
import com.balance.classreminder.ocr.GridParser
import com.balance.classreminder.ocr.ParsedCourse
import com.balance.classreminder.ocr.TimetableOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** 选图 → 离线 OCR → 网格还原 → 人工核对 → 入库。识别结果永远不直接覆盖已有课表。 */
@Composable
fun ImportScreen(settings: AppSettings, onImport: (List<Course>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("选一张课表截图或照片，自动识别成课程") }
    var drafts by remember { mutableStateOf<List<Course>>(emptyList()) }
    var raws by remember { mutableStateOf<List<String>>(emptyList()) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val recognize: (android.net.Uri) -> Unit = { uri ->
        busy = true
        status = "识别中，请稍等…（首次识别需要几秒）"
        scope.launch {
            runCatching {
                val boxes = TimetableOcr.recognize(context, uri)
                withContext(Dispatchers.Default) { GridParser.parse(boxes, settings.totalWeeks) }
            }.onSuccess { result ->
                drafts = result.courses.map { it.toCourse() }
                raws = result.courses.map { it.rawText }
                warnings = result.warnings
                status = if (drafts.isEmpty()) {
                    "没识别出课程。换张更清晰、表格线明显的图，或者直接手动添加。"
                } else {
                    "识别出 ${drafts.size} 门课，核对下面的内容，改好再导入"
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
    // 相册里看不到的图（比如刚从电脑传进 Download 的截图）走文件选择器
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) recognize(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("导入课表图片", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "图片只在本机识别，不会联网上传。识别完先核对，再决定要不要导入。",
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
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

        drafts.forEachIndexed { index, course ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(course.name, fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            append(dayName(course.dayOfWeek))
                            append(" 第 ").append(course.startPeriod)
                            if (course.endPeriod != course.startPeriod) append("-").append(course.endPeriod)
                            append(" 节 · ").append(course.startWeek).append("-").append(course.endWeek).append(" 周")
                            when (course.parity.name) {
                                "ODD" -> append("（单周）")
                                "EVEN" -> append("（双周）")
                            }
                            if (course.location.isNotBlank()) append(" · ").append(course.location)
                            if (course.teacher.isNotBlank()) append(" · ").append(course.teacher)
                        },
                        fontSize = 13.sp,
                    )
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
                Button(onClick = { onImport(drafts) }) { Text("导入这 ${drafts.size} 门课") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    drafts = emptyList()
                    raws = emptyList()
                    warnings = emptyList()
                    status = "已清空识别结果"
                }) { Text("清空") }
            }
        }

        Text(
            "识别不准很正常：表格越规整、字越清晰越准。识别完在这里改，或者导入后在课表页点格子改。",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp),
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
)
