******

### Historique Des Versions

******

# v1.1.0

###### 2026/11/20

* `Amélioration` La page Historique des versions se développe et se réduit désormais par version: une version réduite affiche sa date et le nombre d'entrées par catégorie, une version développée ne nomme chaque catégorie qu'une fois; toutes les versions peuvent être développées ou réduites d'un coup, et la plus récente s'ouvre développée; la page a pour sous-titre le nom du plugin

# v1.0.0

###### 2026/11/14

* `Note` Le module pdf des scripts AutoJs6 et les entrées du gestionnaire de fichiers nécessitent AutoJs6 6.8.1 (version interne 6641) ou ultérieur; les versions plus anciennes d'AutoJs6 affichent le plugin comme incompatible dans le centre des plugins, et la boîte à outils et la visionneuse restent utilisables seules
* `Note` L'utilisation dans les scripts Node.js ou Bun nécessite Node.js Runtime 2.2.0 ou Bun Runtime 0.23.0 ou ultérieur
* `Fonctionnalité` Les scripts AutoJs6 disposent du module pdf (également disponible sous le nom $pdf): lire les informations, le texte, le plan et les images des PDF, rechercher du texte, rendre les pages en images, fusionner, découper, extraire, supprimer, réordonner et faire pivoter des pages, générer des PDF à partir d'images, de texte ou de HTML, chiffrer et déchiffrer; chaque méthode existe sous une forme bloquante et sous une forme Async qui peut signaler sa progression et être annulée
* `Fonctionnalité` L'option ocr de pdf.text reconnaît les pages numérisées sans couche de texte à l'aide d'un plugin OCR installé (ML Kit OCR, Paddle OCR ou Rapid OCR)
* `Fonctionnalité` pdf.view ouvre un PDF dans la visionneuse du plugin; les scripts reçoivent les événements de changement de page et de fermeture, et peuvent aller à une page ou fermer la visionneuse
* `Fonctionnalité` Les scripts Node.js et Bun peuvent aussi utiliser le module pdf; ses méthodes renvoient des Promises, et les appels peuvent être interrompus avec signal ou timeoutMs
* `Fonctionnalité` Les applications empaquetées par AutoJs6 6.8.1 ou ultérieur peuvent aussi utiliser le module pdf, l'autorisation nécessaire étant ajoutée lors de l'empaquetage; les documents et sessions de visionneuse qu'elles ouvrent ne répondent qu'à l'application qui les a créés
* `Fonctionnalité` Visionneuse: défilement continu, zoom à deux doigts, miniatures, plan, surlignage des résultats de recherche, accès direct à une page, saisie du mot de passe et mode nuit; elle s'ouvre depuis le lanceur, et d'autres applications peuvent lui confier des PDF via "Ouvrir avec" ou le partage
* `Fonctionnalité` Gestionnaire de fichiers d'AutoJs6: toucher un PDF l'ouvre dans la visionneuse du plugin, et les PDF de 512 MiB au plus contenus dans une archive s'ouvrent aussi directement; le menu propose fusionner, découper, convertir en images, extraire le texte, chiffrer et supprimer le mot de passe
* `Fonctionnalité` Écran d'accueil: les outils, la progression des tâches, les fichiers récents et l'octroi de l'accès à tous les fichiers (seule la boîte à outils en a besoin)
* `Fonctionnalité` Les fichiers récents retiennent les fichiers ouverts avec le sélecteur de fichiers du système (20 au plus), qui s'ouvrent directement même après un redémarrage; un fichier supprimé ou devenu inaccessible est signalé et retiré de la liste
* `Fonctionnalité` La boîte à outils propose 8 outils: fusionner, découper (par plages de pages ou toutes les n pages), PDF en images (résolution, format, pages), images en PDF (format de page, orientation, marges), chiffrer, supprimer le mot de passe, extraire le texte (enregistré en TXT), ainsi que pivoter, supprimer et réordonner les pages à l'aide de miniatures; le traitement est le même que celui des entrées du gestionnaire de fichiers
* `Fonctionnalité` Les tâches de la boîte à outils s'exécutent l'une après l'autre dans un service de premier plan et continuent après avoir quitté l'écran de l'outil; la notification affiche la progression et permet d'annuler, et les résultats peuvent être ouverts dans la visionneuse ou partagés
* `Fonctionnalité` Un navigateur de fichiers intégré permet de sélectionner plusieurs fichiers et de créer des dossiers; le sélecteur de fichiers du système reste disponible
* `Fonctionnalité` Écran des paramètres: dossier de sortie par défaut (Documents/3-Paper PDF), résolution par défaut de PDF en images, langue, mode nuit, couleur de thème, icône du lanceur, état des autorisations, historique des versions et recherche de mises à jour
* `Fonctionnalité` Recherche de mises à jour: manuelle dans l'écran des paramètres, ou automatique à l'ouverture de l'accueil (au plus une fois toutes les 12 heures); les informations de version proviennent de l'index officiel des plugins
* `Fonctionnalité` Icônes claires et sombres pour le lanceur et le centre des plugins; le lanceur utilise par défaut l'icône adaptative automatique (elle suit le thème clair ou sombre du système), et les styles adaptative claire, adaptative sombre et fond transparent sont intégrés, au choix dans l'écran des paramètres
* `Fonctionnalité` Un seul APK pour toutes les ABI: le traitement des PDF ne contient aucune bibliothèque native; la seule bibliothèque native provient de Jetpack Compose et est alignée sur des pages mémoire de 16 KB
* `Fonctionnalité` README, instructions du centre des plugins et journaux des modifications en 10 langues, générés à partir de sources JSON
* `Dépendance` Ajout de PDFBox-Android 2.0.27.0 (texte, plan, images, opérations sur les pages et chiffrement)
* `Dépendance` Ajout de Jetpack Compose BOM 2026.09.00 (écrans du plugin)
* `Dépendance` Ajout de Navigation Compose 2.10.2
* `Dépendance` Ajout de Material Color Utilities (MaterialKolor) 4.1.1 (couleur de thème des écrans du plugin)
* `Dépendance` Ajout d'AndroidX Core KTX 1.18.0
