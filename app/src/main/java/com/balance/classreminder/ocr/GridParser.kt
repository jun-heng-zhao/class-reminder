package com.balance.classreminder.ocr

import com.balance.classreminder.data.WeekParity
import kotlin.math.abs

/**
 * 把 OCR 出来的散乱文本行还原成课表网格。
 *
 * 假设是常见的"表头一行写周一~周日、最左一列写第几节"的课表截图；
 * 找不到表头时退化成"按 x 坐标聚成 7 列"的猜测，并把猜测写进 warnings。
 * 这里只做纯计算，不碰 Android API，方便单测。
 */
object GridParser {

    private val DAY_TOKEN = listOf(
        Regex("周\\s*([一二三四五六日天1-7])"),
        Regex("星期\\s*([一二三四五六日天1-7])"),
        Regex("礼拜\\s*([一二三四五六日天1-7])"),
    )
    private val PERIOD_TOKEN = Regex("^(?:第)?\\s*(\\d{1,2})\\s*(?:节|大节)?$")
    private val WEEK_RANGE = Regex("(\\d{1,2})\\s*[-~—－至]\\s*(\\d{1,2})\\s*周?")
    private val SINGLE_WEEK = Regex("(\\d{1,2})\\s*周")
    private val ODD_WEEK = Regex("单周")
    private val EVEN_WEEK = Regex("双周")
    private val LOCATION_HINT = Regex("[楼教馆场室区栋]|机房|实验|报告厅|体育馆|操场|中心|学院|[A-Za-z]\\s?\\d{2,4}")
    private val NOISE = Regex("^(课程|时间|教室|课表|上午|下午|晚上|节次|备注)$")

    private data class Row(val indices: List<Int>, val centerY: Int)

    fun parse(boxes: List<OcrBox>, defaultTotalWeeks: Int = 20): ParseResult {
        val items = boxes.filter { it.text.isNotBlank() }
        if (items.isEmpty()) return ParseResult(emptyList(), listOf("图片里没有识别到文字"))

        val warnings = mutableListOf<String>()
        val rows = clusterRows(items)

        // ---- 1. 找星期表头 ----
        var headerRow: Row? = null
        var dayColumns: List<Pair<Int, Int>> = emptyList() // 星期 -> 列中心 x
        for (row in rows) {
            val found = row.indices.mapNotNull { i ->
                dayNumberOf(items[i].text)?.let { it to items[i].centerX }
            }.distinctBy { it.first }
            if (found.size >= 3) {
                headerRow = row
                dayColumns = found.sortedBy { it.second }
                break
            }
        }
        if (headerRow == null) {
            warnings += "没找到「周一…周日」表头，按列位置猜测星期"
            dayColumns = guessDayColumns(items)
        }
        if (dayColumns.isEmpty()) {
            return ParseResult(emptyList(), warnings + "无法确定课表列结构，请在导入页手动添加")
        }

        // ---- 2. 找最左列的节次行 ----
        val leftEdge = dayColumns.first().second
        val periodRows = mutableListOf<Pair<Int, Int>>() // 第几节 -> 行中心 y
        val periodLabelIndices = mutableSetOf<Int>()
        for (row in rows) {
            if (row === headerRow) continue
            val label = row.indices.firstOrNull { i ->
                items[i].centerX < leftEdge - 10 && PERIOD_TOKEN.matches(items[i].text.trim())
            } ?: continue
            val period = PERIOD_TOKEN.find(items[label].text.trim())?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            periodRows += period to row.centerY
            periodLabelIndices += label
        }
        if (periodRows.isEmpty()) {
            warnings += "没找到节次标签，按表头下方的行序当作第 1…N 节"
            periodRows += rows.filter { it !== headerRow }
                .sortedBy { it.centerY }
                .mapIndexed { idx, r -> (idx + 1) to r.centerY }
        }

        // ---- 3. 把每个文本块丢进 (星期, 节次) 格子 ----
        val headerIndices = headerRow?.indices?.toSet() ?: emptySet()
        val buckets = linkedMapOf<Pair<Int, Int>, MutableList<OcrBox>>()
        for ((i, box) in items.withIndex()) {
            if (i in headerIndices || i in periodLabelIndices) continue
            if (dayNumberOf(box.text) != null && box.text.trim().length <= 4) continue // 表头漏网
            val day = nearest(dayColumns.map { it.second }, box.centerX)?.let { dayColumns[it].first } ?: continue
            val period = nearest(periodRows.map { it.second }, box.centerY)?.let { periodRows[it].first } ?: continue
            buckets.getOrPut(day to period) { mutableListOf() } += box
        }
        if (buckets.isEmpty()) return ParseResult(emptyList(), warnings + "网格里没有解析出课程内容")

        // ---- 4. 同一天内，只有地点/周次、没有课名的行并回上面的格子 ----
        val kept = mutableListOf<Pair<Pair<Int, Int>, String>>()
        var absorbed = 0
        buckets.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .forEach { (key, list) ->
                val raw = list.sortedBy { it.centerY }.joinToString("\n") { it.text.trim() }
                val day = key.first
                if (classify(raw, defaultTotalWeeks).name.isBlank()) {
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
            val c = classify(raw, defaultTotalWeeks)
            ParsedCourse(
                dayOfWeek = key.first,
                period = key.second,
                endPeriod = key.second,
                name = c.name,
                location = c.location,
                teacher = c.teacher,
                startWeek = c.startWeek,
                endWeek = c.endWeek,
                parity = c.parity,
                rawText = raw,
                confidence = c.confidence,
            )
        }

        // ---- 5. 连堂课：同一天相邻节、同名同地点 → 合并成一门 ----
        val merged = mergeConsecutive(parsed)

        if (merged.size < parsed.size) {
            warnings += "把 ${parsed.size - merged.size} 个相邻节次的同名课程合并成了连堂课，请核对节次"
        }
        return ParseResult(merged, warnings)
    }

    // ------------------------------------------------------------------

    private data class Classified(
        val name: String,
        val location: String,
        val teacher: String,
        val startWeek: Int,
        val endWeek: Int,
        val parity: WeekParity,
        val confidence: Float,
    )

    private fun classify(raw: String, totalWeeks: Int): Classified {
        var parity = WeekParity.ALL
        var startWeek = 1
        var endWeek = totalWeeks
        var work = raw

        if (ODD_WEEK.containsMatchIn(work)) {
            parity = WeekParity.ODD
            work = work.replace(ODD_WEEK, "\n")
        }
        if (EVEN_WEEK.containsMatchIn(work)) {
            parity = WeekParity.EVEN
            work = work.replace(EVEN_WEEK, "\n")
        }

        WEEK_RANGE.find(work)?.let { m ->
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

        var name = ""
        var location = ""
        var teacher = ""
        val leftovers = mutableListOf<String>()
        work.split('\n', '，', ',', '；', ';', ' ', '\u3000').forEach { piece ->
            val line = piece.trim().trim('(', ')', '（', '）', '[', ']')
            if (line.isEmpty() || NOISE.matches(line)) return@forEach
            if (dayNumberOf(line) != null && line.length <= 4) return@forEach
            when {
                location.isEmpty() && LOCATION_HINT.containsMatchIn(line) -> location = line
                name.isEmpty() -> name = line
                teacher.isEmpty() && line.length <= 6 -> teacher = line
                else -> leftovers += line
            }
        }
        if (location.isEmpty() && leftovers.isNotEmpty()) location = leftovers.joinToString(" ")
        if (name.isEmpty() && location.isNotEmpty()) name = location

        val confidence = when {
            name.isNotBlank() && location.isNotBlank() -> 0.9f
            name.isNotBlank() -> 0.6f
            else -> 0.3f
        }
        return Classified(
            name = name.take(30),
            location = location,
            teacher = teacher,
            startWeek = startWeek,
            endWeek = endWeek,
            parity = parity,
            confidence = confidence,
        )
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

    /** 按 y 中心把文本块聚成行。 */
    private fun clusterRows(items: List<OcrBox>): List<Row> {
        if (items.isEmpty()) return emptyList()
        val tolerance = (median(items.map { it.height }) / 2).coerceAtLeast(6)
        val sorted = items.indices.sortedBy { items[it].centerY }
        val rows = mutableListOf<MutableList<Int>>()
        var lastY = Int.MIN_VALUE
        sorted.forEach { i ->
            val y = items[i].centerY
            if (rows.isEmpty() || abs(y - lastY) > tolerance) {
                rows += mutableListOf(i)
            } else {
                rows.last() += i
            }
            lastY = y
        }
        return rows.map { idx ->
            idx.sortBy { items[it].left }
            Row(idx, items[idx.first()].centerY)
        }
    }

    /** 没有表头时的退路：按 x 间隔把文本块聚成列，取最左一列为节次列。 */
    private fun guessDayColumns(items: List<OcrBox>): List<Pair<Int, Int>> {
        val tolerance = (median(items.map { it.width }) / 3).coerceAtLeast(20)
        val xs = items.map { it.centerX }.sorted()
        val groups = mutableListOf<MutableList<Int>>()
        xs.forEach { x ->
            if (groups.isEmpty() || x - groups.last().last() > tolerance) groups += mutableListOf(x)
            else groups.last() += x
        }
        return groups.drop(1).take(7).mapIndexed { i, g -> (i + 1) to g.average().toInt() }
    }

    private fun nearest(values: List<Int>, target: Int): Int? {
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

    /** "周一" / "星期一" / "周1" → 1..7，认不出返回 null。 */
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
}
