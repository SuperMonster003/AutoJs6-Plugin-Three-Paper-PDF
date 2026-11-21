<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>PDF ファイルの読み込み, レンダリング, 結合, 分割, 生成, 圧縮と暗号化, フォームへの入力と透かしの追加, ビューアーとファイルマネージャーの入口を内蔵</p>

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

3-Paper PDF は AutoJs6 の PDF プラグインであり, AutoJs6 がなくても単独で使える PDF ツールボックス兼ビューアーでもあります. PDF の情報, テキスト, アウトライン, 埋め込み画像を読み取り, ページを画像にレンダリングできます. ページの結合, 分割, 抽出, 削除, 並べ替え, 回転ができ, 画像, テキスト, HTML から PDF を生成し, 文書を暗号化または復号できます. 1.1.0 からは, フォームの読み取りと入力, 透かし, ページ番号, ヘッダーとフッターの追加, OCR プラグインを使った検索可能な PDF の作成, ファイルサイズの圧縮, ドキュメントのプロパティとアウトラインの変更もできます.

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
- フォーム: フォームのフィールド (テキストフィールド, チェックボックス, ラジオボタン, コンボボックス, リストボックス) と現在の値を一覧し, フィールド名を指定して入力できます. 通常のページ内容へフラット化することもできます. `pdf.info` はフォームのフィールド数を返します.
- 透かしとページ番号: テキストまたは画像の透かし, ページ番号, ヘッダーとフッターを追加し, 位置, 回転, 不透明度, タイル状の配置, 対象のページを指定できます. テキストではプレースホルダー `{page}`, `{total}`, `{date}`, `{name}` を使えます.
- 検索可能な PDF: OCR プラグインでスキャンページを認識し, 見えないテキストレイヤーを書き込みます. 以後は文字の検索, 選択, コピーができ, テキストを読み込むときに認識し直す必要もありません. スクリプトで独自に認識した結果は `pdf.addTextLayer` で書き込めます.
- 圧縮: 埋め込み画像を指定した解像度と画質で再エンコードし, 圧縮されていないストリームを圧縮し, 必要に応じてメタデータを削除します. low / medium / high の 3 つのプリセットのほか独自の値も指定でき, 結果には圧縮前後のサイズが示されます.
- ドキュメントのプロパティとアウトライン: タイトル, 作成者, 件名, キーワード, 作成アプリケーションを変更できます. アウトライン (しおり) を読み出し, 変更して書き戻すことも, すべて削除することもできます.
- ビューアー: 連続スクロール, ピンチズーム, サムネイル, アウトライン, 検索結果のハイライト, ページ移動, パスワード入力, ナイトモード, 全画面表示. 他のアプリから "アプリで開く" や共有で PDF を渡せます. スクリプトからビューアーを開き, ページ移動と終了のイベントを受け取れます.
- ツールボックス: 結合, 分割, 画像への変換, 画像から PDF, 暗号化, 復号, テキスト抽出, ページの整理, 透かしの追加, ページ番号の追加 (どちらもページを切り替えられる 1 ページのプレビュー付き), 圧縮, フォームへの入力, ドキュメントのプロパティの 13 のツール. 透かし, ページ番号, 圧縮には独自の値も指定できます. タスクはバックグラウンドで順番に実行され, 進行状況は通知に表示されます. 結果画面では, 今回の処理を Rhino, Node.js, Bun のスクリプトとして書き出せます. 最近のファイルには, システムのファイル選択から開いたファイルも記録されます.
- AutoJs6 のファイルマネージャー: PDF をタップするとプラグインのビューアーで開き, 圧縮ファイル内の 512 MiB 以下の PDF も直接開けます. メニューから結合, 分割, 圧縮, 画像への変換, 透かしの追加, テキスト抽出, 暗号化, パスワードの解除を使え, OCR プラグインがあれば検索可能な PDF も作成できます.
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
- 1.1.0 で追加されたスクリプトのメソッド (フォーム, 透かしとページ番号, 検索可能な PDF, 圧縮, ドキュメントのプロパティとアウトライン): AutoJs6 6.8.1 以降と本プラグイン 1.1.0 以降が必要です. Node.js Runtime と Bun Runtime に必要なバージョンは他のメソッドと同じです. プラグインがまだ 1.0.x の場合, これらのメソッドは `UNSUPPORTED` で失敗してプラグインセンターでの更新を案内し, ファイルは書き出しません. 他のメソッドは通常どおり使えます.
- AutoJs6 でパッケージ化したアプリ: AutoJs6 6.8.1 (内部バージョン 6641) 以降でパッケージ化し, 実行する端末に本プラグインをインストールしておく必要があります. パッケージ化の際に `org.autojs.plugin.pdf.permission.RUNTIME` 権限が自動で追加されるため, `project.json` を変更する必要はありません (Node.js プロジェクトは `files`, Bun プロジェクトは `pdf.control` を宣言します).
- Node.js: Node.js Runtime プラグイン 2.2.0 以降が必要で, `require('autojs6:pdf')` で読み込みます. プロジェクトの `project.json` の `node.permissions` に `files` を宣言し, ファイルの書き込みには `files.write`, 文字認識には `ocr`, ビューアーを開くには `ui` も宣言します.
- Bun: Bun Runtime プラグイン 0.23.0 以降 (Android 13 以降) が必要で, `host.pdf` から使います. Bun Runtime の設定画面で `pdf.control` をオンにし, 文字認識には `ocr.control` もオンにします.
- OCR: スキャンページの文字認識と検索可能な PDF の作成には, 別途 OCR プラグイン (ML Kit OCR, Paddle OCR, Rapid OCR) が必要です. ない場合, この 2 つは `OCR_UNAVAILABLE` で失敗しますが, その他の機能には影響しません.

バージョンごとの動作要件と機能:

| 項目 | 1.0.0 | 1.1.0 |
| --- | --- | --- |
| Android | 7.0 (API 24) 以降 | 7.0 (API 24) 以降 |
| AutoJs6 | 6.8.1 (内部バージョン 6641) 以降 | 6.8.1 (内部バージョン 6641) 以降 |
| Node.js Runtime | 2.2.0 以降 | 2.2.0 以降 |
| Bun Runtime | 0.23.0 以降 (Android 13 以降) | 0.23.0 以降 (Android 13 以降) |
| OCR プラグイン (任意) | スキャンページの認識 | スキャンページの認識, 検索可能な PDF の作成 |
| スクリプトのメソッド | 読み込み, レンダリング, ページ操作, 生成, 暗号化, OCR, ビューアー | さらにフォーム, 透かしとページ番号, 検索可能な PDF, 圧縮, ドキュメントのプロパティとアウトライン |
| ツールボックス | 8 つのツール | 13 のツール, 独自の値, スクリプトの書き出し |
| AutoJs6 のファイルマネージャーのメニュー | 6 項目 | 9 項目 (圧縮, 透かしの追加, 検索可能な PDF の作成が追加) |

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
- 1.1.0 の書き出しメソッド (例えば `pdf.fillForm`, `pdf.stamp`, `pdf.compress`) も, 他の書き出しメソッドと同じく出力パスを省略 (または `null` を指定) でき, 結果は `Documents/3-Paper PDF/` に書き出されます. `PdfDocument` の同名メソッドは開いたときのパスワードをそのまま使うため, オプションに `password` は指定できません. ファイルを書き出すメソッドの結果には, `warnings` の各項目に 1 つずつ対応する `warningCodes` もあり, スクリプトでコードごとに処理できます.
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

#### 入力したフォームの値がビューアーに表示されないのはなぜですか?

Android 14 以前では, プラグインのビューアーはフラット化した後でないと入力した値を表示しません. 他の PDF アプリには影響しません. ビューアーで確認したい場合は, 入力時にフラット化してください (`flatten: true`, または "フォームに入力" ツールの "フィールドをフラット化"). フラット化したフィールドは編集できなくなります.

#### 圧縮してもファイルが小さくならないのはなぜですか?

圧縮で主に小さくなるのは埋め込み画像です. 画像がすでに十分小さい場合や, プラグインが再エンコードしない種類の画像 (例えば JPEG 2000, CMYK, 1 ビット画像) の場合は効果が限られます. 元のファイルより小さくならなくても結果は書き出され, 結果にその旨が示されるので, 元のファイルを使い続けることもできます.

******

### 権限とセキュリティ

******

プラグインは必要な権限をできるだけ少なくしています:

- 保護された入口: 機能サービス, ファイルマネージャーの入口サービス, 有効化の入口は AutoJs6 のプラグイン権限 (`org.autojs.permission.PLUGIN`) で保護されており, 他のアプリから直接呼び出すことはできません. AutoJs6 でパッケージ化したアプリ向けのサービスとビューアーの入口には `org.autojs.plugin.pdf.permission.RUNTIME` 権限が必要です (パッケージ化の際に自動で追加されます). これらの入口で開いた文書とビューアーのセッションは, それを作成したアプリからしか呼び出せず, ファイルもファイル記述子として渡されます.
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

#### v1.1.0

_2026/11/20_

- `ヒント` 新しいスクリプトのメソッドとファイルマネージャーの "検索可能な PDF を作成" には AutoJs6 6.8.1 以降が必要です. Node.js や Bun のスクリプトで使うには, さらに Node.js Runtime 2.2.0 または Bun Runtime 0.23.0 以降が必要です. ツールボックスの新しいツールは AutoJs6 に依存しません
- `機能` pdf.forms と pdf.fillForm: フォームのフィールド (テキストフィールド, チェックボックス, ラジオボタン, コンボボックス, リストボックス) を読み取り, フィールド名を指定して入力します. 通常のページ内容へのフラット化もできます. pdf.info にフォームのフィールド数を追加
- `機能` pdf.watermark と pdf.stamp: テキストまたは画像の透かし, ページ番号, ヘッダーとフッターを追加します. 位置, 回転, 不透明度, タイル状の配置, 対象のページを指定できます
- `機能` pdf.makeSearchable は OCR プラグインを使ってスキャンページに見えないテキストレイヤーを書き込み, 文字の検索とコピーができる PDF を作成します. pdf.addTextLayer はスクリプトで独自に認識した結果を書き込みます
- `機能` pdf.compress は埋め込み画像を再エンコードしてストリームを圧縮し, メタデータの削除もできます. low / medium / high の 3 つのプリセットのほか, 解像度と画質を独自に指定することもでき, 結果には圧縮前後のサイズが示されます
- `機能` pdf.setInfo はタイトル, 作成者, 件名, キーワード, 作成アプリケーションなどのドキュメントのプロパティを変更し, pdf.setOutline は pdf.outline が返す構造でアウトラインを書き換えるか削除します
- `機能` 新しいメソッドにはすべて Async 形式と PdfDocument の同名メソッドがあり, Node.js, Bun, AutoJs6 でパッケージ化したアプリでも使えます. ファイルを書き出すメソッドの結果には warningCodes もあり, スクリプトでメッセージをコードごとに処理できます
- `機能` ツールボックスに "PDF に透かしを追加" (1 ページのプレビュー付き), "ページ番号を追加", "PDF を圧縮", "フォームに入力", "ドキュメントのプロパティ" の 5 つのツールを追加し, 計 13 になりました. 透かし, ページ番号, 圧縮はプリセットのほか独自の値も入力できます
- `機能` 透かしとページ番号のツールの 1 ページのプレビューで, ドキュメントの任意のページに移動できます. プレビューのページ番号と総ページ数は表示中のページに合わせて変わり, 結果と同じになります. 選択したページに含まれないページはそのまま表示し, その旨を示します
- `機能` ツールボックスとファイルマネージャーの操作の結果画面で, 今回の処理を Rhino, Node.js, Bun のスクリプトとして書き出し, "スクリプトをコピー", ".js として保存", "AutoJs6 で開く" を使えます. スクリプト内のパスワードは null のままなので, 自分で入力してください
- `機能` AutoJs6 のファイルマネージャーの PDF メニューに "PDF を圧縮" と "PDF に透かしを追加" を追加し, 結果は元のファイルと同じ場所に保存します. OCR プラグインがあれば "検索可能な PDF を作成" も使えます
- `機能` ビューアーで全画面表示ができるようになりました. ページをタップしてバーを隠すと, ページ番号, ステータスバー, ナビゲーションバーも隠れ (画面の端から内側へスワイプするとシステムバーが一時的に表示されます), もう一度タップすると元に戻ります. 設定に "ビューアー" の項目を追加し, "全画面表示でページ番号を隠す" と "全画面表示でシステムバーを隠す" を個別にオフにできます
- `修正` 一部のシステムフォント (ColorOS 15 など) で, pdf.fromText が生成した PDF の一部のハイフン ("3-Paper" や "A-B" のもの) が, テキストの抽出や検索でヌル文字になる問題
- `修正` ビューアーで文書を開いたときに最初のページの上端が上部のバーに隠れ, 最後のページの末尾がサムネイルバーに隠れる問題. ページは文書の両端でバーを避けるようになり, バーの表示や非表示でページが動かなくなりました
- `改善` リリース履歴のページをバージョンごとに展開, 折りたたみできるようにしました. 折りたたむと日付と分類ごとの項目数を表示し, 展開すると分類名を一度だけ示してその下に項目を並べます. すべてまとめて展開, 折りたたみもでき, 開いたときは最新バージョンが展開されます. ページのタイトルは "バージョン履歴" から "リリース履歴" に変わり, サブタイトルにプラグイン名を表示します. 設定の "アプリについて" と更新ダイアログにある入口の名前も同じく変わりました
- `改善` ツールの結果画面に表示される処理のメッセージを, これまでの英語のみから画面の表示言語に変更
- `改善` プラグインセンターの使い方の説明を, はじめに, 主な機能, スクリプトでの使い方, 動作要件, よくある質問の 5 部構成に書き直し
- `改善` ツールの結果画面と失敗画面で, 上部バーの戻る矢印またはシステムの戻るキーを押すとオプション画面に戻り, 選んだファイルと入力した内容はそのまま残ります. 変更して再度実行でき, 新しい結果には番号が付くので前回の結果は上書きされません. オプション画面でもう一度戻るとツールを終了します

#### v1.0.0

_2026/11/14_

- `ヒント` AutoJs6 スクリプトの pdf モジュールとファイルマネージャーの入口には AutoJs6 6.8.1 (内部バージョン 6641) 以降が必要です. それより古い AutoJs6 ではプラグインセンターでこのプラグインが非対応と表示されますが, ツールボックスとビューアーは単独で使えます
- `ヒント` Node.js や Bun のスクリプトで使うには Node.js Runtime 2.2.0 または Bun Runtime 0.23.0 以降が必要です
- `機能` AutoJs6 のスクリプトで pdf モジュール ($pdf とも書けます) を使えます: PDF の情報, テキスト, アウトライン, 画像の読み込み, テキスト検索, ページの画像へのレンダリング, ページの結合, 分割, 抽出, 削除, 並べ替え, 回転, 画像, テキスト, HTML からの PDF の生成, 暗号化と復号. 各メソッドには完了まで待つ形式と Async 形式があり, 進行状況の通知と取り消しができます
- `機能` pdf.text の ocr オプションは, インストール済みの OCR プラグイン (ML Kit OCR, Paddle OCR, Rapid OCR) を使ってテキストレイヤーのないスキャンページを認識します
- `機能` pdf.view はプラグインのビューアーで PDF を開きます. スクリプトはページ移動と終了のイベントを受け取り, 指定したページへの移動やビューアーを閉じることができます
- `機能` Node.js と Bun のスクリプトでも pdf モジュールを使えます. メソッドは Promise を返し, signal または timeoutMs で呼び出しを中止できます
- `機能` AutoJs6 6.8.1 以降でパッケージ化したアプリでも pdf モジュールを使えます. 必要な権限はパッケージ化の際に自動で追加され, 開いた文書とビューアーのセッションはそれを作成したアプリからしか呼び出せません
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
