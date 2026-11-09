# BrickCraft 0.25.0 RC1 — Démarrer ici

Ce ZIP comprend toutes les sources Java et ressources Fabric 1.21.1 du projet, les scripts et les audits.

**Pour vérifier** : double-cliquer sur `TESTER_AUDIT_V025_WINDOWS.bat` (Java 21 JDK et Python 3 nécessaires). Il lance les régressions hors jeu et contrôle les langues, assets et boutons.

**Pour compiler le JAR** : lancer ensuite `CONSTRUIRE_WINDOWS.bat` (Java 21, téléchargement de Gradle au premier build si absent). Le JAR est généré dans `build/libs/`. Retirer l'ancienne version de `mods/` avant d'installer celle-ci. Ne pas essayer deux JAR BrickCraft simultanément.

**Pour les données** : les projets `.io`, `.mpd`, `.ldr`, `.bcpx` vont dans `config/brickcraft/imports/`; la bibliothèque LDraw `complete.zip` dans `config/brickcraft/complete.zip`; les `.dat` complémentaires dans `config/brickcraft/parts_custom/`. Le mod crée les dossiers nécessaires ; il ne fournit pas le contenu externe de `complete.zip`.

**Pour les réglages avancés** : les touches expérimentales de diagnostic GPU F7/F10 sont masquées. Les remettre via `experimental_gpu_diagnostics=true` dans `config/brickcraft/precise-render.properties`, puis redémarrer Minecraft. F6 (mode legacy), F8 (visibilité des projections) et F9 (statistiques) restent disponibles.

**À lire avant V1.0** : `AUDIT_PRE_V1_0_V025.md`. L'audit statique passe, mais une compilation Fabric et les essais en jeu sont encore nécessaires. Les langues autres que FR/EN sont pour la plupart des traductions partielles.
