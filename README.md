# Fate/stay night [Réalta Nua] Packager

Permet de générer les fichiers de scripts KiriKiri pour le jeu Fate/stay night à partir de documents Google Docs stockés sur Google Drive.  
Les espaces insécables sont ajoutés automatiquement et un traitement permet de mettre en évidence de potentielles coquilles et erreurs de script.

## Compilation

1) Récupérer le fichier `credentials.json` depuis l'API de Google et le placer dans le dossier `src/main/resources`.
2) Utiliser Java 11.
2) À la racine du projet, saisir la commande `gradlew assemble` créera un fichier `-all.jar` dans `build/libs`.

## Utilisation

1) Avoir au minimum Java 11.
2) Autoriser l'accès à Google Drive avec le compte ayant accès au dossier `Fate stay night`.
Ensuite le programme se charge de créer les fichiers dans le dossier spécifié, au même niveau que le `.jar`.


## Crédits
Basé sur https://github.com/louisld/Fate-Stay-Night-Packager  
