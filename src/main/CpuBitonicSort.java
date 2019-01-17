package main;

/**
 * Source code from:
 * https://en.wikipedia.org/w/index.php?title=Bitonic_sorter&oldid=859952826#Example_code
 */
public class CpuBitonicSort {

    private static void kernel(int[] a, final int p, final int q) {
        final int d = 1 << (p - q);

        for (int i = 0; i < a.length; i++) {
            boolean up = ((i >> p) & 2) == 0;

            if ((i & d) == 0 && (a[i] > a[i | d]) == up) {
                int t = a[i];
                a[i] = a[i | d];
                a[i | d] = t;
            }
        }
    }

    private static void bitonicSort(final int logn, int[] a) {
        assert a.length == 1 << logn;

        for (int i = 0; i < logn; i++) {
            for (int j = 0; j <= i; j++) {
                kernel(a, i, j);
            }
        }
    }

    public static void main(String[] args) {
        for (int logn = 10; logn <= 10; logn++) {
            int n = 1 << logn;

            int count = 0;
            double timesSum = 0;
            int[] a0 = new int[n];

            while (count++ < 1) {

                for (int i = 0; i < n; i++) {
                    a0[i] = (int) (Math.random() * 1000);
                }

//                print(a0);

                long time = System.nanoTime();
                bitonicSort(logn, a0);
                timesSum += ((System.nanoTime() - time) / 1_000_000.0);

//                print(a0);
            }
            System.out.println(String.format("N: %d - %f ms", n, timesSum / (count - 1)));
        }
    }

    private static void print(int[] data) {
        for (int a : data) {
            System.out.print(a + " ");
        }
        System.out.println();
    }
}