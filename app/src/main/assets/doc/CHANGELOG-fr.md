******

### Historique Des Versions

******

# v1.0.0

###### 2026/11/12

* `Note` Préversion de développement: le centre des plugins d'AutoJs6 peut détecter le plugin; la lecture, le rendu, les opérations sur les pages, la visionneuse et le module de script pdf ne sont pas encore livrés
* `Note` Nécessite AutoJs6 6.8.1 (6641) ou ultérieur (la version minimale exacte sera indiquée une fois les modifications de l'hôte intégrées)
* `Fonctionnalité` Squelette du plugin: protocole d'activation Wake, service d'identité du centre des plugins, service de capacité PDF (version de contrat 1) et service d'entrée du gestionnaire de fichiers
* `Fonctionnalité` Écran d'accueil: les outils, la progression des tâches, les fichiers récents et l'octroi de l'accès à tous les fichiers (seule la boîte à outils en a besoin)
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
