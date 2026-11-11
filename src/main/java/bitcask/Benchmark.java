
package bitcask;

import java.util.Arrays;

public class Benchmark {

    public static void main(String[] args) {

        Bitcask db = new Bitcask();

        int warmupRecords = 2_000;
        int totalRecords = 10_000;
        int trials = 10;

        // Warm-up
        for (int i = 0; i < warmupRecords; i++) {
            db.put("warmup_" + i, "value_" + i);
        }

        for (int i = 0; i < warmupRecords; i++) {
            db.get("warmup_" + i);
        }

        // Prepare the same workload for every trial.
        String[] keys = new String[totalRecords];
        String[] values = new String[totalRecords];

        for (int i = 0; i < totalRecords; i++) {
            keys[i] = String.format("benchmark_key_%05d", i);
            values[i] = String.format("benchmark_value_%05d", i);
        }

        // Keep the starting log size consistent.
        db.compact();

        long[] putTimes = new long[trials];
        long[] getTimes = new long[trials];

        for (int trial = 0; trial < trials; trial++) {

            // Measure PUT
            long start = System.nanoTime();

            for (int i = 0; i < totalRecords; i++) {
                db.put(keys[i], values[i]);
            }

            long putEnd = System.nanoTime();

            // Measure GET and verify correctness
            for (int i = 0; i < totalRecords; i++) {
                String actual = db.get(keys[i]);

                if (!values[i].equals(actual)) {
                    throw new RuntimeException(
                        "GET verification failed for " + keys[i]
                    );
                }
            }

            long getEnd = System.nanoTime();

            putTimes[trial] = putEnd - start;
            getTimes[trial] = getEnd - putEnd;

            System.out.printf(
                "Trial %d | PUT: %.3f s | GET: %.3f s%n",
                trial + 1,
                putTimes[trial] / 1e9,
                getTimes[trial] / 1e9
            );

            // Keep the log compact between trials.
            db.compact();
        }

        long totalPutTime = Arrays.stream(putTimes).sum();
        long totalGetTime = Arrays.stream(getTimes).sum();

        double averagePut = totalPutTime / (double) trials;
        double averageGet = totalGetTime / (double) trials;

        Arrays.sort(putTimes);
        Arrays.sort(getTimes);

        double medianPut = (putTimes[4] + putTimes[5]) / 2.0;
        double medianGet = (getTimes[4] + getTimes[5]) / 2.0;

        System.out.println("\n=== Benchmark Results ===");
        System.out.println("Records per trial: " + totalRecords);
        System.out.println("Trials: " + trials);

        System.out.printf(
            "Average PUT throughput: %.2f ops/sec%n",
            totalRecords * 1e9 / averagePut
        );

        System.out.printf(
            "Average GET throughput: %.2f ops/sec%n",
            totalRecords * 1e9 / averageGet
        );

        System.out.printf(
            "Median PUT time: %.3f seconds%n", medianPut / 1e9
        );

        System.out.printf(
            "Median GET time: %.3f seconds%n", medianGet / 1e9
        );
    }
}
