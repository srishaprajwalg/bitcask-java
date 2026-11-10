
package bitcask;

public class Benchmark {

    public static void main(String[] args) {

        Bitcask db = new Bitcask();

        int warmupRecords = 2_000;
        int totalRecords = 10_000;
        int trials = 3;

        // Warm-up PUT
        for (int i = 0; i < warmupRecords; i++) {
            db.put("warmup" + i, "value" + i);
        }

        // Warm-up GET
        for (int i = 0; i < warmupRecords; i++) {
            db.get("warmup" + i);
        }

        long totalPutTime = 0;
        long totalGetTime = 0;

        for (int trial = 1; trial <= trials; trial++) {

            String prefix = "trial" + trial + "_";

            // Measure PUT
            long putStart = System.nanoTime();

            for (int i = 0; i < totalRecords; i++) {
                db.put(prefix + i, "value" + i);
            }

            long putEnd = System.nanoTime();

            // Measure GET
            long getStart = System.nanoTime();

            for (int i = 0; i < totalRecords; i++) {
                db.get(prefix + i);
            }

            long getEnd = System.nanoTime();

            long putTime = putEnd - putStart;
            long getTime = getEnd - getStart;

            totalPutTime += putTime;
            totalGetTime += getTime;

            System.out.printf(
                "Trial %d | PUT: %.3f s | GET: %.3f s%n",
                trial,
                putTime / 1e9,
                getTime / 1e9
            );
        }

        double averagePutTime = totalPutTime / (double) trials;
        double averageGetTime = totalGetTime / (double) trials;

        System.out.println("\n=== Benchmark Results ===");
        System.out.println("Records per trial: " + totalRecords);
        System.out.println("Trials: " + trials);

        System.out.printf(
            "Average PUT time: %.3f seconds%n",
            averagePutTime / 1e9
        );

        System.out.printf(
            "Average GET time: %.3f seconds%n",
            averageGetTime / 1e9
        );

        System.out.printf(
            "Average PUT throughput: %.2f ops/sec%n",
            totalRecords * 1e9 / averagePutTime
        );

        System.out.printf(
            "Average GET throughput: %.2f ops/sec%n",
            totalRecords * 1e9 / averageGetTime
        );
    }
}
