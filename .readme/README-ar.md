<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>قراءة ملفات PDF وعرضها ودمجها وتقسيمها وإنشاؤها وتشفيرها, مع عارض مدمج ومداخل في مدير الملفات</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### اللغات (Languages)

******

يدعم README.md الحالي اللغات التالية:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ru.md)
- العربية [ar] # الحالية

******

### مقدمة

******

⁨3-Paper PDF⁩ هي إضافة PDF لتطبيق AutoJs6, وهي أيضا صندوق أدوات وعارض PDF يعملان وحدهما دون AutoJs6. تقرأ معلومات ملف PDF ونصه وفهرسه وصوره المضمنة, وتعرض الصفحات صورا; وتدمج الصفحات وتقسمها وتستخرجها وتحذفها وتعيد ترتيبها وتدوّرها; وتنشئ ملفات PDF من الصور أو النصوص أو HTML, وتشفّر المستندات أو تفك تشفيرها.

تجري المعالجة داخل الإضافة باستخدام `PdfRenderer` في Android ومكتبة PDFBox-Android المكتوبة بلغة Java الخالصة, لذا يبقى مضيف AutoJs6 خفيفا. تستخدم سكربتات AutoJs6 هذه الميزات عبر الوحدة العامة `pdf`; ويفتح AutoJs6 المسارات التي تمررها السكربتات ثم يسلّمها إلى الإضافة, لذا لا تحتاج الإضافة إلى إذن التخزين لمعالجة ملفات السكربتات. وللإضافة عارضها الخاص, ويمكن فتحه من المشغّل أو من مدير ملفات AutoJs6 أو من سكربت (`pdf.view`).

> هذا إصدار معاينة للتطوير: يستطيع مركز الإضافات في AutoJs6 اكتشاف الإضافة, لكن القراءة والعرض وعمليات الصفحات والعارض وصندوق الأدوات والوحدة `pdf` لم تُسلَّم بعد. يصف ما يلي أهداف تصميم الإصدار 1.0.0, وراجع خارطة الطريق لمعرفة التقدم.

******

### أبرز الميزات

******

- القراءة: عدد الصفحات وأحجامها والبيانات الوصفية وحالة التشفير والأذونات; ونص كل صفحة والفهرس (الإشارات المرجعية); والبحث عن النص مع أرقام الصفحات والمواضع; وسرد الصور المضمنة وتصديرها.
- العرض: تحويل الصفحات إلى كائنات `Image` أو ملفات PNG / JPEG / WebP, بدقة 144 dpi افتراضيا, أو حسب الدقة أو نسبة التكبير أو العرض والارتفاع المطلوبين.
- عمليات الصفحات: الدمج (مع الاحتفاظ بالإشارات المرجعية) والتقسيم والاستخراج والحذف وإعادة الترتيب والتدوير; تبدأ أرقام الصفحات من 1 وتُكتب النطاقات بالشكل `'1-3,5,8-'`.
- الإنشاء: من الصور إلى PDF (تُضمَّن صور JPEG كما هي دون إعادة ترميز), ومن النص إلى PDF (بالخط الذي تختاره), ومن HTML إلى PDF (دون تحميل أي عناوين بعيدة).
- التشفير: فتح المستندات المشفرة بكلمة مرور, والتشفير بـ AES-256 مع ضبط الأذونات, وإزالة التشفير.
- العارض: تمرير متواصل وتكبير بإصبعين وصور مصغرة وفهرس وإبراز نتائج البحث وإدخال كلمة المرور ووضع ليلي; ويمكن للسكربتات فتح العارض وتلقي أحداث تغيير الصفحة والإغلاق.
- صندوق الأدوات ومدير الملفات: معالجات للدمج والتقسيم والتحويل إلى صور وتحويل الصور إلى PDF والتشفير / فك التشفير واستخراج النص; ويفتح مدير ملفات AutoJs6 ملفات PDF مباشرة في عارض الإضافة, وتوفر قائمته الإضافية الأدوات نفسها.
- OCR: يستدعي `pdf.text(path, { ocr: true })` محرك OCR في AutoJs6 للصفحات الممسوحة ضوئيا التي لا تحتوي على طبقة نص.

******

### الاستخدام

******

1. نزّل ملف APK من [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) وثبّته. توجد حزمة واحدة لجميع واجهات ABI.
2. لاستخدام الوحدة `pdf` داخل السكربتات, حدّث AutoJs6 إلى الإصدار الداخلي 6641 (6.8.1) أو أحدث وفعّل الإضافة في مركز الإضافات.
3. لاستخدام صندوق الأدوات المدمج, افتح الإضافة وامنحها الوصول إلى جميع الملفات عند الطلب; لا تحتاج عمليات السكربتات إلى هذا الإذن.

> تدعم الإضافة الأجهزة التي تعمل بنظام Android 7.0 (API 24) أو أحدث. لا تستخدم معالجة PDF إلا واجهات النظام ومكتبات Java الخالصة; والمكتبة الأصلية الوحيدة تأتي من Jetpack Compose وهي محاذاة على صفحات ذاكرة بحجم 16 KB, لذا تخدم حزمة واحدة جميع واجهات ABI.

******

### واجهة برمجة النصوص

******

تستخدم السكربتات هذه الميزات عبر الوحدة العامة `pdf` (ويمكن كتابتها أيضا `$pdf`) (مسودة تصميم الإصدار 1.0.0, والمرجع هو توثيق الإصدار المنشور):

```javascript
let info = pdf.info("./book.pdf");
console.log(info.pageCount, info.title);
let pages = pdf.text("./scan.pdf", { ocr: true });
let img = pdf.render("./book.pdf", 1, { dpi: 200 });
images.save(img, "./page-1.png");
img.recycle();

let doc = pdf.open("./book.pdf", { password: "1234" });
let hits = doc.search("AutoJs6");
doc.save("./book-first-3.pdf", { pages: "1-3", rotate: { pages: "2", degrees: 90 } });
doc.close();

pdf.mergeAsync(["./a.pdf", "./b.pdf"], "./ab.pdf", { onProgress: (p) => console.log(p.stage, p.percent) })
    .then((r) => console.log(r.path, r.pageCount));

pdf.fromImages(["./1.jpg", "./2.png"], "./album.pdf", { pageSize: "A4", fit: "contain", margin: 24 });
pdf.fromText(files.read("./notes.txt"), "./notes.pdf", { fontSize: 14 });
pdf.fromHtml("<h1>Report</h1><p>Hello</p>", "./report.pdf");
pdf.encrypt("./report.pdf", "./report-locked.pdf", { userPassword: "open", ownerPassword: "owner", permissions: { print: true, copy: false } });

let session = pdf.view("./book.pdf", { page: 5, search: "AutoJs6" });
session.on("page", (e) => console.log("page", e.page));
session.on("close", () => console.log("closed"));
```

- تفتح الطرق المختصرة مثل `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` المستند وتغلقه في كل استدعاء; وتعيد `pdf.open(path, options)` كائن `PdfDocument` تتشارك فيه عدة عمليات فتحة واحدة, ويمكن لكل سكربت فتح 8 مستندات على الأكثر في وقت واحد.
- لكل طريقة صيغة متزامنة تنتظر حتى الانتهاء وصيغة `xxxAsync` تعيد Promise; تقبل `xxxAsync` الخيار `options.onProgress(progress)` ولوعدها الطريقة `cancel()`, وإيقاف السكربت يلغي العمليات الجارية ويحذف المخرجات غير المكتملة.
- تفتح `pdf.view(path, options)` العارض وتعيد `PdfViewSession` مع الحدثين `page` و `close` والطريقتين `goTo(page)` و `close()`.
- عند الفشل تُرمى `PdfError` برمز `code` ثابت مثل `PASSWORD_REQUIRED` أو `WRONG_PASSWORD` أو `PAGE_OUT_OF_RANGE`; وتتحقق `pdf.isAvailable()` مسبقا من توفر الإضافة.

******

### الأسئلة الشائعة

******

#### هل يمكنني استخدامها دون AutoJs6?

نعم. يعمل صندوق الأدوات والعارض وحدهما. لا يلزم AutoJs6 إلا عندما تعالج السكربتات ملفات PDF.

#### لماذا تطلب الإضافة الوصول إلى جميع الملفات?

لا يحتاج إليه إلا صندوق الأدوات والعارض المفتوح من المشغّل, لقراءة الملفات التي تختارها على الشاشة والكتابة فيها. يفتح AutoJs6 المسارات التي تمررها السكربتات ويسلّمها إلى الإضافة على شكل واصفات ملفات, لذا لا تحتاج السكربتات إلى إذن التخزين الخاص بالإضافة.

#### هل تحتوي الإضافة على مكتبات أصلية?

لا تستخدم معالجة PDF أي مكتبة أصلية: يعرض `PdfRenderer` في النظام الصفحات, وتتولى PDFBox-Android المكتوبة بلغة Java الخالصة بقية الميزات. والمكتبة الأصلية الوحيدة في الحزمة هي `libandroidx.graphics.path.so` المرفقة مع Jetpack Compose.

******

### الأذونات والأمان

******

تبقي الإضافة أذوناتها في أضيق نطاق ممكن:

- نقاط دخول محمية: تتطلب خدمة القدرات وخدمة مدخل مدير الملفات ونقطة التفعيل إذن إضافات AutoJs6 (`org.autojs.permission.PLUGIN`), فلا يمكن للتطبيقات الأخرى استدعاؤها مباشرة.
- التخزين: تصل ملفات السكربتات على شكل واصفات ملفات, لذا لا تطلب الإضافة إذن التخزين من أجل السكربتات; ولا يُستخدم الوصول إلى جميع الملفات إلا للملفات التي تختارها في صندوق الأدوات والعارض.
- من HTML إلى PDF: لا يُعرض إلا نص HTML أو الملف المحلي المقدَّم, ولا تُحمَّل أي عناوين بعيدة.
- الشبكة وكلمات المرور: تقتصر الشبكة على التحقق من التحديثات; ولا تُستخدم كلمات مرور المستندات إلا في العملية الجارية, ولا تحفظها الإضافة أبدا.

******

### المحرك والتبعيات

******

تُعرض الصفحات باستخدام `PdfRenderer` في Android; ويتولى [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, بلغة Java الخالصة) النص والفهرس والصور وعمليات الصفحات والتشفير, ويعتمد التشفير على Bouncy Castle (ترخيص على نمط MIT). لا تحتوي معالجة PDF على مكتبات أصلية ولا تستخدم محركات عرض أصلية مثل pdfium أو MuPDF.

******

### واجهة المكون الإضافي

******

المعلومات التالية موجهة لمطوري مضيف AutoJs6 والمكونات الإضافية; يستخدم المضيف هذه المعرفات لاكتشاف المكون والتفاوض على القدرات:

```text
application id: io.github.supermonster003.autojs6.plugin.three.paper.pdf
plugin id: three-paper-pdf
engine: pdf
variant: default
info service: org.autojs.plugin.INFO (category three-paper-pdf)
capability service: org.autojs.plugin.PDF (category pdf)
file manager entry service: org.autojs.plugin.EXPLORER_ACTION (engine explorer-action)
viewer action: org.autojs.plugin.PDF_VIEWER_OPEN
wake action: org.autojs.plugin.action.WAKE
minimum host build: 6641
native libraries: libandroidx.graphics.path.so of Jetpack Compose only (arm64-v8a, armeabi-v7a, x86_64, x86; 16 KB aligned); a single APK for every ABI
```

******

### خارطة الطريق

******

تتابع الخطط وقرارات التصميم والتقدم في شكل قائمة تحقق في ROADMAP.md. العناصر غير المحددة خطط وليست ميزات في الإصدار الحالي.

- [عرض ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### سجل الإصدارات

******

#### v1.0.0

_2026/11/12_

- `تلميح` معاينة للتطوير: يستطيع مركز الإضافات في AutoJs6 اكتشاف الإضافة, ولم تُسلَّم بعد القراءة والعرض وعمليات الصفحات والعارض وصندوق الأدوات ووحدة السكربت pdf
- `تلميح` يتطلب AutoJs6 6.8.1 (6641) أو أحدث (سيُحدَّد الحد الأدنى الدقيق للإصدار بعد اكتمال تغييرات المضيف)
- `ميزة` هيكل الإضافة: بروتوكول التفعيل Wake, وخدمة الهوية في مركز الإضافات, وخدمة قدرات PDF (إصدار العقد 1), وخدمة مدخل مدير الملفات
- `ميزة` شاشة رئيسية لمنح الوصول إلى جميع الملفات (لا يحتاج إليه إلا صندوق الأدوات)
- `ميزة` أيقونات فاتحة وداكنة للمشغل ولمركز الإضافات; يستخدم المشغل افتراضيا الأيقونة المتكيفة التلقائية (تتبع الوضع الفاتح أو الداكن للنظام), والأنماط متكيفة فاتحة, ومتكيفة داكنة, وخلفية شفافة مضمنة, ويمكن اختيارها من شاشة الإعدادات لاحقا
- `ميزة` ملف APK واحد لجميع واجهات ABI: لا تحتوي معالجة PDF على مكتبات أصلية, والمكتبة الأصلية الوحيدة تأتي من Jetpack Compose وهي محاذاة على صفحات ذاكرة بحجم 16 KB
- `ميزة` ملفات README وتعليمات مركز الإضافات وسجلات التغييرات بعشر لغات, مولَّدة من مصادر JSON
- `تبعية` إرفاق PDFBox-Android 2.0.27.0 (النص والفهرس والصور وعمليات الصفحات والتشفير)
- `تبعية` إرفاق Jetpack Compose BOM 2026.09.00 (شاشات الإضافة)
- `تبعية` إرفاق Navigation Compose 2.10.2
- `تبعية` إرفاق AndroidX Core KTX 1.18.0

##### لمزيد من سجل الإصدارات

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-ar.md)

******

### البناء

******

هذا القسم موجه للمطورين الراغبين في بناء المكون من المصدر.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\generate_icon_studio.py --check
py .python\check_repository.py
```

******

### الترخيص

******

شيفرة المشروع مرخصة بموجب [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE). المكونات والبيانات من أطراف ثالثة مذكورة في إشعارات الأطراف الثالثة.

- [إشعارات الأطراف الثالثة (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### روابط

******

- مشروع AutoJs6: https://github.com/SuperMonster003/AutoJs6
