package com.balance.classreminder.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.Store
import com.balance.classreminder.domain.WeekCalc
import com.balance.classreminder.remind.Notifier
import com.balance.classreminder.remind.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val lifecycleOwner = LocalLifecycleOwner.current // 仅在前台恢复与检查日期

    var courses by remember { mutableStateOf(store.courses) }
    var settings by remember { mutableStateOf(store.settings) }
    val pager = rememberPagerState(pageCount = { 3 }) // 底栏与手势共用唯一页码
    val scope = rememberCoroutineScope()
    val tab = pager.currentPage
    fun openPage(page: Int) { scope.launch { pager.animateScrollToPage(page) } }
    var weekOffset by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Course?>(null) }
    var newSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) } // 跨日后触发今日课程与周数重算

    fun persist(newCourses: List<Course> = courses, newSettings: AppSettings = settings) {
        if (newCourses == courses && newSettings == settings) return
        courses = newCourses
        settings = newSettings
        store.update(newCourses, newSettings)
        ReminderScheduler.reschedule(context)
    }

    LaunchedEffect(Unit) {
        Notifier.ensureChannels(context)
        ReminderScheduler.reschedule(context)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 后台取消循环，恢复立即刷新；不注册解锁广播、不写课表文件。
            while (true) {
                val date = LocalDate.now() // 使用当前时区，避免跨日后仍显示昨天
                if (date != today) {
                    today = date
                    ReminderScheduler.reschedule(context)
                }
                delay(30_000)
            }
        }
    }

    val currentWeek = WeekCalc.teachingWeek(settings, today)
    val shownWeek = (currentWeek + weekOffset).coerceAtLeast(1)

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { openPage(0) },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("课表") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { openPage(1) },
                    icon = { Icon(Icons.Filled.EditCalendar, contentDescription = null) },
                    label = { Text("日历") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { openPage(2) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        }
    ) { padding ->
        // 三个主页面保留组合，低频导入从设置进入，不占底栏位置。
        HorizontalPager(state = pager, beyondViewportPageCount = 2, modifier = Modifier.padding(padding)) { page ->
            when (page) {
                0 -> ScheduleScreen(
                    courses = courses,
                    settings = settings,
                    week = shownWeek,
                    currentWeek = currentWeek,
                    onWeekOffset = { weekOffset += it },
                    onBackToNow = { weekOffset = 0 },
                    onAddAt = { day, period -> newSlot = day to period },
                    onEdit = { editing = it },
                    today = today,
                )

                1 -> CalendarScreen(
                    settings = settings,
                    courses = courses,
                    active = tab == 1,
                    onChange = { persist(newSettings = it) },
                )

                else -> SettingsScreen(
                    settings = settings,
                    courses = courses,
                    active = tab == 2,
                    onImportCourses = { parsed ->
                        val added = parsed.map { it.copy(id = UUID.randomUUID().toString()) }
                        persist(courses + added)
                        openPage(0)
                    },
                    onChange = { persist(newSettings = it) },
                    onRestore = {
                        courses = store.courses
                        settings = store.settings
                        weekOffset = 0
                        ReminderScheduler.reschedule(context)
                    },
                    onTestNotification = {
                        Notifier.show(
                            context = context,
                            id = 1001,
                            courseName = "测试：高等数学",
                            location = "教三 201",
                            startLabel = "10:00",
                            minutes = settings.defaultReminderMinutes,
                            strong = settings.strongReminder,
                        )
                    },
                )
            }
        }
    }

    newSlot?.let { (day, period) ->
        EditCourseDialog(
            course = Course(
                id = UUID.randomUUID().toString(),
                name = "",
                dayOfWeek = day,
                startPeriod = period,
                endPeriod = period,
                startWeek = shownWeek.coerceAtLeast(1),
                endWeek = settings.totalWeeks,
                reminderMinutes = -1,
            ),
            title = "添加课程",
            onDismiss = { newSlot = null },
            onSave = { saved ->
                persist(newCourses = courses.filterNot { it.id == saved.id } + saved)
                newSlot = null
            },
            onDelete = null,
        )
    }

    editing?.let { course ->
        EditCourseDialog(
            course = course,
            title = "编辑课程",
            onDismiss = { editing = null },
            onSave = { saved ->
                val next = courses.map { if (it.id == saved.id) saved else it } // 保留顺序，无改动时不写盘
                persist(newCourses = next)
                editing = null
            },
            onDelete = { deleted ->
                persist(newCourses = courses.filterNot { it.id == deleted.id })
                editing = null
            },
        )
    }
}
