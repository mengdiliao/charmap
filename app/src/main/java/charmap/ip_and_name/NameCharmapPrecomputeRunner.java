package charmap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Charmap-only precompute runner for name datasets.
 *
 * Rules:
 * - Track first name only (ignore last name and the rest of the line)
 * - Keep uppercase/lowercase as-is
 * - Use LMAX = 10
 * - Partition sizes P in {10, 50, 100, 200, 500}
 *
 * Inputs:
 * - data/name_experiment/1m_names.txt
 * - data/name_experiment/sorted_1m_names.txt
 *
 * Outputs:
 * - data/results/precompute/name_charmap/name_charmap_P{P}.txt
 * - data/results/precompute/name_charmap/sorted_name_charmap_P{P}.txt
 *
 * Run with:
 * ./gradlew :app:run -PmainClass=charmap.NameCharmapPrecomputeRunner
 */
public class NameCharmapPrecomputeRunner {

    private static final int LMAX = 10;
    private static final int[] PARTITION_SIZES = {5, 10, 50, 100, 200, 500};

    public static void main(String[] args) throws Exception {
        Path baseDir = IpPrecomputeRunner.resolveBaseDir();
        Path outputDir = baseDir.resolve("results/precompute/name_charmap");
        Files.createDirectories(outputDir);

        Path inputNames = baseDir.resolve("name_experiment/1m_names.txt");
        Path inputSortedNames = baseDir.resolve("name_experiment/sorted_1m_names.txt");

        precomputeSingleDataset(inputNames, "name_charmap", outputDir);
        precomputeSingleDataset(inputSortedNames, "sorted_name_charmap", outputDir);

        System.out.println("Name Charmap precompute complete.");
    }

    private static void precomputeSingleDataset(Path inputPath, String outputPrefix, Path outputDir) throws IOException {
        System.out.println("Reading input: " + inputPath);
        List<String> firstNames = readFirstNames(inputPath);
        System.out.println("Loaded " + firstNames.size() + " first names.");

        for (int p : PARTITION_SIZES) {
            Path outFile = outputDir.resolve(outputPrefix + "_P" + p + ".txt");
            writeCharmapPrecompute(firstNames, outFile, p, LMAX);
            System.out.println("  Wrote: " + outFile.getFileName());
        }
    }

    static List<String> readFirstNames(Path file) throws IOException {
        List<String> names = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                String first = normalizeFirstName(line);
                if (first != null && !first.isEmpty()) {
                    names.add(first);
                }
            }
        }
        return names;
    }

    static String normalizeFirstName(String line) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }

        // Keep only characters supported by CharMapper.
        StringBuilder sb = new StringBuilder(parts[0].length());
        for (int i = 0; i < parts[0].length(); i++) {
            char c = parts[0].charAt(i);
            if (CharMapper.map(c) >= 0) {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static void writeCharmapPrecompute(List<String> values, Path outFile, int p, int lmax) throws IOException {
        List<CharmapSummary> summaries = buildCharmapSummaries(values, p, lmax);

        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("LMAX=" + lmax);
            bw.newLine();
            bw.write("TOTAL_VALUES=" + values.size());
            bw.newLine();
            bw.write("P=" + p);
            bw.newLine();
            bw.write("PARTITIONS=" + summaries.size());
            bw.newLine();

            for (CharmapSummary summary : summaries) {
                StringBuilder row = new StringBuilder();
                for (int i = 0; i < summary.charSet.length; i++) {
                    if (i > 0) {
                        row.append(' ');
                    }
                    row.append(IpPrecomputeRunner.bitmaskToCharset(summary.charSet[i]));
                }
                bw.write(row.toString());
                bw.newLine();
            }
        }
    }

    private static List<CharmapSummary> buildCharmapSummaries(List<String> values, int p, int lmax) {
        List<CharmapSummary> summaries = new ArrayList<>();
        CharmapSummary current = null;

        for (int i = 0; i < values.size(); i++) {
            if (i % p == 0) {
                current = new CharmapSummary(lmax);
                summaries.add(current);
            }
            current.update(values.get(i));
        }

        return summaries;
    }
}
