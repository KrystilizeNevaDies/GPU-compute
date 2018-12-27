package main;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Multithreaded searching for maximum value in array
 */
public class CpuMax {

    public static void main(String[] args) throws InterruptedException {
        // get number of threads
        final int threadsCount = Runtime.getRuntime().availableProcessors();
        System.out.println("Running on " + threadsCount + " thread" + (threadsCount > 1 ? "s" : "") + ".");

        // prepare variables
        final int[] max = new int[threadsCount];
        final CountDownLatch latch1 = new CountDownLatch(threadsCount);
        final CountDownLatch latch2 = new CountDownLatch(threadsCount);

        // prepare data variables
        final int originalDataSize = 150000000;
        // 1.5 billion, 6 GB RAM (integer has 4 bytes) - VM options -Xms8g -Xmx8g
        final int[] data = new int[originalDataSize];

        // generate data
        final long timeTotal1 = System.currentTimeMillis();
        for (int i = 0; i < threadsCount; i++) {
            final int j = i;
            new Thread(() -> {
                final long time = System.currentTimeMillis();
                max[j] = data[0];
                final int groupSize = originalDataSize / threadsCount;
                final Random r = new Random();
                for (int k = groupSize * j; k < groupSize * (j + 1) - 1; k++) {
                    data[k] = r.nextInt(100);
                }
                System.out.println("Thread " + j + ": " + (System.currentTimeMillis() - time));
                latch1.countDown();
            }).start();
        }

        latch1.await();
        System.out.println("Total: " + (System.currentTimeMillis() - timeTotal1));
        System.out.println();

        // run threadsCount
        final long timeTotal2 = System.currentTimeMillis();
        for (int i = 0; i < threadsCount; i++) {
            final int j = i;
            new Thread(() -> {
                final long time = System.currentTimeMillis();
                max[j] = data[0];
                final int groupSize = originalDataSize / threadsCount;
                for (int k = groupSize * j; k < groupSize * (j + 1) - 1; k++) {
                    if (data[k] > max[j]) max[j] = data[k];
                }
                System.out.println("Thread " + j + ": " + (System.currentTimeMillis() - time));
                latch2.countDown();
            }).start();
        }

        // wait for threads to finish
        latch2.await();
        System.out.println("Total: " + (System.currentTimeMillis() - timeTotal2));

        // get the real maximum
        int maxTotal = max[0];
        for (int i = 1; i < threadsCount; i++) {
            if (max[i] > maxTotal) maxTotal = max[i];
        }
        System.out.println("Max value: " + maxTotal);
    }
}
