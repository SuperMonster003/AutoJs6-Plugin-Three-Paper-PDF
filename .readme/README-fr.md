<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Lecture, rendu, fusion, découpage, génération et chiffrement de fichiers PDF, avec une visionneuse intégrée et des entrées dans le gestionnaire de fichiers</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Langues (Languages)

******

Le README.md actuel prend en charge les langues suivantes:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-en.md)
- Français [fr] # actuel
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ar.md)

******

### Introduction

******

3-Paper PDF est le plugin PDF d'AutoJs6, et aussi une boîte à outils et une visionneuse PDF qui fonctionnent seules, sans AutoJs6. Il lit les informations, le texte, le plan et les images intégrées d'un PDF et rend les pages en images; il fusionne, découpe, extrait, supprime, réordonne et fait pivoter les pages; il génère des PDF à partir d'images, de texte ou de HTML, et chiffre ou déchiffre les documents.

Le traitement s'exécute dans le plugin avec le `PdfRenderer` d'Android et PDFBox-Android, en pur Java, si bien que l'hôte AutoJs6 reste léger. Les scripts AutoJs6 utilisent ces fonctions via le module global `pdf`; AutoJs6 ouvre les chemins transmis par les scripts et les remet au plugin, qui n'a donc besoin d'aucune autorisation de stockage pour traiter les fichiers des scripts. Le plugin possède sa propre visionneuse, que l'on ouvre depuis le lanceur, le gestionnaire de fichiers d'AutoJs6 ou un script (`pdf.view`).

> Il s'agit d'une préversion de développement: le centre des plugins d'AutoJs6 peut détecter le plugin, mais la lecture, le rendu, les opérations sur les pages, la visionneuse, la boîte à outils et le module `pdf` ne sont pas encore livrés. La suite décrit les objectifs de conception de la 1.0.0; consultez la feuille de route pour l'avancement.

******

### Points Forts

******

- Lecture: nombre de pages, tailles des pages, métadonnées, état du chiffrement et autorisations; texte et plan (signets) par page; recherche de texte avec numéros de page et positions; liste et export des images intégrées.
- Rendu: pages en objets `Image` ou en fichiers PNG / JPEG / WebP, à 144 dpi par défaut, ou selon une résolution, une échelle ou une largeur et une hauteur cibles.
- Opérations sur les pages: fusionner (en conservant les signets), découper, extraire, supprimer, réordonner et faire pivoter des pages; les numéros de page commencent à 1 et les plages s'écrivent `'1-3,5,8-'`.
- Génération: images vers PDF (JPEG intégrés tels quels, sans réencodage), texte vers PDF (avec la police de votre choix) et HTML vers PDF (aucune URL distante n'est chargée).
- Chiffrement: ouvrir des documents chiffrés avec un mot de passe, chiffrer en AES-256 et définir les autorisations, retirer le chiffrement.
- Visionneuse: défilement continu, zoom à deux doigts, miniatures, plan, surlignage des résultats de recherche, saisie du mot de passe et mode nuit; les scripts peuvent ouvrir la visionneuse et recevoir les événements de changement de page et de fermeture.
- Boîte à outils et gestionnaire de fichiers: assistants pour fusionner, découper, convertir en images, convertir des images en PDF, chiffrer / déchiffrer et extraire le texte; le gestionnaire de fichiers d'AutoJs6 ouvre directement les PDF dans la visionneuse du plugin, et son menu de débordement propose les mêmes outils.
- OCR: `pdf.text(path, { ocr: true })` appelle le moteur OCR d'AutoJs6 pour les pages numérisées sans couche de texte.

******

### Utilisation

******

1. Téléchargez l'APK depuis [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) et installez-le. Il n'existe qu'un seul paquet pour toutes les ABI.
2. Pour utiliser le module `pdf` dans les scripts, mettez à jour AutoJs6 vers la version interne 6641 (6.8.1) ou ultérieure et activez le plugin dans le centre des plugins.
3. Pour utiliser la boîte à outils intégrée, ouvrez le plugin et accordez l'accès à tous les fichiers lorsqu'il est demandé; les opérations des scripts n'ont pas besoin de cette autorisation.

> Le plugin prend en charge les appareils sous Android 7.0 (API 24) ou ultérieur. Le traitement des PDF n'utilise que des API du système et des bibliothèques en pur Java; la seule bibliothèque native provient de Jetpack Compose et est alignée sur des pages mémoire de 16 KB, si bien qu'un seul paquet sert toutes les ABI.

******

### API De Script

******

Les scripts utilisent ces fonctions via le module global `pdf` (également disponible sous le nom `$pdf`) (projet de conception de la 1.0.0; la documentation de la version publiée fait foi):

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

- Les méthodes pratiques comme `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` ouvrent et ferment le document à chaque appel; `pdf.open(path, options)` renvoie un `PdfDocument` qui partage une seule ouverture entre plusieurs opérations, avec au plus 8 documents ouverts par script.
- Chaque méthode existe sous une forme bloquante et sous une forme `xxxAsync` qui renvoie une Promise; `xxxAsync` accepte `options.onProgress(progress)` et sa Promise possède `cancel()`; l'arrêt du script annule les opérations en cours et supprime les sorties inachevées.
- `pdf.view(path, options)` ouvre la visionneuse et renvoie une `PdfViewSession` avec les événements `page` et `close` et les méthodes `goTo(page)` et `close()`.
- Les échecs lèvent une `PdfError` avec un `code` stable comme `PASSWORD_REQUIRED`, `WRONG_PASSWORD` ou `PAGE_OUT_OF_RANGE`; `pdf.isAvailable()` vérifie à l'avance si le plugin est disponible.

******

### FAQ

******

#### Puis-je l'utiliser sans AutoJs6?

Oui. La boîte à outils et la visionneuse fonctionnent seules. AutoJs6 n'est nécessaire que lorsque des scripts traitent des PDF.

#### Pourquoi le plugin demande-t-il l'accès à tous les fichiers?

Seules la boîte à outils et la visionneuse ouverte depuis le lanceur en ont besoin, pour lire et écrire les fichiers que vous choisissez à l'écran. AutoJs6 ouvre les chemins transmis par les scripts et les remet au plugin sous forme de descripteurs de fichiers, si bien que les scripts n'ont besoin d'aucune autorisation de stockage du plugin.

#### Le plugin contient-il des bibliothèques natives?

Le traitement des PDF n'utilise aucune bibliothèque native: les pages sont rendues par le `PdfRenderer` du système, et les autres fonctions utilisent PDFBox-Android, en pur Java. La seule bibliothèque native du paquet est `libandroidx.graphics.path.so`, fournie avec Jetpack Compose.

******

### Permissions Et Sécurité

******

Le plugin réduit autant que possible ses autorisations:

- Entrées protégées: le service de capacité, le service d'entrée du gestionnaire de fichiers et l'entrée d'activation exigent l'autorisation de plugin d'AutoJs6 (`org.autojs.permission.PLUGIN`), les autres applications ne peuvent donc pas les appeler directement.
- Stockage: les fichiers des scripts arrivent sous forme de descripteurs de fichiers, le plugin ne demande donc aucune autorisation de stockage pour les scripts; l'accès à tous les fichiers ne sert qu'aux fichiers que vous choisissez dans la boîte à outils et la visionneuse.
- HTML vers PDF: seuls la chaîne HTML ou le fichier local fournis sont rendus; aucune URL distante n'est chargée.
- Réseau et mots de passe: le réseau ne sert qu'à vérifier les mises à jour; les mots de passe des documents ne servent qu'à l'opération en cours et ne sont jamais enregistrés par le plugin.

******

### Moteur et dépendances

******

Les pages sont rendues avec le `PdfRenderer` d'Android; le texte, le plan, les images, les opérations sur les pages et le chiffrement utilisent [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, pur Java), et le chiffrement s'appuie sur Bouncy Castle (licence de type MIT). Le traitement des PDF ne contient aucune bibliothèque native et n'utilise aucun moteur de rendu natif comme pdfium ou MuPDF.

******

### Interface Du Plugin

******

Les informations suivantes s'adressent aux développeurs de l'hôte AutoJs6 et de plugins; l'hôte utilise ces identifiants pour découvrir le plugin et négocier les capacités:

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

### Feuille De Route

******

Les plans, décisions de conception et l'avancement sont suivis sous forme de liste à cocher dans ROADMAP.md. Les éléments non cochés sont des intentions, pas des fonctionnalités de la version actuelle.

- [Voir ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### Historique Des Versions

******

#### v1.0.0

_2026/11/12_

- `Note` Préversion de développement: le centre des plugins d'AutoJs6 peut détecter le plugin; la lecture, le rendu, les opérations sur les pages, la visionneuse, la boîte à outils et le module de script pdf ne sont pas encore livrés
- `Note` Nécessite AutoJs6 6.8.1 (6641) ou ultérieur (la version minimale exacte sera indiquée une fois les modifications de l'hôte intégrées)
- `Fonctionnalité` Squelette du plugin: protocole d'activation Wake, service d'identité du centre des plugins, service de capacité PDF (version de contrat 1) et service d'entrée du gestionnaire de fichiers
- `Fonctionnalité` Écran d'accueil permettant d'accorder l'accès à tous les fichiers (seule la boîte à outils en a besoin)
- `Fonctionnalité` Icônes claires et sombres pour le lanceur et le centre des plugins; le lanceur utilise par défaut l'icône adaptative automatique (elle suit le thème clair ou sombre du système), et les styles adaptative claire, adaptative sombre et fond transparent sont intégrés, au choix dans l'écran des paramètres à venir
- `Fonctionnalité` Un seul APK pour toutes les ABI: le traitement des PDF ne contient aucune bibliothèque native; la seule bibliothèque native provient de Jetpack Compose et est alignée sur des pages mémoire de 16 KB
- `Fonctionnalité` README, instructions du centre des plugins et journaux des modifications en 10 langues, générés à partir de sources JSON
- `Dépendance` Ajout de PDFBox-Android 2.0.27.0 (texte, plan, images, opérations sur les pages et chiffrement)
- `Dépendance` Ajout de Jetpack Compose BOM 2026.09.00 (écrans du plugin)
- `Dépendance` Ajout de Navigation Compose 2.10.2
- `Dépendance` Ajout d'AndroidX Core KTX 1.18.0

##### Pour plus d'historique des versions

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-fr.md)

******

### Compilation

******

Cette section s'adresse aux développeurs souhaitant compiler le plugin depuis les sources.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\generate_icon_studio.py --check
py .python\check_repository.py
```

******

### Licence

******

Le code du projet est distribué sous [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE). Les composants et données tiers sont répertoriés dans les mentions tierces.

- [Mentions tierces (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### Liens

******

- Projet AutoJs6: https://github.com/SuperMonster003/AutoJs6
