package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import org.autojs.plugin.pdf.api.PdfContract

/**
 * Bundle side of [PdfValues] and [PdfAnswers]: reads request Bundles, writes answer maps as Bundles and measures
 * Bundles as parcels (protocol "Conventions": requests above `MAX_OPTIONS_BYTES` fail with `LIMIT_EXCEEDED`).
 * zh-CN: [PdfValues] 与 [PdfAnswers] 的 Bundle 一侧: 读取请求 Bundle, 把应答 Map 写成 Bundle, 并按 parcel 计量 Bundle (协议
 * "Conventions" 一节: 超过 `MAX_OPTIONS_BYTES` 的请求报 `LIMIT_EXCEEDED`).
 */
object PdfBundles {

    fun values(bundle: Bundle?): PdfValues = BundleValues(bundle)

    fun options(bundle: Bundle?): PdfOptions = PdfOptions(values(bundle))

    /**
     * The Bundle of an answer map. Supported values: Int, Long, Boolean, Double, Float, String, their primitive arrays,
     * String arrays, Bundles, other Parcelables (the region of `render`) and nested maps (as Bundles); anything else is a
     * programming error.
     * zh-CN: 应答 Map 对应的 Bundle. 支持的值: Int, Long, Boolean, Double, Float, String 及其基本类型数组, String 数组, Bundle, 其他
     * Parcelable (`render` 的区域) 与嵌套 Map (写成 Bundle); 其他类型属于编程错误.
     */
    fun toBundle(map: Map<String, Any?>): Bundle = Bundle(map.size).apply {
        for ((key, value) in map) {
            when (value) {
                null -> Unit
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Boolean -> putBoolean(key, value)
                is Double -> putDouble(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
                is IntArray -> putIntArray(key, value)
                is LongArray -> putLongArray(key, value)
                is FloatArray -> putFloatArray(key, value)
                is DoubleArray -> putDoubleArray(key, value)
                is BooleanArray -> putBooleanArray(key, value)
                is Bundle -> putBundle(key, value)
                // The SharedMemory region of `render`. zh-CN: `render` 的 SharedMemory 区域.
                is Parcelable -> putParcelable(key, value)
                is Map<*, *> -> putBundle(key, toBundle(value.entries.associate { (k, v) -> k.toString() to v }))
                is Array<*> -> {
                    require(value.isArrayOf<String>()) { "$key: only String arrays are supported" }
                    @Suppress("UNCHECKED_CAST")
                    putStringArray(key, value as Array<String?>)
                }
                else -> throw IllegalArgumentException("$key: unsupported answer value ${value::class.java.name}")
            }
        }
    }

    /** Bytes of [bundle] written into a parcel. zh-CN: [bundle] 写入 parcel 后的字节数. */
    fun parcelSize(bundle: Bundle): Int {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    /**
     * Refuses a request or options Bundle above `MAX_OPTIONS_BYTES` with `LIMIT_EXCEEDED`.
     * zh-CN: 以 `LIMIT_EXCEEDED` 拒绝超过 `MAX_OPTIONS_BYTES` 的请求或选项 Bundle.
     */
    fun checkRequestSize(bundle: Bundle?) {
        bundle ?: return
        val size = parcelSize(bundle)
        if (size > PdfContract.MAX_OPTIONS_BYTES) {
            throw PdfFailure.limitExceeded("request of $size bytes exceeds ${PdfContract.MAX_OPTIONS_BYTES}")
        }
    }

    private class BundleValues(private val bundle: Bundle?) : PdfValues {

        override val keys: Set<String> get() = bundle?.keySet().orEmpty()

        // The typed getters need the expected type up front; validation reads the raw value to report a wrong type.
        // zh-CN: 类型化的读取方法要求事先知道类型; 校验读取原始值, 以便报告类型错误.
        @Suppress("DEPRECATION")
        override fun get(key: String): Any? = bundle?.get(key)

    }

}
