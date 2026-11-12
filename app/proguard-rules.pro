# The host contract classes (PluginInfo and the AIDL stubs) cross the process boundary by name.
-keep class org.autojs.plugin.** { *; }
-dontwarn kotlinx.parcelize.Parcelize

# PDFBox-Android (roadmap D26, Q1): JPEG 2000 support is an optional dependency (jp2-android) the plugin does not ship,
# so its decoder and encoder are referenced but absent; PDFBox reports JPXDecode images as unsupported at run time.
# zh-CN: PDFBox-Android (路线图 D26, Q1): JPEG 2000 支持是插件不打包的可选依赖 (jp2-android), 其解码器与编码器只被引用而不存在;
# 运行时 PDFBox 把 JPXDecode 图片报告为不支持.
-dontwarn com.gemalto.jp2.**

# Bouncy Castle (roadmap D12, D34): the JCA provider resolves its algorithm classes by name, so R8 must keep the provider
# packages PDFBox reaches for public key and AES-256 encryption; roadmap P2.6 narrows this after the provider spike (Q4).
# zh-CN: Bouncy Castle (路线图 D12, D34): JCA provider 按类名解析算法类, R8 须保留 PDFBox 公钥与 AES-256 加密会用到的 provider 包;
# 路线图 P2.6 在 provider spike (Q4) 之后再收窄.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn org.bouncycastle.**
