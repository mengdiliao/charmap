package charmap;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Precompute runner for the IP address experiment.
 *
 * Reads build_addresses.txt, parses IP addresses into 4 numeric segments (0-255 each),
 * and precomputes both MinMax and Charmap (Saturation) indexes.
 *
 * Output format:
 *   MinMax:  line 1 = P value
 *            then for each partition: "min0 max0 | min1 max1 | min2 max2 | min3 max3"
 *            (min/max values for each segment)
 *
 *   Charmap: line 1 = P value, line 2 = LMAX value
 *            then for each partition: saturation_indices | segment_charsets
 *            saturation_indices: "idx0 idx1 idx2 idx3" (address index that causes saturation, -1 if none)
 *            segment_charsets: charset0|charset1|charset2|charset3 (space-separated values per segment)
 *
 * Run with:
 *   ./gradlew :app:run -PmainClass=charmap.IpPrecomputeRunner
 */
public class IpPrecomputeRunner {

    private static final int LMAX = 12;
    private static final int[] PARTITION_SIZES = {10, 100, 1000, 10000};

    public static void main(String[] args) throws Exception {
        Path baseDir = resolveBaseDir();
        Path buildFile = baseDir.resolve("inputs/ip_addresses/build_addresses.txt");
        Path outputDir = baseDir.resolve("ip_experiment/precompute");
        Files.createDirectories(outputDir);

        System.out.println("Reading IP addresses from: " + buildFile);
        List<int[]> addresses = readAsIPSegments(buildFile);
        System.out.println("Loaded " + addresses.size() + " IP addresses.");

        for (int P : PARTITION_SIZES) {
            System.out.println("\n--- P = " + P + " ---");
            precomputeIpSegmentMinMax(addresses, P, outputDir);
            precomputeIpSegmentSaturation(addresses, P, LMAX, outputDir);
        }

        System.out.println("\nPrecompute phase complete.");
    }

    private static void precomputeIpSegmentMinMax(List<int[]> addresses, int P, Path outputDir) throws IOException {
        List<IpSegmentMinMaxSummary> summaries = new ArrayList<>();
        IpSegmentMinMaxSummary current = null;

        for (int i = 0; i < addresses.size(); i++) {
            if (i % P == 0) {
                current = new IpSegmentMinMaxSummary();
                summaries.add(current);
            }
            current.update(addresses.get(i), i % P);  // Pass relative index within partition
        }

        Path outFile = outputDir.resolve("minmax_P" + P + ".txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();
            for (IpSegmentMinMaxSummary s : summaries) {
                // Line 1 of partition data: saturation indices
                bw.write(s.getSaturationIndicesAsString());
                bw.newLine();
                // Line 2 of partition data: min/max values
                bw.write(s.getMinMaxAsString());
                bw.newLine();
            }
        }

        System.out.println("  IP Segment MinMax (" + summaries.size() + " partitions) -> " + outFile);
    }

    private static void precomputeIpSegmentSaturation(List<int[]> addresses, int P, int lmax, Path outputDir) throws IOException {
        List<IpSegmentSaturationSummary> summaries = new ArrayList<>();
        IpSegmentSaturationSummary current = null;

        for (int i = 0; i < addresses.size(); i++) {
            if (i % P == 0) {
                current = new IpSegmentSaturationSummary();
                summaries.add(current);
            }
            current.update(addresses.get(i), i % P);  // Pass relative index within partition
        }

        Path outFile = outputDir.resolve("charmap_P" + P + "_LMAX" + lmax + ".txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();
            bw.write("LMAX=" + lmax);
            bw.newLine();
            
            for (IpSegmentSaturationSummary s : summaries) {
                // Line 1 of partition data: saturation indices
                bw.write(s.getSaturationIndicesAsString());
                bw.newLine();
                // Line 2 of partition data: segment charsets
                bw.write(s.getAllCharsetsAsString());
                bw.newLine();
            }
        }

        System.out.println("  IP Segment Saturation (" + summaries.size() + " partitions) -> " + outFile);
    }

    /** Read IP addresses from file and parse into 4 numeric segments (0-255 each). */
    static List<int[]> readAsIPSegments(Path file) throws IOException {
        List<int[]> addresses = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split("\\.");
                if (parts.length != 4) {
                    System.err.println("Warning: skipping invalid IP address: " + line);
                    continue;
                }

                try {
                    int[] segments = new int[4];
                    for (int i = 0; i < 4; i++) {
                        segments[i] = Integer.parseInt(parts[i]);
                        if (segments[i] < 0 || segments[i] > 255) {
                            throw new NumberFormatException("Segment out of range 0-255");
                        }
                    }
                    addresses.add(segments);
                } catch (NumberFormatException e) {
                    System.err.println("Warning: skipping invalid IP segment in line: " + line);
                }
            }
        }
        return addresses;
    }

    /** Resolve the base data directory, works whether run from charmap/ or app/. */
    static Path resolveBaseDir() {
        Path fromApp = Paths.get("src/main/resources/data");
        Path fromRoot = Paths.get("app/src/main/resources/data");
        if (Files.exists(fromApp)) return fromApp;
        if (Files.exists(fromRoot)) return fromRoot;
        throw new RuntimeException("Cannot find resources/data directory. Run from charmap/ or app/ directory.");
    }}