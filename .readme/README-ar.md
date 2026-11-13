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

تجري المعالجة داخل الإضافة باستخدام `PdfRenderer` في Android ومكتبة PDFBox-Android المكتوبة بلغة Java الخالصة, لذا يبقى مضيف AutoJs6 خفيفا. تستخدم سكربتات AutoJs6 هذه الميزات عبر الوحدة العامة `pdf`, وكذلك سكربتات إضافتي Node.js Runtime و Bun Runtime; ويفتح AutoJs6 المسارات التي تمررها السكربتات ثم يسلّمها إلى الإضافة, لذا لا تحتاج الإضافة إلى إذن التخزين لمعالجة ملفات السكربتات. وللإضافة عارضها الخاص, ويمكن فتحه من المشغّل أو من مدير ملفات AutoJs6 أو من خيار "فتح باستخدام" في التطبيقات الأخرى أو من سكربت (`pdf.view`).

> تتطلب الوحدة `pdf` في سكربتات AutoJs6 ومداخل مدير الملفات الإصدار 6.8.1 من AutoJs6 (الإصدار الداخلي 6641) أو أحدث; وتعرض إصدارات AutoJs6 الأقدم هذه الإضافة في مركز الإضافات على أنها غير متوافقة. لا يعتمد صندوق الأدوات والعارض على AutoJs6 ويعملان وحدهما.

******

### أبرز الميزات

******

- القراءة: عدد الصفحات وأحجامها والبيانات الوصفية وحالة التشفير والأذونات; ونص كل صفحة والفهرس (الإشارات المرجعية); والبحث عن النص مع أرقام الصفحات والمواضع; وسرد الصور المضمنة وتصديرها.
- العرض: تحويل الصفحات إلى كائنات `Image` أو ملفات PNG / JPEG / WebP, بدقة 144 dpi افتراضيا, أو حسب الدقة أو نسبة التكبير أو العرض والارتفاع المطلوبين.
- عمليات الصفحات: الدمج (مع الاحتفاظ بالإشارات المرجعية) والتقسيم والاستخراج والحذف وإعادة الترتيب والتدوير; تبدأ أرقام الصفحات من 1 وتُكتب النطاقات بالشكل `'1-3,5,8-'`.
- الإنشاء: من الصور إلى PDF (تُضمَّن صور JPEG كما هي دون إعادة ترميز), ومن النص إلى PDF (بالخط الذي تختاره), ومن HTML إلى PDF (دون تحميل أي عناوين بعيدة).
- التشفير: فتح المستندات المشفرة بكلمة مرور, والتشفير بـ AES-256 مع ضبط الأذونات, وإزالة التشفير.
- العارض: تمرير متواصل وتكبير بإصبعين وصور مصغرة وفهرس وإبراز نتائج البحث والانتقال إلى صفحة وإدخال كلمة المرور ووضع ليلي; ويمكن للتطبيقات الأخرى تسليمه ملفات PDF عبر "فتح باستخدام" أو المشاركة; ويمكن للسكربتات فتح العارض وتلقي أحداث تغيير الصفحة والإغلاق.
- صندوق الأدوات: 8 أدوات للدمج والتقسيم والتحويل إلى صور وتحويل الصور إلى PDF والتشفير وفك التشفير واستخراج النص وتنظيم الصفحات; وتُنفَّذ المهام واحدة تلو الأخرى في الخلفية مع عرض التقدم في الإشعار; وتتذكر قائمة الملفات الأخيرة أيضا الملفات المفتوحة بمنتقي الملفات في النظام.
- مدير ملفات AutoJs6: يؤدي النقر على ملف PDF إلى فتحه في عارض الإضافة, وتُفتح مباشرة أيضا ملفات PDF التي لا يتجاوز حجمها 512 MiB داخل الأرشيفات; وتوفر القائمة الدمج والتقسيم والتحويل إلى صور واستخراج النص والتشفير وإزالة كلمة المرور.
- OCR: يتعرّف `pdf.text(path, { ocr: true })` على الصفحات الممسوحة ضوئيا التي لا تحتوي على طبقة نص باستخدام إضافة OCR مثبتة (ML Kit OCR أو Paddle OCR أو Rapid OCR).
- Node.js و Bun: يوفر Node.js Runtime 2.2.0 و Bun Runtime 0.23.0 وما بعدهما الوحدة `pdf` نفسها; وتعيد طرقها Promise, ويمكن إيقاف الاستدعاءات بـ `signal` أو `timeoutMs`.

******

### الاستخدام

******

1. نزّل ملف APK من [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) وثبّته. توجد حزمة واحدة لجميع واجهات ABI.
2. لاستخدامها في سكربتات AutoJs6 أو في مدير الملفات, حدّث AutoJs6 إلى الإصدار 6.8.1 (الإصدار الداخلي 6641) أو أحدث وتأكد من تفعيل الإضافة في مركز الإضافات.
3. لاستخدام صندوق الأدوات المدمج, افتح الإضافة وامنحها الوصول إلى جميع الملفات عند الطلب; لا تحتاج عمليات السكربتات إلى هذا الإذن.

> تدعم الإضافة الأجهزة التي تعمل بنظام Android 7.0 (API 24) أو أحدث. لا تستخدم معالجة PDF إلا واجهات النظام ومكتبات Java الخالصة; والمكتبة الأصلية الوحيدة تأتي من Jetpack Compose وهي محاذاة على صفحات ذاكرة بحجم 16 KB, لذا تخدم حزمة واحدة جميع واجهات ABI.

******

### التوافق

******

- Android 7.0 (API 24) أو أحدث, وحزمة واحدة لجميع واجهات ABI; ولا يعتمد صندوق الأدوات والعارض على AutoJs6.
- سكربتات AutoJs6 ومدير الملفات: تتطلب الإصدار 6.8.1 من AutoJs6 (الإصدار الداخلي 6641) أو أحدث.
- Node.js: تتطلب إضافة Node.js Runtime بالإصدار 2.2.0 أو أحدث, وتُحمَّل الوحدة بـ `require('autojs6:pdf')`; ويعلن المشروع `files` في `node.permissions` ضمن `project.json`, إضافة إلى `files.write` لكتابة الملفات و `ocr` للتعرف على النص و `ui` لفتح العارض.
- Bun: تتطلب إضافة Bun Runtime بالإصدار 0.23.0 أو أحدث (Android 13 أو أحدث), وتُستخدم الوحدة عبر `host.pdf`; فعّل `pdf.control` في شاشة إعدادات Bun Runtime, و `ocr.control` أيضا للتعرف على النص.
- OCR: يتطلب التعرف على الصفحات الممسوحة ضوئيا إضافة OCR (ML Kit OCR أو Paddle OCR أو Rapid OCR); وبدونها يفشل التعرف على الصفحات الممسوحة بالخطأ `OCR_UNAVAILABLE`, ولا تتأثر الميزات الأخرى.

******

### واجهة برمجة النصوص

******

تستخدم سكربتات AutoJs6 هذه الميزات عبر الوحدة العامة `pdf` (ويمكن كتابتها أيضا `$pdf`; المرجع الكامل في [توثيق AutoJs6 > PDF](https://docs.autojs6.com/#/pdf)):

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

في Node.js و Bun تعيد الطرق التي تحمل الأسماء نفسها Promise (ويستخدمها Bun عبر `host.pdf`):

```javascript
"nodejs";
const pdf = require("autojs6:pdf");

(async () => {
    const { texts, pages } = await pdf.text("./book.pdf", { pages: "1-3" });
    console.log(pages, texts[0]);
    const merged = await pdf.merge(["./a.pdf", "./b.pdf"], "./ab.pdf", {
        displayNames: ["Part A", null],
        timeoutMs: 60000,
    });
    console.log(merged.path, merged.pageCount);
})();
```

- تفتح الطرق المختصرة مثل `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` المستند وتغلقه في كل استدعاء; وتعيد `pdf.open(path, options)` كائن `PdfDocument` تتشارك فيه عدة عمليات فتحة واحدة, ويمكن لكل سكربت فتح 8 مستندات على الأكثر في وقت واحد.
- لكل طريقة صيغة متزامنة تنتظر حتى الانتهاء وصيغة `xxxAsync` تعيد Promise, بالوسائط والنتائج نفسها; ولا يمكن استدعاء الصيغة المتزامنة في خيط الواجهة (تفشل بالخطأ `INVALID_ARGUMENT`), لذا تستخدم سكربتات الواجهة `xxxAsync`; وتقبل الطرق التي تكتب ملفات الخيار `onProgress`, ولوعدها الطريقة `cancel()`; ولا يترك الإلغاء أو إيقاف السكربت أي ملف مكتوب جزئيا.
- يسمّي الخيار `displayNames` في `pdf.merge` الإشارة المرجعية لكل مدخل: تأخذ `null` اسم الملف (دون `.pdf`), ويأخذ النص الفارغ أو المكوّن من مسافات عنوان المستند (`Document <n>` إن لم يكن له عنوان); وتفشل الخيارات التي يتجاوز حجمها نحو 63 KiB بالخطأ `LIMIT_EXCEEDED` دون كتابة أي مخرجات.
- تنفّذ `pdf.fromHtml` افتراضيا سكربتات الصفحة, ويعطّلها `javaScript: false`; ولا يُحمَّل أي محتوى خارجي: يُرد على طلبات الشبكة بمحتوى فارغ وتُسجَّل في `warnings`.
- تفتح `pdf.view(path, options)` العارض وتعيد `PdfViewSession` مع الحدثين `page` / `close` والطرق `goTo(page)` و `state()` و `close()`; وتفشل بالخطأ `UI_REQUIRES_FOREGROUND` عندما لا يكون AutoJs6 في المقدمة (على Android 10 أو أحدث دون إذن الظهور فوق التطبيقات الأخرى).
- Node.js و Bun: تعيد كل الطرق Promise; ويوقف `signal` (كائن AbortSignal) و `timeoutMs` الاستدعاء, فيُرفض بالخطأ `CANCELLED` في الحالتين, ويكون `cause` لانتهاء المهلة `TimeoutError`; ويجب أن يكون `timeoutMs` عددا منتهيا أكبر من 0 ولا يتجاوز 2147483647, وتُقرَّب الكسور إلى الأعلى.
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

#### لماذا يعرض مركز الإضافات هذه الإضافة على أنها غير متوافقة?

تتطلب وحدة السكربتات ومداخل مدير الملفات في هذه الإضافة الإصدار 6.8.1 من AutoJs6 (الإصدار الداخلي 6641) أو أحدث. بعد تحديث AutoJs6 يمكن تفعيل الإضافة; وحتى ذلك الحين يعمل صندوق الأدوات والعارض كالمعتاد.

#### هل يستطيع مدير ملفات AutoJs6 فتح ملف PDF داخل أرشيف?

نعم. يُفتح ملف PDF الذي لا يتجاوز حجمه 512 MiB داخل الأرشيف مباشرة في عارض الإضافة; أما الملفات الأكبر فاستخرجها أولا.

#### ماذا أفعل إذا لم يعد ملف في قائمة الملفات الأخيرة يُفتح?

تحتفظ الملفات المفتوحة بمنتقي الملفات في النظام بإذن القراءة وتُفتح مباشرة حتى بعد إعادة التشغيل. إذا حُذف الملف أو نُقل أو أُلغي الإذن, فإن النقر على العنصر يعرض رسالة ويزيله من القائمة; اختر الملف مجددا عبر "فتح ملف PDF".

******

### الأذونات والأمان

******

تبقي الإضافة أذوناتها في أضيق نطاق ممكن:

- نقاط دخول محمية: تتطلب خدمة القدرات وخدمة مدخل مدير الملفات ونقطة التفعيل إذن إضافات AutoJs6 (`org.autojs.permission.PLUGIN`), فلا يمكن للتطبيقات الأخرى استدعاؤها مباشرة.
- التخزين: تصل ملفات السكربتات على شكل واصفات ملفات, لذا لا تطلب الإضافة إذن التخزين من أجل السكربتات; ولا يُستخدم الوصول إلى جميع الملفات إلا للملفات التي تختارها في صندوق الأدوات والعارض.
- من HTML إلى PDF: لا تحمّل الإضافة أي محتوى خارجي (يُرد على طلبات الشبكة بمحتوى فارغ, وتُحظر الملفات المحلية وعناوين `content:`, ولا يمكن للصفحة الانتقال إلى غيرها); ويمكن تعطيل سكربتات الصفحة بـ `javaScript: false`.
- الملفات الأخيرة: تحتفظ الملفات المفتوحة بمنتقي الملفات في النظام بإذن القراءة كي تُفتح مباشرة في المرة التالية, بحد أقصى 20 ملفا; ويُحرَّر الإذن فور خروج العنصر من القائمة أو إزالته أو مسح القائمة. أما الملفات التي تسلّمها التطبيقات الأخرى عبر "فتح باستخدام" أو المشاركة فلها إذن لمرة واحدة فقط ولا تدخل قائمة الملفات الأخيرة.
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

- `تلميح` تتطلب الوحدة pdf في سكربتات AutoJs6 ومداخل مدير الملفات الإصدار 6.8.1 من AutoJs6 (الإصدار الداخلي 6641) أو أحدث; وتعرض إصدارات AutoJs6 الأقدم هذه الإضافة في مركز الإضافات على أنها غير متوافقة, ويبقى صندوق الأدوات والعارض قابلين للاستخدام وحدهما
- `تلميح` يتطلب الاستخدام في سكربتات Node.js أو Bun الإصدار Node.js Runtime 2.2.0 أو Bun Runtime 0.23.0 أو أحدث
- `ميزة` يمكن لسكربتات AutoJs6 استخدام الوحدة pdf (ويمكن كتابتها أيضا $pdf): قراءة معلومات ملفات PDF ونصوصها وفهارسها وصورها, والبحث عن النص, وعرض الصفحات كصور, ودمج الصفحات وتقسيمها واستخراجها وحذفها وإعادة ترتيبها وتدويرها, وإنشاء PDF من الصور أو النص أو HTML, والتشفير وفك التشفير; ولكل طريقة صيغة متزامنة وصيغة Async يمكنها الإبلاغ عن التقدم والإلغاء
- `ميزة` يتعرّف الخيار ocr في pdf.text على الصفحات الممسوحة ضوئيا التي لا تحتوي على طبقة نص باستخدام إضافة OCR مثبتة (ML Kit OCR أو Paddle OCR أو Rapid OCR)
- `ميزة` تفتح pdf.view ملف PDF في عارض الإضافة, وتتلقى السكربتات أحداث تغيير الصفحة والإغلاق, ويمكنها الانتقال إلى صفحة محددة أو إغلاق العارض
- `ميزة` يمكن لسكربتات Node.js و Bun أيضا استخدام الوحدة pdf, وتعيد طرقها Promise, ويمكن إيقاف الاستدعاءات بـ signal أو timeoutMs
- `ميزة` العارض: تمرير متواصل وتكبير بإصبعين وصور مصغرة وفهرس وإبراز نتائج البحث والانتقال إلى صفحة وإدخال كلمة المرور ووضع ليلي; ويمكن فتحه من المشغّل, كما يمكن للتطبيقات الأخرى تسليمه ملفات PDF عبر "فتح باستخدام" أو المشاركة
- `ميزة` مدير ملفات AutoJs6: يؤدي النقر على ملف PDF إلى فتحه في عارض الإضافة, وتُفتح مباشرة أيضا ملفات PDF التي لا يتجاوز حجمها 512 MiB داخل الأرشيفات; وتوفر القائمة الدمج والتقسيم والتحويل إلى صور واستخراج النص والتشفير وإزالة كلمة المرور
- `ميزة` الشاشة الرئيسية: الأدوات وتقدم المهام والملفات الأخيرة ومنح الوصول إلى جميع الملفات (لا يحتاج إليه إلا صندوق الأدوات)
- `ميزة` تتذكر قائمة الملفات الأخيرة الملفات المفتوحة بمنتقي الملفات في النظام (حتى 20 ملفا), وتُفتح مباشرة حتى بعد إعادة التشغيل; وإذا حُذف الملف أو تعذّر الوصول إليه تُعرض رسالة ويُزال من القائمة
- `ميزة` يقدم صندوق الأدوات 8 أدوات: الدمج, والتقسيم (حسب نطاقات الصفحات أو كل n صفحات), وتحويل PDF إلى صور (الدقة والتنسيق والصفحات), وتحويل الصور إلى PDF (حجم الصفحة والاتجاه والهوامش), والتشفير, وإزالة كلمة المرور, واستخراج النص (يُحفظ كملف TXT), وتدوير الصفحات وحذفها وإعادة ترتيبها بالصور المصغرة; والمعالجة نفسها المستخدمة في مداخل مدير الملفات
- `ميزة` تعمل مهام صندوق الأدوات واحدة تلو الأخرى في خدمة الواجهة الأمامية وتستمر بعد مغادرة شاشة الأداة; يعرض الإشعار التقدم ويسمح بالإلغاء, ويمكن فتح النتائج في العارض أو مشاركتها
- `ميزة` متصفح ملفات مدمج يتيح اختيار عدة ملفات وإنشاء مجلدات; ويتوفر أيضا منتقي الملفات في النظام
- `ميزة` شاشة الإعدادات: مجلد المخرجات الافتراضي (Documents/3-Paper PDF), والدقة الافتراضية لتحويل PDF إلى صور, واللغة, والوضع الليلي, ولون السمة, وأيقونة المشغل, وحالة الأذونات, وسجل الإصدارات, والتحقق من التحديثات
- `ميزة` التحقق من التحديثات: يدويا من شاشة الإعدادات, أو تلقائيا عند فتح الشاشة الرئيسية (مرة واحدة كحد أقصى كل 12 ساعة); وتأتي معلومات الإصدارات من الفهرس الرسمي للإضافات
- `ميزة` أيقونات فاتحة وداكنة للمشغل ولمركز الإضافات; يستخدم المشغل افتراضيا الأيقونة المتكيفة التلقائية (تتبع الوضع الفاتح أو الداكن للنظام), والأنماط متكيفة فاتحة, ومتكيفة داكنة, وخلفية شفافة مضمنة, ويمكن اختيارها من شاشة الإعدادات
- `ميزة` ملف APK واحد لجميع واجهات ABI: لا تحتوي معالجة PDF على مكتبات أصلية, والمكتبة الأصلية الوحيدة تأتي من Jetpack Compose وهي محاذاة على صفحات ذاكرة بحجم 16 KB
- `ميزة` ملفات README وتعليمات مركز الإضافات وسجلات التغييرات بعشر لغات, مولَّدة من مصادر JSON
- `تبعية` إرفاق PDFBox-Android 2.0.27.0 (النص والفهرس والصور وعمليات الصفحات والتشفير)
- `تبعية` إرفاق Jetpack Compose BOM 2026.09.00 (شاشات الإضافة)
- `تبعية` إرفاق Navigation Compose 2.10.2
- `تبعية` إرفاق Material Color Utilities (MaterialKolor) 4.1.1 (لون سمة شاشات الإضافة)
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
