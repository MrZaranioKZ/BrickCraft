# BrickCraft V0.25.1 RC2 — Correctifs UI / traductions

**Commence ici :** `TESTER_CORRECTIFS_V0251_WINDOWS.bat`, puis `CONSTRUIRE_WINDOWS.bat`.

Voir `V0.25.1_RC2_CORRECTIFS_FORMATAGE_UI.md` pour les corrections et limites.

---

# BrickCraft 0.25.0 RC1 — Audit général avant V1.0

**Commencer par `DEMARRER_ICI_V025.md` et `AUDIT_PRE_V1_0_V025.md`.**
Cette RC retire un doublon de rotation dans le Studio, désactive les boutons inopérants, rend la gestion des projections adaptée aux petites interfaces, corrige la sélection des modèles lors de l'actualisation, masque les diagnostics GPU expérimentaux et répare l'affichage de version du script Windows.

Les tests hors jeu passent. **Compilation complète Fabric et vérification du jeu non effectuées ici.**

---

# BrickCraft V0.24.1 RC2 — Correctifs i18n et lisibilité

Voir `V0.24.1_RC2_TRADUCTIONS_ET_UI.md` pour les écrans corrigés, les tests et les limites restantes.

# BrickCraft V0.24.0-rc1 — Contrôle pré-V1

Correctifs : dossiers `config/brickcraft` et `imports` créés automatiquement, bouton « Dossier LDraw », différenciation baguette de construction / pièce LDraw liée à une référence, textes corrigés dans les écrans, audit statique des langues.

**Cette version est une candidate, pas une V1.0 finalisée** : les traductions des autres langues contiennent encore de nombreux replis anglais, et les textes Java dynamiques ne sont pas tous internationalisés. Lire `CONTROLE_PRE_V1.md` et les rapports `AUDIT_*`.

---

# BrickCraft V0.23.1 — Guidebook plus lisible

Correctif de la police et de la mise en page du guide. Lire `V0.23.1_GUIDEBOOK_LISIBILITE.md`.

# BrickCraft V0.22.0 — LabPBR 1.3 / Iris (expérimental)

Voir `V0.22.0_LABPBR_SHADER_MATERIALS.md`.

# BrickCraft V0.20.0 — Onglet créatif & commandes de pièces

- **Onglet dédié** dans l'inventaire créatif Minecraft avec l'outil BrickCraft, l'objet LDraw et toutes les formes classiques.
- **Commandes serveur** : `/brickcraft list`, `/brickcraft place <forme> <x y z> [couleur] [angle]`, `/brickcraft ldraw <ref> <x y z> [couleur] [angle]`, `/brickcraft give <ref> [nombre]`.
- **Blocs déplaçables par copie NBT** : les cellules LEGO classiques sauvegardent désormais les positions des pièces relativement aux blocs porteurs. Anciennes sauvegardes relues sans migration manuelle.
- **Important** : cela ne rend pas `//set brickcraft:cell` équivalent à la pose d'une pièce, ni les grands modèles GPU automatiquement compatibles avec les transformations Axiom/WorldEdit.

Détails et commandes d'installation dans [V0.20.0_CREATIF_COMMANDES_WORLDEDIT.md](V0.20.0_CREATIF_COMMANDES_WORLDEDIT.md).

---

# BrickCraft V0.19.0 — Nettoyage des pièces manquantes

Dans **Importer > Projet > Pièces manquantes**, boutons **Suppr.** par référence et **Supprimer tout**. La suppression ne touche que la copie chargée, après confirmation ; les pièces valides sont conservées. L'aperçu et le rapport sont rafraîchis. Sauvegarde la version nettoyée par **Actions > Sauver .bcpx**. Le fichier original reste intact. Voir `V0.19.0_SUPPRESSION_PIECES_MANQUANTES.md` et `TESTER_SUPPRESSION_MANQUANTES_V019_WINDOWS.bat`.

# BrickCraft V0.18.0 — Équivalents pour les références LDraw manquantes

Dans l'écran **Pièces manquantes**, un bouton **Équivalents** propose les références voisines présentes dans la bibliothèque installée. L'utilisateur choisit la pièce cible, la valide, puis enregistre un **BCPX** sans modifier son fichier `.io` initial. Références 4079b → 4079, 4497b → 4497, 57029c01 → 57029ac01 prises en charge comme suggestions, si les cibles LDraw existent réellement.

Documentation : [V0.18.0_EQUIVALENTS_LDRAW.md](V0.18.0_EQUIVALENTS_LDRAW.md). Tester avec `TESTER_EQUIVALENTS_V018_WINDOWS.bat`, puis construire avec `CONSTRUIRE_WINDOWS.bat`.

---

# BrickCraft V0.17.0 — Grands modèles, curseurs et pièces manquantes

- Deux curseurs dans **Importer > Actions > Capacités pièces / physique** (et **P > Capacités / grands sets**). En butée droite : **sans plafond personnel**, avec garde-fous techniques : 250 000 pièces, 400 000 cellules physiques, 20 millions de triangles.
- Préparation des collisions **progressive** ; **Physique OFF** peut annuler le calcul ou interrompre la pose avant son achèvement.
- **Importer > Projet > Pièces manquantes / chercher** : références, quantités, noms connus, recherche Internet, rapports HTML/CSV ; installation de `.dat` dans `config/brickcraft/parts_custom/`, rechargement LDraw depuis le jeu. Extraction des `.dat` présents dans les `.io` non chiffrés.
- Prévisualisation des gros sets et GPU : budgets élargis, mémoire GPU par défaut portée à 512 Mio avec baisse automatique de LOD si saturation. Attention, le Colisée (9 063 pièces) était déjà sous la limite précédente ; un modèle incomplet peut signaler des géométries manquantes ou une mémoire GPU insuffisante.
- Tester : `TESTER_GRANDS_SETS_V017_WINDOWS.bat` ; compiler : `CONSTRUIRE_WINDOWS.bat` (nécessite **Fabric 1.21.1 / JDK 21**). Les tests Java autonomes passent ; **JAR Fabric non compilé/vérifié dans Minecraft**.
- Mode d'emploi et limites : [V0.17.0_CAPACITES_ET_PIECES_MANQUANTES.md](V0.17.0_CAPACITES_ET_PIECES_MANQUANTES.md).

---

# BrickCraft V0.16.0 — Grand import BrickLink Studio (.io)

- **Import .io chiffré / non chiffré** : lecture directe de `model.ldr` dans les archives BrickLink Studio, y compris ZIP ZipCrypto (mot de passe du format), sans outil ni extraction externe. CRC, limites de taille et vérifications ZIP sont appliqués.
- **Gros projets** : jusqu'à **16 384 pièces par projection LDraw précise** (4x la V0.15), tandis que le Studio à grille traditionnel reste volontairement limité à 4 096 pièces pour préserver son moteur de collisions et son historique.
- **Notices .io** : même lecteur pour les étapes `0 STEP` et les sous-modèles, y compris les .io chiffrés.
- **Optimisations** : lots réseau de 32 pièces, validation progressive accélérée (96 pièces/tick), cache des géométries déjà analysées, taille maximum NBT compressée 2 Mio.
- **Limites restantes** : pièces custom `CustomParts/` non automatiquement installées ; nécessité de `complete.zip` / suppléments LDraw sur client et serveur. La physique 8³ conserve ses propres budgets en cellules/triangles. Taille maximale de la construction : 128 blocs par axe.
- **Tests** : `TESTER_IMPORTS_V016_WINDOWS.bat` (Java 21 autonome) + `CONSTRUIRE_WINDOWS.bat` (Fabric). Référence : [`V0.16.0_GRANDS_MODELES_ET_IO.md`](V0.16.0_GRANDS_MODELES_ET_IO.md).

---

# BrickCraft V0.15.0 — Ouverture du dossier d'import et éclairage Minecraft

- **Import > Projet > Ouvrir le dossier imports** ouvre l'Explorateur directement au bon chemin.
- **Lumière du monde** : GPU lightmap SKY/BLOCK, faces éclairées comme les blocs, ombrage de contact LEGO approximatif.
- **P > Recalculer l'éclairage** après l'ajout/retrait de sources lumineuses ; jour/nuit suit le moteur Minecraft automatiquement.
- Code, limitations, installation et vérifications : [`V0.15.0_DOSSIER_IMPORT_ET_LUMIERE_MC.md`](V0.15.0_DOSSIER_IMPORT_ET_LUMIERE_MC.md).
- **Important** : aucune ombre réelle n'est pour l'instant projetée sur les blocs du terrain. Les LEGO restent des maillages BCPX rendus via VBO.

---

# BrickCraft 0.14.0 — Interface et apercu GPU

Consulte [V0.14.0_INTERFACE_ET_APERCU_GPU.md](V0.14.0_INTERFACE_ET_APERCU_GPU.md).

L'importeur est maintenant organise en trois onglets (Projet, Visuel, Actions), les volumes passent sur le rendu GPU mis en cache et le maillage GPU emploie une matrice GUI orthographique explicite. Le Studio classique a des raccourcis Importer / Sauver dans son en-tete quand l'ecran est assez large. Touche `O` : alterne GPU/CPU, `V` : cycle les modes d'apercu. `TESTER_UI_GPU_V014_WINDOWS.bat` teste les regles sans Minecraft.

**Attention :** la compilation Gradle Fabric et les FPS Minecraft reels restent a verifier sur ta machine. Cette archive contient les sources, pas le JAR compile.

---

# BrickCraft V0.13.0 — Sol, portes et pentes LEGO

**NOUVEAU :** correctif des collisions manquantes sous l’ancre, meilleure traversée des arches/portes, suppression des micro-étages invisibles au-dessus des faces et franchissement automatique de petites pentes, à hauteur de marche adaptée à la taille du joueur. La touche `U` est conservée.

**À FAIRE IMPÉRATIVEMENT :** sur la V0.12.0, « Physique OFF » et attendre la fin ; mettre à jour le JAR, puis « Physique ON ». **Tester sur une copie du monde.**

Lire `V0.13.0_PORTES_SOL_ET_PENTES.md`. Vérifier le code avec `TESTER_PHYSIQUE_V013_WINDOWS.bat`, puis compiler avec `CONSTRUIRE_WINDOWS.bat`. Le JAR attendu est `build/libs/brickcraft-fabric-1.21.1-0.13.0.jar`.

---

# BrickCraft V0.12.0 — Physique LDraw fine + taille du joueur

**NOUVEAU :** collisions LDraw en micro-voxels 8×8×8, cavités et pentes préservées selon la géométrie source, ainsi qu'un réglage du joueur (touche `U`, tailles ×0,125 à ×4) synchronisant visuel, caméra et hitbox. **Teste sur une copie du monde uniquement.**

Voir **`V0.12.0_PHYSIQUE_MICROVOXELS_ET_TAILLE.md`** pour la marche à suivre, les limites de performance et les précautions. Lance `TESTER_PHYSIQUE_ET_TAILLE_V012_WINDOWS.bat` puis `CONSTRUIRE_WINDOWS.bat`. Le JAR est à compiler : `build/libs/brickcraft-fabric-1.21.1-0.12.0.jar`.

---

# BrickCraft V0.11.1 — Notices visuelles, collisions et Studio GPU (BETA)

**ATTENTION : test en monde de COPIE uniquement.** Cette version ajoute des blocs invisibles lorsque tu actives la physique : fais une sauvegarde avant de l'essayer.

**Contenu réel de cette passe :**
- Guide : contours turquoise / marqueurs des pièces de la PROCHAINE étape, sans modifier le modèle ; touche `J`.
- Notice : bouton `Vérifier pièces` (référence/couleur proche d'une vraie brique BrickCraft ; **ne vérifie pas encore les matrices ni les positions exactes**) ; bouton `Confirmer (manuel)` pour une étape réalisée non reconnue. Les étapes Studio/LDraw existantes restent prises en charge.
- Physique : dans **Projections du monde**, `Physique ON` et `Physique OFF`. Active des **voxels solides invisibles approximatifs** seulement dans l'AIR, après confirmation. L'activation est étalée sur plusieurs ticks et ne remplace jamais le terrain existant. La physique ne sait pas reproduire les trous Technic, courbes, tenons ou sous-cavités exactes. **Ne casse pas l'ancre pendant l'activation : utilise Physique OFF avant de retirer la projection**.
- Studio précis/import : aperçu 3D mis en cache dans un **VertexBuffer GPU (VBO)** projeté dans l'interface. Appuie sur `O` dans l'importeur ou l'éditeur libre pour revenir au renderer CPU en cas d'artefact. L'atelier grille classique garde son système 2D.

**Non disponible pour l'instant :** vérification automatisée parfaite d'une pose BrickLink arbitraire (rotation + position + attaches), collisions triangulaires exactes et VBO GPU du Studio grille classique. Ne présente pas le bouton de confirmation comme une vraie vérification physique.

**Mise à jour :** sources complètes ou patch depuis la V0.11.0 (Fabric 1.21.1, Java 21). Préserve `config/brickcraft/complete.zip` et `studio_parts.zip`. Lance `TESTER_V0111_WINDOWS.bat`, puis `CONSTRUIRE_WINDOWS.bat`. Le JAR final sera dans `build/libs`. Si la compilation échoue, conserve la V0.11.0 et partage le journal.

**Physique et sauvegardes :** les cellules générées appartiennent au modèle ancré et sont listées dans `BrickCraftPhysicsCells` en NBT. Le retrait essaie d'enlever uniquement les cellules BrickCraft chargées. Ne démonte pas l'ancre pendant qu'une partie des chunks du château est déchargée : des blocs orphelins pourraient rester. Dans ce cas restaure la copie du monde.

**Performances :** l'aperçu GPU est une nouvelle implémentation expérimentale. Le test Java pur ne certifie ni ses matrices d'écran ni ses FPS. La physique d'un modèle 3 245 pièces peut ajouter plus de 10 000 blocs de collision : vérifie les TPS, ne l'active pas en survie ou sur un serveur principal.

---

# BrickCraft V0.11.0 — Notices interactives

**Fabric 1.21.1 / Java 21** — sources uniquement, compilation Minecraft a faire sur Windows.

## Nouveau : J pour les notices

- `J` : ouvrir le panneau des notices pour le projet charge dans le monde.
- Dans le panneau : **Couches**, **Etapes Studio** (si `.io/.mpd/.ldr` reconnu), **Lots de 20**, **Piece / piece**.
- `PageUp`/`PageDown` (ou boutons) : naviguer en conservant la camera du monde.
- `Home`/`End` dans le panneau : tout masquer / tout montrer.
- `Retirer la notice` : montrer instantanement l'assemblage entier, aucune suppression.
- Chaque etape affiche les references LDraw, les quantites et les couleurs.

**Etapes Studio** : deposer le `.io` ORIGINAL (avec `0 STEP`) dans `config/brickcraft/imports/` avant d'ouvrir `J`. Le projet doit correspondre a l'assemblage charge. Les etapes sont lues sans modifier le `.io` ni la sauvegarde Minecraft.

Sans fichier source correspondant, trois modes automatiques sont utilisables. Les couches se basent sur les **origines** (hauteur de plaque 8 LDU) : approximation, pas une notice LEGO officielle.

**Performances** : seuls les triangles deja atteints sont visibles. Le maillage GPU est partitionne par groupes de quelques etapes, avec rechargement de la seule tranche active quand la progression change. Aucun nouveau bloc ou entite Minecraft n'est cree et BPW2/BCPX restent inchanges. Les changements de mode recompilent progressivement les caches GPU une fois.

## Installation

1. Faire une sauvegarde du monde et des sources de la V0.10.1.
2. Utiliser le ZIP complet ou appliquer le patch V0.11.0 sur une copie des sources V0.10.1.
3. `TESTER_NOTICES_011_WINDOWS.bat` (tests Java sans Gradle).
4. `CONSTRUIRE_WINDOWS.bat` (compile Fabric, necessite Internet au premier lancement).
5. Installer le nouveau JAR dans `mods/`, sans conserver le JAR precedent.

### Limitations connues

- L'affichage de la notice est propre a la session client : la position de lecture n'est pas sauvegardee.
- Le navigateur ne donne pas une liste d'instructions officielles. Des steps Studio peuvent correspondre a des sous-modeles dont l'ordre n'est pas celui de la notice LEGO commerciale.
- Sans le `.io` original sur le poste client, un projet deja stocke en monde (BPW2) ne permet pas de retrouver les marqueurs STEP (ils ne sont pas presents dans le format aplati).
- Les modifications faites dans le monde pendant une notice utilisent toujours les index originaux, mais un changement de nombre ou d'ordre de pieces necessite de reouvrir le guide.
- Les performances en mode notice doivent encore etre validees sur ton PC. La compilation Fabric n'a pas pu etre realisee dans cet environnement.

---

# BrickCraft 0.10.1 — Poignées 3D dans le monde

**Nouveau :** F4 pour activer l'édition, clic pour sélectionner, **glisser les poignées rouge/verte/bleue**, `K` pour inspecter les tenons/douilles de la pièce sélectionnée. Le moteur GPU et les projets V0.10.0 sont conservés.

Voir **`V0.10.1_POIGNEES_MONDE.md`** pour les touches, les protections, les limites et les tests.

---

# BrickCraft 0.10.0 — Smart Stud Snap

**NOUVEAU :** en mode édition Minecraft `F4`, `N` active/désactive l'aimant de tenons, `B` raccorde la pièce sélectionnée. Les mouvements à la molette peuvent s'aligner sur les connecteurs connus ; les rotations libres restent intactes. Le placement des pièces complexes reste libre.

**Tests :** `TESTER_SNAP_010_WINDOWS.bat` (Java 21) puis `CONSTRUIRE_WINDOWS.bat` (Fabric). **Notice non officielle :** `PREPARER_GUIDE_010_WINDOWS.bat` exporte un JSON des couches et lots source d'un `.bcpx` (ce n'est pas encore un mode de jeu).

Voir `V0.10.0_SNAP_ET_GUIDES.md` pour les limites, risques, sauvegardes et pièces prises en charge.

---

# BrickCraft V0.9.9 — Edition LEGO dans le monde + curseur GPU

- **F4** : edition en monde (mode Creatif), clic sur une piece virtuelle, **G/R** puis X/Y/Z, molette pour deplacer ou tourner, H pour reinitialiser la rotation.
- **P** : nouveau slider 0–100 % de detail des maillages LEGO. A 100 %, rendu des triangles LDraw originaux (tenons arrondis). F9 affiche le nombre reel de triangles.
- Moteur GPU VBO conserve, sections inchangees reutilisees quand on modifie une seule piece.
- Coordonnees du modele ancrees de facon stable dans le NBT BPW2 : **sauvegarde du monde obligatoire avant mise a jour** (retour a l'ancienne version de mod non compatible apres edition).
- Corrections d'interface dans le Studio. Fonctions layer-by-layer/step-by-step conservees en planificateur futur.
- Details : **V0.9.9_MONDE_ET_TRIANGLES.md**. Tests : `TESTER_MONDE_ET_TRIANGLES_099_WINDOWS.bat`.
- Ce ZIP fournit les **sources**, pas un JAR deja compile/valide en jeu.


## V0.9.8 : interaction 3D dans l'editeur libre

- Selection des briques au clic sur les volumes orientes de l'apercu ; Maj+clic pour parcourir les superpositions.
- Surbrillance filaire jaune et gizmos X/Y/Z toujours visibles a l'ecran.
- Mode deplacement (G) ou rotation (R), glissement avec pas configurables, un seul Ctrl+Z par geste.
- Interface reactive pour plusieurs hauteurs de fenetre ; les boutons ne se chevauchent plus dans les tests de mise en page.
- Le moteur GPU V0.9.5.2 est conserve sans modification, les fichiers .bcpx restent en version 1.
- Prototype non interactif `fr.brickcraft.guide.BrickGuidePlanner` pour de futures instructions couche par couche / pas a pas.
- Details et limites : `V0.9.8_INTERACTION_3D_ET_GUIDES.md`.
- Validation locale : `TESTER_INTERACTION_098_WINDOWS.bat` puis `CONSTRUIRE_WINDOWS.bat`.

# BrickCraft V0.9.7 — Gestion des projets et interface corrigée

Consulte **V0.9.7_GESTION_PROJETS_ET_UI.md**. Décharger un fichier ne supprime jamais une construction du monde ; pour cela, utilise le nouveau gestionnaire de projections proches avec confirmation. Le rendu GPU et les modèles enregistrés sont conservés.

# BrickCraft V0.9.5.2 — formes des pieces BrickLink Studio

Voir **V0.9.5.2_FORMES_STUDIO.md**. Installer `studio_parts.zip` en complement de `complete.zip` ; pas de modification de la projection/NBT, pas de changement du renderer GPU V0.9.5.1. Les textures imprimees restent en attente de prise en charge UV.

# BrickCraft V0.9.5.1 — GPU rendu stable (bêta)

Voir [V0.9.5.1_STABILITE_VISUELLE.md](V0.9.5.1_STABILITE_VISUELLE.md) : culling OFF, matrice explicite, tests et touches F6/F7/F8/F9/F10.

# BrickCraft V0.9.5 — rendu GPU expérimental

Lire **V0.9.5_GPU_LOTS.md** pour les nouveautés, les paramètres GPU, le test F8/F9, le retour à la V0.9.4 et les limites.

# BrickCraft V0.9.3 — LOD de formes LEGO (expérimental)

Cette étape introduit des variantes simplifiées **des véritables triangles LDraw**, au lieu de substituer systématiquement une boîte cubique dès que le budget graphique est épuisé.

- Chaque pièce chargée reçoit trois variantes approximatives (16 / 8 / 4 subdivisions) de sa géométrie originale. Cela préserve les grandes silhouettes, les pentes et les couleurs des faces.
- `shape_lod_mode=all` (nouveau défaut dans un fichier de configuration neuf) : les pièces déjà chargées gardent une vraie forme LDraw, même loin. Ce mode peut utiliser plus de triangles que le budget de détails et coûter des FPS.
- `shape_lod_mode=hybrid` : mode de secours, moins exigeant, qui accepte des gabarits lointains.
- La boucle de rendu réutilise les tableaux de positions au lieu d'allouer des centaines de milliers de petits tableaux à chaque image.
- Les triangles superposés générés volontairement dans les deux sens ont été retirés (une seule face dessinée par triangle). Cela ne résout pas toutes les superpositions propres à LDraw.
- Les améliorations de détails restent distribuées par proximité à la caméra.
- Les fichiers `.bcpx`, NBT, `.bcp`, les couleurs et les exports n'ont pas changé.

**Tests autonomes :** `TESTER_FORMES_WINDOWS.bat` (Java 21 ; teste six pièces avec l’archive installée). `TESTER_LOD_WINDOWS.bat` conserve les tests de priorité.

**Attention :** le premier chargement de `complete.zip` et le remplissage du cache sont progressifs : des boîtes peuvent apparaître brièvement jusqu'à ce que les vrais maillages soient chargés. Les pièces manquantes restent des gabarits, les impressions `TEXMAP` restent non gérées. Ce n'est pas un remeshing garantissant la fermeture parfaite de chaque surface.

Dans `run/config/brickcraft/precise-render.properties`, ajouter :

```properties
shape_lod_mode=all
```

Redémarrer Minecraft après modification du fichier. En cas de perte excessive de FPS, utiliser `shape_lod_mode=hybrid` ou `lod_profile=performance`. Tester **sur une copie du monde**. La compilation Fabric complète et les tests de rendu réel doivent encore être confirmés.

---

# BrickCraft V0.9.2 — correction du LOD inverse

**Correction du bug observe sur Neuschwanstein** : les triangles de rendu haute fidélité sont attribués en premier aux pièces les plus proches **de la caméra**, au lieu de suivre l'ordre des pieces du fichier MPD/BCPX. La distance est calculée à la surface de la boîte orientée approximative de la pièce, et pas seulement depuis son origine.

- Compatible Fabric 1.21.1 / Java 21, à compiler sur Windows.
- Conserve la projection exacte et les anciennes sauvegardes `.bcpx`, `.bcp` et NBT.
- Cache des maillages LRU : une pièce ne reste plus coincée en LOD bas uniquement parce que le cache était rempli.
- Les modèles proches sont décodés progressivement, sans gros chargement d'un seul coup.
- `TESTER_LOD_WINDOWS.bat` vérifie la priorité des pièces proches.

## Modes de qualité

Dans `run/config/brickcraft/precise-render.properties` :

```properties
lod_profile=balanced
max_real_triangles=28000
mesh_radius_blocks=42
```

- `performance` : 16 000 triangles détaillés, rayon 32 blocs.
- `balanced` (par défaut) : conserve tes valeurs `max_real_triangles` et `mesh_radius_blocks` existantes.
- `photo` : 160 000 triangles détaillés, rayon 112 blocs, avec davantage de maillages autorisés. Peut réduire fortement les FPS.
- `custom` : utilise les valeurs précédentes et optionnellement `new_meshes_per_frame`, `max_triangles_per_mesh`, `cache_triangle_limit`.

**Redémarrer Minecraft après chaque changement**. Pour activer Photo, ajoute ou change simplement `lod_profile=photo`. Le fichier de configuration existant V0.9.1 reste compatible.

## Validation

- Les tests autonomes `TEST_LOD_PRIORITY.java` vérifient l'ordre indépendant du fichier source, les distances, les priorités et le budget.
- `TEST_PRECISION_MONDE.java` est conservé pour vérifier la sauvegarde du château (requiert `complete.zip`).
- Compilation Fabric complète et rendu réel dans Minecraft : **non vérifiés ici**. Tester d'abord dans une copie de ton monde.

## Limites conservées

Le monde montre toujours une **projection visuelle non solide**, et non des pièces individuelles éditables. Les 249 substitutions de motifs BrickLink sont toujours des formes de base. Les risques de surfaces superposées et artefacts de profondeur, ainsi que la gestion PBR, restent à traiter.

---

# BrickCraft V0.9.1 — Projection précise des projets BrickLink

**Version expérimentale pour Minecraft Fabric 1.21.1 / Java 21.**

## Nouveauté principale

Depuis BrickCraft Studio (`B` → `Ctrl+I`), ouvre un fichier `.io`, `.ldr`, `.mpd` ou `.bcpx`, clique « Analyser / prévisualiser », puis **« 3. Projeter dans le monde (BETA) »**.

- **Avant d’ouvrir le Studio, vise le sol** à moins de 12 blocs en mode Créatif : le bloc d’ancrage se positionnera à côté du bloc visé, généralement au-dessus du sol.
- Les **coordonnées fractionnaires, les rotations matricielles et les couleurs LDraw** sont conservées sans arrondi.
- Le château `21063-Neuschwanstein-Castle.io.bcpx` contient **3 245 pièces** et mesure environ **26 × 39 × 58 blocs**.
- Ses 9 références décorées `bl_...` correspondent à 5 formes de base présentes dans `complete.zip` : les **249 occurrences restent visibles**, mais leurs **impressions/décorations ne sont pas reproduites**.
- Le moteur affiche les vrais maillages des pièces proches et des **boîtes de substitution** pour une partie des pièces lointaines afin de limiter les ralentissements.

**IMPORTANT :** il s’agit d’une **projection visuelle persistante**, PAS de 3 245 briques physiques. Elle n’a pas de collision par pièce, ne se modifie pas brique par brique et peut traverser le terrain. Un seul bloc d’ancrage est enregistré dans le monde. Pour retirer toute la projection, **casse le petit marqueur cyan au point d’ancrage** (ou restaure une sauvegarde). Les anciennes constructions restent au format classique, inchangé.

## Prérequis / installation

1. Sauvegarder le monde `run/saves/` avant essai.
2. Placer ton fichier `complete.zip` d’octobre dans le répertoire `run/config/brickcraft/` **de l’instance que tu lances**.
3. Décompresser cette archive dans un dossier indépendant et lancer `CONSTRUIRE_WINDOWS.bat`.
4. Lancer Minecraft, en Créatif, en visant le dessus d’une zone de terrain plate et dégagée.
5. Lancer `INSTALLER_EXEMPLE_NEUSCHWANSTEIN_WINDOWS.bat` pour installer une copie du château fourni dans `EXEMPLES/` (ou déposer ton propre `.bcpx` dans `run/config/brickcraft/imports/`). Analyser, projeter, puis fermer le Studio pour revenir en jeu.
6. Si le jeu ralentit, réduire la distance d’affichage des Block Entities dans `run/config/brickcraft/render.properties` et regarder la projection de plus loin.

## Sécurité et limites

- L’envoi réseau se fait par paquets de 16 pièces, avec validation côté serveur avant la création de l’ancre.
- L’ancre ne remplace aucun terrain solide et sa création est réservée au mode Créatif.
- L’ensemble du projet est sauvegardé en **GZIP** dans le NBT du bloc d’ancrage. Si `complete.zip` manque à un démarrage, les octets sont conservés dans la sauvegarde.
- Les collisions précises, les pièces imprimées TEXMAP et le placement de briques éditables séparément **restent à développer**.
- Les maillages complexes peuvent provoquer une baisse de FPS. Par défaut, le rendu applique un budget de **28 000 triangles détaillés** par image, avec réduction du détail au-delà de **42 blocs**. Modifiable dans `run/config/brickcraft/precise-render.properties` (`max_real_triangles`, `mesh_radius_blocks`), après le premier lancement ; redémarrage nécessaire.
- Aucun test de compilation Fabric complet ni test client réel n’a été exécuté ici : lancer d’abord sur **une copie de ton monde**.

Les anciennes sauvegardes `.bcp`, les anciennes constructions et le Studio classique sont conservés.


## V0.9.6 : editeur de placement libre

Voir [V0.9.6_PLACEMENT_LIBRE.md](V0.9.6_PLACEMENT_LIBRE.md) : projet LDraw editable en coordonnees decimales et rotations libres, compatible .bcpx / projection GPU existante.


# BrickCraft V0.21.0 — Guide intégré, visuels d’items et langues

Voir `V0.21.0_GUIDE_ASSETS_LANGUES.md`.
- Guide rapide intégré prévu pour expliquer l’essentiel du mod sans mur de texte.
- Icônes dédiées pour les objets principaux du mod.
- Localisation de base étendue à plusieurs langues majeures, avec repli automatique sur l’anglais si besoin.

# BrickCraft V0.23.0 — Guidebook fonctionnel + localisation des interfaces

Lire [V0.23.0_GUIDEBOOK_TRADUCTIONS_AUDIT.md](V0.23.0_GUIDEBOOK_TRADUCTIONS_AUDIT.md).
- Le guide est un véritable item : `brickcraft:guide_book`, onglet créatif, clic droit pour lire, `/brickcraft guide` pour l'obtenir.
- 6 pages de guide avec de vrais visuels d'objets du mod ; traduites dans 11 langues.
- Les principaux écrans, panneaux et outils disposent désormais de traductions via les ressources Minecraft et d'un repli anglais lorsque les traductions locales manquent.
- **Certaines interfaces techniques et notifications serveur restent à traduire intégralement** ; ne pas considérer les autres langues que le français/anglais comme finalisées.


## V0.25.2 RC3 — correctifs de localisation

Consulter `V0.25.2_RC3_CORRECTIONS_ET_CODEX.md`, `AUDIT_I18N_RESTANT_V0252.csv` et `MISSION_CODEX_BRICKCRAFT.md`. Exécuter `TESTER_CORRECTIFS_V0252_WINDOWS.bat` avant la compilation Fabric.
