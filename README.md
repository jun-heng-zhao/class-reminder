<div align="center">
  <img src="docs/cover.png" width="100%" alt="课表提醒">
</div>

# 课表提醒

把课表**截图**丢进来，自动识别成课程；每节课前 N 分钟提醒你**几点、去哪上课**。
全程离线，课表只存在手机本地，不联网、不上传。

## 功能

| 功能               | 说明                                                                                      |
| ------------------ | ----------------------------------------------------------------------------------------- |
| 图片导入           | 「课表 / 作息时间表 / 教学校历」三种图都能识别（ML Kit 离线中文 OCR，不需要 Google 服务） |
| 周次               | 单双周 + 不规则周次：`1-16周`、`15,16周`、`2-16周(双)`、`11-11周`、`13-14周`              |
| 课前提醒           | 精确闹钟课前 N 分钟发通知；强提醒可通过通知按钮、划掉通知或打开应用停止，最长响铃 1 分钟 |
| 法定调休           | 放假/调休可手动标或从系统日历读取；读不到时套用内置安排，调休上班日默认「待安排」 |
| 手动周次与开屏摘要 | 可校准当前教学周并在每周一自动递增；打开应用即显示今日课程、时间和地点                    |
| 寒暑假             | 学期区间之外的日期自动按学生假期显示，不用手标                                            |
| 课程视图           | **今日课表**按时间显示独立课程卡；**周课表**按星期分组显示列表，不再提供网格              |
| 页面导航           | 底栏点击和左右滑动同步切换课表、日历、设置；图片导入收纳在设置次级页面                    |
| 手动编辑           | 周课表点击加课按钮、点课程改课、编辑框里删课；识别结果一律先核对再入库                    |

## 截图

`b_b03bbaec39eb983de6d1557f7884b37b.jpg` 用作桌面启动图标，首页不展示大图。「今日课表 / 周课表」分开切换，默认显示今日课程；周课表保留切周和按星期分组列表。下方截图为历史版本，不代表最新布局。

本应用包名为 `com.balance.classreminder`（课表提醒）；同源的另一个应用 `com.balance.deadlinereminder` 是独立应用，安装和验证时以包名区分，不操作后者。

|               表格视图                |               识别结果核对                |               教学日历                |
| :-----------------------------------: | :---------------------------------------: | :-----------------------------------: |
| ![表格](docs/screenshots/01-表格.png) | ![导入](docs/screenshots/02-导入识别.png) | ![日历](docs/screenshots/03-日历.png) |

## 下载安装

**[⬇️ 下载最新 APK](https://github.com/jun-heng-zhao/class-reminder/releases/latest)**（`app-release.apk`，minSdk 26 / Android 8.0+）

装完首次进入按提示给通知权限；小米 / HyperOS 建议再到系统设置里允许**自启动**、省电策略改**无限制**，否则提醒可能不派发。

## 从源码构建

```bash
# 需要 Android SDK（compileSdk 37）与 JDK 17+
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

工具链：AGP 9.4（内置 Kotlin，无需再应用 `kotlin-android` 插件）、Gradle 9.6、compileSdk 37 / minSdk 26 / targetSdk 36。

## 上手四步

1. **设置第一周**：设置 → 学期与作息，填「第一周的周一」，或直接校准当前教学周；校准后每周一自动递增，也可以随时恢复按开学日计算。
2. **导课表**：设置 → 图片导入 → 「课表图片」 → 选图 → 核对识别结果 → 导入。
3. **导作息时间**：设置 → 图片导入 → 「作息时间图片」读出每节课几点上下课（默认内置华侨大学教务处公布的 13 节时间表，设置 → 学期与作息可逐节改）。
4. **标调休**：日历页标放假/调休；学期区间之外的日期自动显示为寒暑假。

设置首页分为「学期与作息」「提醒与权限」「图片导入」「备份与恢复」。次级页面支持页面内返回和系统返回；学期、提醒分别保存与取消，返回分类首页不等于保存。表单草稿保留在当前应用会话中；离开图片导入次页会丢弃未导入的识别结果。

## 实现要点

- **表格还原**（`ocr/GridParser.kt`）：横向课表（表头是 `周一…周日`）和纵向课表（左列是 `一…日`、顶行是 `第X-Y节`）都能认；方向靠"哪一边聚出了至少 3 个星期名"自动判断。
  真实截图里会混进浏览器标签栏、地址栏、状态栏、右侧面板，这些按"表头以上/最右列以外"一律不进网格。
  ML Kit 读不出单笔画的 `一`/`二`/`三` 时，用内容行带把缺的星期补回来。
- **周次**（`data/Model.kt` 的 `Course.weekSpec`）：原始写法整串留着（`15,16`），提醒和课表都按它算，光靠"起止周"表达不了只上第 15、16 周的课。
- **调休**（`data/Model.kt` 的 `DayOverride` + `domain/WeekCalc.effectiveDayOfWeek`）：逐日覆盖"这天按星期几上课"。调休上班日 `swapToDayOfWeek = 0` 表示待安排 —— 这天先不排课也不提醒，等用户在日历页指定（解码时要把 0 原样保住，夹到 1 会变成"上周一的课"而误发提醒）。
- **节假日数据**（`data/BuiltInHolidays.kt` + `data/DeviceCalendar.kt`）：MIUI / HyperOS 不对第三方应用开放「中国法定节假日」，所以优先读系统日历、读不到就自动套用内置数据。2025 / 2026 是国务院完整安排（含调休上班日），更靠后的年份只算得出元旦、春节、清明、劳动节、国庆的法定假日，不需要联网也不靠每年更新数据。
- **提醒**（`remind/ReminderScheduler.kt`）：只排未来 8 天的精确闹钟，每次响铃/开机/改数据都重排一次；不常驻后台也不会漏。学期区间（第 1 周~总周数）之外的日期不排提醒。
- **强提醒响铃**（`remind/AlarmPlayer.kt`）：铃声由全局单例管着，通知上的「停止响铃」、划掉通知、点开应用、以及 1 分钟超时都会调 `stop()` 并释放播放器，不会出现"响了只能杀进程"。
- **存储**（`data/Codec.kt`）：数据量极小（几十条），用纯文本行格式写 `filesDir/schedule.txt`，不引 Room/KSP，逻辑纯 Kotlin 可直接跑 JVM 单测。
- **查询与写盘**：系统日历查询在 IO 线程执行，同范围缓存 15 分钟、刷新间隔至少 30 秒、最多保留 4 个范围，并合并并发请求；设置采用草稿确认，不再逐键写盘或重排提醒。今日摘要只在前台检查日期，不新增常驻服务。

## 目录结构

```
app/src/main/java/com/balance/classreminder/
├── MainActivity.kt              入口
├── data/   Model.kt Codec.kt Store.kt DeviceCalendar.kt BuiltInHolidays.kt
├── domain/ WeekCalc.kt          周次、上课时间、调休、寒暑假
├── ocr/    OcrModels.kt GridParser.kt PeriodParser.kt CalendarParser.kt TimetableOcr.kt
├── remind/ Notifier.kt AlarmPlayer.kt ReminderScheduler.kt ReminderReceiver.kt StopAlarmReceiver.kt BootReceiver.kt
└── ui/     AppRoot.kt ScheduleScreen.kt ImportScreen.kt CalendarScreen.kt
            SettingsScreen.kt SettingsPages.kt EditCourseDialog.kt Format.kt
app/src/test/                    单测（含真机 OCR 原文回放）
tools/make_sample_timetable.py   生成示例课表图
docs/SPEC.md                     设计规格
```

## 测试

```bash
./gradlew testDebugUnitTest
```

单测覆盖周次校准、单双周、不规则周次、调休/放假、寒暑假边界、内置节假日、缓存时效/节流/并发合并、设置草稿校验、连堂课合并、编解码，以及保留真机坐标与 OCR 噪声的**匿名化样本回放**（`app/src/test/resources/ocr_dump_real.txt`）。当前样本已替换个人字段，但早期提交中的原始样本仍保留在 Git 历史中。

## 已知限制

- OCR 对小字号、低对比度的文字（周次、节次那一行）识别率有限，识别错字在核对页改一下即可，逻辑不会错。
- 小米/HyperOS 需要手动允许「自启动」并把省电策略设为「无限制」，否则系统可能不派发提醒（应用内设置页有提示和跳转）。
- 调休上班日到底上哪天的课，各校执行不一致，需要自己指定。
- 法定节假日：优先读系统日历里订阅的「中国法定节假日」；读不到就用内置数据。内置的 2025/2026 是国务院完整安排，其他年份只含可计算的法定假日（元旦 / 春节 / 清明 / 劳动节 / 国庆），端午与中秋请从系统日历导入或手动标。
- **桌面图标**：安卓不允许第三方应用在运行时换自己的 launcher 图标，HyperOS 桌面也会拒绝应用发来的“摆图标”请求，所以本应用不提供改图标功能（想换图标请用 MIUI 桌面自带的换图标入口）。

## 许可

[MIT](LICENSE) © 2026 jun-heng-zhao
