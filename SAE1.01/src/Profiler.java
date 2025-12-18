import java.util.function.Function;

public class Profiler {
    static long globalTime;

    static long callCount;

    @FunctionalInterface
    interface IntFloat4Consumer {
        void apply(int n, float xa, float ya, float xb, float yb);
    }

    public static void analyse(IntFloat4Consumer oneMethod, int n, float xa, float ya, float xb, float yb){
        long start = timestamp();
        oneMethod.apply(n,xa,ya,xb,yb);
        long stop  = timestamp();
        globalTime += (stop - start);
        callCount++;
    }
    public static void init(){
        globalTime = 0;
        callCount = 0;
    }
    public static void displayResult(){
        double elapsed = (globalTime) / 1e9;
        String unit = "s";
        if (elapsed < 1.0) {
            elapsed *= 1000.0;
            unit = "ms";
        }
        System.out.println(String.format("%.4g%s elapsed", elapsed, unit));
        System.out.println("Nombre d'appele : " + callCount);
    }

    /**
     * Si clock0 est >0, retourne une chaîne de caractères
     * représentant la différence de temps depuis clock0.
     * @param clock0 instant initial
     * @return expression du temps écoulé depuis clock0
     */
    public static String timestamp(long clock0) {
        String result = null;

        if (clock0 > 0) {
            double elapsed = (System.nanoTime() - clock0) / 1e9;
            String unit = "s";
            if (elapsed < 1.0) {
                elapsed *= 1000.0;
                unit = "ms";
            }
            result = String.format("%.4g%s elapsed", elapsed, unit);
        }
        return result;
    }

    /**
     * retourne l'heure courante en ns.
     * @return
     */
    public static long timestamp() {
        return System.nanoTime();
    }
}

