package android.print

/**
 * The shell class of `fromHtml` (roadmap D8, appendix D Q5 (a)): the constructors of [PrintDocumentAdapter.LayoutResultCallback]
 * and [PrintDocumentAdapter.WriteResultCallback] are hidden (`@hide public` on the device, package-private in the SDK
 * stubs), so the callbacks the plugin hands to the print adapter of a WebView are subclassed here, in their own package.
 * [isAvailable] tells whether this device lets the plugin reach them (hidden API policy); when it does not,
 * `generate.html` is not declared and `fromHtml` ends with `UNSUPPORTED`.
 * zh-CN: `fromHtml` 的同包壳类 (路线图 D8, 附录 D Q5 (a)): [PrintDocumentAdapter.LayoutResultCallback] 与
 * [PrintDocumentAdapter.WriteResultCallback] 的构造器是隐藏的 (设备上为 `@hide public`, SDK 桩中为包私有), 因此插件交给 WebView
 * 打印适配器的回调在其所属的包中继承. [isAvailable] 表示本设备 (隐藏 API 策略) 是否允许插件访问它们; 不允许时不声明
 * `generate.html`, `fromHtml` 以 `UNSUPPORTED` 结束.
 */
object PaperPrintCallbacks {

    /** Receives the layout result. zh-CN: 接收排版结果. */
    interface LayoutListener {
        fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean)
        fun onLayoutFailed(error: CharSequence?)
        fun onLayoutCancelled()
    }

    /** Receives the write result. zh-CN: 接收写出结果. */
    interface WriteListener {
        fun onWriteFinished(pages: Array<out PageRange>)
        fun onWriteFailed(error: CharSequence?)
        fun onWriteCancelled()
    }

    /**
     * Whether both hidden constructors are reachable; reflection answers `NoSuchMethodException` when the hidden API
     * policy blocks them. Computed once.
     * zh-CN: 两个隐藏构造器是否可访问; 隐藏 API 策略阻止时反射报 `NoSuchMethodException`. 只计算一次.
     */
    val isAvailable: Boolean by lazy {
        runCatching {
            PrintDocumentAdapter.LayoutResultCallback::class.java.getDeclaredConstructor()
            PrintDocumentAdapter.WriteResultCallback::class.java.getDeclaredConstructor()
            true
        }.getOrDefault(false)
    }

    fun layout(listener: LayoutListener): PrintDocumentAdapter.LayoutResultCallback = object : PrintDocumentAdapter.LayoutResultCallback() {
        override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) = listener.onLayoutFinished(info, changed)
        override fun onLayoutFailed(error: CharSequence?) = listener.onLayoutFailed(error)
        override fun onLayoutCancelled() = listener.onLayoutCancelled()
    }

    fun write(listener: WriteListener): PrintDocumentAdapter.WriteResultCallback = object : PrintDocumentAdapter.WriteResultCallback() {
        override fun onWriteFinished(pages: Array<out PageRange>) = listener.onWriteFinished(pages)
        override fun onWriteFailed(error: CharSequence?) = listener.onWriteFailed(error)
        override fun onWriteCancelled() = listener.onWriteCancelled()
    }

}
