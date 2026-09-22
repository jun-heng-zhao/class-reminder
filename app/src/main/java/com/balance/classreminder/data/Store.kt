package com.balance.classreminder.data

import android.content.Context
import java.io.File

/**
 * 课表仓库：内存态 + 一个文本文件。数据量极小，不需要数据库。
 * 所有写操作立即落盘，避免进程被杀丢数据。
 */
class Store private constructor(private val appContext: Context) {

    private val file = File(appContext.filesDir, "schedule.txt")

    /** 外部目录里的备份：换包/换手机时用它，非 debuggable 的正式包也能靠它恢复数据。 */
    private val externalFile: File?
        get() = appContext.getExternalFilesDir(null)?.let { File(it, "schedule.txt") }

    var courses: List<Course> = emptyList()
        private set

    var settings: AppSettings = AppSettings()
        private set

    init {
        load()
    }

    private fun load() {
        // 刚装完/刚换包时内部数据是空的，但外部目录里可能留着上次的备份 → 自动恢复
        if (!file.exists()) {
            externalFile?.takeIf { it.exists() }?.let { backup ->
                runCatching { file.writeText(backup.readText()) }
            }
        }
        if (!file.exists()) return
        val (c, s) = runCatching { Codec.decode(file.readText()) }.getOrNull() ?: return
        courses = c
        settings = s
    }

    fun update(courses: List<Course>, settings: AppSettings) {
        this.courses = courses
        this.settings = settings
        runCatching { file.writeText(Codec.encode(courses, settings)) }
    }

    /** 导出一份到外部目录，返回路径；失败返回 null。 */
    fun exportToExternal(): String? {
        val target = externalFile ?: return null
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeText(Codec.encode(courses, settings))
            target.absolutePath
        }.getOrNull()
    }

    fun externalBackupPath(): String? = externalFile?.takeIf { it.exists() }?.absolutePath

    /** 从外部目录恢复。 */
    fun importFromExternal(): Boolean {
        val source = externalFile?.takeIf { it.exists() } ?: return false
        val parsed = runCatching { Codec.decode(source.readText()) }.getOrNull() ?: return false
        update(parsed.first, parsed.second)
        return true
    }

    fun upsert(course: Course): List<Course> {
        val next = courses.toMutableList()
        val idx = next.indexOfFirst { it.id == course.id }
        if (idx >= 0) next[idx] = course else next += course
        update(next, settings)
        return next
    }

    fun delete(courseId: String): List<Course> {
        val next = courses.filterNot { it.id == courseId }
        update(next, settings)
        return next
    }

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }
}
