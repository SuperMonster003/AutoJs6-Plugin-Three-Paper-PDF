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

Le traitement s'exécute dans le plugin avec le `PdfRenderer` d'Android et PDFBox-Android, en pur Java, si bien que l'hôte AutoJs6 reste léger. Les scripts AutoJs6 utilisent ces fonctions via le module global `pdf`, tout comme les scripts des plugins Node.js Runtime et Bun Runtime; AutoJs6 ouvre les chemins transmis par les scripts et les remet au plugin, qui n'a donc besoin d'aucune autorisation de stockage pour traiter les fichiers des scripts. Le plugin possède sa propre visionneuse, que l'on ouvre depuis le lanceur, le gestionnaire de fichiers d'AutoJs6, la commande "Ouvrir avec" d'autres applications ou un script (`pdf.view`).

> Le module `pdf` des scripts AutoJs6 et les entrées du gestionnaire de fichiers nécessitent AutoJs6 6.8.1 (version interne 6641) ou ultérieur; les versions plus anciennes d'AutoJs6 affichent le plugin comme incompatible dans le centre des plugins. La boîte à outils et la visionneuse ne dépendent pas d'AutoJs6 et fonctionnent seules.

******

### Points Forts

******

- Lecture: nombre de pages, tailles des pages, métadonnées, état du chiffrement et autorisations; texte et plan (signets) par page; recherche de texte avec numéros de page et positions; liste et export des images intégrées.
- Rendu: pages en objets `Image` ou en fichiers PNG / JPEG / WebP, à 144 dpi par défaut, ou selon une résolution, une échelle ou une largeur et une hauteur cibles.
- Opérations sur les pages: fusionner (en conservant les signets), découper, extraire, supprimer, réordonner et faire pivoter des pages; les numéros de page commencent à 1 et les plages s'écrivent `'1-3,5,8-'`.
- Génération: images vers PDF (JPEG intégrés tels quels, sans réencodage), texte vers PDF (avec la police de votre choix) et HTML vers PDF (aucune URL distante n'est chargée).
- Chiffrement: ouvrir des documents chiffrés avec un mot de passe, chiffrer en AES-256 et définir les autorisations, retirer le chiffrement.
- Visionneuse: défilement continu, zoom à deux doigts, miniatures, plan, surlignage des résultats de recherche, accès direct à une page, saisie du mot de passe et mode nuit; d'autres applications peuvent lui confier des PDF via "Ouvrir avec" ou le partage; les scripts peuvent ouvrir la visionneuse et recevoir les événements de changement de page et de fermeture.
- Boîte à outils: 8 outils pour fusionner, découper, convertir en images, convertir des images en PDF, chiffrer, déchiffrer, extraire le texte et organiser les pages; les tâches s'exécutent l'une après l'autre en arrière-plan, avec leur progression dans la notification; les fichiers récents retiennent aussi les fichiers ouverts avec le sélecteur de fichiers du système.
- Gestionnaire de fichiers d'AutoJs6: toucher un PDF l'ouvre dans la visionneuse du plugin, et les PDF de 512 MiB au plus contenus dans une archive s'ouvrent aussi directement; le menu propose fusionner, découper, convertir en images, extraire le texte, chiffrer et supprimer le mot de passe.
- OCR: `pdf.text(path, { ocr: true })` reconnaît les pages numérisées sans couche de texte à l'aide d'un plugin OCR installé (ML Kit OCR, Paddle OCR ou Rapid OCR).
- Node.js et Bun: Node.js Runtime 2.2.0 et Bun Runtime 0.23.0 ou ultérieurs proposent le même module `pdf`; ses méthodes renvoient des Promises, et les appels peuvent être interrompus avec `signal` ou `timeoutMs`.

******

### Utilisation

******

1. Téléchargez l'APK depuis [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) et installez-le. Il n'existe qu'un seul paquet pour toutes les ABI.
2. Pour l'utiliser dans les scripts AutoJs6 ou le gestionnaire de fichiers, mettez à jour AutoJs6 vers 6.8.1 (version interne 6641) ou ultérieur et vérifiez que le plugin est activé dans le centre des plugins.
3. Pour utiliser la boîte à outils intégrée, ouvrez le plugin et accordez l'accès à tous les fichiers lorsqu'il est demandé; les opérations des scripts n'ont pas besoin de cette autorisation.

> Le plugin prend en charge les appareils sous Android 7.0 (API 24) ou ultérieur. Le traitement des PDF n'utilise que des API du système et des bibliothèques en pur Java; la seule bibliothèque native provient de Jetpack Compose et est alignée sur des pages mémoire de 16 KB, si bien qu'un seul paquet sert toutes les ABI.

******

### Compatibilité

******

- Android 7.0 (API 24) ou ultérieur, un seul paquet pour toutes les ABI; la boîte à outils et la visionneuse ne dépendent pas d'AutoJs6.
- Scripts AutoJs6 et gestionnaire de fichiers: nécessitent AutoJs6 6.8.1 (version interne 6641) ou ultérieur.
- Applications empaquetées par AutoJs6: doivent être empaquetées par AutoJs6 6.8.1 (version interne 6641) ou ultérieur, et ce plugin doit être installé sur l'appareil qui les exécute; l'empaquetage ajoute l'autorisation `org.autojs.plugin.pdf.permission.RUNTIME`, `project.json` n'a donc pas besoin d'être modifié (un projet Node.js déclare `files`, un projet Bun déclare `pdf.control`).
- Node.js: nécessite le plugin Node.js Runtime 2.2.0 ou ultérieur, chargé avec `require('autojs6:pdf')`; le projet déclare `files` dans `node.permissions` de `project.json`, ainsi que `files.write` pour écrire des fichiers, `ocr` pour reconnaître du texte et `ui` pour ouvrir la visionneuse.
- Bun: nécessite le plugin Bun Runtime 0.23.0 ou ultérieur (Android 13 ou ultérieur), utilisé via `host.pdf`; activez `pdf.control` dans l'écran des paramètres de Bun Runtime, ainsi que `ocr.control` pour reconnaître du texte.
- OCR: la reconnaissance des pages numérisées nécessite un plugin OCR (ML Kit OCR, Paddle OCR ou Rapid OCR); sans lui, la reconnaissance des pages numérisées échoue avec `OCR_UNAVAILABLE`, sans effet sur les autres fonctions.

******

### API De Script

******

Les scripts AutoJs6 utilisent ces fonctions via le module global `pdf` (également disponible sous le nom `$pdf`; référence complète dans [Documentation d'AutoJs6 > PDF](https://docs.autojs6.com/#/pdf)):

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

Dans Node.js et Bun, les méthodes de même nom renvoient des Promises (Bun les utilise via `host.pdf`):

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

- Les méthodes pratiques comme `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` ouvrent et ferment le document à chaque appel; `pdf.open(path, options)` renvoie un `PdfDocument` qui partage une seule ouverture entre plusieurs opérations, avec au plus 8 documents ouverts par script.
- Chaque méthode existe sous une forme bloquante et sous une forme `xxxAsync` qui renvoie une Promise, avec les mêmes arguments et résultats; la forme bloquante ne peut pas être appelée sur le thread de l'interface (elle échoue avec `INVALID_ARGUMENT`), les scripts d'interface utilisent donc `xxxAsync`; les méthodes qui écrivent des fichiers acceptent `onProgress` et leur Promise possède `cancel()`; annuler ou arrêter le script ne laisse jamais de fichier à moitié écrit.
- Les `displayNames` de `pdf.merge` nomment le signet de chaque entrée: `null` reprend le nom du fichier (sans `.pdf`), et une chaîne vide ou faite d'espaces reprend le titre du document (`Document <n>` s'il n'en a pas); des options de plus de 63 KiB environ échouent avec `LIMIT_EXCEEDED` sans rien écrire.
- `pdf.fromHtml` exécute par défaut les scripts de la page, et `javaScript: false` les désactive; aucun contenu externe n'est chargé: les requêtes réseau reçoivent une réponse vide et sont consignées dans `warnings`.
- `pdf.view(path, options)` ouvre la visionneuse et renvoie une `PdfViewSession` avec les événements `page` / `close` et les méthodes `goTo(page)`, `state()` et `close()`; elle échoue avec `UI_REQUIRES_FOREGROUND` lorsque AutoJs6 n'est pas au premier plan (sous Android 10 ou ultérieur sans l'autorisation de superposition).
- Node.js et Bun: chaque méthode renvoie une Promise; `signal` (un AbortSignal) et `timeoutMs` interrompent l'appel, qui est rejeté avec `CANCELLED` dans les deux cas, et un délai dépassé a pour `cause` une `TimeoutError`; `timeoutMs` doit être un nombre fini supérieur à 0 et au plus égal à 2147483647, et les décimales sont arrondies à l'entier supérieur.
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

#### Pourquoi le centre des plugins affiche-t-il le plugin comme incompatible?

Le module de script et les entrées du gestionnaire de fichiers du plugin nécessitent AutoJs6 6.8.1 (version interne 6641) ou ultérieur. Après la mise à jour d'AutoJs6, le plugin peut être activé; en attendant, la boîte à outils et la visionneuse fonctionnent normalement.

#### Le gestionnaire de fichiers d'AutoJs6 peut-il ouvrir un PDF contenu dans une archive?

Oui. Un PDF de 512 MiB au plus contenu dans une archive s'ouvre directement dans la visionneuse du plugin; extrayez d'abord les plus gros.

#### Que faire si un fichier des fichiers récents ne s'ouvre plus?

Les fichiers ouverts avec le sélecteur de fichiers du système conservent leur autorisation de lecture et s'ouvrent directement même après un redémarrage. Si le fichier a été supprimé ou déplacé, ou si l'autorisation a été révoquée, toucher l'entrée affiche un message et la retire de la liste; choisissez de nouveau le fichier avec "Ouvrir un PDF".

******

### Permissions Et Sécurité

******

Le plugin réduit autant que possible ses autorisations:

- Entrées protégées: le service de capacité, le service d'entrée du gestionnaire de fichiers et l'entrée d'activation exigent l'autorisation de plugin d'AutoJs6 (`org.autojs.permission.PLUGIN`), les autres applications ne peuvent donc pas les appeler directement. Le service et l'entrée de visionneuse destinés aux applications empaquetées par AutoJs6 exigent `org.autojs.plugin.pdf.permission.RUNTIME` (ajoutée lors de l'empaquetage); les documents et sessions de visionneuse ouverts par ces entrées ne répondent qu'à l'application qui les a créés, et les fichiers y arrivent aussi sous forme de descripteurs de fichiers.
- Stockage: les fichiers des scripts arrivent sous forme de descripteurs de fichiers, le plugin ne demande donc aucune autorisation de stockage pour les scripts; l'accès à tous les fichiers ne sert qu'aux fichiers que vous choisissez dans la boîte à outils et la visionneuse.
- HTML vers PDF: le plugin ne charge aucun contenu externe (les requêtes réseau reçoivent une réponse vide, les fichiers locaux et les adresses `content:` sont bloqués, et la page ne peut pas naviguer); les scripts de la page peuvent être désactivés avec `javaScript: false`.
- Fichiers récents: les fichiers ouverts avec le sélecteur de fichiers du système conservent leur autorisation de lecture pour s'ouvrir directement la fois suivante, dans la limite de 20 fichiers; l'autorisation est libérée dès qu'une entrée sort de la liste ou est retirée, ou que la liste est effacée. Les fichiers transmis par d'autres applications via "Ouvrir avec" ou le partage n'ont qu'une autorisation ponctuelle et n'entrent pas dans les fichiers récents.
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

#### v1.1.0

_2026/11/20_

- `Amélioration` La page Historique des versions se développe et se réduit désormais par version: une version réduite affiche sa date et le nombre d'entrées par catégorie, une version développée ne nomme chaque catégorie qu'une fois; toutes les versions peuvent être développées ou réduites d'un coup, et la plus récente s'ouvre développée; la page a pour sous-titre le nom du plugin

#### v1.0.0

_2026/11/14_

- `Note` Le module pdf des scripts AutoJs6 et les entrées du gestionnaire de fichiers nécessitent AutoJs6 6.8.1 (version interne 6641) ou ultérieur; les versions plus anciennes d'AutoJs6 affichent le plugin comme incompatible dans le centre des plugins, et la boîte à outils et la visionneuse restent utilisables seules
- `Note` L'utilisation dans les scripts Node.js ou Bun nécessite Node.js Runtime 2.2.0 ou Bun Runtime 0.23.0 ou ultérieur
- `Fonctionnalité` Les scripts AutoJs6 disposent du module pdf (également disponible sous le nom $pdf): lire les informations, le texte, le plan et les images des PDF, rechercher du texte, rendre les pages en images, fusionner, découper, extraire, supprimer, réordonner et faire pivoter des pages, générer des PDF à partir d'images, de texte ou de HTML, chiffrer et déchiffrer; chaque méthode existe sous une forme bloquante et sous une forme Async qui peut signaler sa progression et être annulée
- `Fonctionnalité` L'option ocr de pdf.text reconnaît les pages numérisées sans couche de texte à l'aide d'un plugin OCR installé (ML Kit OCR, Paddle OCR ou Rapid OCR)
- `Fonctionnalité` pdf.view ouvre un PDF dans la visionneuse du plugin; les scripts reçoivent les événements de changement de page et de fermeture, et peuvent aller à une page ou fermer la visionneuse
- `Fonctionnalité` Les scripts Node.js et Bun peuvent aussi utiliser le module pdf; ses méthodes renvoient des Promises, et les appels peuvent être interrompus avec signal ou timeoutMs
- `Fonctionnalité` Les applications empaquetées par AutoJs6 6.8.1 ou ultérieur peuvent aussi utiliser le module pdf, l'autorisation nécessaire étant ajoutée lors de l'empaquetage; les documents et sessions de visionneuse qu'elles ouvrent ne répondent qu'à l'application qui les a créés
- `Fonctionnalité` Visionneuse: défilement continu, zoom à deux doigts, miniatures, plan, surlignage des résultats de recherche, accès direct à une page, saisie du mot de passe et mode nuit; elle s'ouvre depuis le lanceur, et d'autres applications peuvent lui confier des PDF via "Ouvrir avec" ou le partage
- `Fonctionnalité` Gestionnaire de fichiers d'AutoJs6: toucher un PDF l'ouvre dans la visionneuse du plugin, et les PDF de 512 MiB au plus contenus dans une archive s'ouvrent aussi directement; le menu propose fusionner, découper, convertir en images, extraire le texte, chiffrer et supprimer le mot de passe
- `Fonctionnalité` Écran d'accueil: les outils, la progression des tâches, les fichiers récents et l'octroi de l'accès à tous les fichiers (seule la boîte à outils en a besoin)
- `Fonctionnalité` Les fichiers récents retiennent les fichiers ouverts avec le sélecteur de fichiers du système (20 au plus), qui s'ouvrent directement même après un redémarrage; un fichier supprimé ou devenu inaccessible est signalé et retiré de la liste
- `Fonctionnalité` La boîte à outils propose 8 outils: fusionner, découper (par plages de pages ou toutes les n pages), PDF en images (résolution, format, pages), images en PDF (format de page, orientation, marges), chiffrer, supprimer le mot de passe, extraire le texte (enregistré en TXT), ainsi que pivoter, supprimer et réordonner les pages à l'aide de miniatures; le traitement est le même que celui des entrées du gestionnaire de fichiers
- `Fonctionnalité` Les tâches de la boîte à outils s'exécutent l'une après l'autre dans un service de premier plan et continuent après avoir quitté l'écran de l'outil; la notification affiche la progression et permet d'annuler, et les résultats peuvent être ouverts dans la visionneuse ou partagés
- `Fonctionnalité` Un navigateur de fichiers intégré permet de sélectionner plusieurs fichiers et de créer des dossiers; le sélecteur de fichiers du système reste disponible
- `Fonctionnalité` Écran des paramètres: dossier de sortie par défaut (Documents/3-Paper PDF), résolution par défaut de PDF en images, langue, mode nuit, couleur de thème, icône du lanceur, état des autorisations, historique des versions et recherche de mises à jour
- `Fonctionnalité` Recherche de mises à jour: manuelle dans l'écran des paramètres, ou automatique à l'ouverture de l'accueil (au plus une fois toutes les 12 heures); les informations de version proviennent de l'index officiel des plugins
- `Fonctionnalité` Icônes claires et sombres pour le lanceur et le centre des plugins; le lanceur utilise par défaut l'icône adaptative automatique (elle suit le thème clair ou sombre du système), et les styles adaptative claire, adaptative sombre et fond transparent sont intégrés, au choix dans l'écran des paramètres
- `Fonctionnalité` Un seul APK pour toutes les ABI: le traitement des PDF ne contient aucune bibliothèque native; la seule bibliothèque native provient de Jetpack Compose et est alignée sur des pages mémoire de 16 KB
- `Fonctionnalité` README, instructions du centre des plugins et journaux des modifications en 10 langues, générés à partir de sources JSON
- `Dépendance` Ajout de PDFBox-Android 2.0.27.0 (texte, plan, images, opérations sur les pages et chiffrement)
- `Dépendance` Ajout de Jetpack Compose BOM 2026.09.00 (écrans du plugin)
- `Dépendance` Ajout de Navigation Compose 2.10.2
- `Dépendance` Ajout de Material Color Utilities (MaterialKolor) 4.1.1 (couleur de thème des écrans du plugin)
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
