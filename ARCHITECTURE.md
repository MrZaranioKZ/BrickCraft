# V0.9.3 / géométrie LOD

`PreciseMeshLods` effectue un regroupement quantifié de sommets sur trois résolutions, supprime les triangles devenus dégénérés et dédoublonne les triangles coplanaires identiques (même couleur) par pièce. Le renderer conserve ces niveaux dans un cache LRU et choisit le niveau selon la distance et le budget graphique. Aucune transformation `.bcpx` n'est réécrite.

Le mode `all` conserve le maillage le plus simple de chaque pièce chargée indépendamment du budget supplémentaire de détail, ce qui est visuellement plus fidèle qu'un rectangle, mais moins strict sur les performances. Le mode `hybrid` reste disponible. Les surfaces imprimées, les collisions et l'édition individuelle ne sont pas implémentées par ce changement.

# V0.9.2 — Allocation LOD par proximité

- Étape 1 : calcul de la distance caméra -> AABB approximative de chaque pièce en coordonnées monde avec matrice LDraw exacte.
- Étape 2 : tri croissant des distances et allocation du budget de triangles détaillés aux pièces les plus proches en premier.
- Étape 3 : rendu en ordre source conservé, avec une sélection fixée pour la frame.
- Cache LRU du renderer et dégagement des anciennes entrées au lieu de marquer un modèle valide comme impossible.
- Config `lod_profile` : balanced, performance, photo, custom.

# Architecture V0.9.1 — branche d’export précise (expérimentale)

- `PreciseWorldModel` : préflight exact du volume transformé (8 coins par pièce), contrôle des matrices, sérialisation GZIP avec bornes.
- `PreciseReferenceMapper` : substitue les noms de pièces décorées BrickLink (`bl_*pb*`) par leur forme LDraw sans perdre l’original dans le modèle.
- `PreciseExportPayload` : protocole C2S BEGIN/PARTS/COMMIT/CANCEL par lots de 16.
- `PreciseExportManager` : validation progressive côté serveur, puis placement atomique d’un seul bloc de projection.
- `PreciseDisplayBlock` + `PreciseDisplayEntity` : objet non solide, sérialisation NBT compressée et synchronisation client.
- `PreciseDisplayRenderer` : maillages réels transformés via matrice 3x3, boîtes LOD, cache local de 240k triangles, budget réglable, 28k triangles détaillés/image par défaut.

**Aucune migration intrusive de `BrickPiece` ou de `BrickCellEntity`.** Le modèle de projection n’est pas une construction physique, ne possède pas de points d’accroche et n’est pas encore éditable à l’unité. C’est un étage de transition pour les projets BrickLink complexes.
