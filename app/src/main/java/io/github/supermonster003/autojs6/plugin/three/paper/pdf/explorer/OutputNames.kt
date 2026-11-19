package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

/**
 * Names of the tool results (appendix D Q74): one file next to the source named `<name>_merged.pdf`,
 * `<name>_encrypted.pdf`, `<name>_decrypted.pdf` or `<name>_text.txt` (the toolbox adds `_edited`, `_watermarked`,
 * `_numbered`, `_compressed`, `_filled` and `_properties`, appendix D Q101 and Q261), and one folder for tools giving many files,
 * `<name>_split/` with `<name>-01.pdf` ... and `<name>_images/` with `<name>-p01.png` ... (numbers padded to the
 * widest). `<name>` is the source name without `.pdf`, with characters the file manager refuses replaced and cut to
 * [MAX_STEM_BYTES] bytes of UTF-8, so every name stays below the 255 bytes of a file name. The host adds ` (1)` and so
 * on when the name is taken.
 * zh-CN: 工具结果的名称 (附录 D Q74): 在源文件旁生成一个文件, 名为 `<名称>_merged.pdf`, `<名称>_encrypted.pdf`,
 * `<名称>_decrypted.pdf` 或 `<名称>_text.txt` (工具箱另有 `_edited`, `_watermarked`, `_numbered`, `_compressed`, `_filled` 与
 * `_properties`, 附录 D Q101 与 Q261); 生成多个文件的工具生成一个文件夹, 即 `<名称>_split/` 内含 `<名称>-01.pdf` ...,
 * 以及 `<名称>_images/` 内含 `<名称>-p01.png` ... (序号按最大值补零). `<名称>` 是去掉 `.pdf` 的源文件名, 文件管理器拒绝的字符被
 * 替换, 并截断到 [MAX_STEM_BYTES] 字节的 UTF-8, 因此每个名称都短于文件名的 255 字节. 名称已被占用时由宿主追加 ` (1)` 等.
 */
internal object OutputNames {

    const val MAX_STEM_BYTES = 200

    private const val FALLBACK_STEM = "document"

    private const val PDF_SUFFIX = ".pdf"

    fun merged(firstName: String) = "${stem(firstName)}_merged.pdf"

    fun encrypted(name: String) = "${stem(name)}_encrypted.pdf"

    fun decrypted(name: String) = "${stem(name)}_decrypted.pdf"

    fun text(name: String) = "${stem(name)}_text.txt"

    /**
     * Images to PDF (roadmap P4.2, appendix D Q101): the name of the first image with `.pdf` for its extension.
     * zh-CN: 图片转 PDF (路线图 P4.2, 附录 D Q101): 第一张图片的名称, 扩展名换成 `.pdf`.
     */
    fun imagesPdf(firstImageName: String): String {
        val trimmed = firstImageName.trim()
        val dot = trimmed.lastIndexOf('.')
        return "${stem(if (dot > 0) trimmed.substring(0, dot) else trimmed)}.pdf"
    }

    /** Organized pages (roadmap P4.2, appendix D Q101). zh-CN: 整理后的页面 (路线图 P4.2, 附录 D Q101). */
    fun edited(name: String) = "${stem(name)}_edited.pdf"

    /** A watermark added (roadmap P6.12, appendix D Q261). zh-CN: 加了水印 (路线图 P6.12, 附录 D Q261). */
    fun watermarked(name: String) = "${stem(name)}_watermarked.pdf"

    /** Page numbers, headers or footers added (roadmap P6.12, appendix D Q261). zh-CN: 加了页码, 页眉或页脚 (路线图 P6.12, 附录 D Q261). */
    fun numbered(name: String) = "${stem(name)}_numbered.pdf"

    /** Compressed (roadmap P6.12, appendix D Q261). zh-CN: 压缩后 (路线图 P6.12, 附录 D Q261). */
    fun compressed(name: String) = "${stem(name)}_compressed.pdf"

    /** The form filled (roadmap P6.12, appendix D Q261). zh-CN: 填写了表单 (路线图 P6.12, 附录 D Q261). */
    fun filled(name: String) = "${stem(name)}_filled.pdf"

    /** The document properties or the outline changed (roadmap P6.12, appendix D Q261). zh-CN: 改了文档属性或书签 (路线图 P6.12, 附录 D Q261). */
    fun properties(name: String) = "${stem(name)}_properties.pdf"

    fun splitFolder(name: String) = "${stem(name)}_split"

    /** Part [index] (from 1) of [count]. zh-CN: [count] 个部分中的第 [index] 个 (从 1 起). */
    fun splitPart(name: String, index: Int, count: Int) = "${stem(name)}-${padded(index, count)}.pdf"

    fun imagesFolder(name: String) = "${stem(name)}_images"

    /** The image of [page] in a document of [pageCount] pages. zh-CN: [pageCount] 页文档中第 [page] 页的图片. */
    fun imagePage(name: String, page: Int, pageCount: Int, extension: String) = "${stem(name)}-p${padded(page, pageCount)}.$extension"

    /**
     * [name] without `.pdf`, its separators, control characters and surrounding blanks replaced or removed, at most
     * [MAX_STEM_BYTES] bytes of UTF-8 (never cut inside a surrogate pair); `document` when nothing is left.
     * zh-CN: 去掉 `.pdf` 的 [name], 其分隔符, 控制字符与首尾空白被替换或去除, 至多 [MAX_STEM_BYTES] 字节 UTF-8 (不会截在代理对中间);
     * 什么都不剩时为 `document`.
     */
    fun stem(name: String): String {
        val trimmed = name.trim()
        val base = if (trimmed.length > PDF_SUFFIX.length && trimmed.endsWith(PDF_SUFFIX, ignoreCase = true)) trimmed.dropLast(PDF_SUFFIX.length) else trimmed
        val cleaned = buildString(base.length) {
            base.forEach { char -> append(if (char == '/' || char == '\\' || char.isISOControl()) '_' else char) }
        }.trim()
        val cut = cutToBytes(cleaned, MAX_STEM_BYTES).trimEnd()
        return cut.takeUnless { it.isEmpty() || it.all { char -> char == '.' } } ?: FALLBACK_STEM
    }

    private fun padded(number: Int, of: Int): String = number.toString().padStart(maxOf(2, of.toString().length), '0')

    private fun cutToBytes(text: String, maxBytes: Int): String {
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val size = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (bytes + size > maxBytes) break
            bytes += size
            index += Character.charCount(codePoint)
        }
        return text.substring(0, index)
    }

}
