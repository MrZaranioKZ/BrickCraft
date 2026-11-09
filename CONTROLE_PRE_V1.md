# BrickCraft V0.24.0-rc1 — audit de pré-version 1.0

## Corrections intégrées

- **Dossiers** : `config/brickcraft/`, `imports/`, `imports/reports/` et `parts_custom/` sont créés à l'initialisation côté commun. `complete.zip` doit être copié tel quel dans `config/brickcraft/complete.zip` (aucun téléchargement automatique).
- **Onglet Projet** : boutons distincts « Dossier imports » et « Dossier LDraw » pour ouvrir le dossier de configuration où déposer `complete.zip`.
- **Objets** : l'outil BrickCraft demeure l'outil polyvalent et l'accès au Studio. Le sélecteur LDraw générique redondant est retiré de l'onglet créatif. Les pièces LDraw réellement identifiées s'obtiennent via `/brickcraft give <id>` et sont liées à leur ID pour le placement. Le serveur refuse qu'un item générique sans ID se substitue à un item précis.
- **Pick Block** : préserve l'ID LDraw dans l'item copié, et ne plante pas si la bibliothèque de cette ancienne pièce est absente.
- **Traductions** : reprise des libellés de navigation des captures, quelques textes contextuels, et explication des rôles dans le Guidebook dans les 11 langues. Ajout d'un script d'audit statique détaillé et de rapports machine lisibles.

## Audit vérifiable

`python CONTROLER_TRADUCTIONS_V024.py` régénère `AUDIT_TRADUCTIONS_V024.json` et `AUDIT_TEXTES_V024.csv`.

Les nombres de clés égaux ne prouvent pas une traduction complète. L'audit signale les entrées de langues encore identiques à l'anglais et les littéraux Java affichables qui n'ont pas de clé connue. Les chaînes assemblées à l'exécution ne sont pas toutes inférables statiquement.

## Important — pas encore une V1.0 validée

- Les neuf autres langues conservent **de nombreuses valeurs anglaises**, à traduire humainement.
- Une partie des messages dynamiques, notamment ceux du serveur et des modes de diagnostic, utilisent encore une concaténation de fragments. Leur traduction complète demande un remplacement progressif par des `Text.translatable(key, args...)` structurés.
- Les tests statiques de Java ne garantissent pas la compilation du mod avec Fabric/Loom, ni sa compatibilité réelle avec Axiom/WorldEdit/Iris.
- Préserver la sauvegarde du monde avant de changer de version ; compiler et essayer en jeu.

## Chemins attendus

- Modèles `.io`, `.mpd`, `.ldr`, `.bcpx` : `.minecraft/config/brickcraft/imports/`
- LDraw : `.minecraft/config/brickcraft/complete.zip`
- Pièces personnalisées : `.minecraft/config/brickcraft/parts_custom/`

Les chemins se rapportent au répertoire de jeu actif : avec un lanceur multi-instances, ils ne sont pas forcément sous `%APPDATA%/.minecraft`.
