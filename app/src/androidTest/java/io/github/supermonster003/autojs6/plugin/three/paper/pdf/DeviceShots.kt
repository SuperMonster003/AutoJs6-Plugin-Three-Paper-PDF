package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.graphics.Bitmap
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Screenshots for the hand-over of a device run, taken only when the instrumentation argument `paperShots` is `1`
 * (`device_instrumented_run.py --shots DIR` passes it and pulls the files): [take] saves the screen as
 * `<name>-api<level>.png` under `files/shots/` of the plugin, so a run without the argument takes none.
 * zh-CN: 设备运行交接用的截图, 只有仪器参数 `paperShots` 为 `1` 时才截取 (`device_instrumented_run.py --shots DIR` 传入该参数并拉回文件):
 * [take] 把屏幕存为插件 `files/shots/` 下的 `<name>-api<级别>.png`, 不带该参数的运行不截图.
 */
internal object DeviceShots {

    /** Whether this run takes screenshots; tests skip their preparation for a shot otherwise. zh-CN: 本次运行是否截图; 否则测试跳过为截图所做的准备. */
    val enabled: Boolean by lazy { InstrumentationRegistry.getArguments().getString("paperShots") == "1" }

    /** The folder the shots go to. zh-CN: 截图所在的目录. */
    val folder: File get() = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shots")

    fun take(name: String) {
        if (!enabled) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Let animations of the bars end. zh-CN: 等工具栏的动画结束.
        Thread.sleep(SETTLE_MILLIS)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            folder.mkdirs()
            File(folder, "$name-api${Build.VERSION.SDK_INT}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private const val SETTLE_MILLIS = 700L

}
