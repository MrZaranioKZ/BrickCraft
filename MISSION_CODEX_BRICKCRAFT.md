# Mission Codex — BrickCraft, préparation de la V1.0

Tu travailles sur l'intégralité du dépôt BrickCraft, mod Minecraft Fabric **1.21.1 / Java 21**, version de départ **0.25.2 RC3**. Le but est de sortir une **V1.0 stable**, pas de modifier seulement les captures jointes.

## Priorité 1 — Internationalisation intégrale

1. Répertorier **tous** les textes visibles par le joueur : GUI, overlays, barres d'action, chat, confirmations, tooltips, boutons, erreurs, logs affichés, noms d'items, Guidebook, commandes côté serveur, messages réseau, imports et physique.
2. Les convertir en **clés sémantiques stables** avec arguments typés (`Text.translatable` côté serveur, et templates bruts formatés correctement côté client). Interdire `tr()` de fragments et l'application de la traduction sur une chaîne déjà tronquée. Ne pas traduire les ID LDraw, noms de fichiers, chemins et commandes littérales.
3. Réviser **réellement** les fichiers `fr_fr`, `en_us`, `es_es`, `de_de`, `pt_br`, `ru_ru`, `zh_cn`, `zh_tw`, `ja_jp`, `ar_sa`, `hi_in`. Le taux de clés présentes n'est pas un taux de traduction. Utiliser des relectures linguistiques pour les textes ambigus.
4. Écrire un test qui échoue pour toute clé absente, tout placeholder incorrect, toute chaîne affichée contenant `Format error`, et toute chaîne dynamique non traduisible.
5. Exploiter `AUDIT_I18N_RESTANT_V0252.csv` comme **point de départ** (pas comme liste exhaustive). Pour chaque occurrence, marquer corrigé / intentionnel (noms techniques) / non visible.

## Priorité 2 — Interface et ergonomie

- Parcourir **chaque écran et chaque état** : sans projet, avec fichier, erreur, grande bibliothèque, grande collection, pièces manquantes, alternatives vides/pleines, projet en cours de modification, GPU/CPU, fenêtre de confirmation.
- Tester plusieurs échelles GUI Minecraft, résolutions et langues longues. Les libellés ne doivent ni se chevaucher ni dépasser. Autoriser les lignes multiples, scroll/infobulles pour messages longs.
- Regrouper ou masquer les boutons redondants, mais ne supprimer aucune fonction sans vérifier son usage. Éviter les actions accessibles en état invalide.
- Réduire le spam du chat de physique ; conserver des notifications utiles d'échec et de fin.

## Priorité 3 — Validation réelle du mod

- Exécuter la **compilation Fabric complète** (`CONSTRUIRE_WINDOWS.bat` ou Gradle compatible). Corriger erreurs de mapping et API 1.21.1.
- Lancer le client de développement et un serveur dédié (pas seulement syntaxe Java). Tester les menus, import `.io/.mpd/.ldr/.bcpx`, `complete.zip`, pièces personnalisées, remplacement/suppression et sauvegardes, gros sets, collisions, physique progressive, commandes, item créatif, livre, GPU legacy/modern, PBR avec shader compatible.
- Vérifier les mondes et sauvegardes d'une ancienne version dans une **copie**, sans corruption ni pertes.
- WorldEdit/Axiom : décrire séparément la compatibilité des briques classiques et celle des grandes projections. Ne pas prétendre à une intégration universelle.
- Ne qualifier de « V1.0 finale » qu'après compilations, tests réels, vérification des traductions et clôture des anomalies bloquantes.

## Commandes utiles

```text
TESTER_CORRECTIFS_V0252_WINDOWS.bat
CONSTRUIRE_WINDOWS.bat
python tools/AUDIT_I18N_LAYOUT_V0252.py
```

Livrables attendus : code versionné, tests automatisés, rapport des clés/translations, liste des bugs corrigés, problèmes connus non bloquants, résultats compilations et scénarios de test en jeu.
