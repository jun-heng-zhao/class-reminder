package com.balance.classreminder.ocr

import com.balance.classreminder.data.WeekParity
import com.balance.classreminder.domain.WeekCalc
import kotlin.math.abs

/**
 * 把 OCR 出来的散乱文本行还原成课表网格。
 *
 * 横向课表（表头一行写 周一…周日）和纵向课表（最左一列写 一…日、顶上一行写 第几节）
 * 都支持，方向靠"哪一边聚出了至少 3 个星期名"自动判断。
 * 截图里常混进浏览器标签、地址栏、状态栏，所以表头之外的东西一律不放进网格。
 *
 * 这里只做纯计算，不碰 Android API，方便单测。
 */
object GridParser {

    private val DAY_TOKEN = listOf(
        Regex("周\\s*([一二三四五六日天1-7])"),
        Regex("星期\\s*([一二三四五六日天1-7])"),
        Regex("礼拜\\s*([一二三四五六日天1-7])"),
    )
    private val PERIOD_TOKEN = Regex("^(?:第)?\\s*(\\d{1,2})\\s*(?:节|大节)?$")
    /** 表头里的时间带：第1-2节 / 第3节。 */
    private val PERIOD_BAND = Regex("第\\s*(\\d{1,2})\\s*(?:[-~—－]\\s*(\\d{1,2}))?\\s*节")
    /** 截图放大后 OCR 常把时间带认成 "1-25"、"#3-55" 这种，只求拿到起始节。 */
    private val PERIOD_BAND_LOOSE = Regex("^[^0-9]{0,3}(\\d{1,2})\\s*[-~—－]\\s*(\\d{1,2})[^0-9]{0,2}$")
    /** 格子里的上课节次：第1节-第2节 / 第1-2节。 */
    private val PERIOD_EXPLICIT = Regex("第\\s*(\\d{1,2})\\s*节?\\s*[-~—－]\\s*第?\\s*(\\d{1,2})\\s*节")
    private val PERIOD_SINGLE = Regex("第\\s*(\\d{1,2})\\s*节")
    /** 周次范围必须带「周」字，否则"经3-1"这种房间号会被当成 3-1 周。 */
    private val WEEK_RANGE = Regex("(\\d{1,2})\\s*[-~—－至]\\s*(\\d{1,2})\\s*周")
    private val SINGLE_WEEK = Regex("(\\d{1,2})\\s*周")
    /** 完整周次写法：1-16周 / 15,16周 / 1-4,6-8周 / 11-11周。 */
    private val WEEK_SPEC = Regex(
        "((?:\\d{1,2}\\s*[-~—－至]\\s*\\d{1,2}|\\d{1,2})(?:\\s*[,，、]\\s*(?:\\d{1,2}\\s*[-~—－至]\\s*\\d{1,2}|\\d{1,2}))*)\\s*周"
    )
    /** 整行只有一个范围（1-16）时也当周次。 */
    private val BARE_RANGE = Regex("^\\s*(\\d{1,2})\\s*[-~—－至]\\s*(\\d{1,2})\\s*$")
    /** 行首的范围也当周次：OCR 常把"1-16周"读成"1-16 #65-985"。 */
    private val LEAD_RANGE = Regex("^\\s*(\\d{1,2})\\s*[-~—－至]\\s*(\\d{1,2})")
    private val CREDITS = Regex("[（(]\\s*[\\d.]+\\s*学分\\s*[)）]")
    private val PARITY_MARKERS = listOf(
        Regex("单数周") to WeekParity.ODD,
        Regex("双数周") to WeekParity.EVEN,
        Regex("单周") to WeekParity.ODD,
        Regex("双周") to WeekParity.EVEN,
        Regex("[（(]\\s*单\\s*[)）]") to WeekParity.ODD,
        Regex("[（(]\\s*双\\s*[)）]") to WeekParity.EVEN,
    )
    /** 教室长这样：实验楼C102 / 教三201 / 经3-1 / A402。 */
    private val ROOM_LIKE = Regex("^[\\u4e00-\\u9fa5A-Za-z]{1,6}[A-Za-z]?\\s?\\d{1,4}(?:[-—]\\d{1,4})?$")
    private val PLACE_EXACT = setOf("操场", "体育馆", "图书馆", "食堂", "报告厅", "礼堂", "机房", "实验楼", "教学楼", "运动场")
    private val PLACE_WORD = Regex("[楼室馆场区栋]|中心|机房")
    private val NOISE = Regex("^(课程|时间|教室|课表|上午|下午|晚上|节次|备注|无调课信息|其他课程|暂无数据|学生组)$")

    private data class Row(val indices: List<Int>, val centerY: Int)
    private data class Col(val indices: List<Int>, val centerX: Int)

    fun parse(boxes: List<OcrBox>, defaultTotalWeeks: Int = 20): ParseResult {
        val items = boxes.filter { it.text.isNotBlank() }
        if (items.isEmpty()) return ParseResult(emptyList(), listOf("图片里没有识别到文字"))

        val warnings = mutableListOf<String>()
        val rows = clusterRows(items)
        val cols = clusterCols(items)

        // 星期是"行"还是"列"：哪一边聚出了至少 3 个星期名就算哪一边
        val dayCol = cols.firstOrNull { c ->
            c.indices.count { isDayToken(items[it].text.trim()) } >= 3
        }
        val dayRow = rows.firstOrNull { r ->
            r.indices.count { dayNumberOf(items[it].text) != null && items[it].text.trim().length <= 4 } >= 3
        }

        val grid = when {
            dayCol != null -> parseDaysAsRows(items, rows, cols, dayCol, defaultTotalWeeks, warnings)
            dayRow != null -> parseDaysAsColumns(items, rows, dayRow, defaultTotalWeeks, warnings)
            else -> null
        }
        if (grid != null && grid.courses.isNotEmpty()) return grid

        // 不是表格也别放弃：有些课表是一行一门的清单
        val flat = parseFlatList(items, defaultTotalWeeks, warnings)
        if (flat.courses.isNotEmpty()) return flat

        return grid ?: ParseResult(
            emptyList(),
            warnings + "没认出课表结构（既不是表格，也不像一行一门的清单）",
        )
    }

    /**
     * 第三路兜底：清单式课表。按行扫，遇到带"周X"的行就开一条，
     * 后面直到下一条带星期名字的行都算这一条的补充（地点、周次、老师）。
     */
    private fun parseFlatList(
        items: List<OcrBox>,
        totalWeeks: Int,
        warnings: MutableList<String>,
    ): ParseResult {
        val rows = clusterRows(items).sortedBy { it.centerY }
        val found = mutableListOf<ParsedCourse>()
        var day: Int? = null
        var buffer = mutableListOf<OcrBox>()

        fun flush() {
            val d = day ?: return
            if (buffer.isEmpty()) return
            val raw = buffer.sortedBy { it.centerY }.joinToString("\n") { it.text.trim() }
            val c = classify(raw, totalWeeks)
            val explicit = PERIOD_EXPLICIT.find(raw)
            val start = explicit?.groupValues?.get(1)?.toIntOrNull()
            val end = explicit?.groupValues?.get(2)?.toIntOrNull()
            if (c.name.isNotBlank() && start != null) {
                found += ParsedCourse(
                    dayOfWeek = d,
                    period = start,
                    endPeriod = (end ?: start).coerceAtLeast(start),
                    name = c.name,
                    location = c.location,
                    teacher = c.teacher,
                    startWeek = c.startWeek,
                    endWeek = c.endWeek,
                    parity = c.parity,
                    rawText = raw,
                    confidence = c.confidence,
                    weekSpec = c.weekSpec,
                )
            }
            buffer = mutableListOf()
        }

        rows.forEach { row ->
            val rowBoxes = row.indices.map { items[it] }
            val rowDay = rowBoxes.firstNotNullOfOrNull { dayNumberOf(it.text) }
            if (rowDay != null) {
                flush()
                day = rowDay
                buffer += rowBoxes
            } else if (day != null && buffer.isNotEmpty()) {
                buffer += rowBoxes
            }
        }
        flush()

        if (found.isNotEmpty()) warnings += "按「一行一门课」的清单格式解析的，请核对星期和节次"
        return ParseResult(found, warnings)
    }

    // ------------------------------------------------------------------
    // 纵向课表：左列是星期，顶行是节次时间带（教务系统常见）
    // ------------------------------------------------------------------

    private fun parseDaysAsRows(
        items: List<OcrBox>,
        rows: List<Row>,
        cols: List<Col>,
        dayCol: Col,
        totalWeeks: Int,
        warnings: MutableList<String>,
    ): ParseResult {
        val knownDays = dayCol.indices
            .mapNotNull { i -> dayNumberOfLoose(items[i].text.trim())?.let { it to items[i].centerY } }
            .distinctBy { it.first }
            .sortedBy { it.second }
        if (knownDays.size < 2) return ParseResult(emptyList(), warnings + "星期那一列没认全")

        // 表头 = 含「节次」的那一行，加上紧跟其后、还带「第X节」的行；
        // 不能简单地把"第一个星期行以上的所有行"都当表头——星期行的课程文字本来就在星期名上方。
        val headerRow = rows.lastOrNull { r -> r.indices.any { items[it].text.contains("节次") } }
        val firstDayY = knownDays.first().second
        val headerRows = mutableListOf<Row>()
        if (headerRow != null) {
            headerRows += headerRow
            var idx = rows.indexOf(headerRow) + 1
            while (idx < rows.size && rows[idx].centerY < firstDayY) {
                val r = rows[idx]
                if (r.indices.none { isBandLabel(items[it].text) }) break
                headerRows += r
                idx++
            }
        } else {
            headerRows += rows.filter { it.centerY < firstDayY }
                .filter { r -> r.indices.any { isBandLabel(items[it].text) } }
        }

        val rightEdge = dayCol.indices.maxOf { items[it].right }
        val columnDefs = mutableListOf<Triple<Int, Int, Int>>() // 起始节, 结束节, 列中心 x
        headerRows.forEach { row ->
            row.indices.forEach { i ->
                val box = items[i]
                if (box.centerX <= rightEdge + 10) return@forEach
                val band = parseBand(box.text) ?: return@forEach
                columnDefs += Triple(band.first, band.second, box.centerX)
            }
        }
        if (columnDefs.isEmpty()) {
            warnings += "没找到「第几节」表头，节次只能靠格子里的文字"
        }
        val spacing = medianGap(columnDefs.map { it.third }.sorted())

        val tableTop = headerRows.minOfOrNull { it.centerY } ?: firstDayY
        val headerIndices = headerRows.flatMap { it.indices }.toSet()

        // 先筛出"可能是课程内容"的文字块：表头之下、星期列之右、且落在某一列附近
        val contentIndices = items.indices.filter { i ->
            if (i in dayCol.indices || i in headerIndices) return@filter false
            val box = items[i]
            if (box.centerY < tableTop) return@filter false
            if (isNoiseText(box.text)) return@filter false
            val colIdx = nearestIndex(columnDefs.map { it.third }, box.centerX) ?: return@filter false
            val col = columnDefs[colIdx]
            !(spacing > 0 && abs(col.third - box.centerX) > spacing * 0.7)
        }

        // 单笔画的「一」「二」「三」ML Kit 经常读不出来，用内容行带把缺的星期补回来
        val bandTol = (median(items.map { it.height }) * 3).coerceAtLeast(30)
        val lastDayY = knownDays.last().second
        val contentInRows = contentIndices.filter { items[it].centerY <= lastDayY + bandTol }
        val dayRows = resolveDayRows(knownDays, contentInRows.map { items[it] })
        if (dayRows.isEmpty()) return ParseResult(emptyList(), warnings + "星期那一列没认全")

        val buckets = linkedMapOf<Pair<Int, Int>, MutableList<OcrBox>>()
        val explicitPeriods = linkedMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        contentInRows.forEach { i ->
            val box = items[i]
            val day = nearestIndex(dayRows.map { it.second }, box.centerY)?.let { dayRows[it].first } ?: return@forEach
            val colIdx = nearestIndex(columnDefs.map { it.third }, box.centerX) ?: return@forEach
            val period = columnDefs[colIdx].first
            buckets.getOrPut(day to period) { mutableListOf() } += box
            PERIOD_EXPLICIT.find(box.text)?.let { m ->
                val s = m.groupValues[1].toIntOrNull()
                val e = m.groupValues[2].toIntOrNull()
                if (s != null && e != null) explicitPeriods[day to period] = s to e
            }
        }
        if (buckets.isEmpty()) return ParseResult(emptyList(), warnings + "网格里没解析出课程内容")

        // 格子里的第 3、4 行（地点、周次）常常贴到下一行去，按列并回上一格
        val kept = linkedMapOf<Pair<Int, Int>, String>()
        var absorbed = 0
        buckets.entries
            .sortedWith(compareBy({ it.key.second }, { it.key.first }))
            .forEach { (key, list) ->
                val raw = list.sortedBy { it.centerY }.joinToString("\n") { it.text.trim() }
                if (classify(raw, totalWeeks).name.isBlank()) {
                    val prevKey = (key.first - 1) to key.second
                    val prev = kept[prevKey]
                    if (prev != null) {
                        kept[prevKey] = prev + "\n" + raw
                        absorbed++
                    }
                    return@forEach
                }
                kept[key] = kept[key]?.let { it + "\n" + raw } ?: raw
            }
        if (absorbed > 0) warnings += "有 $absorbed 处零散文字（地点/周次）被并回上一格，请核对"

        val parsed = kept.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (key, raw) ->
                val c = classify(raw, totalWeeks)
                val band = columnDefs.firstOrNull { it.first == key.second }
                val explicit = PERIOD_EXPLICIT.find(raw)
                val start = explicit?.groupValues?.get(1)?.toIntOrNull() ?: band?.first ?: key.second
                val end = explicit?.groupValues?.get(2)?.toIntOrNull() ?: band?.second ?: start
                ParsedCourse(
                    dayOfWeek = key.first,
                    period = start,
                    endPeriod = end.coerceAtLeast(start),
                    name = c.name,
                    location = c.location,
                    teacher = c.teacher,
                    startWeek = c.startWeek,
                    endWeek = c.endWeek,
                    parity = c.parity,
                    rawText = raw,
                    confidence = c.confidence,
                    weekSpec = c.weekSpec,
                )
            }
            .filter { it.name.isNotBlank() }

        val deduped = mergeLookalikes(parsed)
        if (deduped.size < parsed.size) {
            warnings += "有 ${parsed.size - deduped.size} 条重复的课被合并（课表在相邻时间带里画了同一门课）"
        }
        if (deduped.isEmpty()) warnings += "识别到的文字都不像课程，核对下是不是课表截图"
        return ParseResult(deduped, warnings)
    }

    // ------------------------------------------------------------------
    // 横向课表：表头一行是星期，左列是节次
    // ------------------------------------------------------------------

    private fun parseDaysAsColumns(
        items: List<OcrBox>,
        rows: List<Row>,
        headerRow: Row,
        totalWeeks: Int,
        warnings: MutableList<String>,
    ): ParseResult {
        val dayColumns = headerRow.indices
            .mapNotNull { i -> dayNumberOf(items[i].text)?.let { it to items[i].centerX } }
            .distinctBy { it.first }
            .sortedBy { it.second }
        if (dayColumns.isEmpty()) return ParseResult(emptyList(), warnings + "无法确定课表列结构")
        val columnSpacing = medianGap(dayColumns.map { it.second })

        val leftEdge = dayColumns.first().second
        val periodRows = mutableListOf<Pair<Int, Int>>()
        val periodLabelIndices = mutableSetOf<Int>()
        for (row in rows) {
            if (row === headerRow) continue
            val label = row.indices.firstOrNull { i ->
                items[i].centerX < leftEdge - 10 && PERIOD_TOKEN.matches(items[i].text.trim())
            } ?: continue
            val period = PERIOD_TOKEN.find(items[label].text.trim())?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (period !in 1..30) continue
            periodRows += period to row.centerY
            periodLabelIndices += label
        }
        if (periodRows.isEmpty()) {
            warnings += "没找到节次标签，按表头下方的行序当作第 1…N 节"
            periodRows += rows.filter { it !== headerRow }
                .sortedBy { it.centerY }
                .mapIndexed { idx, r -> (idx + 1) to r.centerY }
        }
        val rowSpacing = medianGap(periodRows.map { it.second }.sorted())

        val headerIndices = headerRow.indices.toSet()
        val buckets = linkedMapOf<Pair<Int, Int>, MutableList<OcrBox>>()
        for ((i, box) in items.withIndex()) {
            if (i in headerIndices || i in periodLabelIndices) continue
            if (box.centerY < headerRow.centerY - 4) continue   // 表头上面的东西（状态栏、标题）不要
            if (box.centerX < leftEdge - 10) continue           // 最左一列整列是标签
            if (dayNumberOf(box.text) != null && box.text.trim().length <= 4) continue
            if (isNoiseText(box.text)) continue
            val dayIdx = nearestIndex(dayColumns.map { it.second }, box.centerX) ?: continue
            if (columnSpacing > 0 && abs(dayColumns[dayIdx].second - box.centerX) > columnSpacing * 0.7) continue
            val periodIdx = nearestIndex(periodRows.map { it.second }, box.centerY) ?: continue
            if (rowSpacing > 0 && abs(periodRows[periodIdx].second - box.centerY) > rowSpacing * 0.8) continue
            buckets.getOrPut(dayColumns[dayIdx].first to periodRows[periodIdx].first) { mutableListOf() } += box
        }
        if (buckets.isEmpty()) return ParseResult(emptyList(), warnings + "网格里没有解析出课程内容")

        val kept = mutableListOf<Pair<Pair<Int, Int>, String>>()
        var absorbed = 0
        buckets.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .forEach { (key, list) ->
                val raw = list.sortedBy { it.centerY }.joinToString("\n") { it.text.trim() }
                val day = key.first
                if (classify(raw, totalWeeks).name.isBlank()) {
                    val idx = kept.indexOfLast { it.first.first == day && key.second - it.first.second <= 2 }
                    if (idx >= 0) {
                        kept[idx] = kept[idx].first to (kept[idx].second + "\n" + raw)
                        absorbed++
                    }
                    return@forEach
                }
                kept += key to raw
            }
        if (absorbed > 0) warnings += "有 $absorbed 处零散文字（地点/周次）被并回上一格，请核对"

        val parsed = kept.map { (key, raw) ->
            val c = classify(raw, totalWeeks)
            val explicit = PERIOD_EXPLICIT.find(raw)
            val start = explicit?.groupValues?.get(1)?.toIntOrNull() ?: key.second
            val end = explicit?.groupValues?.get(2)?.toIntOrNull() ?: start
            ParsedCourse(
                dayOfWeek = key.first,
                period = start,
                endPeriod = end.coerceAtLeast(start),
                name = c.name,
                location = c.location,
                teacher = c.teacher,
                startWeek = c.startWeek,
                endWeek = c.endWeek,
                parity = c.parity,
                rawText = raw,
                confidence = c.confidence,
                weekSpec = c.weekSpec,
            )
        }

        val merged = mergeConsecutive(parsed)
        if (merged.size < parsed.size) {
            warnings += "把 ${parsed.size - merged.size} 个相邻节次的同名课程合并成了连堂课，请核对节次"
        }
        return ParseResult(merged, warnings)
    }

    // ------------------------------------------------------------------
    // 文本 → 课程字段
    // ------------------------------------------------------------------

    private data class Classified(
        val name: String,
        val location: String,
        val teacher: String,
        val startWeek: Int,
        val endWeek: Int,
        val parity: WeekParity,
        val confidence: Float,
        val weekSpec: String = "",
    )

    private fun classify(raw: String, totalWeeks: Int): Classified {
        var parity = WeekParity.ALL
        var startWeek = 1
        var endWeek = totalWeeks
        var weekSpec = ""
        var work = raw

        for ((pattern, value) in PARITY_MARKERS) {
            if (pattern.containsMatchIn(work)) {
                parity = value
                work = work.replace(pattern, "\n")
            }
        }
        // 节次文字先摘掉，别让它混进课名
        work = work.replace(PERIOD_EXPLICIT, "\n")
        work = work.replace(PERIOD_SINGLE, "\n")
        // 不规则周次（15,16 / 1-4,6-8）整串留下，起止周只是它的摘要
        WEEK_SPEC.find(work)?.let { m ->
            weekSpec = m.groupValues[1].replace(" ", "")
            val weeks = WeekCalc.parseWeeks(weekSpec, totalWeeks)
            if (weeks.isNotEmpty()) {
                startWeek = weeks.first()
                endWeek = weeks.last()
            }
            work = work.replace(m.value, "\n")
        } ?: WEEK_RANGE.find(work)?.let { m ->
            startWeek = m.groupValues[1].toIntOrNull() ?: 1
            endWeek = m.groupValues[2].toIntOrNull() ?: totalWeeks
            work = work.replace(m.value, "\n")
        } ?: run {
            SINGLE_WEEK.find(work)?.let { m ->
                startWeek = m.groupValues[1].toIntOrNull() ?: 1
                endWeek = startWeek
                work = work.replace(m.value, "\n")
            }
        }
        if (endWeek < startWeek) endWeek = startWeek

        val lines = mutableListOf<String>()
        work.split('\n', '，', ',', '；', ';', ' ', '\u3000').forEach { piece ->
            // 先去掉"（3学分）"，再考虑括号——顺序反了会把"形势与政策(五)"这种名字剪坏
            val line = piece.trim().replace(CREDITS, "").trim().trim('|', '丨', '·', '•', '。', ' ').trim()
            if (line.isEmpty() || NOISE.matches(line)) return@forEach
            if (dayNumberOf(line) != null && line.length <= 4) return@forEach
            BARE_RANGE.find(line)?.let { m ->
                startWeek = m.groupValues[1].toIntOrNull() ?: startWeek
                endWeek = m.groupValues[2].toIntOrNull() ?: endWeek
                if (endWeek < startWeek) endWeek = startWeek
                return@forEach
            }
            // OCR 把"周"字吃掉了，行首的 1-16 / 9-16 仍然当周次
            if (weekSpec.isBlank()) {
                LEAD_RANGE.find(line)?.let { m ->
                    val a = m.groupValues[1].toIntOrNull()
                    val b = m.groupValues[2].toIntOrNull()
                    if (a != null && b != null && a in 1..30 && b in a..30) {
                        startWeek = a
                        endWeek = b
                        return@forEach
                    }
                }
            }
            lines += line
        }
        if (endWeek < startWeek) endWeek = startWeek

        // 地点从所有行里挑"最像教室"的那一行，而不是碰到就认定
        val location = lines
            .filter { roomScore(it) > 0 }
            .maxWithOrNull(
                compareBy({ roomScore(it) }, { line -> line.count { it.isDigit() } }, { -it.length })
            )
            .orEmpty()
        val rest = lines.filter { it != location }
        var name = rest.firstOrNull().orEmpty()
        val teacher = rest.drop(1).firstOrNull { it.length <= 6 && it.none { ch -> ch.isDigit() } }.orEmpty()
        if (name.isEmpty() && location.isNotEmpty()) name = location

        val confidence = when {
            name.isNotBlank() && location.isNotBlank() -> 0.9f
            name.isNotBlank() -> 0.6f
            else -> 0.3f
        }
        return Classified(name.take(30), location, teacher, startWeek, endWeek, parity, confidence, weekSpec)
    }


    private fun isBandLabel(text: String): Boolean = parseBand(text) != null

    /**
     * 解析表头的时间带。严格写法（第3-5节）直接用；OCR 认成 "1-25"、"#3-55" 这种时，
     * 只信起始节——结束节不可信就退回起始节，避免把一节课铺到第 25 节。
     */
    private fun parseBand(text: String): Pair<Int, Int>? {
        PERIOD_BAND.find(text)?.let { m ->
            val start = m.groupValues[1].toIntOrNull() ?: return@let
            val end = m.groupValues[2].toIntOrNull() ?: start
            if (start !in 1..30) return@let
            return start to end.coerceIn(start, start + 5)
        }
        PERIOD_BAND_LOOSE.find(text)?.let { m ->
            val start = m.groupValues[1].toIntOrNull() ?: return@let
            val end = m.groupValues[2].toIntOrNull() ?: start
            if (start !in 1..30) return@let
            return if (end in start..(start + 5)) start to end else start to start
        }
        return null
    }

    /**
     * 补齐星期行。已知的星期标签落在各自的行带里，从上往下走：
     * 行带里有标签就用标签，没有就顺延到下一个星期。
     */
    private fun resolveDayRows(
        known: List<Pair<Int, Int>>,
        contentBoxes: List<OcrBox>,
    ): List<Pair<Int, Int>> {
        if (known.isEmpty() || contentBoxes.isEmpty()) return known
        if (known.map { it.first }.toSet().size >= 7) return known


        val tolerance = (median(contentBoxes.map { it.height }) * 3).coerceAtLeast(30)
        val sorted = contentBoxes.sortedBy { it.centerY }
        val bands = mutableListOf<Pair<Int, Int>>()
        sorted.forEach { box ->
            val last = bands.lastOrNull()
            if (last == null || box.centerY - last.second > tolerance) {
                bands += box.centerY to box.centerY
            } else {
                bands[bands.size - 1] = last.first to box.centerY
            }
        }

        // 已知的星期标签就是骨架，缺的星期才用行带补
        val resolved = known.toMutableList()
        var expected = 1
        bands.forEach { (top, bottom) ->
            val day = known.firstOrNull { it.second in (top - tolerance)..(bottom + tolerance) }?.first ?: expected
            if (resolved.none { it.first == day }) resolved += day to ((top + bottom) / 2)
            expected = day + 1
        }
        return resolved.distinctBy { it.first }.sortedBy { it.second }
    }

    private fun roomScore(line: String): Int {
        if (PLACE_EXACT.contains(line)) return 2
        if (!ROOM_LIKE.matches(line)) return 0
        return if (PLACE_WORD.containsMatchIn(line)) 3 else 1
    }

    /** 同一天、同周次、名字几乎一样的两条，当成学校表格里的重复绘制，合并成一条连堂课。 */
    private fun mergeLookalikes(list: List<ParsedCourse>): List<ParsedCourse> {
        val out = mutableListOf<ParsedCourse>()
        list.sortedWith(compareBy({ it.dayOfWeek }, { it.period })).forEach { cur ->
            val idx = out.indexOfFirst { prev ->
                prev.dayOfWeek == cur.dayOfWeek &&
                    prev.startWeek == cur.startWeek &&
                    prev.endWeek == cur.endWeek &&
                    prev.parity == cur.parity &&
                    prev.location == cur.location &&
                    nameSimilar(prev.name, cur.name) &&
                    // 课名完全一样时不看老师（老师名字 OCR 常出错）；只是前缀像才要求老师对得上
                    (prev.name == cur.name || prev.teacher.isBlank() || cur.teacher.isBlank() || prev.teacher == cur.teacher) &&
                    cur.period <= prev.endPeriod + 3
            }
            if (idx >= 0) {
                val prev = out[idx]
                out[idx] = prev.copy(
                    endPeriod = maxOf(prev.endPeriod, cur.period),
                    name = if (cur.name.length > prev.name.length) cur.name else prev.name,
                    rawText = prev.rawText + "\n" + cur.rawText,
                )
            } else {
                out += cur
            }
        }
        return out
    }

    private fun nameSimilar(a: String, b: String): Boolean {
        if (a == b) return true
        val n = minOf(a.length, b.length, 5)
        return n >= 3 && a.take(n) == b.take(n)
    }

    private fun mergeConsecutive(list: List<ParsedCourse>): List<ParsedCourse> {
        val out = mutableListOf<ParsedCourse>()
        list.sortedWith(compareBy({ it.dayOfWeek }, { it.period })).forEach { cur ->
            val prev = out.lastOrNull()
            if (prev != null &&
                prev.dayOfWeek == cur.dayOfWeek &&
                prev.endPeriod + 1 == cur.period &&
                prev.name == cur.name &&
                prev.location == cur.location
            ) {
                out[out.size - 1] = prev.copy(
                    endPeriod = cur.period,
                    rawText = prev.rawText + "\n" + cur.rawText,
                )
            } else {
                out += cur
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // 聚类与工具
    // ------------------------------------------------------------------

    private fun clusterRows(items: List<OcrBox>): List<Row> {
        if (items.isEmpty()) return emptyList()
        val tolerance = (median(items.map { it.height }) / 2).coerceAtLeast(6)
        val sorted = items.indices.sortedBy { items[it].centerY }
        val groups = mutableListOf<MutableList<Int>>()
        var lastY = Int.MIN_VALUE
        sorted.forEach { i ->
            val y = items[i].centerY
            if (groups.isEmpty() || abs(y - lastY) > tolerance) groups += mutableListOf(i)
            else groups.last() += i
            lastY = y
        }
        return groups.map { idx ->
            idx.sortBy { items[it].left }
            Row(idx, items[idx.first()].centerY)
        }
    }

    private fun clusterCols(items: List<OcrBox>): List<Col> {
        if (items.isEmpty()) return emptyList()
        val tolerance = (median(items.map { it.width }) / 2).coerceAtLeast(10)
        val sorted = items.indices.sortedBy { items[it].centerX }
        val groups = mutableListOf<MutableList<Int>>()
        var lastX = Int.MIN_VALUE
        sorted.forEach { i ->
            val x = items[i].centerX
            if (groups.isEmpty() || abs(x - lastX) > tolerance) groups += mutableListOf(i)
            else groups.last() += i
            lastX = x
        }
        return groups.map { idx ->
            idx.sortBy { items[it].top }
            Col(idx, items[idx.first()].centerX)
        }
    }

    private fun nearestIndex(values: List<Int>, target: Int): Int? {
        if (values.isEmpty()) return null
        var best = 0
        var bestDiff = Int.MAX_VALUE
        values.forEachIndexed { i, v ->
            val d = abs(v - target)
            if (d < bestDiff) {
                bestDiff = d
                best = i
            }
        }
        return best
    }

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        return s[s.size / 2]
    }

    /** 相邻值的典型间距，用来判断"这一格离列中心太远了，不属于任何列"。 */
    private fun medianGap(sorted: List<Int>): Int {
        if (sorted.size < 2) return 0
        val gaps = sorted.zipWithNext { a, b -> b - a }.filter { it > 0 }
        return if (gaps.isEmpty()) 0 else median(gaps)
    }

    private fun isNoiseText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        if (Regex("^\\d{1,2}[:：]\\d{2}(\\s*[-~—]\\s*\\d{1,2}[:：]\\d{2})?$").matches(t)) return true
        if (t.none { it.isLetterOrDigit() }) return true
        if (t.length == 1 && !isDayToken(t)) return true
        return false
    }

    /** "周一" / "星期一" / "周1" → 1..7。 */
    fun dayNumberOf(token: String): Int? {
        DAY_TOKEN.forEach { re ->
            val m = re.find(token) ?: return@forEach
            return when (val g = m.groupValues[1]) {
                "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6
                "日", "天" -> 7
                else -> g.toIntOrNull()?.takeIf { it in 1..7 }
            }
        }
        return null
    }

    /** 教务系统左列常只写"一、二、三"，这里连这种也认。 */
    fun dayNumberOfLoose(token: String): Int? = dayNumberOf(token) ?: when (token) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7
        else -> null
    }

    private fun isDayToken(token: String): Boolean = dayNumberOfLoose(token) != null
}
