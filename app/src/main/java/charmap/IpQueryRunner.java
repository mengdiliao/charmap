package charmap;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Query runner for the IP address experiment.
 *
 * Reads build_addresses.txt and query_addresses.txt as IP addresses,
 * parses them into 4 numeric segments (0-255 each),
 * then evaluates MinMax and Charmap (Saturation) pruning for each query.
 *
 * Output format:
 *   MinMax:  line 1 = P value, then N lines each with the pruned partition count
 *   Charmap: line 1 = P value, line 2 = LMAX value, then N lines each with the pruned partition count
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
        List<int[]> buildAddresses = IpPrecomputeRunner.readAsIPSegments(buildFile);
        System.out.println("Loaded " + buildAddresses.size() + " build addresses.");

        System.out.println("Reading query addresses from: " + queryFile);
        List<int[]> queryAddresses = IpPrecomputeRunner.readAsIPSegments(queryFile);
        System.out.println("Loaded " + queryAddresses.size() + " query addresses.");

        for (int P : PARTITION_SIZES) {
            System.out.println("\n--- P = " + P + " ---");
            
            // Build summaries once
            List<IpSegmentMinMaxSummary> minmaxSummaries = buildMinMaxSummaries(buildAddresses, P);
            List<IpSegmentSaturationSummary> charmapSummaries = buildCharmapSummaries(buildAddresses, P);
            
            // Query using built summaries
            queryMinMax(minmaxSummaries, queryAddresses, P, outputDir);
            queryCharmap(charmapSummaries, queryAddresses, P, LMAX, outputDir);
            queryOR(minmaxSummaries, charmapSummaries, queryAddresses, P, outputDir);
        }

        System.out.println("\nQuery phase complete.");
    }

    private static List<IpSegmentMinMaxSummary> buildMinMaxSummaries(List<int[]> buildAddresses, int P) {
        List<IpSegmentMinMaxSummary> summaries = new ArrayList<>();
        IpSegmentMinMaxSummary current = null;

        for (int i = 0; i < buildAddresses.size(); i++) {
            if (i % P == 0) {
                current = new IpSegmentMinMaxSummary();
                summaries.add(current);
            }
            current.update(buildAddresses.get(i), i % P);
        }

        return summaries;
    }

    private static List<IpSegmentSaturationSummary> buildCharmapSummaries(List<int[]> buildAddresses, int P) {
        List<IpSegmentSaturationSummary> summaries = new ArrayList<>();
        IpSegmentSaturationSummary current = null;

        for (int i = 0; i < buildAddresses.size(); i++) {
            if (i % P == 0) {
                current = new IpSegmentSaturationSummary();
                summaries.add(current);
            }
            current.update(buildAddresses.get(i), i % P);
        }

        return summaries;
    }

    private static void queryMinMax(List<IpSegmentMinMaxSummary> summaries, List<int[]> queryAddresses,
                                     int P, Path outputDir) throws IOException {
        int totalPartitions = summaries.size();

        // Query and count pruned partitions
        Path outFile = outputDir.resolve("minmax_P" + P + "_query.txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();

            for (int[] query : queryAddresses) {
                int pruned = 0;
                for (IpSegmentMinMaxSummary s : summaries) {
                    if (!s.mayContain(query)) {
                        pruned++;
                    }
                }
                bw.write(String.valueOf(pruned));
                bw.newLine();
            }
        }

        System.out.println("  MinMax query (" + totalPartitions + " partitions, "
                + queryAddresses.size() + " queries) -> " + outFile);
    }

    private static void queryCharmap(List<IpSegmentSaturationSummary> summaries, List<int[]> queryAddresses,
                                      int P, int lmax, Path outputDir) throws IOException {
        int totalPartitions = summaries.size();

        // Query and count pruned partitions using saturation charsets
        Path outFile = outputDir.resolve("charmap_P" + P + "_LMAX" + lmax + "_query.txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();
            bw.write("LMAX=" + lmax);
            bw.newLine();

            for (int[] query : queryAddresses) {
                int pruned = 0;
                for (IpSegmentSaturationSummary s : summaries) {
                    if (!mayContainBySaturation(s, query)) {
                        pruned++;
                    }
                }
                bw.write(String.valueOf(pruned));
                bw.newLine();
            }
        }

        System.out.println("  Charmap query (" + totalPartitions + " partitions, "
                + queryAddresses.size() + " queries) -> " + outFile);
    }

    /**
     * Check if a query may be contained in a partition based on saturation charsets.
     * Returns false if ANY segment value is NOT in the partition's charset (pruned).
     */
    private static boolean mayContainBySaturation(IpSegmentSaturationSummary summary, int[] querySegments) {
        // Check each segment: if the query value was NOT seen in this segment, prune
        for (int seg = 0; seg < 4; seg++) {
            if (!summary.isValueSeen(seg, querySegments[seg])) {
                return false;  // Pruned: query segment value not in partition's charset
            }
        }
        return true;  // May contain: all query segment values were seen
    }

    private static void queryOR(List<IpSegmentMinMaxSummary> minmaxSummaries,
                                List<IpSegmentSaturationSummary> charmapSummaries,
                                List<int[]> queryAddresses, int P, Path outputDir) throws IOException {
        int totalPartitions = minmaxSummaries.size();

        // Query and count pruned partitions (prune if EITHER algorithm prunes)
        Path outFile = outputDir.resolve("or_P" + P + "_query.txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();

            for (int[] query : queryAddresses) {
                int pruned = 0;
                for (int i = 0; i < minmaxSummaries.size(); i++) {
                    boolean minmaxPrunes = !minmaxSummaries.get(i).mayContain(query);
                    boolean charmapPrunes = !mayContainBySaturation(charmapSummaries.get(i), query);
                    
                    // Prune if at least one algorithm prunes
                    if (minmaxPrunes || charmapPrunes) {
                        pruned++;
                    }
                }
                bw.write(String.valueOf(pruned));
                bw.newLine();
            }
        }

        System.out.println("  OR query (" + totalPartitions + " partitions, "
                + queryAddresses.size() + " queries) -> " + outFile);
    }
}
