package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ThreePaperPdfViewerActivity
import java.io.File
import java.util.UUID

/**
 * "Open in AutoJs6" (roadmap P6.13, D51): hands a [GeneratedScript] to the host's `CREATE_SCRIPT` entry, which opens it
 * in the editor as a new, unsaved file named after [GeneratedScript.fileName] (the `.node.js` and `.bun.js` suffixes and
 * the `"nodejs";` / `"bun";` first lines pick the engine). Code within the inline limits travels in the Intent; larger
 * code (up to [MAX_CODE_URI_BYTES]) is written to `cache/create-script/` and sent as a content URI of the plugin's
 * FileProvider with a read grant. The entry needs the host build of [ThreePaperPdfContract.REQUIRED_HOST_VERSION] or
 * later; older or missing hosts get no button, nor does a style the device cannot run ([supports]: Bun needs Android
 * 13), only copy and save.
 *
 * The literals are those of the host's `AutoJs6CreateScriptContract` (common-plugin-api, host commits `5fc200c3ab` and
 * `271949817a`), frozen by the host and repeated here as 3-Slate Studio does (its `codegen/AutoJs6Scripts.kt`).
 * zh-CN: "在 AutoJs6 中打开" (路线图 P6.13, D51): 把 [GeneratedScript] 交给宿主的 `CREATE_SCRIPT` 入口, 宿主在编辑器中以 [GeneratedScript.fileName]
 * 命名的未保存新文件打开 (`.node.js` 与 `.bun.js` 后缀以及首行的 `"nodejs";` / `"bun";` 决定引擎). 不超过内联上限的代码随 Intent
 * 发送; 更大的代码 (至多 [MAX_CODE_URI_BYTES]) 写入 `cache/create-script/`, 以插件 FileProvider 的 content URI 加读取授权发送. 入口需要
 * [ThreePaperPdfContract.REQUIRED_HOST_VERSION] 或更新的宿主构建; 宿主过旧或未安装时不显示按钮, 设备无法运行的写法 ([supports]: Bun
 * 需要 Android 13) 也不显示, 只提供复制与保存.
 *
 * 字面值取自宿主的 `AutoJs6CreateScriptContract` (common-plugin-api, 宿主提交 `5fc200c3ab` 与 `271949817a`), 已由宿主冻结, 与 3-Slate
 * Studio 一样在此重复 (其 `codegen/AutoJs6Scripts.kt`).
 */
internal object AutoJs6Scripts {

    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val ACTION_CREATE_SCRIPT = "org.autojs.autojs.action.CREATE_SCRIPT"
    const val EXTRA_CODE = "code"
    const val EXTRA_CODE_URI = "codeUri"
    const val EXTRA_SUGGESTED_NAME = "suggestedName"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_ERROR = "error"

    /** Inline limits: UTF-8 bytes and UTF-16 chars. zh-CN: 内联上限: UTF-8 字节数与 UTF-16 字符数. */
    const val MAX_CODE_BYTES = 512 * 1024
    const val MAX_CODE_CHARS = 200_000

    /** The host reads at most this many bytes of a URI. zh-CN: 宿主经 URI 至多读取的字节数. */
    const val MAX_CODE_URI_BYTES = 4 * 1024 * 1024

    /** The cache folder exposed by `res/xml/viewer_file_paths.xml`. zh-CN: `res/xml/viewer_file_paths.xml` 暴露的缓存目录. */
    const val CACHE_FOLDER = "create-script"

    /** Files older than this are left from earlier sends and deleted. zh-CN: 早于此时长的文件是此前发送遗留的, 予以删除. */
    const val STALE_MILLIS = 60 * 60 * 1000L

    /** The Android version the Bun runtime needs. zh-CN: Bun 运行时所需的 Android 版本. */
    const val BUN_MIN_SDK = Build.VERSION_CODES.TIRAMISU

    private const val TAG = "PaperAutoJs6Scripts"

    /** How a script travels to the host. zh-CN: 脚本发送给宿主的方式. */
    sealed interface Transport {
        data object Inline : Transport
        data object ContentUri : Transport
        data class TooLarge(val bytes: Int) : Transport
    }

    fun transportOf(code: String): Transport {
        val bytes = code.toByteArray(Charsets.UTF_8).size
        return when {
            bytes <= MAX_CODE_BYTES && code.length <= MAX_CODE_CHARS -> Transport.Inline
            bytes <= MAX_CODE_URI_BYTES -> Transport.ContentUri
            else -> Transport.TooLarge(bytes)
        }
    }

    fun isCompatible(hostVersionCode: Long?): Boolean = hostVersionCode != null && hostVersionCode >= ThreePaperPdfContract.REQUIRED_HOST_VERSION

    /** Whether a device of [sdk] runs scripts of [style]. zh-CN: [sdk] 的设备能否运行 [style] 写法的脚本. */
    fun supports(style: ScriptStyle, sdk: Int = Build.VERSION.SDK_INT): Boolean = style != ScriptStyle.BUN || sdk >= BUN_MIN_SDK

    @Suppress("DEPRECATION")
    fun hostVersionCode(context: Context): Long? = try {
        val info = context.packageManager.getPackageInfo(HOST_PACKAGE_NAME, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * True when the host is installed, new enough and its entry resolves for this plugin.
     * zh-CN: 宿主已安装, 版本足够且入口对本插件可解析时为 true.
     */
    @Suppress("DEPRECATION")
    fun isAvailable(context: Context): Boolean =
        isCompatible(hostVersionCode(context)) && context.packageManager.resolveActivity(baseIntent(), 0) != null

    /** The result of [intentOf]. zh-CN: [intentOf] 的结果. */
    sealed interface Request {
        /** Start [intent] for a result, then call [cleanUp] with [file]. zh-CN: 以等待结果的方式启动 [intent], 之后用 [file] 调用 [cleanUp]. */
        data class Ready(val intent: Intent, val file: File?) : Request
        data class TooLarge(val bytes: Int) : Request
    }

    /**
     * The `CREATE_SCRIPT` Intent of [script]: the code inline, or a content URI with a read grant (written to the cache
     * on the calling thread). [source] is the plugin name the host shows.
     * zh-CN: [script] 的 `CREATE_SCRIPT` Intent: 内联代码, 或带读取授权的 content URI (在调用线程写入缓存). [source] 为宿主显示的插件名.
     */
    fun intentOf(context: Context, script: GeneratedScript, source: String): Request {
        val intent = baseIntent()
            .putExtra(EXTRA_SUGGESTED_NAME, script.fileName)
            .putExtra(EXTRA_SOURCE, source)
        return when (val transport = transportOf(script.code)) {
            Transport.Inline -> Request.Ready(intent.putExtra(EXTRA_CODE, script.code), null)
            Transport.ContentUri -> {
                cleanUp(context, null)
                val folder = File(context.cacheDir, "$CACHE_FOLDER/${UUID.randomUUID()}").apply { mkdirs() }
                val file = File(folder, script.fileName.substringAfterLast('/').ifEmpty { "script.js" })
                file.writeText(script.code, Charsets.UTF_8)
                val uri: Uri = FileProvider.getUriForFile(context, ThreePaperPdfViewerActivity.authorityOf(context), file)
                // The grant covers the data and ClipData URIs, not extras (host protocol "URI mode").
                // zh-CN: 授权只覆盖 data 与 ClipData 中的 URI, 不覆盖 extra (宿主协议 "URI mode").
                intent.putExtra(EXTRA_CODE_URI, uri)
                intent.clipData = ClipData.newRawUri(null, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Request.Ready(intent, file)
            }
            is Transport.TooLarge -> Request.TooLarge(transport.bytes)
        }
    }

    /**
     * Deletes [file] (the host keeps its own copy once it answered) and files of earlier sends older than
     * [STALE_MILLIS].
     * zh-CN: 删除 [file] (宿主应答后已保留自己的副本) 以及此前发送遗留的, 早于 [STALE_MILLIS] 的文件.
     */
    fun cleanUp(context: Context, file: File?) {
        runCatching {
            file?.parentFile?.deleteRecursively()
            val now = System.currentTimeMillis()
            File(context.cacheDir, CACHE_FOLDER).listFiles()?.filter { now - it.lastModified() > STALE_MILLIS }?.forEach { it.deleteRecursively() }
        }.onFailure { Log.w(TAG, "cannot clean the script cache", it) }
    }

    private fun baseIntent(): Intent = Intent(ACTION_CREATE_SCRIPT).setPackage(HOST_PACKAGE_NAME)

}
