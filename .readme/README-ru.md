<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Чтение, отрисовка, объединение, разделение, создание и шифрование PDF-файлов, встроенный просмотрщик и пункты в файловом менеджере</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Языки (Languages)

******

Текущий README.md поддерживает следующие языки:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ko.md)
- Русский [ru] # текущий
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ar.md)

******

### Введение

******

3-Paper PDF - это PDF-плагин для AutoJs6, а также набор инструментов и просмотрщик PDF, которые работают и без AutoJs6. Он читает сведения, текст, оглавление и встроенные изображения PDF и отрисовывает страницы в изображения; объединяет, разделяет, извлекает, удаляет, переупорядочивает и поворачивает страницы; создаёт PDF из изображений, текста или HTML, а также шифрует и расшифровывает документы.

Обработка выполняется в плагине с помощью `PdfRenderer` из Android и PDFBox-Android на чистом Java, поэтому хост AutoJs6 остаётся лёгким. Скрипты AutoJs6 используют эти возможности через глобальный модуль `pdf`; AutoJs6 открывает пути, переданные скриптами, и передаёт их плагину, поэтому плагину не нужно разрешение на доступ к хранилищу для обработки файлов скриптов. У плагина есть собственный просмотрщик, который открывается из лаунчера, файлового менеджера AutoJs6 или скрипта (`pdf.view`).

> Это предварительная версия для разработки: центр плагинов AutoJs6 может обнаружить плагин, но чтение, отрисовка, операции со страницами, просмотрщик, набор инструментов и модуль `pdf` ещё не готовы. Ниже описаны цели версии 1.0.0; ход работы смотрите в плане развития.

******

### Основные Возможности

******

- Чтение: число страниц, размеры страниц, метаданные, состояние шифрования и разрешения; текст и оглавление (закладки) по страницам; поиск текста с номерами страниц и позициями; список и экспорт встроенных изображений.
- Отрисовка: страницы в объекты `Image` или файлы PNG / JPEG / WebP, по умолчанию 144 dpi, либо по разрешению, масштабу или целевым ширине и высоте.
- Операции со страницами: объединение (с сохранением закладок), разделение, извлечение, удаление, переупорядочивание и поворот страниц; номера страниц начинаются с 1, диапазоны записываются как `'1-3,5,8-'`.
- Создание: изображения в PDF (JPEG встраиваются как есть, без перекодирования), текст в PDF (с выбранным шрифтом) и HTML в PDF (удалённые URL не загружаются).
- Шифрование: открытие зашифрованных документов по паролю, шифрование AES-256 с настройкой разрешений, снятие шифрования.
- Просмотрщик: непрерывная прокрутка, масштабирование двумя пальцами, миниатюры, оглавление, подсветка результатов поиска, ввод пароля и ночной режим; скрипты могут открыть просмотрщик и получать события смены страницы и закрытия.
- Набор инструментов и файловый менеджер: мастера объединения, разделения, преобразования в изображения, преобразования изображений в PDF, шифрования / расшифровки и извлечения текста; файловый менеджер AutoJs6 открывает PDF прямо в просмотрщике плагина, а его дополнительное меню предлагает те же инструменты.
- OCR: `pdf.text(path, { ocr: true })` вызывает OCR-движок AutoJs6 для отсканированных страниц без текстового слоя.

******

### Использование

******

1. Скачайте APK со страницы [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) и установите его. Для всех ABI есть один общий пакет.
2. Чтобы использовать модуль `pdf` в скриптах, обновите AutoJs6 до внутренней сборки 6641 (6.8.1) или новее и включите плагин в центре плагинов.
3. Чтобы пользоваться встроенным набором инструментов, откройте плагин и по запросу предоставьте доступ ко всем файлам; операциям скриптов это разрешение не нужно.

> Плагин поддерживает устройства с Android 7.0 (API 24) и новее. Для обработки PDF используются только системные API и библиотеки на чистом Java; единственная нативная библиотека входит в Jetpack Compose и выровнена по страницам памяти 16 KB, поэтому один пакет подходит для всех ABI.

******

### API Скриптов

******

Скрипты используют эти возможности через глобальный модуль `pdf` (также доступен как `$pdf`) (черновик проекта версии 1.0.0; основной считается документация выпущенной версии):

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

- Удобные методы, например `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages`, открывают и закрывают документ при каждом вызове; `pdf.open(path, options)` возвращает `PdfDocument`, который использует одно открытие для нескольких операций; один скрипт может держать открытыми не более 8 документов.
- У каждого метода есть блокирующая форма и форма `xxxAsync`, возвращающая Promise; `xxxAsync` принимает `options.onProgress(progress)`, а у её Promise есть `cancel()`; остановка скрипта отменяет выполняемые операции и удаляет незавершённые результаты.
- `pdf.view(path, options)` открывает просмотрщик и возвращает `PdfViewSession` с событиями `page` и `close` и методами `goTo(page)` и `close()`.
- При ошибке выбрасывается `PdfError` со стабильным `code`, например `PASSWORD_REQUIRED`, `WRONG_PASSWORD` или `PAGE_OUT_OF_RANGE`; `pdf.isAvailable()` заранее проверяет, доступен ли плагин.

******

### Частые Вопросы

******

#### Можно ли пользоваться им без AutoJs6?

Да. Набор инструментов и просмотрщик работают сами по себе. AutoJs6 нужен только тогда, когда PDF обрабатывают скрипты.

#### Почему плагин запрашивает доступ ко всем файлам?

Он нужен только набору инструментов и просмотрщику, открытому из лаунчера, чтобы читать и записывать файлы, выбранные на экране. AutoJs6 открывает пути, переданные скриптами, и передаёт их плагину как файловые дескрипторы, поэтому скриптам не нужно разрешение плагина на доступ к хранилищу.

#### Есть ли в плагине нативные библиотеки?

Для обработки PDF нативные библиотеки не используются: страницы отрисовывает системный `PdfRenderer`, а остальные функции выполняет PDFBox-Android на чистом Java. Единственная нативная библиотека в пакете - `libandroidx.graphics.path.so` из состава Jetpack Compose.

******

### Разрешения И Безопасность

******

Плагин запрашивает минимально необходимые разрешения:

- Защищённые точки входа: служба возможностей, служба точки входа файлового менеджера и точка активации требуют разрешения плагинов AutoJs6 (`org.autojs.permission.PLUGIN`), поэтому другие приложения не могут вызвать их напрямую.
- Хранилище: файлы скриптов поступают как файловые дескрипторы, поэтому плагин не запрашивает доступ к хранилищу ради скриптов; доступ ко всем файлам используется только для файлов, выбранных вами в наборе инструментов и просмотрщике.
- HTML в PDF: отрисовываются только переданная строка HTML или локальный файл; удалённые URL не загружаются.
- Сеть и пароли: сеть используется только для проверки обновлений; пароли документов используются только в текущей операции и плагином не сохраняются.

******

### Движок и зависимости

******

Страницы отрисовываются системным `PdfRenderer` из Android; текст, оглавление, изображения, операции со страницами и шифрование выполняет [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, чистый Java), а шифрование опирается на Bouncy Castle (лицензия в стиле MIT). Обработка PDF не содержит нативных библиотек и не использует нативные движки отрисовки, такие как pdfium или MuPDF.

******

### Интерфейс Плагина

******

Следующая информация предназначена разработчикам хоста AutoJs6 и плагинов; хост использует эти идентификаторы для обнаружения плагина и согласования возможностей:

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

### Дорожная Карта

******

Планы, проектные решения и ход работ ведутся в виде списка с отметками в ROADMAP.md. Неотмеченные пункты являются планами, а не возможностями текущей версии.

- [Открыть ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### История Выпусков

******

#### v1.0.0

_2026/11/12_

- `Подсказка` Предварительная версия для разработки: центр плагинов AutoJs6 может обнаружить плагин; чтение, отрисовка, операции со страницами, просмотрщик, набор инструментов и модуль скриптов pdf ещё не готовы
- `Подсказка` Требуется AutoJs6 6.8.1 (6641) или новее (точная минимальная сборка будет указана после внесения изменений в хост)
- `Функция` Каркас плагина: протокол активации Wake, служба идентификации центра плагинов, служба возможностей PDF (версия контракта 1) и служба точки входа файлового менеджера
- `Функция` Главный экран с предоставлением доступа ко всем файлам (нужен только набору инструментов)
- `Функция` Один APK для всех ABI: обработка PDF не содержит нативных библиотек; единственная нативная библиотека входит в Jetpack Compose и выровнена по страницам памяти 16 KB
- `Функция` README, инструкции центра плагинов и журналы изменений на 10 языках, созданные из исходных файлов JSON
- `Зависимость` Добавлен PDFBox-Android 2.0.27.0 (текст, оглавление, изображения, операции со страницами и шифрование)
- `Зависимость` Добавлен Jetpack Compose BOM 2026.09.00 (экраны плагина)
- `Зависимость` Добавлен Navigation Compose 2.10.2
- `Зависимость` Добавлен AndroidX Core KTX 1.18.0

##### Больше истории выпусков

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-ru.md)

******

### Сборка

******

Этот раздел предназначен разработчикам, желающим собрать плагин из исходников.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\check_repository.py
```

******

### Лицензия

******

Код проекта распространяется по лицензии [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE). Сторонние компоненты и данные перечислены в уведомлениях о сторонних компонентах.

- [Сведения о сторонних компонентах (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### Ссылки

******

- Проект AutoJs6: https://github.com/SuperMonster003/AutoJs6
