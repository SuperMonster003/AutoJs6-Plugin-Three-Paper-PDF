package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

/**
 * Read-only view of the keys of one request Bundle. Validation ([PdfOptions]) and answers ([PdfAnswers]) work on this
 * view and on plain maps, so the JVM tests cover them without `android.os.Bundle`; [PdfBundles] adapts real Bundles.
 * zh-CN: 一个请求 Bundle 的只读视图. 校验 ([PdfOptions]) 与应答 ([PdfAnswers]) 基于该视图与普通 Map, 因此 JVM 测试不依赖
 * `android.os.Bundle` 即可覆盖; [PdfBundles] 负责适配真实 Bundle.
 */
interface PdfValues {

    val keys: Set<String>

    operator fun get(key: String): Any?

    fun contains(key: String): Boolean = key in keys

}

/**
 * [PdfValues] over a map (answers before they become Bundles, JVM tests).
 * zh-CN: 基于 Map 的 [PdfValues] (转成 Bundle 之前的应答, JVM 测试).
 */
class MapValues(private val map: Map<String, Any?>) : PdfValues {

    override val keys: Set<String> get() = map.keys

    override fun get(key: String): Any? = map[key]

}
