# Harness d'analyse du cycle de vie des VertexConsumer

Ces 15 petites classes Java de substitution permettent de compiler la vraie classe `BrickCellRenderer.java` sans les dépendances Minecraft. Le `VertexConsumerProvider` simulé termine le buffer précédent lorsque le code demande un autre RenderLayer. Une écriture dans un buffer terminé déclenche exactement `IllegalStateException: Not building!`.

Le test inclut des briques opaques, transparentes et une pièce LDraw fictive; le rendu réel Minecraft/GL et la qualité visuelle ne sont pas simulés.

Lancez `TESTER_RENDERER_WINDOWS.bat` à la racine du projet pour vérifier la non-régression.
