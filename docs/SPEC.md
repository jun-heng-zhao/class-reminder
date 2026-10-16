# 规格：课表提醒（ClassReminder）

## 2026-10-16 v1.0.5 合并发布验证

- 保留本地三栏导航、今日/周课表、设置草稿保存校验、教学周校准与异步日历缓存；合入远程多年份节假日、学期范围限制和停止响铃链路，保留通知权限检查。
- 验证环境：`GRADLE_USER_HOME=/home/Balance/AgentWork/.gradle-home ANDROID_USER_HOME=/home/Balance/AgentWork/.android-home`；执行 `/home/Balance/AgentWork/.gradle-home/dist/gradle-9.6.0/bin/gradle -p /home/Balance/AgentWork/ClassReminder testDebugUnitTest assembleRelease lintDebug --rerun-tasks` → BUILD SUCCESSFUL，80 个任务实际执行，65 项测试零失败。
- Release APK 的包名为 `com.balance.classreminder`，versionName 1.0.5 / versionCode 6，apksigner v2 签名验证通过；lint 无 error，保留 16 个 warning、3 个 hint，另有 CalendarParser 的既有冗余 Elvis 编译警告。
- 当前 OCR 测试样本及相关断言已匿名化，保留坐标、周次与噪声结构；不改写历史，历史提交中的原始个人样本仍存在。公共图片及校历未删除。
- 本轮未做真机安装、通知响铃/停止、长时后台与耗电验证；上述自动化结果不能替代真机验收。

## 2026-10-15 新需求验收

- 手动校准当前教学周：保存校准周数和所在周周一，之后每逢周一自动递增；可恢复按开学日计算。首页、日历、课前提醒使用同一基准，旧备份保持兼容。
- 打开应用展示今日课程、时间和地点；无课程、未配置、放假及调休待安排有明确提示。不通过解锁广播常驻后台。
- 系统日历查询移至 IO 线程，同范围结果缓存 15 分钟，连续刷新至少间隔 30 秒；最多缓存 4 个范围。月历和课表计算仅在相关输入变化时重算。设置采用草稿确认，避免按键触发写盘和闹钟重排。
- 提示使用轻量二次元口吻，但权限、错误、课程时间和地点保持明确可读。最终确认 JPG 用于桌面启动图标，首页不展示大图。
- 验证：新增 JVM 回归测试、testDebugUnitTest、assembleDebug、lintDebug；连接设备后无损安装验证，不卸载、不清空数据、不使用 Root。

### 本轮验证结果

- 版本：1.0.3（versionCode 4）。`testDebugUnitTest assembleDebug lintDebug` 成功，56 项测试零失败；`assembleRelease` 成功。
- 正式签名包 `adb install -r app/build/outputs/apk/release/app-release.apk` 返回 Success；真机读取首页封面、今日课程时间地点及设置页校准入口，原有课程仍在。
- Debug 包不能覆盖手机正式签名包；禁止以卸载或清数据绕过。实际耗电收益、跨日长期运行和课前通知送达未作真机测量。

## 目标（Objective）

一个自用的安卓课表提醒应用，解决"不知道下节课在哪、什么时候上"的问题。

用户故事：

1. 开学时设定"第一周的周一"是哪天，之后应用自动算出今天是第几周。
2. 把教务系统导出的课表**图片**丢进应用，自动识别出课程、星期、节次、周次范围、上课地点。
3. 每节课开始前 N 分钟（默认 20，可逐课修改）发通知："10:00 高等数学 @ 教三201，还有 20 分钟"，并能响铃震动。
4. 识别错了、临时调课，可以点课程修改或删除，通过周课表加课按钮新建课程。

## 2026-10-15 布局迭代与验证

- 今日课程和周课表独立切换；周课表最终仅保留星期分组列表，删除网格及表格/列表开关。
- 主导航最终只保留课表、日历、设置三个页面，使用 HorizontalPager，左右滑动与底栏点击共用页码。低频图片导入移至设置次级页面。
- 设置首页提供学期与作息、提醒与权限、图片导入、备份与恢复四个分类；次页支持页面内返回和系统返回。学期与提醒独立校验、保存与取消，跨分类保留表单草稿；离开图片导入次页会丢弃未导入识别结果。不保证进程回收后的临时草稿恢复。
- 导入类型及操作区允许换行，识别中禁止切换类型；日历增加页面标题、回本月及纵向导入按钮，日期格最小高度 72dp；节假日弹窗分开标题与状态选项。
- 日历仅当前页执行查询，无权限时由用户主动点击导入才申请，避免预加载提前弹窗。底层查询缓存和节流保持不变。
- 验证命令：`GRADLE_USER_HOME=/home/Balance/AgentWork/.gradle-home ANDROID_USER_HOME=/home/Balance/AgentWork/.android-home /home/Balance/AgentWork/.gradle-home/dist/gradle-9.6.0/bin/gradle -p /home/Balance/AgentWork/ClassReminder testDebugUnitTest assembleRelease lintDebug`，BUILD SUCCESSFUL；56 项测试，0 失败、0 错误。
- 三栏导航版本再次执行上述构建命令成功（42 秒），`git diff --check` 通过；正式包无损覆盖安装 Success。
- 设备 e7781e7b 上 UIAutomator 确认包名 `com.balance.classreminder`，横滑从今日课表依次进入教学日历、设置；底栏仅课表、日历、设置。设置首页四个分类均存在，图片导入次页打开成功，系统返回回到设置分类首页。原有 12 门课程仍显示，未卸载、清数据或保存测试设置。
- 本次未逐一操作学期、提醒、备份次页，未实际导入图片或验证草稿保存/取消；相关编译与原有校验单测通过不等同于这些交互的真机验收。
- 尚未验证：大字体/横屏、屏幕阅读器、进程回收后的表单恢复、长时耗电及真实通知送达。UI 节点烟测不等同于截图视觉验收。

### 可复用布局经验

1. 同源应用必须按包名、源码路径和 APK 路径区分，不能凭相似界面判断。
2. 页面切换共用唯一状态；保留组合时同时限制副作用，不能让预加载页面自动申请权限或查询。
3. 长文案按钮用纵向布局，短选项用可换行布局，避免为了挤进一行而缩小字号。
4. 先明确今日摘要与整周浏览的任务边界，再安排内容；用户不需要的网格应删除，不留下失效入口和说明。
5. 文档分别记录编译单测、UI 节点烟测和未验证边界，不能用单测通过代替视觉或耗电结论。
6. 按使用频率分配导航位置：课表、日历留在主导航；导入与备份放入设置次页。分类表单各自保存，不能让提醒设置受未配置开学日期阻塞。

成功的标准：在小米手机（Android 16 / HyperOS 3）上装上后，导入一张课表截图能自动填充出大部分课程，人工修几处即可用；到点能收到提醒通知。

## 技术选型（Tech Stack）

| 项      | 选择                                                                                   | 理由                                                                     |
| ------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| 语言/UI | Kotlin 2.4.20 + Jetpack Compose (BOM 2026.09.00)                                       | 单 Activity，无需 XML 布局                                               |
| 构建    | AGP 9.4.0 + Gradle 9.6.0 + JDK 25，compileSdk 37 / minSdk 26 / targetSdk 36            | 与本机 Android Studio 模板一致                                           |
| OCR     | `com.google.mlkit:text-recognition-chinese:16.0.1`（**bundled** 版）                   | 离线，不依赖 Google Play 服务（国行 HyperOS 没有 GMS）                   |
| 存储    | 自研纯文本行格式，写在 `filesDir/schedule.txt`                                         | 数据量极小（<200 条），避免 Room/KSP 版本匹配风险；格式纯 Kotlin，可单测 |
| 提醒    | AlarmManager `setExactAndAllowWhileIdle` + NotificationChannel（高优先级，可响铃震动） | 不用 WorkManager 的 15 分钟最小间隔，课前提醒需要精确到分钟              |
| 异步    | kotlinx.coroutines（随 lifecycle-runtime-ktx 传入）                                    | ML Kit 的 Task 用 suspendCancellableCoroutine 包一层                     |

明确不做：不依赖任何后端、不联网、不上传课表；不做多用户/多设备同步。

## 命令（Commands）

构建环境全部落在工作区，避开沙箱只读限制：

```bash
export GRADLE_USER_HOME=/home/Balance/AgentWork/.gradle-home
export ANDROID_USER_HOME=/home/Balance/AgentWork/.android-home
GRADLE=$GRADLE_USER_HOME/dist/gradle-9.6.0/bin/gradle

# 编译 debug APK
$GRADLE -p /home/Balance/AgentWork/ClassReminder assembleDebug

# 单元测试（周次计算 / 课表解析）
$GRADLE -p /home/Balance/AgentWork/ClassReminder testDebugUnitTest

# 安装到手机
~/Android/Sdk/platform-tools/adb install -r \
  /home/Balance/AgentWork/ClassReminder/app/build/outputs/apk/debug/app-debug.apk
```

## 目录结构（Project Structure）

```
ClassReminder/
├── app/src/main/java/com/balance/classreminder/
│   ├── MainActivity.kt            入口
│   ├── data/  Model.kt Codec.kt Store.kt        模型 / 编解码 / 落盘
│   ├── domain/WeekCalc.kt                       周次与上课时间计算（纯函数）
│   ├── ocr/   OcrModels.kt GridParser.kt TimetableOcr.kt   图片→网格→课程
│   ├── remind/ Notifier.kt ReminderScheduler.kt ReminderReceiver.kt BootReceiver.kt
│   └── ui/    AppRoot.kt ScheduleScreen.kt ImportScreen.kt SettingsScreen.kt EditCourseDialog.kt
├── app/src/test/java/com/balance/classreminder/  单测（WeekCalcTest / GridParserTest / CodecTest）
└── docs/SPEC.md
```

## 代码风格（Code Style）

- 数据类不可变，改动走 `copy()`。
- 领域逻辑（周次、解析）是纯函数，不碰 Android API，方便单测。
- 中文用户提示写在 Compose 里，不抽 strings.xml（自用应用，不做国际化）。
- 注释只写"为什么"，不写"是什么"。

```kotlin
// 第几周：以第一周周一为基准，按整周向下取整
fun weekOf(termStart: LocalDate, date: LocalDate): Int =
    (ChronoUnit.DAYS.between(termStart, date).toInt() / 7) + 1
```

## 测试策略（Testing Strategy）

- JUnit4 单测，覆盖三块纯逻辑：周次计算、单双周过滤、OCR 网格解析（用合成的 OcrBox 模拟 ML Kit 输出）。
- UI 与通知不做自动化测试，靠真机手动验证：装到 487ba63c 上，导入样例图、改一条课、把提醒时间设成 2 分钟后验证通知。
- 涉及提醒链路的改动到真机上验：`dumpsys alarm` 看挂了哪些闹钟、`dumpsys notification` 看通知里有没有「停止响铃」、`dumpsys audio` 看 `USAGE_ALARM` 播放器是否随停止而释放。给某天标「放假」后当天闹钟必须立刻消失。
- 每轮改动后必须 `assembleDebug` 通过。

## 边界（Boundaries）

- 总是：改完代码跑一次 `assembleDebug`；OCR 结果一律先给用户确认再入库。
- 先问：新增第三方依赖；改动 /home/Balance/Codes/Android 下用户已有工程；卸载手机上已有应用。
- 绝不：上传课表数据；把识别结果不经确认直接覆盖用户已编辑的课表；清空应用数据。

## 已知限制 / 待确认（Open Questions）

1. **样例图片**：还没有拿到用户的真实课表图，网格解析目前按常见"表头周一~周日 + 左列节次"结构做，容许一定偏差，最终靠人工确认页兜底。
2. **作息时间**：内置一套常见的 12 节时间（8:00 起，上下午+晚上），可在设置页逐节修改。
3. **节次合并**：单元格跨多节（如 1-2 节连上）会被识别成相邻两条同名课，解析后用"同一天相邻节同名同地点则合并"修复。
4. **省电限制**：HyperOS 上需用户手动允许"自启动"和"后台弹出界面"，应用内给提示和跳转入口。
5. **法定节假日**：MIUI / HyperOS 不通过 CalendarContract 对第三方应用暴露「中国法定节假日」，所以日历页优先读系统日历，读不到就自动套用内置数据（`data/BuiltInHolidays.kt`），假期不再误发提醒。2025 / 2026 是国务院完整安排（含调休上班日）；其他年份退到可计算的法定假日（元旦、春节除夕~初三、清明、劳动节、国庆），端午与中秋需要系统日历或手动标注。
6. **学期区间**：`WeekCalc.courseOnDate` 只认第 1 周~总周数之间的日子，区间外（寒暑假）不排课也不提醒 —— 有的课 `endWeek` 写得比总周数大，光看周次范围会算到假期里去。
7. **强提醒铃声**（`remind/AlarmPlayer.kt`）：铃声由全局单例播放，通知上的「停止响铃」、划掉通知、点开应用、以及 1 分钟超时都会 `stop()` 并释放播放器，避免"响了只能杀进程"。
8. **桌面图标不可自定义**（已尝试后放弃）：安卓不允许应用在运行时替换自己的 launcher 图标（必须是编译期资源），HyperOS 桌面也会拒绝第三方的 `requestPinShortcut` / `requestPinAppWidget`（日志：`add shortcut failed, ... has no permission`，即使声明了 `com.android.launcher.permission.INSTALL_SHORTCUT` 也没用，而且这两个 API 会返回 true，靠返回值判断不了成败）。真要换图标只能用户自己在 MIUI 桌面里改。
9. **导航结构**（`ui/AppRoot.kt` + `ui/SettingsScreen.kt`）：课表 / 日历 / 设置三个主页面用 `HorizontalPager` 装，底部导航与左右滑动共用页码；图片导入位于设置分类内。分类共享外层草稿，返回键先回设置菜单，不自动保存。
