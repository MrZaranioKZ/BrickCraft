package fr.brickcraft.client;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Language;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;

/** Client-language sensitive rendering of legacy screen labels.
 * Full static labels use a stable keyed dictionary; dynamic counters retain their values.
 * No language file is read manually: the Minecraft resource manager provides current locale. */
public final class BrickI18n {
    private BrickI18n(){}
    private static String key(String value){
        String hex=Integer.toHexString(value.hashCode());
        return "brickcraft.ui.u"+"00000000".substring(hex.length())+hex;
    }
    private static String translated(String source){
        String k=key(source);
        String value=I18n.translate(k);
        return k.equals(value)?source:value;
    }
    private static final String[] FRAGMENTS = {
        "Bibliotheque rechargee : retour puis Analyser pour un bilan actualise. ",
        " occurrences retirees du projet charge. Le .io original est conserve.",
        " imprimees sans texture. Retourne en jeu pour voir le resultat.",
        "Cette reference n'a aucune geometrie LDraw utilisable : ",
        " (l'objet projete ne compte pas comme une piece posee)",
        " references. Sauvegarde ta copie .bcpx dans Actions.",
        " : maintiens et glisse, relache pour enregistrer.",
        "BrickCraft : sauvegarde qualite impossible : ",
        " pieces au serveur. Verification en cours...",
        " | K: connecteurs | N: aimant | B: connecter",
        " - variantes non garanties interchangeables",
        " | Verifie la geometrie avant de confirmer.",
        " pieces. Utilise 'Projeter dans le monde'.",
        " references. Le .io original reste intact.",
        " pieces. Sauve la copie .bcpx (Actions).",
        " pieces (modifications non enregistrees)",
        "Pas de pieces jointes importables : ",
        "Sauvegarde precise (sans arrondi) : ",
        "Ouverture navigateur impossible : ",
        "GPU non disponible (affichage) : ",
        "Impossible d'ouvrir le dossier : ",
        " origines de pieces (mode rapide)",
        " pieces; utilise un sous-modele.",
        "GPU : preparation des pieces... ",
        "Lecture du dossier impossible : ",
        " | LUMIERE:MINECRAFT | CONTACT:",
        "GPU non disponible (upload) : ",
        "Sauvegarde auto non chargee : ",
        "BrickCraft : edition en monde ",
        "BrickCraft : pas de rotation ",
        " | Rapport non enregistre : ",
        "Etapes Studio retrouvees : ",
        "Ouverture non disponible : ",
        "Dernier atelier restaure : ",
        "N : fermer | R : tourner | ",
        "Pieces du .io installees : ",
        "Manipulation interrompue : ",
        "Piece invalide a la ligne ",
        "Reference non installee : ",
        "Projet externe importe : ",
        " | relacher pour valider",
        "BRICKCRAFT WORLD EDIT | ",
        "BrickCraft : projection ",
        "BrickCraft : inspecteur ",
        "Supprimer la reference ",
        "Studio grille limite a ",
        "Erreur de sauvegarde : ",
        "Editeur libre limite a ",
        "Projections proches : ",
        "BrickCraft : rotation ",
        " (Ctrl+Z pour annuler)",
        "References absentes : ",
        "Numero invalide : 1 a ",
        "Projection envoyee : ",
        " | Ctrl+V pour coller",
        "Projet introuvable : ",
        "Rapport impossible : ",
        "BrickCraft : pipette ",
        "Deplacement refuse : ",
        "Conversion refusee : ",
        "Projection refusee : ",
        "Conversion echouee : ",
        " resultats (max 500)",
        "BRICKCRAFT | NOTICE ",
        "Guides d'accroche : ",
        "Points d'accroche : ",
        " | clique pour poser",
        " triangles | O : CPU",
        " pieces supprimees (",
        "Formes classiques : ",
        "Rotation refusee : ",
        "Erreur d'analyse : ",
        "BrickCraft : axe X ",
        " (source conservee)",
        "Champs invalides : ",
        "Rotations libres : ",
        "Fichiers trouves : ",
        "BrickCraft : axe Z ",
        "Echec sauvegarde : ",
        "Echec chargement : ",
        "Glisse la poignee ",
        "Pieces visibles : ",
        " | MEMOIRE BLOQUE ",
        "Piece recoloree : ",
        "Couleur choisie : ",
        " non verifiee : ",
        " pieces, grille ",
        "Atelier libre : ",
        "Projet charge : ",
        "Apercu Studio : ",
        " | O : reessayer",
        "Piece tournee : ",
        "Materiaux page ",
        " | VRAM estim.:",
        "Ajout refuse : ",
        " occurrences : ",
        " pieces, ancre ",
        "Pas entre 0 et ",
        "Vue cadree sur ",
        " occurrences, ",
        "Suggestions : ",
        "Progression : ",
        " (deplacement)",
        " | F4: quitter",
        "Manquantes : ",
        "Sauvegarde : ",
        "BrickCraft : ",
        " | plafond : ",
        "Projection : ",
        " (non charge)",
        "Deplacement ",
        " | visibles:",
        " deg (pas : ",
        "GPU actif | ",
        "Selection : ",
        "GPU DETAIL:",
        "GLISSEMENT ",
        "% | CAMERA:",
        "Finition : ",
        "Cible 3D : ",
        " (rotation)",
        " manquants:",
        "Rotation : ",
        "Supprime : ",
        "Physique : ",
        " references",
        "Remplacer ",
        "Opacite : ",
        "Hauteur : ",
        " pieces | ",
        "Dossier : ",
        " triangles",
        " | hitbox ",
        " | AIMANT ",
        "Couleur : ",
        " rejetes:",
        "Envoi de ",
        " | prets:",
        "Rotation ",
        "Pieces : ",
        " couleurs",
        "Taille : ",
        "Apercu : ",
        " pieces, ",
        " invalide",
        "Charge : ",
        "Moteur : ",
        "Erreur : ",
        "Ajoute : ",
        "Detail : ",
        "Molette: ",
        " cellules",
        "Remplace ",
        " pieces)",
        "LDRAW : ",
        " draw | ",
        "Pas Y : ",
        "Palette ",
        "Angle : ",
        " | page ",
        " volumes",
        "Plateau ",
        "Poignee ",
        "Rendu : ",
        "Cible : ",
        "Copie : ",
        "Piece : ",
        " | CULL:",
        " tris | ",
        "LDraw : ",
        "FORMES ",
        " ms CPU",
        "Projet ",
        "° (pas ",
        "BCPX : ",
        "Grille ",
        " | AXE ",
        "Brique ",
        "Mode : ",
        " pieces",
        "Apercu ",
        "Cible :",
        "Plaque ",
        "Alpha ",
        "Tuile ",
        "LDraw ",
        " blocs",
        "Ligne ",
        "LDRAW ",
        "Piece ",
        "Pente ",
        "Code ",
        " par ",
        "GRID ",
        " deg)",
        "Page ",
        " LDU",
        " deg",
        "Axe ",
        " MiB",
        "les "
    };
    public static String tr(String source){
        if(source==null || source.isEmpty())return source;
        String direct=translated(source);
        if(!direct.equals(source))return direct;
        String result=source;
        for(String fragment:FRAGMENTS){
            if(result.contains(fragment)){
                String replacement=translated(fragment);
                if(!fragment.equals(replacement))result=result.replace(fragment,replacement);
            }
        }
        return result;
    }
    /** Translate a complete message with parameters. Never localize sentence fragments.
     * Keys are named and stable, unlike the compatibility hash dictionary. */
    public static String fmt(String translationKey,Object...args){
        // I18n.translate(key) eagerly formats placeholders with zero arguments.
        // Consequently "%s" becomes Minecraft's "Format error: ..." BEFORE
        // String.format gets our arguments. Read the untranslated template.
        String template=Language.getInstance().get(translationKey);
        if(translationKey.equals(template))return translationKey;
        try{return String.format(java.util.Locale.ROOT,template,args);}
        catch(java.util.IllegalFormatException exception){
            // Never display Minecraft's internal Format error in a public UI.
            // Preserve the localized template and report the missing placeholder
            // in the test tool instead of discarding a whole screen label.
            return template.replace("%%", "%");
        }
    }
    public static Text tr(Text text){return text;}
    public static OrderedText tr(OrderedText text){return text;}
}
