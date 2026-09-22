# 规格：课表提醒（ClassReminder）

## 目标（Objective）

一个自用的安卓课表提醒应用，解决"不知道下节课在哪、什么时候上"的问题。

用户故事：
1. 开学时设定"第一周的周一"是哪天，之后应用自动算出今天是第几周。
2. 把教务系统导出的课表**图片**丢进应用，自动识别出课程、星期、节次、周次范围、上课地点。
3. 每节课开始前 N 分钟（默认 20，可逐课修改）发通知："10:00 高等数学 @ 教三201，还有 20 分钟"，并能响铃震动。
4. 识别错了、临时调课，可以手动增/删/改任意一条，也可以直接在课表网格上点格子新建。

成功的标准：在小米手机（Android 16 / HyperOS 3）上装上后，导入一张课表截图能自动填充出大部分课程，人工修几处即可用；到点能收到提醒通知。

## 技术选型（Tech Stack）

| 项 | 选择 | 理由 |
|---|---|---|
| 语言/UI | Kotlin 2.4.20 + Jetpack Compose (BOM 2026.09.00) | 单 Activity，无需 XML 布局 |
| 构建 | AGP 9.4.0 + Gradle 9.6.0 + JDK 25，compileSdk 37 / minSdk 26 / targetSdk 36 | 与本机 Android Studio 模板一致 |
| OCR | `com.google.mlkit:text-recognition-chinese:16.0.1`（**bundled** 版） | 离线，不依赖 Google Play 服务（国行 HyperOS 没有 GMS） |
| 存储 | 自研纯文本行格式，写在 `filesDir/schedule.txt` | 数据量极小（<200 条），避免 Room/KSP 版本匹配风险；格式纯 Kotlin，可单测 |
| 提醒 | AlarmManager `setExactAndAllowWhileIdle` + NotificationChannel（高优先级，可响铃震动） | 不用 WorkManager 的 15 分钟最小间隔，课前提醒需要精确到分钟 |
| 异步 | kotlinx.coroutines（随 lifecycle-runtime-ktx 传入） | ML Kit 的 Task 用 suspendCancellableCoroutine 包一层 |

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
