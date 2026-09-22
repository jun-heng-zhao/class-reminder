package com.balance.classreminder.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.DAY_NAMES
import com.balance.classreminder.data.WeekParity

/** 手动添加/修改一门课。所有字段都可改，改完点保存。 */
@Composable
fun EditCourseDialog(
    course: Course,
    title: String,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: ((Course) -> Unit)?,
) {
    var name by remember(course.id) { mutableStateOf(course.name) }
    var location by remember(course.id) { mutableStateOf(course.location) }
    var teacher by remember(course.id) { mutableStateOf(course.teacher) }
    var day by remember(course.id) { mutableIntStateOf(course.dayOfWeek) }
    var startPeriod by remember(course.id) { mutableStateOf(course.startPeriod.toString()) }
    var endPeriod by remember(course.id) { mutableStateOf(course.endPeriod.toString()) }
    var startWeek by remember(course.id) { mutableStateOf(course.startWeek.toString()) }
    var endWeek by remember(course.id) { mutableStateOf(course.endWeek.toString()) }
    var parity by remember(course.id) { mutableStateOf(course.parity) }
    var remind by remember(course.id) { mutableStateOf(course.reminderMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("上课地点（会用在提醒里）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("星期", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = day == d,
                            onClick = { day = d },
                            label = { Text(DAY_NAMES[d - 1]) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }

                Row(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = startPeriod,
                        onValueChange = { startPeriod = it.filter { ch -> ch.isDigit() } },
                        label = { Text("起始节") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = endPeriod,
                        onValueChange = { endPeriod = it.filter { ch -> ch.isDigit() } },
                        label = { Text("结束节") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = startWeek,
                        onValueChange = { startWeek = it.filter { ch -> ch.isDigit() } },
                        label = { Text("起始周") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = endWeek,
                        onValueChange = { endWeek = it.filter { ch -> ch.isDigit() } },
                        label = { Text("结束周") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("单双周", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Row {
                    FilterChip(
                        selected = parity == WeekParity.ALL,
                        onClick = { parity = WeekParity.ALL },
                        label = { Text("每周") },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    FilterChip(
                        selected = parity == WeekParity.ODD,
                        onClick = { parity = WeekParity.ODD },
                        label = { Text("单周") },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    FilterChip(
                        selected = parity == WeekParity.EVEN,
                        onClick = { parity = WeekParity.EVEN },
                        label = { Text("双周") },
                    )
                }

                OutlinedTextField(
                    value = remind,
                    onValueChange = { remind = it },
                    label = { Text("提前提醒分钟（-1 = 跟随全局默认）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sp = (startPeriod.toIntOrNull() ?: 1).coerceAtLeast(1)
                val ep = (endPeriod.toIntOrNull() ?: sp).coerceAtLeast(sp)
                val sw = (startWeek.toIntOrNull() ?: 1).coerceAtLeast(1)
                val ew = (endWeek.toIntOrNull() ?: sw).coerceAtLeast(sw)
                onSave(
                    course.copy(
                        name = name.trim().ifBlank { "未命名课程" },
                        location = location.trim(),
                        teacher = teacher.trim(),
                        dayOfWeek = day,
                        startPeriod = sp,
                        endPeriod = ep,
                        startWeek = sw,
                        endWeek = ew,
                        parity = parity,
                        reminderMinutes = remind.trim().toIntOrNull() ?: -1,
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(course) }) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
