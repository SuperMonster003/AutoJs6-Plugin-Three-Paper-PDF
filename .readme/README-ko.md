<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>PDF 파일 읽기, 렌더링, 병합, 분할, 생성, 압축과 암호화, 양식 작성과 워터마크 추가, 뷰어와 파일 관리자 진입점 내장</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 언어 (Languages)

******

현재 README.md 는 다음 언어를 지원합니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ar.md)

******

### 소개

******

3-Paper PDF는 AutoJs6의 PDF 플러그인이자, AutoJs6 없이도 단독으로 쓸 수 있는 PDF 도구 상자 겸 뷰어입니다. PDF의 정보, 텍스트, 개요, 내장 이미지를 읽고 페이지를 이미지로 렌더링할 수 있습니다. 페이지를 병합, 분할, 추출, 삭제, 재정렬, 회전할 수 있고, 이미지, 텍스트, HTML로 PDF를 생성하며 문서를 암호화하거나 복호화할 수 있습니다. 1.1.0부터는 양식 읽기와 작성, 워터마크, 페이지 번호, 머리글과 바닥글 추가, OCR 플러그인을 이용한 검색 가능한 PDF 만들기, 파일 크기 압축, 문서 속성과 개요 변경도 할 수 있습니다.

처리는 플러그인 안에서 Android의 `PdfRenderer`와 순수 Java인 PDFBox-Android로 이루어지므로 AutoJs6 호스트는 가볍게 유지됩니다. AutoJs6 스크립트는 전역 모듈 `pdf`로 이 기능을 사용하며, Node.js Runtime과 Bun Runtime 플러그인의 스크립트에서도 쓸 수 있습니다. 스크립트가 넘긴 경로는 AutoJs6가 열어서 플러그인에 넘기므로, 플러그인은 스크립트 파일을 처리하는 데 저장소 권한이 필요 없습니다. 플러그인에는 뷰어가 내장되어 있어 런처, AutoJs6 파일 관리자, 다른 앱의 "연결 프로그램", 스크립트 (`pdf.view`)에서 열 수 있습니다.

> AutoJs6 스크립트의 `pdf` 모듈과 파일 관리자 진입점에는 AutoJs6 6.8.1 (내부 빌드 6641) 이상이 필요합니다. 이보다 오래된 AutoJs6에서는 플러그인 센터에 이 플러그인이 호환되지 않는다고 표시됩니다. 도구 상자와 뷰어는 AutoJs6에 의존하지 않으므로 단독으로 쓸 수 있습니다.

******

### 기능 하이라이트

******

- 읽기: 페이지 수, 페이지 크기, 메타데이터, 암호화 상태와 권한; 페이지별 텍스트와 개요 (북마크); 페이지 번호와 위치를 돌려주는 텍스트 검색; 내장 이미지 목록과 내보내기.
- 렌더링: 페이지를 `Image` 객체나 PNG / JPEG / WebP 파일로 렌더링합니다. 기본값은 144 dpi이며, dpi, 배율, 목표 너비와 높이로도 지정할 수 있습니다.
- 페이지 작업: 병합 (북마크 유지), 분할, 추출, 삭제, 재정렬, 회전; 페이지 번호는 1부터 세며, 범위는 `'1-3,5,8-'`처럼 씁니다.
- 생성: 이미지를 PDF로 (JPEG는 다시 인코딩하지 않고 그대로 포함), 텍스트를 PDF로 (글꼴 지정 가능), HTML을 PDF로 (원격 URL은 불러오지 않음).
- 암호화: 암호로 암호화된 문서 열기, AES-256으로 암호화하고 권한 설정, 암호화 제거.
- 양식: 양식 필드 (텍스트 필드, 확인란, 라디오 단추, 콤보 상자, 목록 상자)와 현재 값을 나열하고, 필드 이름으로 값을 채울 수 있습니다. 일반 페이지 내용으로 평면화할 수도 있습니다. `pdf.info`는 양식 필드 수를 반환합니다.
- 워터마크와 페이지 번호: 텍스트 또는 이미지 워터마크, 페이지 번호, 머리글과 바닥글을 추가하며, 위치, 회전, 불투명도, 바둑판식 배열, 적용할 페이지를 지정할 수 있습니다. 텍스트에는 자리 표시자 `{page}`, `{total}`, `{date}`, `{name}`을 쓸 수 있습니다.
- 검색 가능한 PDF: OCR 플러그인으로 스캔 페이지를 인식하고 보이지 않는 텍스트 레이어를 기록합니다. 이후에는 글자를 검색, 선택, 복사할 수 있고, 텍스트를 다시 읽을 때 새로 인식할 필요도 없습니다. 스크립트에서 직접 인식한 결과는 `pdf.addTextLayer`로 기록할 수 있습니다.
- 압축: 내장 이미지를 지정한 해상도와 화질로 다시 인코딩하고, 압축되지 않은 스트림을 압축하며, 필요하면 메타데이터를 제거합니다. low / medium / high 세 가지 프리셋 외에 직접 값을 지정할 수도 있으며, 결과에 압축 전후의 크기가 표시됩니다.
- 문서 속성과 개요: 제목, 작성자, 주제, 키워드, 작성 프로그램을 바꿀 수 있습니다. 개요 (책갈피)를 읽어 고친 뒤 다시 기록하거나 모두 지울 수도 있습니다.
- 뷰어: 연속 스크롤, 핀치 줌, 썸네일, 개요, 검색 강조, 페이지 이동, 암호 입력, 야간 모드, 전체 화면 읽기. 다른 앱에서 "연결 프로그램"이나 공유로 PDF를 넘길 수 있고, 스크립트에서 뷰어를 열고 페이지 이동과 닫기 이벤트를 받을 수 있습니다.
- 도구 상자: 병합, 분할, 이미지로 변환, 이미지를 PDF로, 암호화, 복호화, 텍스트 추출, 페이지 정리, 워터마크 추가, 페이지 번호 추가 (둘 다 페이지를 넘길 수 있는 한 페이지 미리 보기 포함), 압축, 양식 작성, 문서 속성의 13가지 도구. 워터마크, 페이지 번호, 압축에는 직접 값을 지정할 수도 있습니다. 작업은 백그라운드에서 차례로 실행되며 진행 상황은 알림에 표시됩니다. 결과 화면에서 이번 처리를 Rhino, Node.js, Bun 스크립트로 만들 수 있습니다. 최근 파일에는 시스템 파일 선택기로 연 파일과 각 도구에서 선택한 PDF도 기록되며, 파일 브라우저 위쪽의 "최근 사용"에서 바로 선택할 수 있습니다. 브라우저는 마지막으로 파일을 선택한 폴더에서 시작합니다.
- AutoJs6 파일 관리자: PDF를 탭하면 플러그인 뷰어에서 열리며, 압축 파일 안의 512 MiB 이하 PDF도 바로 열 수 있습니다. 메뉴에서 병합, 분할, 압축, 이미지로 변환, 워터마크 추가, 텍스트 추출, 암호화, 비밀번호 제거를 쓸 수 있고, OCR 플러그인이 있으면 검색 가능한 PDF도 만들 수 있습니다.
- OCR: `pdf.text(path, { ocr: true })`는 설치된 OCR 플러그인 (ML Kit OCR, Paddle OCR, Rapid OCR)으로 텍스트 레이어가 없는 스캔 페이지를 인식합니다.
- Node.js와 Bun: Node.js Runtime 2.2.0과 Bun Runtime 0.23.0부터 같은 `pdf` 모듈을 쓸 수 있습니다. 메서드는 Promise를 반환하며, `signal`이나 `timeoutMs`로 호출을 중단할 수 있습니다.

******

### 사용 방법

******

1. [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases)에서 APK를 내려받아 설치합니다. 모든 ABI에 공통인 패키지 하나만 있습니다.
2. AutoJs6 스크립트나 파일 관리자에서 쓰려면 AutoJs6를 6.8.1 (내부 빌드 6641) 이상으로 업데이트하고, 플러그인 센터에서 플러그인이 활성화되어 있는지 확인하세요.
3. 내장 도구 상자를 쓰려면 플러그인을 열고 요청이 나오면 모든 파일 접근 권한을 허용하세요. 스크립트 작업에는 이 권한이 필요 없습니다.

> 플러그인은 Android 7.0 (API 24) 이상 기기를 지원합니다. PDF 처리에는 시스템 API와 순수 Java 라이브러리만 쓰며, 유일한 네이티브 라이브러리는 Jetpack Compose의 것으로 16 KB 메모리 페이지에 맞춰 정렬되어 있으므로, 모든 ABI가 패키지 하나를 함께 씁니다.

******

### 호환성

******

- Android 7.0 (API 24) 이상, 모든 ABI에 공통인 패키지 하나. 도구 상자와 뷰어는 AutoJs6에 의존하지 않습니다.
- AutoJs6 스크립트와 파일 관리자: AutoJs6 6.8.1 (내부 빌드 6641) 이상이 필요합니다.
- 1.1.0에 추가된 스크립트 메서드 (양식, 워터마크와 페이지 번호, 검색 가능한 PDF, 압축, 문서 속성과 개요): AutoJs6 6.8.1 이상과 이 플러그인 1.1.0 이상이 필요하며, Node.js Runtime과 Bun Runtime의 요구 버전은 다른 메서드와 같습니다. 플러그인이 아직 1.0.x이면 이 메서드들은 `UNSUPPORTED`로 실패하면서 플러그인 센터에서 업데이트하라고 안내하고, 파일을 쓰지 않습니다. 다른 메서드는 평소처럼 쓸 수 있습니다.
- AutoJs6로 패키징한 앱: AutoJs6 6.8.1 (내부 빌드 6641) 이상으로 패키징해야 하며, 실행하는 기기에 이 플러그인이 설치되어 있어야 합니다. 패키징할 때 `org.autojs.plugin.pdf.permission.RUNTIME` 권한이 자동으로 추가되므로 `project.json`을 고칠 필요가 없습니다 (Node.js 프로젝트는 `files`, Bun 프로젝트는 `pdf.control`을 선언합니다).
- Node.js: Node.js Runtime 플러그인 2.2.0 이상이 필요하며 `require('autojs6:pdf')`로 불러옵니다. 프로젝트의 `project.json`에 있는 `node.permissions`에 `files`를 선언하고, 파일을 쓰려면 `files.write`, 문자를 인식하려면 `ocr`, 뷰어를 열려면 `ui`도 선언합니다.
- Bun: Bun Runtime 플러그인 0.23.0 이상 (Android 13 이상)이 필요하며 `host.pdf`로 사용합니다. Bun Runtime 설정 화면에서 `pdf.control`을 켜고, 문자를 인식하려면 `ocr.control`도 켭니다.
- OCR: 스캔 페이지의 문자 인식과 검색 가능한 PDF 만들기에는 OCR 플러그인 (ML Kit OCR, Paddle OCR, Rapid OCR)을 따로 설치해야 합니다. 없으면 이 두 가지는 `OCR_UNAVAILABLE`로 실패하며, 다른 기능에는 영향이 없습니다.

버전별 요구 사항과 기능:

| 항목 | 1.0.0 | 1.1.0 |
| --- | --- | --- |
| Android | 7.0 (API 24) 이상 | 7.0 (API 24) 이상 |
| AutoJs6 | 6.8.1 (내부 빌드 6641) 이상 | 6.8.1 (내부 빌드 6641) 이상 |
| Node.js Runtime | 2.2.0 이상 | 2.2.0 이상 |
| Bun Runtime | 0.23.0 이상 (Android 13 이상) | 0.23.0 이상 (Android 13 이상) |
| OCR 플러그인 (선택) | 스캔 페이지 인식 | 스캔 페이지 인식, 검색 가능한 PDF 만들기 |
| 스크립트 메서드 | 읽기, 렌더링, 페이지 작업, 생성, 암호화, OCR, 뷰어 | 추가로 양식, 워터마크와 페이지 번호, 검색 가능한 PDF, 압축, 문서 속성과 개요 |
| 도구 상자 | 8가지 도구 | 13가지 도구, 직접 값 지정, 스크립트 만들기 |
| AutoJs6 파일 관리자 메뉴 | 6가지 | 9가지 (압축, 워터마크 추가, 검색 가능한 PDF 만들기 추가) |

******

### 스크립트 API

******

AutoJs6 스크립트는 전역 모듈 `pdf` (`$pdf`로도 쓸 수 있음)로 이 기능을 사용합니다 (자세한 내용은 [AutoJs6 문서 > PDF](https://docs.autojs6.com/#/pdf) 참고):

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

// 1.1.0
pdf.fillForm("./form.pdf", "./form-filled.pdf", { "applicant.name": "AutoJs6", "applicant.agree": true }, { flatten: true });
pdf.watermark("./report.pdf", "./report-draft.pdf", { text: "DRAFT" });
pdf.stamp("./report.pdf", "./report-numbered.pdf", { pageNumbers: { format: "{page} / {total}" } });
pdf.compress("./photos.pdf", "./photos-small.pdf", { preset: "low" });
pdf.setInfo("./report.pdf", "./report-info.pdf", { title: "Report", author: "AutoJs6" });

let session = pdf.view("./book.pdf", { page: 5, search: "AutoJs6" });
session.on("page", (e) => console.log("page", e.page));
session.on("close", () => console.log("closed"));
```

Node.js와 Bun에서는 같은 이름의 메서드가 Promise를 반환합니다 (Bun은 `host.pdf`로 사용):

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

- `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` 같은 간편 메서드는 호출할 때마다 문서를 열고 닫습니다. `pdf.open(path, options)`는 `PdfDocument`를 반환하며 여러 작업이 한 번의 열기를 공유합니다. 스크립트마다 동시에 최대 8개 문서를 열 수 있습니다.
- 각 메서드에는 완료될 때까지 기다리는 형태와 Promise를 반환하는 `xxxAsync` 형태가 있으며, 인수와 결과는 같습니다. 기다리는 형태는 UI 스레드에서 호출할 수 없으므로 (`INVALID_ARGUMENT` 발생) UI 스크립트에서는 `xxxAsync`를 쓰세요. 파일을 쓰는 메서드는 `onProgress`를 받고, Promise에는 `cancel()`이 있습니다. 취소하거나 스크립트를 멈춰도 쓰다 만 파일은 남지 않습니다.
- `pdf.merge`의 `displayNames`는 입력마다 북마크 이름을 정합니다. `null`은 파일 이름 (`.pdf` 제외), 공백 문자열은 그 문서의 제목 (제목이 없으면 `Document <n>`)을 씁니다. 옵션이 약 63 KiB를 넘으면 `LIMIT_EXCEEDED`가 발생하고 아무것도 쓰지 않습니다.
- `pdf.fromHtml`은 기본적으로 페이지의 스크립트를 실행하며, `javaScript: false`로 끌 수 있습니다. 외부 콘텐츠는 전혀 불러오지 않으며, 네트워크 요청에는 빈 내용으로 응답하고 `warnings`에 기록합니다.
- 1.1.0의 쓰기 메서드 (예: `pdf.fillForm`, `pdf.stamp`, `pdf.compress`)도 다른 쓰기 메서드처럼 출력 경로를 생략 (또는 `null` 전달)할 수 있으며, 결과는 `Documents/3-Paper PDF/`에 기록됩니다. `PdfDocument`의 같은 이름 메서드는 열 때 준 비밀번호를 그대로 쓰므로 옵션에 `password`를 지정할 수 없습니다. 파일을 쓰는 메서드의 결과에는 `warnings`의 항목마다 하나씩 대응하는 `warningCodes`도 있어, 스크립트에서 코드별로 처리할 수 있습니다.
- `pdf.view(path, options)`는 뷰어를 열고 `PdfViewSession`을 반환합니다. `page` / `close` 이벤트를 받을 수 있고 `goTo(page)`, `state()`, `close()`를 호출할 수 있습니다. AutoJs6가 포그라운드에 없으면 (Android 10 이상이고 다른 앱 위에 표시 권한이 없을 때) `UI_REQUIRES_FOREGROUND`가 발생합니다.
- Node.js와 Bun: 모든 메서드가 Promise를 반환합니다. `signal` (AbortSignal)과 `timeoutMs`로 호출을 중단할 수 있으며, 둘 다 `CANCELLED`로 거부되고 시간 초과의 `cause`는 `TimeoutError`입니다. `timeoutMs`는 0보다 크고 2147483647 이하인 유한한 수여야 하며, 소수는 올림합니다.
- 실패하면 `PdfError`가 발생하며, 그 `code`는 `PASSWORD_REQUIRED`, `WRONG_PASSWORD`, `PAGE_OUT_OF_RANGE` 같은 안정적인 오류 코드입니다. `pdf.isAvailable()`로 플러그인을 쓸 수 있는지 미리 확인할 수 있습니다.

******

### 자주 묻는 질문

******

#### AutoJs6 없이 쓸 수 있나요?

쓸 수 있습니다. 도구 상자와 뷰어는 단독으로 동작합니다. AutoJs6는 스크립트로 PDF를 처리할 때만 필요합니다.

#### 플러그인이 모든 파일 접근 권한을 요청하는 이유는 무엇인가요?

도구 상자와 런처에서 연 뷰어만 이 권한이 필요하며, 화면에서 선택한 파일을 읽고 쓰는 데 씁니다. 스크립트가 넘긴 경로는 AutoJs6가 열어 파일 디스크립터로 플러그인에 넘기므로 플러그인의 저장소 권한이 필요 없습니다.

#### 플러그인에 네이티브 라이브러리가 들어 있나요?

PDF 처리에는 네이티브 라이브러리를 쓰지 않습니다. 페이지 렌더링은 시스템의 `PdfRenderer`, 나머지 기능은 순수 Java인 PDFBox-Android가 맡습니다. 패키지에 들어 있는 유일한 네이티브 라이브러리는 Jetpack Compose에 포함된 `libandroidx.graphics.path.so`입니다.

#### 플러그인 센터에 이 플러그인이 호환되지 않는다고 표시되는 이유는 무엇인가요?

이 플러그인의 스크립트 모듈과 파일 관리자 진입점에는 AutoJs6 6.8.1 (내부 빌드 6641) 이상이 필요합니다. AutoJs6를 업데이트하면 활성화할 수 있으며, 그 전에도 도구 상자와 뷰어는 평소처럼 쓸 수 있습니다.

#### AutoJs6 파일 관리자에서 압축 파일 안의 PDF를 바로 열 수 있나요?

열 수 있습니다. 압축 파일 안의 512 MiB 이하 PDF는 플러그인 뷰어에서 바로 열리며, 더 큰 파일은 먼저 압축을 풀어 주세요.

#### 최근 파일의 항목이 열리지 않으면 어떻게 하나요?

시스템 파일 선택기로 연 파일은 읽기 권한을 유지하므로 다시 시작한 뒤에도 바로 열 수 있습니다. 파일이 삭제되거나 이동되었거나 권한이 취소된 경우에는 항목을 탭하면 안내가 표시되고 목록에서 제거됩니다. "PDF 열기"로 다시 선택하세요.

#### 작성한 양식 값이 뷰어에 표시되지 않는 이유는 무엇인가요?

Android 14 이하에서는 플러그인 뷰어가 평면화한 뒤에만 작성한 값을 표시합니다. 다른 PDF 앱에는 영향이 없습니다. 뷰어에서 확인하려면 작성할 때 평면화하세요 (`flatten: true`, 또는 "양식 작성" 도구의 "필드 평면화"). 평면화한 필드는 더 이상 편집할 수 없습니다.

#### 압축한 파일이 작아지지 않는 이유는 무엇인가요?

압축으로 주로 줄어드는 것은 내장 이미지입니다. 이미지가 이미 충분히 작거나, 플러그인이 다시 인코딩하지 않는 종류 (예: JPEG 2000, CMYK, 1비트 이미지)이면 효과가 제한적입니다. 원본보다 작지 않아도 결과는 기록되고 결과에 안내가 표시되므로, 원본 파일을 그대로 써도 됩니다.

******

### 권한과 보안

******

플러그인은 권한 범위를 최대한 좁게 유지합니다:

- 보호된 진입점: 기능 서비스, 파일 관리자 진입 서비스, 활성화 진입점은 AutoJs6 플러그인 권한 (`org.autojs.permission.PLUGIN`)으로 보호되어 다른 앱이 직접 호출할 수 없습니다. AutoJs6로 패키징한 앱을 위한 서비스와 뷰어 진입점에는 `org.autojs.plugin.pdf.permission.RUNTIME` 권한이 필요합니다 (패키징할 때 자동으로 추가됨). 이 진입점으로 연 문서와 뷰어 세션은 그것을 만든 앱만 호출할 수 있으며, 파일도 파일 디스크립터로 전달됩니다.
- 저장소: 스크립트의 파일은 파일 디스크립터로 전달되므로 플러그인은 스크립트를 위해 저장소 권한을 요청하지 않습니다. 모든 파일 접근 권한은 도구 상자와 뷰어에서 선택한 파일에만 쓰입니다.
- HTML을 PDF로: 플러그인은 외부 콘텐츠를 전혀 불러오지 않습니다 (네트워크 요청에는 빈 내용으로 응답하고, 로컬 파일과 `content:` 주소는 차단되며, 페이지 이동도 할 수 없습니다). 페이지의 스크립트는 `javaScript: false`로 끌 수 있습니다.
- 최근 파일: 시스템 파일 선택기로 연 파일은 다음에 바로 열 수 있도록 읽기 권한을 유지하며, 최대 20개까지입니다. 항목이 밀려나거나 제거되거나 목록을 지우면 곧바로 권한을 해제합니다. 다른 앱에서 "연결 프로그램"이나 공유로 넘겨받은 파일은 일회성 권한만 있으므로 최근 파일에 들어가지 않습니다.
- 네트워크와 암호: 네트워크는 업데이트 확인에만 씁니다. 문서 암호는 해당 작업에서만 쓰이며 플러그인에 저장되지 않습니다.

******

### 엔진과 의존성

******

페이지 렌더링에는 Android의 `PdfRenderer`를 씁니다. 텍스트, 개요, 이미지, 페이지 작업, 암호화에는 [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, 순수 Java)를 쓰며, 암호화는 Bouncy Castle (MIT 방식 라이선스)에 의존합니다. PDF 처리에는 네이티브 라이브러리가 없으며, pdfium이나 MuPDF 같은 네이티브 렌더러를 쓰지 않습니다.

******

### 플러그인 인터페이스

******

다음 정보는 AutoJs6 호스트와 플러그인 개발자를 위한 것으로, 호스트는 이 식별자들로 플러그인을 발견하고 기능을 협상합니다:

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

### 개발 로드맵

******

계획, 설계 결정, 진행 상황은 ROADMAP.md의 체크리스트로 관리합니다. 체크되지 않은 항목은 계획이며 현재 버전의 기능이 아닙니다.

- [ROADMAP.md 보기](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### 릴리스 기록

******

#### v1.1.0

_2026/11/20_

- `힌트` 새 스크립트 메서드와 파일 관리자의 "검색 가능한 PDF 만들기"에는 AutoJs6 6.8.1 이상이 필요합니다. Node.js나 Bun 스크립트에서 쓰려면 Node.js Runtime 2.2.0 또는 Bun Runtime 0.23.0 이상도 필요합니다. 도구 상자의 새 도구는 AutoJs6에 의존하지 않습니다
- `기능` pdf.forms와 pdf.fillForm: 양식 필드 (텍스트 필드, 확인란, 라디오 단추, 콤보 상자, 목록 상자)를 읽고 필드 이름으로 값을 채웁니다. 일반 페이지 내용으로 평면화할 수도 있습니다. pdf.info에 양식 필드 수 추가
- `기능` pdf.watermark와 pdf.stamp: 텍스트 또는 이미지 워터마크, 페이지 번호, 머리글과 바닥글을 추가하며, 위치, 회전, 불투명도, 바둑판식 배열, 적용할 페이지를 지정할 수 있습니다
- `기능` pdf.makeSearchable은 OCR 플러그인으로 스캔 페이지에 보이지 않는 텍스트 레이어를 기록해, 글자를 검색하고 복사할 수 있는 PDF를 만듭니다. pdf.addTextLayer는 스크립트에서 직접 인식한 결과를 기록합니다
- `기능` pdf.compress는 내장 이미지를 다시 인코딩하고 스트림을 압축하며 메타데이터를 제거할 수도 있습니다. low / medium / high 세 가지 프리셋 외에 해상도와 화질을 직접 지정할 수도 있으며, 결과에 압축 전후의 크기가 표시됩니다
- `기능` pdf.setInfo는 제목, 작성자, 주제, 키워드, 작성 프로그램 같은 문서 속성을 바꾸고, pdf.setOutline은 pdf.outline이 반환하는 구조로 개요를 다시 쓰거나 지웁니다
- `기능` 새 메서드는 모두 Async 형태와 PdfDocument의 같은 이름 메서드가 있으며, Node.js, Bun, AutoJs6로 패키징한 앱에서도 쓸 수 있습니다. 파일을 쓰는 메서드의 결과에는 warningCodes도 있어 스크립트에서 안내를 코드별로 처리할 수 있습니다
- `기능` 도구 상자에 "PDF에 워터마크 추가" (한 페이지 미리 보기 포함), "페이지 번호 추가", "PDF 압축", "양식 작성", "문서 속성"의 5가지 도구가 추가되어 모두 13가지가 되었습니다. 워터마크, 페이지 번호, 압축은 프리셋 외에 직접 값을 입력할 수도 있습니다
- `기능` 워터마크와 페이지 번호 도구의 한 페이지 미리 보기를 문서의 어느 페이지로든 넘길 수 있습니다. 미리 보기의 페이지 번호와 전체 페이지 수는 보고 있는 페이지에 따라 바뀌어 결과와 같고, 선택한 페이지에 포함되지 않는 페이지는 그대로 표시하며 안내를 붙입니다
- `기능` 도구 상자와 파일 관리자 작업의 결과 화면에서 이번 처리를 Rhino, Node.js, Bun 스크립트로 만들고 "스크립트 복사", ".js로 저장", "AutoJs6에서 열기"를 쓸 수 있습니다. 스크립트 안의 비밀번호는 null로 남겨 두므로 직접 채워야 합니다
- `기능` AutoJs6 파일 관리자의 PDF 메뉴에 "PDF 압축"과 "PDF에 워터마크 추가"가 추가되어 결과를 원본 파일 옆에 저장합니다. OCR 플러그인이 있으면 "검색 가능한 PDF 만들기"도 쓸 수 있습니다
- `기능` 뷰어에서 전체 화면으로 읽을 수 있습니다. 페이지를 탭해 막대를 숨기면 페이지 번호, 상태 표시줄, 탐색 메뉴도 함께 숨겨지고 (화면 가장자리에서 안쪽으로 스와이프하면 시스템 표시줄이 잠시 표시됩니다), 다시 탭하면 돌아옵니다. 설정에 "뷰어" 항목이 추가되어 "전체 화면에서 페이지 번호 숨기기"와 "전체 화면에서 시스템 표시줄 숨기기"를 각각 끌 수 있습니다
- `수정` 일부 시스템 글꼴(예: ColorOS 15)에서 pdf.fromText로 생성한 PDF의 일부 하이픈("3-Paper", "A-B" 등)이 텍스트 추출이나 검색 시 널 문자로 바뀌던 문제
- `수정` 뷰어에서 문서를 열 때 첫 페이지의 위쪽이 상단 막대에, 마지막 페이지의 끝이 미리보기 막대에 가려지던 문제. 이제 페이지가 문서 양 끝에서 막대를 피하고, 막대를 숨기거나 표시해도 페이지가 움직이지 않습니다
- `수정` 모양이 AutoJs6를 따르고 시스템 언어나 야간 모드가 AutoJs6와 다를 때, 플러그인을 열거나 설정에서 모양을 바꾼 뒤 화면이 잠시 시스템 언어와 야간 모드로 표시되던 (검은 화면이 한 번 깜박이는 등) 문제
- `수정` 설정에서 언어, 야간 모드, 테마 색을 바꾼 뒤 선택 대화 상자가 새로 고친 화면 위에 2초 정도 남아 있던 문제
- `수정` 다른 앱으로 전환한 동안 (생성된 파일을 보는 중 등) 시스템이 플러그인을 종료하면 도구 상자의 도구로 돌아왔을 때 빈 옵션 화면이 되고 선택한 파일과 결과가 사라지던 문제. 이제 결과 화면과 선택한 파일이 복원되며 암호화된 파일은 비밀번호를 다시 입력해야 합니다. 파일 관리자 작업도 결과 화면이 복원됩니다
- `개선` 릴리스 기록 페이지를 버전별로 펼치거나 접을 수 있습니다. 접으면 날짜와 분류별 항목 수를 보여 주고, 펼치면 분류 이름을 한 번만 표시하고 그 아래에 항목을 나열합니다. 모두 한꺼번에 펼치거나 접을 수 있으며 열 때는 최신 버전이 펼쳐집니다. 페이지 제목이 "버전 기록"에서 "릴리스 기록"으로 바뀌고 부제목에 플러그인 이름이 표시되며, 설정의 "정보" 섹션과 업데이트 대화 상자에 있는 항목의 이름도 함께 바뀝니다
- `개선` 도구 결과 화면의 처리 안내가 영어로만 표시되던 것을 화면 언어로 표시하도록 변경
- `개선` 플러그인 센터의 사용 설명을 시작하기, 주요 기능, 스크립트에서 사용하기, 요구 사항, 자주 묻는 질문의 다섯 부분으로 다시 작성함
- `개선` 도구의 결과 화면과 실패 화면에서 상단 막대의 뒤로 화살표나 시스템 뒤로 키를 누르면 옵션 화면으로 돌아가며, 선택한 파일과 입력한 내용이 그대로 유지됩니다. 바꾼 뒤 다시 실행할 수 있고, 새 결과에는 번호가 붙어 이전 결과를 덮어쓰지 않습니다. 옵션 화면에서 한 번 더 뒤로 가면 도구를 닫습니다
- `개선` 파일 선택이 빨라졌습니다: 파일 브라우저 위쪽에 "최근 사용"이 표시되어 (홈 화면의 최근 파일과 같으며, 이제 각 도구에서 선택한 PDF도 포함) 한 번 탭으로 선택할 수 있습니다; 브라우저는 마지막으로 파일을 선택한 폴더에서 시작하며, 모든 도구와 홈 화면의 "PDF 열기"가 함께 씁니다 (이미지는 따로 기억)

#### v1.0.0

_2026/11/14_

- `힌트` AutoJs6 스크립트의 pdf 모듈과 파일 관리자 진입점에는 AutoJs6 6.8.1 (내부 빌드 6641) 이상이 필요합니다. 이보다 오래된 AutoJs6에서는 플러그인 센터에 이 플러그인이 호환되지 않는다고 표시되지만, 도구 상자와 뷰어는 단독으로 쓸 수 있습니다
- `힌트` Node.js나 Bun 스크립트에서 쓰려면 Node.js Runtime 2.2.0 또는 Bun Runtime 0.23.0 이상이 필요합니다
- `기능` AutoJs6 스크립트에서 pdf 모듈 ($pdf로도 쓸 수 있음)을 쓸 수 있습니다: PDF의 정보, 텍스트, 개요, 이미지 읽기, 텍스트 검색, 페이지를 이미지로 렌더링, 페이지 병합, 분할, 추출, 삭제, 재정렬, 회전, 이미지, 텍스트, HTML로 PDF 생성, 암호화와 복호화. 각 메서드에는 완료될 때까지 기다리는 형태와 Async 형태가 있으며 진행 상황 보고와 취소를 지원합니다
- `기능` pdf.text의 ocr 옵션은 설치된 OCR 플러그인 (ML Kit OCR, Paddle OCR, Rapid OCR)으로 텍스트 레이어가 없는 스캔 페이지를 인식합니다
- `기능` pdf.view는 플러그인 뷰어에서 PDF를 엽니다. 스크립트는 페이지 이동과 닫기 이벤트를 받을 수 있고, 지정한 페이지로 이동하거나 뷰어를 닫을 수 있습니다
- `기능` Node.js와 Bun 스크립트에서도 pdf 모듈을 쓸 수 있습니다. 메서드는 Promise를 반환하며, signal이나 timeoutMs로 호출을 중단할 수 있습니다
- `기능` AutoJs6 6.8.1 이상으로 패키징한 앱에서도 pdf 모듈을 쓸 수 있습니다. 필요한 권한은 패키징할 때 자동으로 추가되며, 연 문서와 뷰어 세션은 그것을 만든 앱만 호출할 수 있습니다
- `기능` 뷰어: 연속 스크롤, 핀치 줌, 썸네일, 개요, 검색 강조, 페이지 이동, 암호 입력, 야간 모드. 런처에서 열 수 있고, 다른 앱에서 "연결 프로그램"이나 공유로 PDF를 넘길 수도 있습니다
- `기능` AutoJs6 파일 관리자: PDF를 탭하면 플러그인 뷰어에서 열리며, 압축 파일 안의 512 MiB 이하 PDF도 바로 열 수 있습니다. 메뉴에서 병합, 분할, 이미지로 변환, 텍스트 추출, 암호화, 비밀번호 제거를 쓸 수 있습니다
- `기능` 홈 화면: 도구, 작업 진행 상황, 최근 파일, 모든 파일 접근 권한 허용 (도구 상자에만 필요합니다)
- `기능` 최근 파일에 시스템 파일 선택기로 연 파일 (최대 20개)이 기록되어 다시 시작한 뒤에도 바로 열 수 있습니다. 파일이 삭제되었거나 접근할 수 없으면 안내를 표시하고 목록에서 제거합니다
- `기능` 도구 상자에 8가지 도구 제공: 병합, 분할 (페이지 범위 또는 n페이지마다), PDF를 이미지로 (해상도, 형식, 페이지), 이미지를 PDF로 (페이지 크기, 방향, 여백), 암호화, 비밀번호 제거, 텍스트 추출 (TXT로 저장), 미리보기 이미지로 페이지 회전, 삭제, 재정렬. 처리 로직은 파일 관리자 진입점과 같습니다
- `기능` 도구 상자 작업은 포그라운드 서비스에서 차례로 실행되며 도구 화면을 떠나도 계속됩니다. 알림에 진행 상황이 표시되고 취소할 수 있으며, 완료 후 결과를 뷰어에서 열거나 공유할 수 있습니다
- `기능` 내장 파일 탐색기에서 여러 파일을 선택하고 폴더를 만들 수 있습니다. 시스템 파일 선택기도 사용할 수 있습니다
- `기능` 설정 화면: 기본 출력 폴더 (Documents/3-Paper PDF), PDF를 이미지로 변환할 때의 기본 해상도, 언어, 야간 모드, 테마 색, 런처 아이콘, 권한 상태, 릴리스 기록, 업데이트 확인
- `기능` 업데이트 확인: 설정 화면에서 직접 확인하거나 홈 화면을 열 때 자동으로 확인합니다 (최대 12시간마다 한 번). 릴리스 정보는 공식 플러그인 색인에서 가져옵니다
- `기능` 런처와 플러그인 센터 아이콘을 밝게 / 어둡게 두 가지로 제공합니다. 런처는 기본으로 적응형 자동 아이콘 (시스템의 밝게 / 어둡게를 따름) 을 사용하며, 적응형 밝게, 적응형 어둡게, 투명 배경 스타일도 내장되어 있습니다. 설정 화면에서 전환할 수 있습니다
- `기능` 모든 ABI에 공통인 APK 하나: PDF 처리에는 네이티브 라이브러리가 없으며, 유일한 네이티브 라이브러리는 Jetpack Compose의 것으로 16 KB 메모리 페이지에 맞춰 정렬되어 있습니다
- `기능` 10개 언어의 README, 플러그인 센터 설명, 변경 기록을 JSON 소스에서 생성
- `의존성` PDFBox-Android 2.0.27.0 추가 (텍스트, 개요, 이미지, 페이지 작업, 암호화)
- `의존성` Jetpack Compose BOM 2026.09.00 추가 (플러그인 화면)
- `의존성` Navigation Compose 2.10.2 추가
- `의존성` Material Color Utilities (MaterialKolor) 4.1.1 추가 (플러그인 화면의 테마 색)
- `의존성` AndroidX Core KTX 1.18.0 추가

##### 더 많은 릴리스 기록은 다음을 참고

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-ko.md)

******

### 빌드

******

이 절은 소스에서 플러그인을 빌드하려는 개발자를 위한 것입니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\generate_icon_studio.py --check
py .python\check_repository.py
```

******

### 라이선스

******

프로젝트 코드는 [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE)으로 배포됩니다. 서드파티 구성 요소와 데이터는 서드파티 고지에서 확인할 수 있습니다.

- [제3자 고지 (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### 관련 링크

******

- AutoJs6 프로젝트: https://github.com/SuperMonster003/AutoJs6
