package charmap;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Query runner for the IP address experiment.
 *
 * Reads build_addresses.txt and query_addresses.txt, strips dots,
 * builds MinMax and Charmap indexes for each partition size,
 * then counts how many partitions are pruned (mayContain == false) for each query.
 *
 * Output format:
 *   MinMax:  line 1 = P value, then 1000 lines each with the pruned partition count
 *   Charmap: line 1 = P value, line 2 = LMAX value, then 1000 lines each with the pruned partition count
 *
 * Run with:
 *   ./gradlew :app:run -PmainClass=charmap.IpQueryRunner
 */
public class IpQueryRunner {

    private static final int LMAX = 12;
    private static final int[] PARTITION_SIZES = {10, 100, 1000, 10000};

    public static void main(String[] args) throws Exception {
        Path baseDir = IpPrecomputeRunner.resolveBaseDir();
        Path buildFile = baseDir.resolve("inputs/ip_addresses/build_addresses.txt");
        Path queryFile = baseDir.resolve("inputs/ip_addresses/query_addresses.txt");
        Path outputDir = baseDir.resolve("ip_experiment/query");
        Files.createDirectories(outputDir);

        System.out.println("Reading build addresses from: " + buildFile);
        List<String> buildLines = IpPrecomputeRunner.readAndStripDots(buildFile);
        System.out.println("Loaded " + buildLines.size() + " build addresses.");

        System.out.println("Reading query addresses from: " + queryFile);
        List<String> queryLines = IpPrecomputeRunner.readAndStripDots(queryFile);
        System.out.println("Loaded " + queryLines.size() + " query addresses.");

        for (int P : PARTITION_SIZES) {
            System.out.println("\n--- P = " + P + " ---");
            queryMinMax(buildLines, queryLines, P, outputDir);
            queryCharmap(buildLines, queryLines, P, LMAX, outputDir);
        }

        System.out.println("\nQuery phase complete.");
    }

    private static void queryMinMax(List<String> buildLines, List<String> queryLines,
                                     int P, Path outputDir) throws IOException {
        // Build index
        List<MinMaxSummary> summaries = new ArrayList<>();
        MinMaxSummary current = null;

        for (int i = 0; i < buildLines.size(); i++) {
            if (i % P == 0) {
                current = new MinMaxSummary();
                summaries.add(current);
            }
            current.update(buildLines.get(i));
        }

        int totalPartitions = summaries.size();

        // Query and count pruned partitions
        Path outFile = outputDir.resolve("minmax_P" + P + "_query.txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();

            for (String query : queryLines) {
                int pruned = 0;
                for (MinMaxSummary s : summaries) {
                    if (!s.mayContain(query)) {
                        pruned++;
                    }
                }
                bw.write(String.valueOf(pruned));
                bw.newLine();
            }
        }

        System.out.println("  MinMax query (" + totalPartitions + " partitions, "
                + queryLines.size() + " queries) -> " + outFile);
    }

    private static void queryCharmap(List<String> buildLines, List<String> queryLines,
                                      int P, int lmax, Path outputDir) throws IOException {
        // Build index
        List<CharmapSummary> summaries = new ArrayList<>();
        CharmapSummary current = null;

        for (int i = 0; i < buildLines.size(); i++) {
            if (i % P == 0) {
                current = new CharmapSummary(lmax);
                summaries.add(current);
            }
            current.update(buildLines.get(i));
        }

        int totalPartitions = summaries.size();

        // Query and count pruned partitions
        Path outFile = outputDir.resolve("charmap_P" + P + "_LMAX" + lmax + "_query.txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();
            bw.write("LMAX=" + lmax);
            bw.newLine();

            for (String query : queryLines) {
                int pruned = 0;
                for (CharmapSummary s : summaries) {
                    if (!s.mayContain(query)) {
                        pruned++;
                    }
                }
                bw.write(String.valueOf(pruned));
                bw.newLine();
            }
        }

        System.out.println("  Charmap query (" + totalPartitions + " partitions, "
                + queryLines.size() + " queries) -> " + outFile);
    }
}
