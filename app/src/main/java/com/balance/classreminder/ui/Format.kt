package com.balance.classreminder.ui

import com.balance.classreminder.data.Course
import com.balance.classreminder.data.WeekParity
import com.balance.classreminder.data.dayName

/** 课程的一句话描述，课表页和导入页共用。 */
fun courseSubtitle(course: Course): String = buildString {
    append(dayName(course.dayOfWeek))
    append(" 第 ").append(course.startPeriod)
    if (course.endPeriod != course.startPeriod) append("-").append(course.endPeriod)
    append(" 节 · ")
    if (course.weekSpec.isNotBlank()) append(course.weekSpec).append(" 周")
    else append(course.startWeek).append("-").append(course.endWeek).append(" 周")
    when (course.parity) {
        WeekParity.ODD -> append("（单周）")
        WeekParity.EVEN -> append("（双周）")
        WeekParity.ALL -> Unit
    }
    if (course.location.isNotBlank()) append(" · ").append(course.location)
    if (course.teacher.isNotBlank()) append(" · ").append(course.teacher)
}
