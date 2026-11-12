# The host contract classes (PluginInfo and the AIDL stubs) cross the process boundary by name.
-keep class org.autojs.plugin.** { *; }
-dontwarn kotlinx.parcelize.Parcelize

# fromHtml (roadmap P2.5, Q5 (a)): the shell class subclasses the hidden print callbacks from inside android.print, so it
# must keep its package and names. zh-CN: fromHtml (路线图 P2.5, Q5 (a)): 壳类在 android.print 包内继承隐藏的打印回调, 必须
# 保留包名与类名.
-keep class android.print.PaperPrintCallbacks** { *; }

# PDFBox-Android (roadmap D26, Q1): JPEG 2000 support is an optional dependency (jp2-android) the plugin does not ship,
# so its decoder and encoder are referenced but absent; PDFBox reports JPXDecode images as unsupported at run time.
# zh-CN: PDFBox-Android (路线图 D26, Q1): JPEG 2000 支持是插件不打包的可选依赖 (jp2-android), 其解码器与编码器只被引用而不存在;
# 运行时 PDFBox 把 JPXDecode 图片报告为不支持.
-dontwarn com.gemalto.jp2.**

# Bouncy Castle (roadmap D12, D34, P2.6): password encryption (AES-128 / AES-256) uses the PDFBox standard security
# handler with the platform JCA providers only, and no provider is registered (appendix D Q4 (c)). Only the certificate
# handler (Adobe.PubSec, outside the contract) reaches Bouncy Castle, so R8 keeps just what it references; a password
# given for such a document is refused before Bouncy Castle is touched.
# zh-CN: Bouncy Castle (路线图 D12, D34, P2.6): 密码加密 (AES-128 / AES-256) 使用 PDFBox 标准安全处理器, 只用平台 JCA provider,
# 不注册任何 provider (附录 D Q4 (c)). 只有证书加密处理器 (Adobe.PubSec, 不在契约内) 会用到 Bouncy Castle, 因此 R8 只保留它引用的
# 部分; 对这类文档给出密码时, 在触及 Bouncy Castle 之前就被拒绝.
-dontwarn org.bouncycastle.**
