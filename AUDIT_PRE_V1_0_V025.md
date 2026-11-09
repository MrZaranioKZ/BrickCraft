# BrickCraft — Audit transversal de pré-V1.0 (V0.25.0 RC1)

## Portée et preuves

Analyse hors jeu de la source complète issue de la **V0.24.1 RC2** (Fabric 1.21.1, Java 21) : **107 fichiers Java**, **13 classes d'écran**, **154 occurrences de création de boutons dans ces écrans**, **46 fichiers JSON**, **32 PNG**, **29 modèles d'items attendus**, **11 locales** et **3 atlas LabPBR**. L'inventaire brut des boutons figure dans `AUDIT_CONTROLES_V025.csv` ; le contrôle automatisé est dans `tools/AUDIT_COMPLET_V025.py`.

Ce n'est **pas** une certification du comportement dans Minecraft. Le classpath Fabric/Loom, le rendu GPU/Iris, les interactions souris, les FPS/TPS et la cohabitation WorldEdit/Axiom n'ont pas été testés en client Minecraft dans cet environnement. Le test `TEST_SYNTAX_V017` parse le code sans faire de compilation Fabric.

## Éléments corrigés dans cette RC

| Domaine | Problème observé | Correctif |
|---|---|---|
| Studio, barre du bas | « Tourner » faisait doublon avec R et les commandes de rotation dans Réglages | Suppression de ce bouton **seulement** ; la rotation reste accessible |
| Studio, actions | Annuler, Rétablir, Dupliquer, Construire accessibles alors qu'aucune action n'était possible | Boutons désactivés selon le contenu et **réactivés à chaque image** après modification |
| Projections | ON/OFF physique et suppression cliquables sans sélection | Activation conditionnée à la sélection ; pages désactivées aux limites |
| Projections, petites interfaces | Liste de 6 lignes pouvant recouvrir les commandes | Pagination adaptative de 1 à 6 entrées selon la hauteur |
| Notices interactives | Contrôles de progression cliquables sans projection active | Contrôles de navigation, vérification, pose et matériaux désactivés lorsqu'ils sont sans objet |
| Importeur | La liste des modèles était limitée silencieusement à 200 fichiers | Limite silencieuse supprimée, liste issue du dossier |
| Importeur | Actualiser après ajout/retrait de fichiers pouvait faire sélectionner un autre modèle et enregistrer un diagnostic sous le mauvais nom | Conservation de la sélection par **chemin de fichier** et diagnostic associé au fichier effectivement chargé |
| Importeur → Actions | « Studio strict » accepté même lorsqu'un projet était incompatible avec l'atelier 4 096 pièces | Renommage « Studio sur grille », inactif si dépassement, référence manquante ou rotation libre |
| Sauvegarde `.bcpx` | Nom de fichier généré encore suffixé `_corrige_v019` | Suffixe stable `_corrige` (+ index de collision si nécessaire) |
| Raccourcis GPU | Deux touches expérimentales F7 et F10 étaient mélangées avec les commandes normales | Masquées par défaut ; réactivables par `experimental_gpu_diagnostics=true` dans `precise-render.properties` après redémarrage |
| Construction Windows | Le script affichait toujours « V0.16.0 » et indiquait un ancien JAR | Version et nom d'archive lus depuis `gradle.properties` ; page de code UTF-8 côté `.bat` |
| Tests historiques | Test V0.16 refusait 16 385 pièces, contrairement à la nouvelle capacité | Test actualisé, incluant le plafond technique actuel (250 000), sans créer 250 001 géométries |
| Métadonnées | `fabric.mod.json` décrivait toujours le mod comme un « prototype » | Description descriptive actualisée sans revendiquer une V1.0 stable |

## Contrôles réussis

Les **11 groupes de tests Java autonomes** passent (`AUDIT_TESTS_V025.json`) : disposition du livre, viewport Studio/GPU, profils PBR, commandes/coordonnées, collisions, remplacement de pièces manquantes, suppression de pièces manquantes, import/sauvegarde de grands modèles, atelier sur grille, lecture `.io`/notices et création des répertoires. Les six vrais fichiers `.io` fournis auparavant ont été relus pendant cette session :

- Chess Set Deluxe : 2 506 pièces.
- Caribbean Clipper : 1 318 pièces.
- Flying Dutchman : 4 290 pièces.
- Death Star 10188 : 4 010 pièces.
- Colisée 10276 : 9 063 pièces.
- Notre-Dame 21061 : 4 421 pièces.

Le contrôle de ressources trouve 29 modèles d'items et 29 textures dédiées présents, avec 3 atlas PBR de 160×16. Tous les fichiers JSON testés sont lisibles. Les onze langues possèdent les mêmes 880 clés, et les signatures de format vérifiées sont cohérentes.

## Ce qui ne doit **pas** être supprimé

- Les modes *Points*, *Volumes* et *Maillages* : utiles pour diagnostiquer une géométrie absente ou réduire le coût d'aperçu.
- Le mode legacy F6 : repli de diagnostic si un shader ou le GPU pose problème. F8 pour la visibilité des projections, F9 pour les statistiques restent utiles.
- Studio sur grille : distinct de la projection précise, réservé à un modèle compatible (4 096 pièces au maximum).
- Capacité pièces et physique : plafonds séparés, avec protections serveur, ce ne sont pas des boutons redondants.
- Pièces manquantes : remplacer, supprimer et rapport servent à trois cas différents.
- « Fermer la notice » et « Détacher la notice » : le premier quitte l'interface, le second retire son suivi en cours.

## Risques et limites ouverts avant une vraie V1.0

**Élevé — validation Fabric réelle.** Aucune compilation et aucun lancement du JAR dans Minecraft 1.21.1 dans cet environnement. Il faut compiler et tester sur une copie de monde avant diffusion.

**Élevé — internationalisation.** 11 fichiers de langue et 880 clés par langue **ne signifient pas** 11 traductions complètes. Environ 754–758 valeurs des neuf locales supplémentaires sont encore identiques à l'anglais. En français, 183 sont identiques à l'anglais (parmi lesquelles des noms de commandes et termes techniques). Le contrôle signale par ailleurs 93 occurrences potentielles de messages dynamiques : il ne peut pas garantir les phrases reconstruites à l'exécution ni leur mise en page.

**Moyen — grandes projections.** Le moteur impose des limites techniques (250 000 pièces, 400 000 cellules, 20 millions de triangles, volume maximal de 256 blocs par axe). Les réglages « sans limite » retirent uniquement les plafonds choisis par l'utilisateur. La physique 1/8 de bloc et l'enregistrement NBT peuvent coûter de la RAM, du temps CPU et des TPS sur de grands sets.

**Moyen — rendu GPU/PBR.** Les profils LabPBR sont présents, mais leur utilisation réelle par un shader Iris et la transparence ne sont pas certifiées par la présence des atlas ; essais avec et sans shaders requis.

**Moyen — WorldEdit/Axiom.** Une partie des briques classiques avec NBT relatif peut être copiée, mais les transformations/rotations d'assemblages et les grandes projections ne sont pas une intégration native complète. Test de `//copy`/`//paste` en copie de monde nécessaire.

**Faible — scripts historiques.** Le projet contient de nombreux `TESTER_*.bat` anciens. Ils restent disponibles comme outils de régression mais il faut utiliser **`TESTER_AUDIT_V025_WINDOWS.bat`** pour la vérification générale. Certains tests sont liés aux versions passées et ne sont pas des étapes d'installation.

## Étapes de validation finale

1. Sauvegarder le monde et conserver le JAR actuellement fonctionnel.
2. Exécuter `TESTER_AUDIT_V025_WINDOWS.bat` ; aucune erreur acceptable.
3. Exécuter `CONSTRUIRE_WINDOWS.bat` et vérifier l'absence d'erreur `compileJava`.
4. Dans Minecraft 1.21.1, vérifier Studio, recherche LDraw, import de plusieurs `.io`, suppression/remplacement, guide, notices et commandes.
5. Essayer « Projections → Physique ON/OFF », sauvegarde/rechargement du monde, puis un set de plusieurs milliers de pièces avec contrôle des FPS/TPS.
6. Comparer français et anglais en changeant la langue du jeu ; tester l'affichage sans chevauchement à plusieurs échelles de GUI. Auditer réellement les autres langues avant de déclarer 11 langues complètes.

**Statut de livraison : RC d'audit, pas V1.0 officielle.**
