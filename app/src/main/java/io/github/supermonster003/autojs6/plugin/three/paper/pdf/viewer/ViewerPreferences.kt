package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

/**
 * The viewer settings of the settings screen (roadmap P6.15, E4 feedback F2, appendix D Q343 (a)): whether full-screen
 * reading also hides the page number and the system bars. Both are on by default; turning one off keeps that element
 * as it was before full-screen reading. They live in the viewer's own preferences file, next to the night mode.
 * zh-CN: 设置页的查看器设置 (路线图 P6.15, E4 反馈 F2, 附录 D Q343 (a)): 全屏阅读时是否同时隐藏页码与系统栏. 两项默认开启; 关闭其中
 * 一项时, 该元素保持全屏阅读之前的样子. 它们保存在查看器自己的偏好文件中, 与夜间模式放在一起.
 */
internal object ViewerPreferences {

    /** The preferences file of the viewer. zh-CN: 查看器的偏好文件. */
    const val FILE = ViewerViewModel.PREFERENCES

    const val KEY_HIDE_PAGE_INDICATOR = "fullscreen_hide_page_indicator"

    const val KEY_HIDE_SYSTEM_BARS = "fullscreen_hide_system_bars"

    /** Both settings start on. zh-CN: 两项设置默认开启. */
    const val DEFAULT_HIDE = true

    fun of(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun fullscreen(preferences: SharedPreferences): Fullscreen = Fullscreen(
        hidePageIndicator = preferences.getBoolean(KEY_HIDE_PAGE_INDICATOR, DEFAULT_HIDE),
        hideSystemBars = preferences.getBoolean(KEY_HIDE_SYSTEM_BARS, DEFAULT_HIDE),
    )

    /** Saves the page number setting; false when it could not be written. zh-CN: 保存页码设置; 无法写入时返回 false. */
    fun setHidePageIndicator(preferences: SharedPreferences, hide: Boolean): Boolean = put(preferences, KEY_HIDE_PAGE_INDICATOR, hide)

    /** Saves the system bars setting; false when it could not be written. zh-CN: 保存系统栏设置; 无法写入时返回 false. */
    fun setHideSystemBars(preferences: SharedPreferences, hide: Boolean): Boolean = put(preferences, KEY_HIDE_SYSTEM_BARS, hide)

    // The settings screen reports a failed write, so the result of commit is needed. zh-CN: 设置页要报告写入失败, 因此需要 commit 的结果.
    @SuppressLint("UseKtx", "ApplySharedPref")
    private fun put(preferences: SharedPreferences, key: String, value: Boolean): Boolean = preferences.edit().putBoolean(key, value).commit()

    /**
     * What full-screen reading hides. The viewer is full screen while a tap has hid its bars and no search, outline,
     * jump dialog or password prompt is open.
     * zh-CN: 全屏阅读时隐藏的内容. 单击隐藏了工具栏, 且没有打开搜索, 大纲, 跳页对话框或密码提示时, 查看器处于全屏.
     */
    data class Fullscreen(val hidePageIndicator: Boolean, val hideSystemBars: Boolean) {

        fun pageIndicatorVisible(fullscreen: Boolean): Boolean = !(fullscreen && hidePageIndicator)

        fun systemBarsHidden(fullscreen: Boolean): Boolean = fullscreen && hideSystemBars

    }

}
