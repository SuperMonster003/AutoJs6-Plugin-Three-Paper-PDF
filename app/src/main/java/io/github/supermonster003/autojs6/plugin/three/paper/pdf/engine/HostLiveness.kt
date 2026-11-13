package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants

/**
 * Whether the process of a caller still exists (roadmap D44, as the 3-Tally Sheet watchdog): `kill(pid, 0)` sends no
 * signal and only `ESRCH` means the process is gone; `EPERM` (a live process of another uid) counts as alive. Callers
 * in the plugin process (the file manager tools, the instrumentation tests) are always alive.
 * zh-CN: 调用方进程是否仍存在 (路线图 D44, 同 3-Tally Sheet 的看门狗): `kill(pid, 0)` 不发送信号, 只有 `ESRCH` 表示进程已不存在;
 * `EPERM` (其他 uid 的存活进程) 视为存活. 插件进程内的调用方 (文件管理器工具, 仪器测试) 始终存活.
 */
object HostLiveness {

    fun isAlive(host: HostKey): Boolean = host.pid == Process.myPid() || processExists(host.pid)

    fun processExists(pid: Int): Boolean = try {
        Os.kill(pid, 0)
        true
    } catch (e: ErrnoException) {
        e.errno != OsConstants.ESRCH
    }

}
