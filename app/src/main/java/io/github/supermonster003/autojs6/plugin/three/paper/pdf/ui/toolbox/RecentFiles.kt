package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder

/**
 * One recent file (roadmap P4.1, appendix D Q106 = B): a [Local] file the plugin's browser picked or the toolbox wrote,
 * or a [Document] picked with the system file picker, kept by its `content:` uri under a persistable read grant.
 * zh-CN: 一个最近文件 (路线图 P4.1, 附录 D Q106 = B): 插件自带浏览器选中或工具箱写出的本地文件 [Local], 或用系统文件选择器选取的文档
 * [Document], 后者按 `content:` uri 保存, 依靠持久读取授权.
 */
internal sealed interface RecentEntry {

    /** The identity that drops duplicates. zh-CN: 去重用的标识. */
    val key: String

    /** The name shown in the list. zh-CN: 列表中显示的名称. */
    val name: String

    data class Local(val path: String) : RecentEntry {
        override val key: String get() = "file:$path"
        override val name: String get() = file.name
        val file: File get() = File(path)
    }

    data class Document(val uri: String, override val name: String) : RecentEntry {
        override val key: String get() = uri
    }

}

/**
 * The list rules of the recent files, free of Android so the JVM tests cover them: the stored JSON (key `entries`, the
 * paths of the first version under key `paths` are still read), newest first without duplicates, at most a limit, and
 * which persisted grants no entry holds any more.
 * zh-CN: 最近文件的列表规则, 不依赖 Android 以便 JVM 测试覆盖: 存储的 JSON (键 `entries`, 仍读取第一版存于键 `paths` 的路径), 最新的在前且
 * 不重复, 至多若干条, 以及哪些持久授权已没有条目持有.
 */
internal object RecentList {

    private const val FIELD_PATH = "path"

    private const val FIELD_URI = "uri"

    private const val FIELD_NAME = "name"

    /** Reads [entries], or the legacy [paths] while [entries] is absent; broken data reads as empty. zh-CN: 读取 [entries], 没有时读取旧版 [paths]; 损坏的数据读作空列表. */
    fun parse(entries: String?, paths: String?): List<RecentEntry> = runCatching {
        when {
            entries != null -> JSONArray(entries).let { array -> List(array.length()) { array.optJSONObject(it)?.let(::entryOf) }.filterNotNull() }
            paths != null -> JSONArray(paths).let { array -> List(array.length()) { array.optString(it) }.filter { it.isNotEmpty() }.map { RecentEntry.Local(it) } }
            else -> emptyList()
        }
    }.getOrDefault(emptyList()).distinctBy { it.key }

    fun serialize(list: List<RecentEntry>): String = JSONArray().apply {
        list.forEach { entry ->
            put(
                when (entry) {
                    is RecentEntry.Local -> JSONObject().put(FIELD_PATH, entry.path)
                    is RecentEntry.Document -> JSONObject().put(FIELD_URI, entry.uri).put(FIELD_NAME, entry.name)
                },
            )
        }
    }.toString()

    /** [entry] first, an older entry with the same key dropped, at most [max] entries. zh-CN: [entry] 放在最前, 去掉同一标识的旧条目, 至多 [max] 条. */
    fun add(list: List<RecentEntry>, entry: RecentEntry, max: Int): List<RecentEntry> = (listOf(entry) + list.filter { it.key != entry.key }).take(max)

    fun remove(list: List<RecentEntry>, key: String): List<RecentEntry> = list.filter { it.key != key }

    /** The [persisted] uris that no document of [list] holds, to release. zh-CN: [persisted] 中没有任何文档条目持有的 uri, 应当释放. */
    fun unheld(persisted: Collection<String>, list: List<RecentEntry>): List<String> {
        val held = list.filterIsInstance<RecentEntry.Document>().mapTo(HashSet()) { it.uri }
        return persisted.filter { it !in held }
    }

    /** A name for a document stored without one: the last segment of its uri, decoded. zh-CN: 没有保存名称的文档的名称: uri 最后一段解码后的值. */
    fun fallbackName(uri: String): String {
        val segment = uri.substringBefore('?').trimEnd('/').substringAfterLast('/')
        val decoded = runCatching { URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8") }.getOrDefault(segment)
        return decoded.substringAfterLast('/').substringAfterLast(':').ifEmpty { uri }
    }

    private fun entryOf(json: JSONObject): RecentEntry? {
        val uri = json.optString(FIELD_URI)
        if (uri.isNotEmpty()) return RecentEntry.Document(uri, json.optString(FIELD_NAME).ifEmpty { fallbackName(uri) })
        val path = json.optString(FIELD_PATH)
        return if (path.isNotEmpty()) RecentEntry.Local(path) else null
    }

}

/**
 * The files the toolbox opened or wrote, newest first, at most [MAX] (roadmap P4.1, appendix D Q106 = B). Local files
 * that no longer exist are left out when read. Documents picked with the system file picker are kept under the
 * persistable read grant the picker offers; every change releases the grants no entry holds, so the plugin never holds
 * more than [MAX] (the system allows 128, 512 from Android 11). A document is checked when opened: when the grant is gone
 * or the provider no longer finds it, the entry is removed. One-time uris that other apps pass with VIEW or SEND stay
 * out of the list.
 * zh-CN: 工具箱打开或写出的文件, 最新的在前, 至多 [MAX] 个 (路线图 P4.1, 附录 D Q106 = B). 读取时略去已不存在的本地文件. 用系统文件选择器
 * 选取的文档凭选择器给出的可持久读取授权保留; 每次改动都释放没有条目持有的授权, 因此插件持有的授权从不超过 [MAX] 个 (系统上限 128,
 * Android 11 起 512). 文档在打开时检查: 授权已失效或提供方找不到该文档时移除条目. 其他应用经 VIEW 或 SEND 传入的一次性 uri 不进列表.
 */
internal object RecentFiles {

    const val FILE = "recent-files"

    private const val KEY_ENTRIES = "entries"

    /** The plain path array of the first version, migrated on the next change. zh-CN: 第一版的路径数组, 下次改动时迁移. */
    private const val KEY_PATHS = "paths"

    const val MAX = 20

    /** Newest first; local files that no longer exist are left out, documents are checked when opened. zh-CN: 最新的在前; 略去已不存在的本地文件, 文档在打开时检查. */
    fun read(context: Context): List<RecentEntry> {
        val prefs = prefs(context)
        return RecentList.parse(prefs.getString(KEY_ENTRIES, null), prefs.getString(KEY_PATHS, null))
            .filter { it !is RecentEntry.Local || it.file.isFile }
    }

    @Synchronized
    fun add(context: Context, file: File) = update(context) { RecentList.add(it, RecentEntry.Local(file.absolutePath), MAX) }

    @Synchronized
    fun remove(context: Context, entry: RecentEntry) = update(context) { RecentList.remove(it, entry.key) }

    /** Empties the list and releases every grant it held. zh-CN: 清空列表并释放它持有的全部授权. */
    @Synchronized
    fun clear(context: Context) = update(context) { emptyList() }

    /**
     * Records a document the system file picker returned: takes the read grant when the result offers it as persistable
     * ([Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION] in [flags]) and puts the document first. Answers the entry, or null
     * when the grant cannot be kept (the document then opens this once only). Queries the provider, so call it off the
     * main thread.
     * zh-CN: 记录系统文件选择器返回的文档: 结果把读取授权标为可持久 ([flags] 含 [Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION]) 时持久化该授权,
     * 并把文档放在最前. 返回条目; 授权无法保留时返回 null (该文档只在本次打开). 会查询提供方, 因此不要在主线程调用.
     */
    fun adopt(context: Context, uri: Uri, flags: Int): RecentEntry.Document? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return null
        val name = nameOf(context, uri) ?: RecentList.fallbackName(uri.toString())
        synchronized(this) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                return null
            }
            val entry = RecentEntry.Document(uri.toString(), name)
            update(context) { RecentList.add(it, entry, MAX) }
            return entry
        }
    }

    /**
     * Checks a document before it opens: the persisted read grant is still there and the provider still finds the
     * document. Answers the entry, moved first with its current name, or null after removing it. Queries the provider,
     * so call it off the main thread.
     * zh-CN: 在打开文档之前检查: 持久读取授权仍在, 且提供方仍能找到该文档. 返回移到最前并更新名称的条目; 否则移除条目并返回 null. 会查询
     * 提供方, 因此不要在主线程调用.
     */
    fun reopen(context: Context, entry: RecentEntry.Document): RecentEntry.Document? {
        val uri = entry.uri.toUri()
        val name = if (holdsGrant(context, uri)) nameOf(context, uri) else null
        synchronized(this) {
            if (name == null) {
                update(context) { RecentList.remove(it, entry.key) }
                return null
            }
            val fresh = entry.copy(name = name)
            update(context) { RecentList.add(it, fresh, MAX) }
            return fresh
        }
    }

    /** Whether the plugin holds a persisted read grant for [uri]. zh-CN: 插件是否持有 [uri] 的持久读取授权. */
    fun holdsGrant(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }.getOrDefault(false)

    /** Writes the changed list, then releases the grants no entry holds. Callers hold the lock. zh-CN: 写入改动后的列表, 再释放没有条目持有的授权. 调用方持有锁. */
    private fun update(context: Context, change: (List<RecentEntry>) -> List<RecentEntry>) {
        val next = change(read(context))
        prefs(context).edit {
            putString(KEY_ENTRIES, RecentList.serialize(next))
            remove(KEY_PATHS)
        }
        release(context, next)
    }

    private fun release(context: Context, list: List<RecentEntry>) {
        val resolver = context.contentResolver
        val persisted = runCatching { resolver.persistedUriPermissions }.getOrDefault(emptyList())
        val unheld = RecentList.unheld(persisted.map { it.uri.toString() }, list).toSet()
        persisted.filter { it.uri.toString() in unheld }.forEach { permission ->
            val modes = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            runCatching { resolver.releasePersistableUriPermission(permission.uri, modes) }
        }
    }

    /**
     * The display name of a document, or null when the provider does not find it (no row, a refusal or a failure).
     * zh-CN: 文档的显示名称; 提供方找不到它 (没有结果行, 拒绝访问或出错) 时为 null.
     */
    private fun nameOf(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)?.takeIf { it.isNotBlank() } ?: RecentList.fallbackName(uri.toString())
        }
    }.getOrNull()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

}

/** A document the system file picker returned, with the grant flags of the result. zh-CN: 系统文件选择器返回的文档, 附结果中的授权标志. */
internal data class PickedDocument(val uri: Uri, val flags: Int)

/**
 * [ActivityResultContracts.OpenDocument] that keeps the grant flags of the result, which tell whether the read grant
 * can be kept (appendix D Q106 = B).
 * zh-CN: 保留结果中授权标志的 [ActivityResultContracts.OpenDocument], 据此判断读取授权能否保留 (附录 D Q106 = B).
 */
internal class OpenPersistableDocument : ActivityResultContract<Array<String>, PickedDocument?>() {

    private val open = ActivityResultContracts.OpenDocument()

    override fun createIntent(context: Context, input: Array<String>): Intent = open.createIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): PickedDocument? {
        val uri = intent?.takeIf { resultCode == Activity.RESULT_OK }?.data ?: return null
        return PickedDocument(uri, intent.flags)
    }

}
