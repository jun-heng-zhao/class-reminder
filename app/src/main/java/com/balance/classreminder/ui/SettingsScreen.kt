package com.balance.classreminder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course

/** 设置里的二级页面；顺序就是左右滑动的顺序。 */
private val SECTIONS = listOf(
    "学期与提醒" to "第一周的周一、总周数、默认提前几分钟、响铃",
    "上课节次时间" to "每节课几点上下课",
    "软件图标" to "用自己的图做桌面图标",
    "课表数据" to "导出 / 从备份恢复",
    "提醒权限" to "通知、精确闹钟、HyperOS 设置",
)

/**
 * 设置：一级是分组菜单，点进去才是具体设置项。
 * 二级页面之间可以左右滑动切换，返回键或左上角「返回」回到菜单。
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    courses: List<Course>,
    onChange: (AppSettings) -> Unit,
    onTestNotification: () -> Unit,
) {
    var sectionIndex by remember { mutableIntStateOf(-1) }   // -1 = 停在一级菜单

    if (sectionIndex < 0) {
        SettingsMenu(
            settings = settings,
            courses = courses,
            onOpen = { sectionIndex = it },
        )
        return
    }

    BackHandler { sectionIndex = -1 }
    // 只在进二级页面时创建，initialPage 就用点进来的那一项
    val pagerState = rememberPagerState(initialPage = sectionIndex) { SECTIONS.size }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { sectionIndex = -1 }) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text(
                SECTIONS[pagerState.currentPage].first,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider()
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                when (page) {
                    0 -> TermSettingsPage(settings = settings, onChange = onChange)
                    1 -> PeriodsPage(settings = settings, onChange = onChange)
                    2 -> AppIconPage()
                    3 -> DataPage(courses = courses)
                    else -> PermissionsPage(settings = settings, onTestNotification = onTestNotification)
                }
            }
        }
    }
}

/** 一级菜单：一行一个入口。 */
@Composable
private fun SettingsMenu(
    settings: AppSettings,
    courses: List<Course>,
    onOpen: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("点分组改具体项；进了分组还能左右滑动换页。", fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))

        SECTIONS.forEachIndexed { index, (title, subtitle) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(index) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
            }
            HorizontalDivider()
        }

        Text(
            "第一周 " + settings.termStartDate.ifBlank { "未设置" } +
                " · 总周数 " + settings.totalWeeks +
                " · 已有 " + courses.size + " 门课",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
