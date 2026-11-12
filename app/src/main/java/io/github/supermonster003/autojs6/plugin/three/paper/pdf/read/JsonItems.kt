package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import org.json.JSONObject

/**
 * The `items` value of a list answer: a JSON array of objects whose fields are the `FIELD_*` names (protocol "Methods").
 * Appending stops at [budgetChars] so the answer stays below `MAX_ANSWER_BYTES` (a Parcel stores two bytes per
 * character); the caller answers `hasMore` when [add] refuses an item.
 * zh-CN: 列表应答的 `items` 值: 字段名为 `FIELD_*` 的对象组成的 JSON 数组 (协议 "Methods" 一节). 追加到 [budgetChars] 为止, 使应答
 * 保持在 `MAX_ANSWER_BYTES` 之下 (Parcel 每个字符占两个字节); [add] 拒绝条目时, 调用方在应答中给出 `hasMore`.
 */
internal class JsonItems(private val budgetChars: Int = BUDGET_CHARS) {

    private val builder = StringBuilder("[")

    var count = 0
        private set

    /** Appends [item]; false, appending nothing, when it would pass the budget. zh-CN: 追加 [item]; 会超出预算时不追加并返回 false. */
    fun add(item: JSONObject): Boolean {
        val text = item.toString()
        val separator = if (count > 0) 1 else 0
        if (builder.length + separator + text.length + 1 > budgetChars) return false
        if (separator > 0) builder.append(',')
        builder.append(text)
        count++
        return true
    }

    override fun toString(): String = "$builder]"

    companion object {

        /**
         * Characters of one `items` value: 640,000 bytes in a Parcel, leaving room for the other keys of the answer.
         * zh-CN: 一个 `items` 值的字符数: 在 Parcel 中占 640,000 字节, 为应答的其他键留出余量.
         */
        const val BUDGET_CHARS = 320_000

    }

}
