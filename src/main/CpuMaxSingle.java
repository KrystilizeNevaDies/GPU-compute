package main;

import java.util.Random;

/**
 * Single-threaded searching for maximum value in array with time measurement.
 */
public class CpuMaxSingle {
// VM options:
    // -Xms4g -Xmx4g - allocate more memory from the start - so GC is run as late as possible
    // -Djava.compiler=NONE - turn off JIT
    // -XX:+PrintCompilation - to watch if JIT is done
    // -verbose:gc -to watch how much work GC is doing

    public static void main(String[] args) {

        for (int m = 1; m <= 30; m++) {

            final int dataSize = 1 << m;

            double times = 0;
            int zeros = 0;
            long max = 0; // only done so JIT doesn't over-optimize for loop, which was happening when maxTemp was not used
            final int count = m <= 20 ? 100000 : (m <= 25 ? 10000 : 500);

            for (int c = 0; c < count; c++) {
                final int[] data = new int[dataSize];

                final Random r = new Random();
                for (int k = 0; k < data.length; k++) {
                    data[k] = r.nextInt(100);
                }

                final long time = System.nanoTime();

                int maxTemp = data[0];
                for (int i = 1; i < data.length; i++) {
                    if (maxTemp < data[i]) maxTemp = data[i];
                }

                long result = (System.nanoTime() - time);
                times += (result / 1000000.0);
                if (result <= 0) zeros++;
                max += maxTemp;
            }
            System.gc(); // to make as much as possible garbage collection outside stop-watch
            System.out.println("------------");
            System.out.println(max);
            System.out.println(m);
            System.out.println(String.format("Zeros: %f", zeros / (float) count) + " %");
            System.out.println(String.format("%f", times / count));
        }
    }
}
