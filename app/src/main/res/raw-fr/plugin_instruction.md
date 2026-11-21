3-Paper PDF fournit le module `pdf` aux scripts AutoJs6 pour lire, rendre, fusionner, découper, générer et chiffrer des fichiers PDF, ainsi que pour remplir des formulaires, ajouter des filigranes et des numéros de page et réduire la taille des fichiers. C'est aussi une visionneuse et une boîte à outils PDF utilisables seules, sans AutoJs6.

### Prise en main

1. Utilisez directement le module global `pdf` dans les scripts AutoJs6; voir l'exemple ci-dessous.
2. Pour consulter ou traiter des PDF sur le téléphone, ouvrez 3-Paper PDF depuis le lanceur, accordez l'accès à tous les fichiers lorsqu'il est demandé, puis touchez "Ouvrir un PDF" ou choisissez le traitement voulu dans "Outils".
3. Toucher un fichier PDF dans le gestionnaire de fichiers d'AutoJs6 l'ouvre dans la visionneuse de 3-Paper PDF.

### Fonctionnalités principales

- **Lecture**: nombre de pages, tailles des pages, métadonnées et état du chiffrement; texte et plan (signets) par page; recherche de texte; liste et export des images intégrées.
- **Rendu**: pages en objets image ou en fichiers PNG / JPEG / WebP, dimensionnés selon une résolution en dpi, une échelle ou une largeur et une hauteur cibles.
- **Opérations sur les pages**: fusionner (en conservant les signets), découper, extraire, supprimer, réordonner et faire pivoter des pages.
- **Génération et chiffrement**: créer des PDF à partir d'images, de texte ou de HTML; définir un mot de passe et des autorisations sur un document, ou retirer le mot de passe.
- **Modification**: remplir des formulaires, ajouter des filigranes, des numéros de page, des en-têtes et des pieds de page, réduire la taille des fichiers, et modifier le plan et les propriétés du document comme le titre et l'auteur.
- **Reconnaissance des pages numérisées**: reconnaître le texte des pages numérisées sans couche de texte à l'aide d'un plugin OCR installé, ou écrire une couche de texte dans un document numérisé pour en faire un PDF interrogeable.
- **Visionneuse**: défilement continu, zoom à deux doigts, miniatures, plan, surlignage des résultats de recherche, accès direct à une page, mode nuit et lecture en plein écran (un appui sur la page masque les barres; les paramètres décident si le numéro de page et les barres système se masquent aussi). Les autres applications peuvent lui transmettre des PDF via "Ouvrir avec" ou le partage.
- **Boîte à outils**: 13 outils comme fusionner, découper, compresser et ajouter un filigrane; après un traitement, le même traitement peut être écrit sous forme de script. Les tâches s'exécutent l'une après l'autre en arrière-plan, avec la progression affichée dans la notification.
- **Gestionnaire de fichiers d'AutoJs6**: toucher un PDF l'ouvre dans la visionneuse, et les PDF contenus dans une archive s'ouvrent aussi directement; le menu des fichiers propose des traitements comme fusionner, découper, compresser et ajouter un filigrane, et peut créer un PDF interrogeable.

### Utilisation dans les scripts

Utilisez directement le module global `pdf` dans les scripts AutoJs6. Les numéros de page commencent à 1, et les plages s'écrivent `'1-3,5,8-'`:

```js
let info = pdf.info("./book.pdf");
console.log(info.pageCount, info.title);

let texts = pdf.text("./book.pdf", { pages: "1-3" });
console.log(texts[0]);

let img = pdf.render("./book.pdf", 1, { dpi: 200 });
images.save(img, "./page-1.png");
img.recycle();

pdf.merge(["./a.pdf", "./b.pdf"], "./ab.pdf");
```

- Chaque méthode a une forme `Async` qui renvoie une Promise (par exemple `pdf.mergeAsync`). Les formes bloquantes ne peuvent pas être appelées sur le thread UI; utilisez les formes `Async` dans les scripts d'interface.
- Passez `password` dans les options pour ouvrir un document chiffré. Les échecs lèvent une erreur avec un `code`, comme `PASSWORD_REQUIRED` ou `PAGE_OUT_OF_RANGE`.
- Dans Node.js, chargez-le avec `require('autojs6:pdf')`; dans Bun, utilisez `host.pdf`. Les méthodes sont les mêmes et renvoient toutes des Promise.

### Prérequis

- Android 7.0 ou version ultérieure. La visionneuse et la boîte à outils s'utilisent seules, sans AutoJs6.
- Le module `pdf` des scripts et les entrées du gestionnaire de fichiers nécessitent AutoJs6 6.8.1 ou version ultérieure. AutoJs6 ouvre les fichiers que les scripts lisent et écrivent; il a donc besoin de l'autorisation de stockage.
- La boîte à outils et la visionneuse ouverte depuis le lanceur nécessitent l'accès à tous les fichiers; les scripts n'ont pas besoin de cette autorisation du plugin pour traiter des fichiers.
- Les applications empaquetées par AutoJs6 peuvent aussi utiliser le module `pdf`: elles doivent être empaquetées par AutoJs6 6.8.1 ou version ultérieure, et ce plugin doit être installé sur l'appareil qui les exécute.
- Les scripts Node.js nécessitent le plugin Node.js Runtime 2.2.0 ou version ultérieure et doivent déclarer `files` dans `node.permissions` de `project.json`; les scripts Bun nécessitent le plugin Bun Runtime 0.23.0 ou version ultérieure (Android 13 ou version ultérieure), avec `pdf.control` activé dans ses paramètres.
- La reconnaissance du texte des pages numérisées et la création de PDF interrogeables nécessitent un plugin OCR supplémentaire (ML Kit OCR, Paddle OCR ou Rapid OCR).

### Questions fréquentes

**Utilisation sans AutoJs6**

La visionneuse et la boîte à outils s'utilisent seules; AutoJs6 n'est nécessaire que lorsque des scripts traitent des PDF.

**Raisons pour lesquelles le centre des plugins affiche le plugin comme incompatible**

Le module de script et les entrées du gestionnaire de fichiers nécessitent AutoJs6 6.8.1 ou version ultérieure, et le plugin peut être activé après la mise à jour d'AutoJs6; en attendant, la visionneuse et la boîte à outils fonctionnent normalement.

**Cas où un fichier des fichiers récents ne s'ouvre plus**

Lorsqu'un fichier a été supprimé ou déplacé, ou que son autorisation d'accès a été révoquée, toucher l'entrée affiche un message et la retire de la liste. Choisissez de nouveau le fichier via "Ouvrir un PDF".

**Raisons pour lesquelles les valeurs saisies dans le formulaire ne s'affichent pas dans la visionneuse**

Sous Android 14 et versions antérieures, la visionneuse intégrée n'affiche les valeurs saisies qu'après aplatissement; les autres applications PDF ne sont pas concernées. Pour les voir dans la visionneuse, choisissez "Aplatir les champs" lors du remplissage.

La référence complète des scripts se trouve dans [Documentation AutoJs6 > PDF](https://docs.autojs6.com/#/pdf), et les autres informations sur la [page du projet](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF).
