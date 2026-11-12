<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>PDF 파일 읽기, 렌더링, 병합, 분할, 생성과 암호화, 뷰어와 파일 관리자 진입점 내장</p>

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

3-Paper PDF는 AutoJs6의 PDF 플러그인이자, AutoJs6 없이도 단독으로 쓸 수 있는 PDF 도구 상자 겸 뷰어입니다. PDF의 정보, 텍스트, 개요, 내장 이미지를 읽고 페이지를 이미지로 렌더링할 수 있습니다. 페이지를 병합, 분할, 추출, 삭제, 재정렬, 회전할 수 있고, 이미지, 텍스트, HTML로 PDF를 생성하며 문서를 암호화하거나 복호화할 수 있습니다.

처리는 플러그인 안에서 Android의 `PdfRenderer`와 순수 Java인 PDFBox-Android로 이루어지므로 AutoJs6 호스트는 가볍게 유지됩니다. AutoJs6 스크립트는 전역 모듈 `pdf`로 이 기능을 사용합니다. 스크립트가 넘긴 경로는 AutoJs6가 열어서 플러그인에 넘기므로, 플러그인은 스크립트 파일을 처리하는 데 저장소 권한이 필요 없습니다. 플러그인에는 뷰어가 내장되어 있어 런처, AutoJs6 파일 관리자, 스크립트 (`pdf.view`)에서 열 수 있습니다.

> 현재는 개발 미리보기 버전입니다: AutoJs6 플러그인 센터가 플러그인을 인식할 수 있지만, 읽기, 렌더링, 페이지 작업, 뷰어, 도구 상자, `pdf` 모듈은 아직 제공되지 않습니다. 아래 내용은 1.0.0의 설계 목표이며, 진행 상황은 로드맵을 참고하세요.

******

### 기능 하이라이트

******

- 읽기: 페이지 수, 페이지 크기, 메타데이터, 암호화 상태와 권한; 페이지별 텍스트와 개요 (북마크); 페이지 번호와 위치를 돌려주는 텍스트 검색; 내장 이미지 목록과 내보내기.
- 렌더링: 페이지를 `Image` 객체나 PNG / JPEG / WebP 파일로 렌더링합니다. 기본값은 144 dpi이며, dpi, 배율, 목표 너비와 높이로도 지정할 수 있습니다.
- 페이지 작업: 병합 (북마크 유지), 분할, 추출, 삭제, 재정렬, 회전; 페이지 번호는 1부터 세며, 범위는 `'1-3,5,8-'`처럼 씁니다.
- 생성: 이미지를 PDF로 (JPEG는 다시 인코딩하지 않고 그대로 포함), 텍스트를 PDF로 (글꼴 지정 가능), HTML을 PDF로 (원격 URL은 불러오지 않음).
- 암호화: 암호로 암호화된 문서 열기, AES-256으로 암호화하고 권한 설정, 암호화 제거.
- 뷰어: 연속 스크롤, 핀치 줌, 썸네일, 개요, 검색 강조, 암호 입력, 야간 모드; 스크립트에서 뷰어를 열고 페이지 이동과 닫기 이벤트를 받을 수 있습니다.
- 도구 상자와 파일 관리자: 병합, 분할, 이미지로 변환, 이미지를 PDF로, 암호화 / 복호화, 텍스트 추출 마법사; AutoJs6 파일 관리자에서 PDF를 플러그인 뷰어로 바로 열 수 있고, 더보기 메뉴에서 같은 도구를 쓸 수 있습니다.
- OCR: `pdf.text(path, { ocr: true })`는 텍스트 레이어가 없는 스캔 페이지에 AutoJs6의 OCR 엔진을 호출합니다.

******

### 사용 방법

******

1. [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases)에서 APK를 내려받아 설치합니다. 모든 ABI에 공통인 패키지 하나만 있습니다.
2. 스크립트에서 `pdf` 모듈을 쓰려면 AutoJs6를 내부 빌드 6641 (6.8.1) 이상으로 업데이트하고 플러그인 센터에서 플러그인을 활성화하세요.
3. 내장 도구 상자를 쓰려면 플러그인을 열고 요청이 나오면 모든 파일 접근 권한을 허용하세요. 스크립트 작업에는 이 권한이 필요 없습니다.

> 플러그인은 Android 7.0 (API 24) 이상 기기를 지원합니다. PDF 처리에는 시스템 API와 순수 Java 라이브러리만 쓰며, 유일한 네이티브 라이브러리는 Jetpack Compose의 것으로 16 KB 메모리 페이지에 맞춰 정렬되어 있으므로, 모든 ABI가 패키지 하나를 함께 씁니다.

******

### 스크립트 API

******

스크립트는 전역 모듈 `pdf` (`$pdf`로도 쓸 수 있음)로 이 기능을 사용합니다 (1.0.0 설계 초안이며, 출시 버전의 문서가 우선합니다):

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

- `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` 같은 간편 메서드는 호출할 때마다 문서를 열고 닫습니다. `pdf.open(path, options)`는 `PdfDocument`를 반환하며 여러 작업이 한 번의 열기를 공유합니다. 스크립트마다 동시에 최대 8개 문서를 열 수 있습니다.
- 각 메서드에는 완료될 때까지 기다리는 형태와 Promise를 반환하는 `xxxAsync` 형태가 있습니다. `xxxAsync`는 `options.onProgress(progress)`를 받고, 반환된 Promise에는 `cancel()`이 있습니다. 스크립트를 멈추면 진행 중인 작업이 취소되고 완성되지 않은 출력은 삭제됩니다.
- `pdf.view(path, options)`는 뷰어를 열고 `PdfViewSession`을 반환합니다. `page`와 `close` 이벤트를 받을 수 있고 `goTo(page)`와 `close()`를 호출할 수 있습니다.
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

******

### 권한과 보안

******

플러그인은 권한 범위를 최대한 좁게 유지합니다:

- 보호된 진입점: 기능 서비스, 파일 관리자 진입 서비스, 활성화 진입점은 AutoJs6 플러그인 권한 (`org.autojs.permission.PLUGIN`)으로 보호되어 다른 앱이 직접 호출할 수 없습니다.
- 저장소: 스크립트의 파일은 파일 디스크립터로 전달되므로 플러그인은 스크립트를 위해 저장소 권한을 요청하지 않습니다. 모든 파일 접근 권한은 도구 상자와 뷰어에서 선택한 파일에만 쓰입니다.
- HTML을 PDF로: 전달된 HTML 문자열이나 로컬 파일만 렌더링하며 원격 URL은 불러오지 않습니다.
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

#### v1.0.0

_2026/11/12_

- `힌트` 개발 미리보기: AutoJs6 플러그인 센터가 플러그인을 인식할 수 있지만, 읽기, 렌더링, 페이지 작업, 뷰어, 도구 상자, pdf 스크립트 모듈은 아직 제공되지 않습니다
- `힌트` AutoJs6 6.8.1 (6641) 이상이 필요합니다 (정확한 최소 버전은 호스트 쪽 변경이 반영된 뒤 채워 넣습니다)
- `기능` 플러그인 골격: Wake 활성화 프로토콜, 플러그인 센터 식별 서비스, PDF 기능 서비스 (계약 버전 1), 파일 관리자 진입 서비스
- `기능` 홈 화면에서 모든 파일 접근 권한을 허용할 수 있습니다 (도구 상자에만 필요합니다)
- `기능` 모든 ABI에 공통인 APK 하나: PDF 처리에는 네이티브 라이브러리가 없으며, 유일한 네이티브 라이브러리는 Jetpack Compose의 것으로 16 KB 메모리 페이지에 맞춰 정렬되어 있습니다
- `기능` 10개 언어의 README, 플러그인 센터 설명, 변경 기록을 JSON 소스에서 생성
- `의존성` PDFBox-Android 2.0.27.0 추가 (텍스트, 개요, 이미지, 페이지 작업, 암호화)
- `의존성` Jetpack Compose BOM 2026.09.00 추가 (플러그인 화면)
- `의존성` Navigation Compose 2.10.2 추가
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
