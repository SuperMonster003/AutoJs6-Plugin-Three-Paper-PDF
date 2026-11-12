******

### Historique Des Versions

******

# v1.0.0

###### 2026/11/12

* `Note` Préversion de développement: le centre des plugins d'AutoJs6 peut détecter le plugin; la lecture, le rendu, les opérations sur les pages, la visionneuse, la boîte à outils et le module de script pdf ne sont pas encore livrés
* `Note` Nécessite AutoJs6 6.8.1 (6641) ou ultérieur (la version minimale exacte sera indiquée une fois les modifications de l'hôte intégrées)
* `Fonctionnalité` Squelette du plugin: protocole d'activation Wake, service d'identité du centre des plugins, service de capacité PDF (version de contrat 1) et service d'entrée du gestionnaire de fichiers
* `Fonctionnalité` Écran d'accueil permettant d'accorder l'accès à tous les fichiers (seule la boîte à outils en a besoin)
* `Fonctionnalité` Un seul APK pour toutes les ABI: le traitement des PDF ne contient aucune bibliothèque native; la seule bibliothèque native provient de Jetpack Compose et est alignée sur des pages mémoire de 16 KB
* `Fonctionnalité` README, instructions du centre des plugins et journaux des modifications en 10 langues, générés à partir de sources JSON
* `Dépendance` Ajout de PDFBox-Android 2.0.27.0 (texte, plan, images, opérations sur les pages et chiffrement)
* `Dépendance` Ajout de Jetpack Compose BOM 2026.09.00 (écrans du plugin)
* `Dépendance` Ajout de Navigation Compose 2.10.2
* `Dépendance` Ajout d'AndroidX Core KTX 1.18.0
