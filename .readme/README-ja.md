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

処理はプラグイン内で Android の `PdfRenderer` と純 Java の PDFBox-Android によって行われるため, AutoJs6 ホストは軽量なままです. AutoJs6 のスクリプトはグローバルモジュール `pdf` からこれらの機能を使い, Node.js Runtime と Bun Runtime のプラグインのスクリプトからも使えます. スクリプトが渡したパスは AutoJs6 が開いてからプラグインに渡すので, プラグインはスクリプトのファイルを処理するのにストレージ権限を必要としません. プラグインにはビューアーが内蔵されており, ランチャー, AutoJs6 のファイルマネージャー, 他のアプリの "アプリで開く", スクリプト (`pdf.view`) から開けます.

> AutoJs6 スクリプトの `pdf` モジュールとファイルマネージャーの入口には AutoJs6 6.8.1 (内部バージョン 6641) 以降が必要です. それより古い AutoJs6 では, プラグインセンターでこのプラグインが非対応と表示されます. ツールボックスとビューアーは AutoJs6 に依存せず, 単独で使えます.

******

### 機能ハイライト

******

- 読み込み: ページ数, ページサイズ, メタデータ, 暗号化の状態と権限. ページごとのテキストとアウトライン (しおり). ページ番号と位置を返すテキスト検索. 埋め込み画像の一覧と書き出し.
- レンダリング: ページを `Image` オブジェクトまたは PNG / JPEG / WebP ファイルにします. 既定は 144 dpi で, dpi, 拡大率, 目標の幅と高さでも指定できます.
- ページ操作: 結合 (しおりを保持), 分割, 抽出, 削除, 並べ替え, 回転. ページ番号は 1 から数え, 範囲は `'1-3,5,8-'` のように書きます.
- 生成: 画像から PDF (JPEG は再エンコードせずにそのまま埋め込み), テキストから PDF (フォント指定可), HTML から PDF (リモートの URL は読み込みません).
- 暗号化: パスワードで暗号化文書を開く, AES-256 で暗号化して権限を設定する, 暗号化を解除する.
- ビューアー: 連続スクロール, ピンチズーム, サムネイル, アウトライン, 検索結果のハイライト, ページ移動, パスワード入力, ナイトモード. 他のアプリから "アプリで開く" や共有で PDF を渡せます. スクリプトからビューアーを開き, ページ移動と終了のイベントを受け取れます.
- ツールボックス: 結合, 分割, 画像への変換, 画像から PDF, 暗号化, 復号, テキスト抽出, ページの整理の 8 つのツール. タスクはバックグラウンドで順番に実行され, 進行状況は通知に表示されます. 最近のファイルには, システムのファイル選択から開いたファイルも記録されます.
- AutoJs6 のファイルマネージャー: PDF をタップするとプラグインのビューアーで開き, 圧縮ファイル内の 512 MiB 以下の PDF も直接開けます. メニューから結合, 分割, 画像への変換, テキスト抽出, 暗号化, パスワードの解除を使えます.
- OCR: `pdf.text(path, { ocr: true })` はインストール済みの OCR プラグイン (ML Kit OCR, Paddle OCR, Rapid OCR) を使って, テキストレイヤーのないスキャンページを認識します.
- Node.js と Bun: Node.js Runtime 2.2.0 と Bun Runtime 0.23.0 以降で同じ `pdf` モジュールを使えます. メソッドは Promise を返し, `signal` または `timeoutMs` で呼び出しを中止できます.

******

### 使用方法

******

1. [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) から APK をダウンロードしてインストールします. パッケージはすべての ABI 共通の 1 つだけです.
2. AutoJs6 のスクリプトやファイルマネージャーで使うには, AutoJs6 を 6.8.1 (内部バージョン 6641) 以降に更新し, プラグインセンターでこのプラグインが有効になっていることを確認します.
3. 内蔵のツールボックスを使うには, プラグインを開き, 求められたらすべてのファイルへのアクセスを許可します. スクリプトの操作にはこの権限は不要です.

> プラグインは Android 7.0 (API 24) 以降の端末に対応しています. PDF の処理にはシステム API と純 Java のライブラリだけを使います. 唯一のネイティブライブラリは Jetpack Compose のもので, 16 KB メモリページに揃えられているため, パッケージはすべての ABI 共通の 1 つです.

******

### 互換性

******

- Android 7.0 (API 24) 以降. パッケージはすべての ABI 共通の 1 つです. ツールボックスとビューアーは AutoJs6 に依存しません.
- AutoJs6 のスクリプトとファイルマネージャー: AutoJs6 6.8.1 (内部バージョン 6641) 以降が必要です.
- Node.js: Node.js Runtime プラグイン 2.2.0 以降が必要で, `require('autojs6:pdf')` で読み込みます. プロジェクトの `project.json` の `node.permissions` に `files` を宣言し, ファイルの書き込みには `files.write`, 文字認識には `ocr`, ビューアーを開くには `ui` も宣言します.
- Bun: Bun Runtime プラグイン 0.23.0 以降 (Android 13 以降) が必要で, `host.pdf` から使います. Bun Runtime の設定画面で `pdf.control` をオンにし, 文字認識には `ocr.control` もオンにします.
- OCR: スキャンページの文字認識には別途 OCR プラグイン (ML Kit OCR, Paddle OCR, Rapid OCR) が必要です. ない場合, スキャンページの認識は `OCR_UNAVAILABLE` で失敗しますが, その他の機能には影響しません.

******

### スクリプト API

******

AutoJs6 のスクリプトはグローバルモジュール `pdf` (`$pdf` とも書けます) からこれらの機能を使います (詳しくは [AutoJs6 ドキュメント > PDF](https://docs.autojs6.com/#/pdf) を参照):

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

Node.js と Bun では同名のメソッドが Promise を返します (Bun は `host.pdf` から使います):

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

- `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` などの簡易メソッドは呼び出しごとに文書を開いて閉じます. `pdf.open(path, options)` は `PdfDocument` を返し, 複数の操作で 1 回のオープンを共有します. 1 つのスクリプトで同時に開ける文書は最大 8 つです.
- 各メソッドには完了まで待つ形式と Promise を返す `xxxAsync` 形式があり, 引数と結果は同じです. 完了まで待つ形式は UI スレッドでは呼び出せない (`INVALID_ARGUMENT` になる) ため, UI スクリプトでは `xxxAsync` を使います. ファイルを書き込むメソッドは `onProgress` を受け付け, Promise には `cancel()` があります. 取り消しやスクリプトの停止で書きかけのファイルが残ることはありません.
- `pdf.merge` の `displayNames` は入力ごとのしおりの名前です. `null` はファイル名 (`.pdf` を除く), 空白の文字列はその文書のタイトル (タイトルがなければ `Document <n>`) になります. オプションが約 63 KiB を超えると `LIMIT_EXCEEDED` になり, 何も書き出しません.
- `pdf.fromHtml` は既定でページ内のスクリプトを実行し, `javaScript: false` で無効にできます. 外部のコンテンツは一切読み込まず, ネットワーク要求には空の内容で応答して `warnings` に記録します.
- `pdf.view(path, options)` はビューアーを開いて `PdfViewSession` を返します. `page` / `close` のイベントを受け取り, `goTo(page)`, `state()`, `close()` を呼び出せます. AutoJs6 がフォアグラウンドにないとき (Android 10 以降で, 他のアプリの上に重ねて表示する権限がない場合) は `UI_REQUIRES_FOREGROUND` になります.
- Node.js と Bun: すべてのメソッドが Promise を返します. `signal` (AbortSignal) と `timeoutMs` で呼び出しを中止でき, どちらも `CANCELLED` で拒否され, タイムアウトの `cause` は `TimeoutError` です. `timeoutMs` は 0 より大きく 2147483647 以下の有限の数で, 小数は切り上げられます.
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

#### プラグインセンターでこのプラグインが非対応と表示されるのはなぜですか?

このプラグインのスクリプトモジュールとファイルマネージャーの入口には AutoJs6 6.8.1 (内部バージョン 6641) 以降が必要です. AutoJs6 を更新すると有効にできます. それまでもツールボックスとビューアーは普段どおり使えます.

#### AutoJs6 のファイルマネージャーで圧縮ファイル内の PDF を直接開けますか?

開けます. 圧縮ファイル内の 512 MiB 以下の PDF はプラグインのビューアーで直接開けます. それより大きいものは先に展開してください.

#### 最近のファイルの項目が開けなくなったときは?

システムのファイル選択から開いたファイルは読み取りの許可を保持するので, 再起動後もそのまま開けます. ファイルが削除または移動された場合や, 許可が取り消された場合は, 項目をタップするとメッセージが表示され, リストから削除されます. "PDF を開く" からもう一度選んでください.

******

### 権限とセキュリティ

******

プラグインは必要な権限をできるだけ少なくしています:

- 保護された入口: 機能サービス, ファイルマネージャーの入口サービス, 有効化の入口は AutoJs6 のプラグイン権限 (`org.autojs.permission.PLUGIN`) で保護されており, 他のアプリから直接呼び出すことはできません.
- ストレージ: スクリプトのファイルはファイル記述子として渡されるため, プラグインはスクリプトのためにストレージ権限を求めません. すべてのファイルへのアクセスは, ツールボックスとビューアーで選んだファイルにだけ使われます.
- HTML から PDF: プラグインは外部のコンテンツを一切読み込みません (ネットワーク要求には空の内容で応答し, ローカルファイルと `content:` アドレスはブロックされ, ページの移動もできません). ページ内のスクリプトは `javaScript: false` で無効にできます.
- 最近のファイル: システムのファイル選択から開いたファイルは, 次回そのまま開けるよう読み取りの許可を保持します (最大 20 件). 項目が押し出されたとき, 削除されたとき, リストがクリアされたときは, すぐに許可を解放します. 他のアプリから "アプリで開く" や共有で渡されたファイルには一時的な許可しかなく, 最近のファイルには入りません.
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

- `ヒント` AutoJs6 スクリプトの pdf モジュールとファイルマネージャーの入口には AutoJs6 6.8.1 (内部バージョン 6641) 以降が必要です. それより古い AutoJs6 ではプラグインセンターでこのプラグインが非対応と表示されますが, ツールボックスとビューアーは単独で使えます
- `ヒント` Node.js や Bun のスクリプトで使うには Node.js Runtime 2.2.0 または Bun Runtime 0.23.0 以降が必要です
- `機能` AutoJs6 のスクリプトで pdf モジュール ($pdf とも書けます) を使えます: PDF の情報, テキスト, アウトライン, 画像の読み込み, テキスト検索, ページの画像へのレンダリング, ページの結合, 分割, 抽出, 削除, 並べ替え, 回転, 画像, テキスト, HTML からの PDF の生成, 暗号化と復号. 各メソッドには完了まで待つ形式と Async 形式があり, 進行状況の通知と取り消しができます
- `機能` pdf.text の ocr オプションは, インストール済みの OCR プラグイン (ML Kit OCR, Paddle OCR, Rapid OCR) を使ってテキストレイヤーのないスキャンページを認識します
- `機能` pdf.view はプラグインのビューアーで PDF を開きます. スクリプトはページ移動と終了のイベントを受け取り, 指定したページへの移動やビューアーを閉じることができます
- `機能` Node.js と Bun のスクリプトでも pdf モジュールを使えます. メソッドは Promise を返し, signal または timeoutMs で呼び出しを中止できます
- `機能` ビューアー: 連続スクロール, ピンチズーム, サムネイル, アウトライン, 検索結果のハイライト, ページ移動, パスワード入力, ナイトモード. ランチャーから開けるほか, 他のアプリから "アプリで開く" や共有で PDF を渡せます
- `機能` AutoJs6 のファイルマネージャー: PDF をタップするとプラグインのビューアーで開き, 圧縮ファイル内の 512 MiB 以下の PDF も直接開けます. メニューから結合, 分割, 画像への変換, テキスト抽出, 暗号化, パスワードの解除を使えます
- `機能` ホーム画面: ツール, タスクの進行状況, 最近のファイル, すべてのファイルへのアクセスの許可 (必要なのはツールボックスだけです)
- `機能` 最近のファイルに, システムのファイル選択から開いたファイル (最大 20 件) が記録され, 再起動後もそのまま開けます. ファイルが削除された場合やアクセスできなくなった場合は, メッセージを表示してリストから削除します
- `機能` ツールボックスに 8 つのツール: 結合, 分割 (ページ範囲または n ページごと), PDF から画像 (解像度, 形式, ページ), 画像から PDF (用紙サイズ, 向き, 余白), 暗号化, パスワードの解除, テキストの抽出 (TXT で保存), サムネイルによるページの回転, 削除, 並べ替え. 処理はファイルマネージャーの入口と共通です
- `機能` ツールボックスのタスクはフォアグラウンドサービスで順番に実行され, ツール画面を離れても続行します. 通知に進行状況が表示されてキャンセルでき, 完了後は結果をビューアーで開くか共有できます
- `機能` 内蔵のファイルブラウザーで複数のファイルを選択したりフォルダーを作成したりできます. システムのファイル選択も使えます
- `機能` 設定画面: 既定の出力フォルダー (Documents/3-Paper PDF), PDF から画像への既定の解像度, 言語, ナイトモード, テーマカラー, ランチャーアイコン, 権限の状態, リリース履歴, 更新の確認
- `機能` 更新の確認: 設定画面で手動で確認するか, ホーム画面を開いたときに自動で確認します (12 時間あたり最大 1 回). リリース情報は公式プラグインインデックスから取得します
- `機能` ランチャーとプラグインセンターのアイコンに明暗 2 種類を用意. ランチャーは既定でアダプティブ自動アイコン (システムの明暗に追従) を使い, アダプティブ明色, アダプティブ暗色, 透明背景のスタイルも内蔵しています. 設定画面で切り替えられます
- `機能` すべての ABI 共通の APK が 1 つ: PDF の処理にネイティブライブラリは含まれず, 唯一のネイティブライブラリは Jetpack Compose のもので, 16 KB メモリページに揃えられています
- `機能` 10 言語の README, プラグインセンターの説明, 変更履歴を JSON ソースから生成
- `依存関係` PDFBox-Android 2.0.27.0 を追加 (テキスト, アウトライン, 画像, ページ操作, 暗号化)
- `依存関係` Jetpack Compose BOM 2026.09.00 を追加 (プラグインの画面)
- `依存関係` Navigation Compose 2.10.2 を追加
- `依存関係` Material Color Utilities (MaterialKolor) 4.1.1 を追加 (プラグインの画面のテーマカラー)
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
