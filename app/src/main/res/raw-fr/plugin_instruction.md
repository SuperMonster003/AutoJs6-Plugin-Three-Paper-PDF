3-Paper PDF est le plugin PDF d'AutoJs6, et aussi une boîte à outils et une visionneuse PDF qui fonctionnent seules, sans AutoJs6. Il lit les informations, le texte, le plan et les images intégrées d'un PDF et rend les pages en images; il fusionne, découpe, extrait, supprime, réordonne et fait pivoter les pages; il génère des PDF à partir d'images, de texte ou de HTML, et chiffre ou déchiffre les documents.

> Le module `pdf` des scripts AutoJs6 et les entrées du gestionnaire de fichiers nécessitent AutoJs6 6.8.1 (version interne 6641) ou ultérieur; les versions plus anciennes d'AutoJs6 affichent le plugin comme incompatible dans le centre des plugins. La boîte à outils et la visionneuse ne dépendent pas d'AutoJs6 et fonctionnent seules.

### Points Forts

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

Consultez [Documentation d'AutoJs6 > PDF](https://docs.autojs6.com/#/pdf) pour l'utilisation dans les scripts et la [page du projet](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF) pour l'avancement.
