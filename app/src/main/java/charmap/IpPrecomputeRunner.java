package charmap;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Precompute runner for the IP address experiment.
 *
 * Reads build_addresses.txt, strips dots (tracking only the 12 digit positions),
 * and builds MinMax and Charmap indexes for each partition size.
 *
 * Output format:
 *   MinMax:  line 1 = P value, then each line = "minValue maxValue" per partition
 *   Charmap: line 1 = P value, line 2 = LMAX value, then each line = charSet bitmasks per partition
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

        System.out.println("Reading build addresses from: " + buildFile);
        List<String> lines = readAndStripDots(buildFile);
        System.out.println("Loaded " + lines.size() + " addresses (dots stripped).");

        for (int P : PARTITION_SIZES) {
            System.out.println("\n--- P = " + P + " ---");
            precomputeMinMax(lines, P, outputDir);
            precomputeCharmap(lines, P, LMAX, outputDir);
        }

        System.out.println("\nPrecompute phase complete.");
    }

    private static void precomputeMinMax(List<String> lines, int P, Path outputDir) throws IOException {
        List<MinMaxSummary> summaries = new ArrayList<>();
        MinMaxSummary current = null;

        for (int i = 0; i < lines.size(); i++) {
            if (i % P == 0) {
                current = new MinMaxSummary();
                summaries.add(current);
            }
            current.update(lines.get(i));
        }

        Path outFile = outputDir.resolve("minmax_P" + P + ".txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();
            for (MinMaxSummary s : summaries) {
                bw.write(s.minValue + " " + s.maxValue);
                bw.newLine();
            }
        }

        System.out.println("  MinMax index (" + summaries.size() + " partitions) -> " + outFile);
    }

    private static void precomputeCharmap(List<String> lines, int P, int lmax, Path outputDir) throws IOException {
        List<CharmapSummary> summaries = new ArrayList<>();
        CharmapSummary current = null;

        for (int i = 0; i < lines.size(); i++) {
            if (i % P == 0) {
                current = new CharmapSummary(lmax);
                summaries.add(current);
            }
            current.update(lines.get(i));
        }

        Path outFile = outputDir.resolve("charmap_P" + P + "_LMAX" + lmax + ".txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("P=" + P);
            bw.newLine();
            bw.write("LMAX=" + lmax);
            bw.newLine();
            for (CharmapSummary s : summaries) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < s.charSet.length; j++) {
                    if (j > 0) sb.append(" ");
                    sb.append(bitmaskToCharset(s.charSet[j]));
                }
                bw.write(sb.toString());
                bw.newLine();
            }
        }

        System.out.println("  Charmap index (" + summaries.size() + " partitions) -> " + outFile);
    }

    /** Convert a bitmask to a string of characters that were present. */
    static String bitmaskToCharset(long mask) {
        StringBuilder sb = new StringBuilder();
        // Uppercase: bits 0-25
        for (int i = 0; i < 26; i++) {
            if ((mask & (1L << i)) != 0) {
                sb.append((char) ('A' + i));
            }
        }
        // Lowercase: bits 26-51
        for (int i = 0; i < 26; i++) {
            if ((mask & (1L << (26 + i))) != 0) {
                sb.append((char) ('a' + i));
            }
        }
        // Digits: bits 52-61
        for (int i = 0; i < 10; i++) {
            if ((mask & (1L << (52 + i))) != 0) {
                sb.append((char) ('0' + i));
            }
        }
        // Special: bits 62-63 (_ and -)
        if ((mask & (1L << 62)) != 0) sb.append('_');
        if ((mask & (1L << 63)) != 0) sb.append('-');
        return sb.toString();
    }

    /** Read lines from file, stripping dots to get 12-digit strings. */
    static List<String> readAndStripDots(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line.replace(".", ""));
            }
        }
        return lines;
    }

    /** Resolve the base data directory, works whether run from charmap/ or app/. */
    static Path resolveBaseDir() {
        Path fromApp = Paths.get("src/main/resources/data");
        Path fromRoot = Paths.get("app/src/main/resources/data");
        if (Files.exists(fromApp)) return fromApp;
        if (Files.exists(fromRoot)) return fromRoot;
        throw new RuntimeException("Cannot find resources/data directory. Run from charmap/ or app/ directory.");
    }
}
