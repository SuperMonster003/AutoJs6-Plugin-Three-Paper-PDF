<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>PDF ファイルの読み込み, レンダリング, 結合, 分割, 生成と暗号化, ビューアーとファイルマネージャーの入口を内蔵</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 言語 (Languages)

******

現在の README.md は次の言語に対応しています:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ar.md)

******

### 概要

******

3-Paper PDF は AutoJs6 の PDF プラグインであり, AutoJs6 がなくても単独で使える PDF ツールボックス兼ビューアーでもあります. PDF の情報, テキスト, アウトライン, 埋め込み画像を読み取り, ページを画像にレンダリングできます. ページの結合, 分割, 抽出, 削除, 並べ替え, 回転ができ, 画像, テキスト, HTML から PDF を生成し, 文書を暗号化または復号できます.

処理はプラグイン内で Android の `PdfRenderer` と純 Java の PDFBox-Android によって行われるため, AutoJs6 ホストは軽量なままです. AutoJs6 のスクリプトはグローバルモジュール `pdf` からこれらの機能を使います. スクリプトが渡したパスは AutoJs6 が開いてからプラグインに渡すので, プラグインはスクリプトのファイルを処理するのにストレージ権限を必要としません. プラグインにはビューアーが内蔵されており, ランチャー, AutoJs6 のファイルマネージャー, スクリプト (`pdf.view`) から開けます.

> 現在は開発プレビュー版です: AutoJs6 のプラグインセンターはプラグインを検出できますが, 読み込み, レンダリング, ページ操作, ビューアー, ツールボックス, `pdf` モジュールはまだ提供されていません. 以下は 1.0.0 の設計目標です. 進捗はロードマップを参照してください.

******

### 機能ハイライト

******

- 読み込み: ページ数, ページサイズ, メタデータ, 暗号化の状態と権限. ページごとのテキストとアウトライン (しおり). ページ番号と位置を返すテキスト検索. 埋め込み画像の一覧と書き出し.
- レンダリング: ページを `Image` オブジェクトまたは PNG / JPEG / WebP ファイルにします. 既定は 144 dpi で, dpi, 拡大率, 目標の幅と高さでも指定できます.
- ページ操作: 結合 (しおりを保持), 分割, 抽出, 削除, 並べ替え, 回転. ページ番号は 1 から数え, 範囲は `'1-3,5,8-'` のように書きます.
- 生成: 画像から PDF (JPEG は再エンコードせずにそのまま埋め込み), テキストから PDF (フォント指定可), HTML から PDF (リモートの URL は読み込みません).
- 暗号化: パスワードで暗号化文書を開く, AES-256 で暗号化して権限を設定する, 暗号化を解除する.
- ビューアー: 連続スクロール, ピンチズーム, サムネイル, アウトライン, 検索結果のハイライト, パスワード入力, ナイトモード. スクリプトからビューアーを開き, ページ移動と終了のイベントを受け取れます.
- ツールボックスとファイルマネージャー: 結合, 分割, 画像への変換, 画像から PDF, 暗号化 / 復号, テキスト抽出のウィザード. AutoJs6 のファイルマネージャーでは PDF をプラグインのビューアーで直接開け, オーバーフローメニューから同じツールを使えます.
- OCR: `pdf.text(path, { ocr: true })` はテキストレイヤーのないスキャンページに対して AutoJs6 の OCR エンジンを呼び出します.

******

### 使用方法

******

1. [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) から APK をダウンロードしてインストールします. パッケージはすべての ABI 共通の 1 つだけです.
2. スクリプトで `pdf` モジュールを使うには, AutoJs6 を内部バージョン 6641 (6.8.1) 以降に更新し, プラグインセンターでプラグインを有効にします.
3. 内蔵のツールボックスを使うには, プラグインを開き, 求められたらすべてのファイルへのアクセスを許可します. スクリプトの操作にはこの権限は不要です.

> プラグインは Android 7.0 (API 24) 以降の端末に対応しています. PDF の処理にはシステム API と純 Java のライブラリだけを使います. 唯一のネイティブライブラリは Jetpack Compose のもので, 16 KB メモリページに揃えられているため, パッケージはすべての ABI 共通の 1 つです.

******

### スクリプト API

******

スクリプトはグローバルモジュール `pdf` (`$pdf` とも書けます) からこれらの機能を使います (1.0.0 の設計草案です. リリース版のドキュメントが優先されます):

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

- `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` などの簡易メソッドは呼び出しごとに文書を開いて閉じます. `pdf.open(path, options)` は `PdfDocument` を返し, 複数の操作で 1 回のオープンを共有します. 1 つのスクリプトで同時に開ける文書は最大 8 つです.
- 各メソッドには, 完了まで待つ形式と Promise を返す `xxxAsync` 形式があります. `xxxAsync` は `options.onProgress(progress)` を受け付け, 返される Promise には `cancel()` があります. スクリプトを停止すると実行中の操作は取り消され, 未完成の出力は削除されます.
- `pdf.view(path, options)` はビューアーを開いて `PdfViewSession` を返します. `page` と `close` のイベントを受け取り, `goTo(page)` と `close()` を呼び出せます.
- 失敗すると `PdfError` が投げられ, その `code` は `PASSWORD_REQUIRED`, `WRONG_PASSWORD`, `PAGE_OUT_OF_RANGE` のような安定したエラーコードです. `pdf.isAvailable()` でプラグインが使えるかを事前に確認できます.

******

### よくある質問

******

#### AutoJs6 がなくても使えますか?

使えます. ツールボックスとビューアーは単独で動作します. AutoJs6 が必要なのは, スクリプトで PDF を処理するときだけです.

#### プラグインがすべてのファイルへのアクセスを求めるのはなぜですか?

必要なのはツールボックスと, ランチャーから開いたビューアーだけで, 画面で選んだファイルを読み書きするために使います. スクリプトが渡したパスは AutoJs6 が開いてファイル記述子としてプラグインに渡すので, プラグインのストレージ権限は不要です.

#### プラグインにネイティブライブラリは含まれていますか?

PDF の処理にネイティブライブラリは使いません. ページのレンダリングはシステムの `PdfRenderer`, その他の機能は純 Java の PDFBox-Android で行います. パッケージに含まれる唯一のネイティブライブラリは, Jetpack Compose に付属する `libandroidx.graphics.path.so` です.

******

### 権限とセキュリティ

******

プラグインは必要な権限をできるだけ少なくしています:

- 保護された入口: 機能サービス, ファイルマネージャーの入口サービス, 有効化の入口は AutoJs6 のプラグイン権限 (`org.autojs.permission.PLUGIN`) で保護されており, 他のアプリから直接呼び出すことはできません.
- ストレージ: スクリプトのファイルはファイル記述子として渡されるため, プラグインはスクリプトのためにストレージ権限を求めません. すべてのファイルへのアクセスは, ツールボックスとビューアーで選んだファイルにだけ使われます.
- HTML から PDF: 渡された HTML 文字列またはローカルファイルだけをレンダリングし, リモートの URL は読み込みません.
- ネットワークとパスワード: ネットワークは更新の確認にだけ使います. 文書のパスワードはその操作の間だけ使われ, プラグインには保存されません.

******

### エンジンと依存関係

******

ページのレンダリングには Android の `PdfRenderer` を使います. テキスト, アウトライン, 画像, ページ操作, 暗号化には [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, 純 Java) を使い, 暗号化は Bouncy Castle (MIT 形式のライセンス) に依存します. PDF の処理にネイティブライブラリは含まれず, pdfium や MuPDF のようなネイティブのレンダラーは使いません.

******

### プラグインインターフェース

******

以下の情報は AutoJs6 ホストとプラグイン開発者向けです. ホストはこれらの識別子でプラグインを検出し, 機能ネゴシエーションを行います:

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

### 開発ロードマップ

******

計画, 設計上の決定, 進捗は ROADMAP.md のチェックリストで管理しています. 未チェックの項目は計画であり, 現在のバージョンの機能ではありません.

- [ROADMAP.md を見る](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### リリース履歴

******

#### v1.0.0

_2026/11/12_

- `ヒント` 開発プレビュー: AutoJs6 のプラグインセンターはプラグインを検出できますが, 読み込み, レンダリング, ページ操作, ビューアー, ツールボックス, pdf スクリプトモジュールはまだ提供されていません
- `ヒント` AutoJs6 6.8.1 (6641) 以降が必要です (正確な最低バージョンはホスト側の変更が入り次第記入します)
- `機能` プラグインの骨格: Wake 起動プロトコル, プラグインセンターの識別サービス, PDF の機能サービス (契約バージョン 1), ファイルマネージャーの入口サービス
- `機能` ホーム画面からすべてのファイルへのアクセスを許可できます (必要なのはツールボックスだけです)
- `機能` ランチャーとプラグインセンターのアイコンに明暗 2 種類を用意. ランチャーは既定でアダプティブ自動アイコン (システムの明暗に追従) を使い, アダプティブ明色, アダプティブ暗色, 透明背景のスタイルも内蔵しています. 切り替えは今後の設定画面で提供します
- `機能` すべての ABI 共通の APK が 1 つ: PDF の処理にネイティブライブラリは含まれず, 唯一のネイティブライブラリは Jetpack Compose のもので, 16 KB メモリページに揃えられています
- `機能` 10 言語の README, プラグインセンターの説明, 変更履歴を JSON ソースから生成
- `依存関係` PDFBox-Android 2.0.27.0 を追加 (テキスト, アウトライン, 画像, ページ操作, 暗号化)
- `依存関係` Jetpack Compose BOM 2026.09.00 を追加 (プラグインの画面)
- `依存関係` Navigation Compose 2.10.2 を追加
- `依存関係` AndroidX Core KTX 1.18.0 を追加

##### その他のリリース履歴は以下を参照

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-ja.md)

******

### ビルド

******

この節はソースからプラグインをビルドしたい開発者向けです.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\generate_icon_studio.py --check
py .python\check_repository.py
```

******

### ライセンス

******

プロジェクトのコードは [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE) で公開されています. サードパーティのコンポーネントとデータについてはサードパーティ通知を参照してください.

- [サードパーティの表示 (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### 関連リンク

******

- AutoJs6 プロジェクト: https://github.com/SuperMonster003/AutoJs6
