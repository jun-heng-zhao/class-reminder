package com.balance.classreminder.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.Store
import com.balance.classreminder.domain.WeekCalc
import com.balance.classreminder.remind.Notifier
import com.balance.classreminder.remind.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { Store.get(context) }

    var courses by remember { mutableStateOf(store.courses) }
    var settings by remember { mutableStateOf(store.settings) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Course?>(null) }
    var newSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 四个页面装在一个 pager 里：底部导航和左右滑动改的是同一个状态，不会各跑各的
    val pagerState = rememberPagerState { 4 }
    val tab = pagerState.currentPage
    val scope = rememberCoroutineScope()

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    fun persist(newCourses: List<Course> = courses, newSettings: AppSettings = settings) {
        courses = newCourses
        settings = newSettings
        store.update(newCourses, newSettings)
        ReminderScheduler.reschedule(context)
    }

    LaunchedEffect(Unit) {
        Notifier.ensureChannels(context)
        ReminderScheduler.reschedule(context)
        if (store.settings.termStartDate.isBlank()) pagerState.scrollToPage(3) // 没设第一周就直接去设置页
    }

    val termStart = WeekCalc.parseDate(settings.termStartDate)
    val currentWeek = WeekCalc.weekOf(termStart, LocalDate.now())
    val shownWeek = (currentWeek + weekOffset).coerceAtLeast(1)

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { goTo(0) },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("课表") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { goTo(1) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    label = { Text("导入") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { goTo(2) },
                    icon = { Icon(Icons.Filled.EditCalendar, contentDescription = null) },
                    label = { Text("日历") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { goTo(3) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        }
    ) { padding ->
        // 左右滑动切页；设置页里的二级页面自己也带 pager，滑动会优先给内层
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
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
                )

                1 -> ImportScreen(
                    settings = settings,
                    onImportCourses = { parsed ->
                        val added = parsed.map { it.copy(id = UUID.randomUUID().toString()) }
                        persist(courses + added)
                        goTo(0)
                    },
                    onChangeSettings = { persist(newSettings = it) },
                )

                2 -> CalendarScreen(
                    settings = settings,
                    courses = courses,
                    onChange = { persist(newSettings = it) },
                )

                else -> SettingsScreen(
                    settings = settings,
                    courses = courses,
                    onChange = { persist(newSettings = it) },
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
            onSave = { persist(newCourses = store.upsert(it)); newSlot = null },
            onDelete = null,
        )
    }

    editing?.let { course ->
        EditCourseDialog(
            course = course,
            title = "编辑课程",
            onDismiss = { editing = null },
            onSave = { persist(newCourses = store.upsert(it)); editing = null },
            onDelete = { persist(newCourses = store.delete(it.id)); editing = null },
        )
    }
}
