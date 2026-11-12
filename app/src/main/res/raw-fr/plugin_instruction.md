3-Paper PDF est le plugin PDF d'AutoJs6, et aussi une boîte à outils et une visionneuse PDF qui fonctionnent seules, sans AutoJs6. Il lit les informations, le texte, le plan et les images intégrées d'un PDF et rend les pages en images; il fusionne, découpe, extrait, supprime, réordonne et fait pivoter les pages; il génère des PDF à partir d'images, de texte ou de HTML, et chiffre ou déchiffre les documents.

> Il s'agit d'une préversion de développement: le centre des plugins d'AutoJs6 peut détecter le plugin, mais la lecture, le rendu, les opérations sur les pages, la visionneuse, la boîte à outils et le module `pdf` ne sont pas encore livrés. La suite décrit les objectifs de conception de la 1.0.0; consultez la feuille de route pour l'avancement.

### Points Forts

- Lecture: nombre de pages, tailles des pages, métadonnées, état du chiffrement et autorisations; texte et plan (signets) par page; recherche de texte avec numéros de page et positions; liste et export des images intégrées.
- Rendu: pages en objets `Image` ou en fichiers PNG / JPEG / WebP, à 144 dpi par défaut, ou selon une résolution, une échelle ou une largeur et une hauteur cibles.
- Opérations sur les pages: fusionner (en conservant les signets), découper, extraire, supprimer, réordonner et faire pivoter des pages; les numéros de page commencent à 1 et les plages s'écrivent `'1-3,5,8-'`.
- Génération: images vers PDF (JPEG intégrés tels quels, sans réencodage), texte vers PDF (avec la police de votre choix) et HTML vers PDF (aucune URL distante n'est chargée).
- Chiffrement: ouvrir des documents chiffrés avec un mot de passe, chiffrer en AES-256 et définir les autorisations, retirer le chiffrement.
- Visionneuse: défilement continu, zoom à deux doigts, miniatures, plan, surlignage des résultats de recherche, saisie du mot de passe et mode nuit; les scripts peuvent ouvrir la visionneuse et recevoir les événements de changement de page et de fermeture.
- Boîte à outils et gestionnaire de fichiers: assistants pour fusionner, découper, convertir en images, convertir des images en PDF, chiffrer / déchiffrer et extraire le texte; le gestionnaire de fichiers d'AutoJs6 ouvre directement les PDF dans la visionneuse du plugin, et son menu de débordement propose les mêmes outils.
- OCR: `pdf.text(path, { ocr: true })` appelle le moteur OCR d'AutoJs6 pour les pages numérisées sans couche de texte.

L'avancement et la conception sont disponibles sur la [page du projet](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF).
