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

                System.out.println("\nClé trouvée : " + foundKey);
                System.out.println("Temps d'exécution : " + (end - start) + " ms");

                // Génération de l'image avec la clé trouvée
                int[] perm = generatePermutation(inputImage.getHeight(), foundKey);
                BufferedImage imageDechiffré = unScrambleLines(inputImage, perm);

                File outputFile = new File("trouve_" + foundKey + ".png");
                boolean success = ImageIO.write(imageDechiffré, "png", outputFile);

                if (success) {
                    System.out.println("Résultat sauvegardé dans : " + outputFile.getAbsolutePath());
                } else {
                    System.err.println("Erreur lors de l'écriture de l'image");
                }
            }
            return;
        }

        // --- BLOC NORMAL (Si on n'est pas en mode break) ---
        int key = Integer.parseInt(args[1]) & 0x7FFF;
        String outPath = (args.length >= 3) ? args[2] : "out.png";
        String process = (args.length >= 4) ? args[3] : "scramble";
        int[] perm = generatePermutation(inputImage.getHeight(), key);
        BufferedImage resultat;
        if (process.equals("unscramble")) {
            resultat = unScrambleLines(inputImage, perm);
        } else {
            resultat = scrambleLines(inputImage, perm);
        }
        // Calcul du score de différence pixel par pixel ( haut precision )
        DistancePixel(inputImage, resultat);
        ImageIO.write(resultat, "png", new File(outPath));
        System.out.println("Image enregistrée : " + outPath);
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
        int[] permutation = new int[size];
        for (int i = 0; i < size; i++) {
            permutation[i] = scrambledId(i, size, key);
        }
        return permutation;
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



    public static void DistancePixel(BufferedImage inputImg, BufferedImage processedImg) {
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
    }


    public static int breakKey(int[][] imageGL, String mode) {
        // mode brute-force, on teste tout
        int cleTrouvee = 0;
        
        // On fait deux blocs séparés
        if (mode.equals("Pearson")) {
            double maxScore = -1.0; // On cherche le plus grand
            
            for (int k = 0; k < 32768; k++) {
                int[] p = generatePermutation(imageGL.length, k);
                // On dechiffre en calculant le score sur les lignes permutées
                double sc = scorePearson(imageGL, p);
                
                if (sc > maxScore) {
                    maxScore = sc;
                    cleTrouvee = k;
                }
            }
        } else {
            // mode distance euclidienne
            double minScore = 999999999.0; // Un très grand nombre
            
            for (int k = 0; k < 32768; k++) {
                int[] p = generatePermutation(imageGL.length, k);
                double sc = scoreEuclide(imageGL, p);
                
                if (sc < minScore) {
                    minScore = sc;
                    cleTrouvee = k;
                }
            }
        }
        
        return cleTrouvee;
    }

    // Distance simple entre deux lignes
    public static double euclideanDistance(int[] l1, int[] l2) {
        double somme = 0;
        for (int i = 0; i < l1.length; i++) {
            double d = l1[i] - l2[i];
            somme += d * d;
        }
        return Math.sqrt(somme);
    }

    public static double scoreEuclide(int[][] img, int[] p) {
        double total = 0;
        for (int i = 0; i < img.length - 1; i++) {
            // On regarde la ligne i et i+1 dans l'image reconstituée
            // Donc on prend les lignes p[i] et p[i+1] dans l'image brouillée
            total += euclideanDistance(img[p[i]], img[p[i+1]]);
        }
        return total;
    }

    public static double pearsonCorrelation(int[] l1, int[] l2) {
        int n = l1.length;
        double m1 = 0;
        double m2 = 0;
        
        // Moyennes
        for(int x : l1) m1 += x;
        for(int x : l2) m2 += x;
        m1 /= n;
        m2 /= n;
        
        double num = 0;
        double den1 = 0;
        double den2 = 0;
        
        for (int i = 0; i < n; i++) {
            double ecart1 = l1[i] - m1;
            double ecart2 = l2[i] - m2;
            
            num += ecart1 * ecart2;
            den1 += ecart1 * ecart1;
            den2 += ecart2 * ecart2;
        }
        
        return num / (Math.sqrt(den1) * Math.sqrt(den2));
    }

    public static double scorePearson(int[][] img, int[] p) {
        double tot = 0;
        for (int i = 0; i < img.length - 1; i++) {
            tot += pearsonCorrelation(img[p[i]], img[p[i+1]]);
        }
        return tot;
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