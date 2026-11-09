# Validation V1.0 — Phase 2

Base: 0.25.2 RC3, Minecraft 1.21.1, Java 21.

| Gravité | État | Vérification |
| --- | --- | --- |
| Bloquant | Ouvert | Le Wrapper démarre, mais le poste de validation ne résout pas services.gradle.org. Le Gradle déjà installé avait échoué à se connecter à son processus sur 127.0.0.1, avant la compilation. |
| Bloquant | Ouvert | [Première exécution GitHub Actions](https://github.com/MrZaranioKZ/BrickCraft/actions/runs/42624550304) en échec sans runner ni étape enregistrée. Cause distante non confirmée. |
| Majeur | Ouvert | 135 candidats textuels. Le CSV produit par tools/AUDIT_I18N_LAYOUT_V0252.py fournit les fichiers et lignes. De 856 à 860 valeurs identiques à l'anglais pour chacune des neuf langues secondaires. |
| Majeur | Ouvert | Audit visuel à réaliser sur les états vides et chargés, les longues listes, les confirmations et plusieurs échelles GUI, dont une langue longue et une langue RTL. |
| Bloquant | Ouvert | Scénarios Fabric client et serveur dédié à vérifier après compilation. |
| — | Réussi | 11 groupes autonomes: guide, disposition/GPU, PBR, commandes, physique, équivalences, suppression, capacité, assemblage, IO et dossiers. |
| — | Réussi | Le JAR préexistant correspond aux ressources de la RC3 d'origine, avec les 11 langues et les points d'entrée Fabric. Aucun JAR neuf validé. |

## Modifications

- Wrapper officiel 8.10.2, empreintes SHA-256 du JAR et de la distribution, workflow Java 21 et vérificateur du JAR.
- Secours Python sous Windows et utilisation du Wrapper par le lanceur PowerShell.
- Audit des clés, des doublons et des paramètres indexés. Deux messages d'édition dans le monde traduits dans les onze langues.

## Suite de la validation

Exécuter le workflow avec un runner disponible. Une fois le JAR neuf validé, tester le client et un serveur dédié sur des copies de mondes et de projets anciens. Vérifier les cinq formats d'import, complete.zip, le Colisée et la Death Star, les pièces manquantes, les notices, le rendu GPU/LOD/PBR, les collisions et changements de hitbox, ainsi que les interactions WorldEdit/Axiom et la persistance après redémarrage. Terminer l'inventaire des textes, les traductions et le contrôle visuel avant de déclarer la V1.0 prête.
