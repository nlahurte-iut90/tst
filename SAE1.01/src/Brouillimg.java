import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.Math;
import java.util.Scanner;

public class Brouillimg {

    public static void main(String[] args) throws IOException {
        // Vérification du nombre d'arguments minimum
        if (args.length < 2) {
            System.err.println("Usage normal : java Brouillimg <image> <clé> [image_sortie] [process]");
            System.err.println("Usage cassage : java Brouillimg <image> break");
            System.exit(1);
        }

        String inPath = args[0];
        BufferedImage inputImage = ImageIO.read(new File(inPath));
        if (inputImage == null) throw new IOException("Image introuvable : " + inPath);

        // --- BLOC CASSAGE ---
        if (args[1].equalsIgnoreCase("break")) {
            int[][] inputGL = rgb2gl(inputImage);

            try (Scanner sc = new Scanner(System.in)) {
                System.out.println("Choix de la méthode :\n1 - Euclidienne (Distance)\n2 - Pearson (Corrélation)");
                System.out.print("Votre choix : ");
                String choix = sc.nextLine();
                String mode = choix.equals("2") ? "Pearson" : "Euclid";

                System.out.println("Analyse des 32768 clés possibles...");
                long start = System.currentTimeMillis();
                int foundKey = breakKey(inputGL, mode);
                long end = System.currentTimeMillis();

                System.out.println("\nSuccès ! Clé trouvée : " + foundKey);
                System.out.println("Temps d'exécution : " + (end - start) + " ms");

                // Génération de l'image avec la clé trouvée
                int[] perm = generatePermutation(inputImage.getHeight(), foundKey);
                BufferedImage recovered = unScrambleLines(inputImage, perm);

                // Vérification et écriture avec gestion d'erreur
                if (recovered == null) {
                    System.err.println("Erreur : image récupérée est null");
                    return;
                }

                File outputFile = new File("trouve_" + foundKey + ".png");
                boolean success = ImageIO.write(recovered, "png", outputFile);

                if (success) {
                    System.out.println("Résultat sauvegardé dans : " + outputFile.getAbsolutePath());
                } else {
                    System.err.println("Erreur lors de l'écriture de l'image");
                }
            }
            return; // Fin du programme pour le mode break
        }

        // --- BLOC NORMAL (Si on n'est pas en mode break) ---
        int key = Integer.parseInt(args[1]) & 0x7FFF;
        String outPath = (args.length >= 3) ? args[2] : "out.png";
        String process = (args.length >= 4) ? args[3] : "scramble";

        int[] perm = generatePermutation(inputImage.getHeight(), key);
        BufferedImage result;

        if (process.equalsIgnoreCase("unscramble")) {
            result = unScrambleLines(inputImage, perm);
        } else {
            result = scrambleLines(inputImage, perm);
        }

        // Calcul du score de différence
        DistancePixel(inputImage, result);

        ImageIO.write(result, "png", new File(outPath));
        System.out.println("Traitement terminé. Image enregistrée : " + outPath);
    }

    /**
     * Convertit une image RGB en niveaux de gris (GL).
     * @param inputRGB image d'entrée en RGB
     * @return tableau 2D des niveaux de gris (0-255)
     */
    public static int[][] rgb2gl(BufferedImage inputRGB) {
        final int height = inputRGB.getHeight();
        final int width = inputRGB.getWidth();
        int[][] outGL = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = inputRGB.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                // luminance simple (évite float)
                int gray = (r * 299 + g * 587 + b * 114) / 1000;
                outGL[y][x] = gray;
            }
        }
        return outGL;
    }

    /**
     * Génère une permutation des entiers 0..size-1 en fonction d'une clé.
     * @param size taille de la permutation
     * @param key clé de génération (15 bits)
     * @return tableau de taille 'size' contenant une permutation des entiers 0..size-1
     */
    public static int[] generatePermutation(int size, int key) {
        int[] scrambleTable = new int[size];
        for (int i = 0; i < size; i++) {
            scrambleTable[i] = scrambledId(i, size, key);
        }
        return scrambleTable;
    }

    /**
     * Mélange les lignes d'une image selon une permutation donnée.
     * @param inputImg image d'entrée
     * @param perm permutation des lignes (taille = hauteur de l'image)
     * @return image de sortie avec les lignes mélangées
     */
    public static BufferedImage scrambleLines(BufferedImage inputImg, int[] perm) {
        int width = inputImg.getWidth();
        int height = inputImg.getHeight();
        if (perm.length != height) {
            throw new IllegalArgumentException("Taille d'image <> taille permutation");
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            int nouveau_y = perm[y];
            for (int x = 0; x < width; x++) {
                int pixel = inputImg.getRGB(x, y);
                out.setRGB(x, nouveau_y, pixel);
            }
        }

        return out;
    }

    /**
     * Démélange les lignes d'une image selon une permutation donnée.
     * @param inputImg image d'entrée (brouillée)
     * @param perm permutation des lignes
     * @return image de sortie avec les lignes démélangées
     */
    public static BufferedImage unScrambleLines(BufferedImage inputImg, int[] perm) {
        int width = inputImg.getWidth();
        int height = inputImg.getHeight();
        if (perm.length != height) {
            throw new IllegalArgumentException("Taille d'image <> taille permutation");
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            int nouveau_y = perm[y];
            for (int x = 0; x < width; x++) {
                int pixel = inputImg.getRGB(x, nouveau_y);
                out.setRGB(x, y, pixel);
            }
        }

        return out;
    }

    /**
     * Calcule la distance euclidienne entre deux images.
     * @param inputImg image d'entrée originale
     * @param processedImg image traitée
     * @return score de différence sur 100
     */
    public static int DistancePixel(BufferedImage inputImg, BufferedImage processedImg) {
        int width = inputImg.getWidth();
        int height = inputImg.getHeight();
        int Size = width * height;
        double score = 0.0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int inputPixel = inputImg.getRGB(x, y);
                int processedPixel = processedImg.getRGB(x, y);

                int Bip = inputPixel & 0xFF;
                int Bpp = processedPixel & 0xFF;
                int Gip = (inputPixel >> 8) & 0xFF;
                int Gpp = (processedPixel >> 8) & 0xFF;
                int Rip = (inputPixel >> 16) & 0xFF;
                int Rpp = (processedPixel >> 16) & 0xFF;
                int Aip = (inputPixel >> 24) & 0xFF;
                int App = (processedPixel >> 24) & 0xFF;

                int deltaA = Aip - App;
                int deltaR = Rip - Rpp;
                int deltaG = Gip - Gpp;
                int deltaB = Bip - Bpp;

                score += Math.sqrt(
                        deltaA * deltaA +
                                deltaR * deltaR +
                                deltaG * deltaG +
                                deltaB * deltaB
                );
            }
        }

        int scoreSurCent = (int) (score / (Size * 4.41));
        System.out.println("scoreSurCent = " + scoreSurCent + "/ 100");
        return scoreSurCent;
    }

    /**
     * Trouve la clé de brouillage par force brute.
     * @param imageGL image en niveaux de gris
     * @param mode "Pearson" ou "Euclid"
     * @return la clé trouvée
     */
    public static int breakKey(int[][] imageGL, String mode) {
        int bestKey = -1;
        double bestScore = mode.equals("Pearson") ? -1e18 : 1e18;

        for (int k = 0; k < 32768; k++) {
            int[] perm = generatePermutation(imageGL.length, k);

            // Débrouiller l'image avec cette clé
            int[][] unscrambled = applyUnscramble(imageGL, perm);

            // Calculer le score sur l'image débrouillée
            double score = mode.equals("Pearson") ?
                    scorePearsonDirect(unscrambled) :
                    scoreEuclideanDirect(unscrambled);

            if ((mode.equals("Pearson") && score > bestScore) ||
                    (mode.equals("Euclid") && score < bestScore)) {
                bestScore = score;
                bestKey = k;
            }

            // Affichage de progression tous les 2048 essais
            if (k % 2048 == 0 && k > 0) {
                System.out.print(".");
            }
        }
        System.out.println(); // Retour à la ligne après la progression
        return bestKey;
    }

    /**
     * Applique le débrouillage à une image en niveaux de gris.
     * @param gl image brouillée
     * @param perm permutation
     * @return image débrouillée
     */
    public static int[][] applyUnscramble(int[][] gl, int[] perm) {
        int height = gl.length;
        int width = gl[0].length;
        int[][] result = new int[height][width];

        for (int y = 0; y < height; y++) {
            result[y] = gl[perm[y]].clone();
        }
        return result;
    }

    /**
     * Score euclidien : somme des distances entre lignes adjacentes.
     * Plus le score est faible, plus l'image est cohérente.
     */
    public static double scoreEuclideanDirect(int[][] gl) {
        double total = 0;
        for (int i = 0; i < gl.length - 1; i++) {
            int[] l1 = gl[i], l2 = gl[i + 1];
            double d = 0;
            for (int j = 0; j < l1.length; j++) {
                d += Math.pow(l1[j] - l2[j], 2);
            }
            total += Math.sqrt(d);
        }
        return total;
    }

    /**
     * Score Pearson : somme des corrélations entre lignes adjacentes.
     * Plus le score est élevé, plus l'image est cohérente.
     */
    public static double scorePearsonDirect(int[][] gl) {
        double total = 0;
        for (int i = 0; i < gl.length - 1; i++) {
            int[] x = gl[i], y = gl[i + 1];
            double mx = 0, my = 0, n = x.length;
            for (int v : x) mx += v;
            for (int v : y) my += v;
            mx /= n;
            my /= n;
            double num = 0, dx = 0, dy = 0;
            for (int j = 0; j < n; j++) {
                double diffX = x[j] - mx, diffY = y[j] - my;
                num += diffX * diffY;
                dx += diffX * diffX;
                dy += diffY * diffY;
            }
            if (dx * dy != 0) {
                total += num / Math.sqrt(dx * dy);
            }
        }
        return total;
    }

    /**
     * Renvoie la position de la ligne id dans l'image brouillée.
     * @param id indice de la ligne dans l'image claire (0..size-1)
     * @param size nombre total de lignes dans l'image
     * @param key clé de brouillage (15 bits)
     * @return indice de la ligne dans l'image brouillée (0..size-1)
     */
    public static int scrambledId(int id, int size, int key) {
        int s = key & 0x7F;
        int r = (key >> 7) & 0xFF;
        return ((r + (2 * s + 1) * id) % size);
    }
}