package charmap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Query runner for precomputed Charmap summaries over name data.
 *
 * Reads:
 * - Queries from data/name_experiment/1000_query_names.txt
 * - Precomputed summaries from data/results/precompute/name_charmap/*.txt
 *
 * Writes one query result file per precompute summary to:
 * - data/results/query/name_charmap
 *
 * Run with:
 * ./gradlew :app:run -PmainClass=charmap.NameCharmapQueryRunner
 */
public class NameCharmapQueryRunner {

    public static void main(String[] args) throws Exception {
        Path baseDir = IpPrecomputeRunner.resolveBaseDir();

        Path queryFile = baseDir.resolve("name_experiment/1000_query_names.txt");
        Path precomputeDir = baseDir.resolve("results/precompute/name_charmap");
        Path outputDir = baseDir.resolve("results/query/name_charmap");
        Files.createDirectories(outputDir);

        System.out.println("Reading query file: " + queryFile);
        List<String> queryFirstNames = NameCharmapPrecomputeRunner.readFirstNames(queryFile);
        System.out.println("Loaded " + queryFirstNames.size() + " first-name queries.");

        List<Path> precomputeFiles = listPrecomputeFiles(precomputeDir);
        System.out.println("Found " + precomputeFiles.size() + " precompute files in: " + precomputeDir);

        for (Path precomputeFile : precomputeFiles) {
            processSinglePrecomputeFile(precomputeFile, queryFirstNames, outputDir);
        }

        System.out.println("Name query phase complete.");
    }

    private static List<Path> listPrecomputeFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("Precompute directory does not exist: " + dir);
        }

        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(files::add);
        }
        return files;
    }

    private static void processSinglePrecomputeFile(Path precomputeFile, List<String> queries, Path outputDir)
            throws IOException {
        PrecomputeSummary summary = parsePrecomputeFile(precomputeFile);

        Path outFile = outputDir.resolve(
                precomputeFile.getFileName().toString().replace(".txt", "_query.txt")
        );

        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("SOURCE=" + precomputeFile.getFileName());
            bw.newLine();
            bw.write("P=" + summary.partitionSize);
            bw.newLine();
            bw.write("LMAX=" + summary.lmax);
            bw.newLine();
            bw.write("PARTITIONS=" + summary.partitions.size());
            bw.newLine();
            bw.write("QUERIES=" + queries.size());
            bw.newLine();

            for (String query : queries) {
                int pruned = 0;
                for (String[] partitionCharsets : summary.partitions) {
                    if (!mayContain(query, partitionCharsets, summary.lmax)) {
                        pruned++;
                    }
                }
                bw.write(String.valueOf(pruned));
                bw.newLine();
            }
        }

        System.out.println("  Processed " + precomputeFile.getFileName() + " -> " + outFile.getFileName());
    }

    private static PrecomputeSummary parsePrecomputeFile(Path file) throws IOException {
        Integer lmax = null;
        Integer p = null;
        Integer declaredPartitions = null;
        List<String[]> partitions = new ArrayList<>();

        List<String> lines = Files.readAllLines(file);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("LMAX=")) {
                lmax = Integer.parseInt(trimmed.substring("LMAX=".length()));
                continue;
            }
            if (trimmed.startsWith("P=")) {
                p = Integer.parseInt(trimmed.substring("P=".length()));
                continue;
            }
            if (trimmed.startsWith("PARTITIONS=")) {
                declaredPartitions = Integer.parseInt(trimmed.substring("PARTITIONS=".length()));
                continue;
            }
            if (trimmed.contains("=")) {
                // Skip other metadata fields such as TOTAL_VALUES.
                continue;
            }

            partitions.add(trimmed.split(" "));
        }

        if (lmax == null || p == null) {
            throw new IllegalArgumentException("Invalid precompute file (missing LMAX/P): " + file);
        }

        if (declaredPartitions != null && declaredPartitions != partitions.size()) {
            throw new IllegalArgumentException(
                    "Invalid precompute file (partition count mismatch) in " + file
                            + ": declared=" + declaredPartitions + ", actual=" + partitions.size()
            );
        }

        return new PrecomputeSummary(lmax, p, partitions);
    }

    private static boolean mayContain(String query, String[] partitionCharsets, int lmax) {
        int limit = Math.min(Math.min(query.length(), partitionCharsets.length), lmax);

        for (int i = 0; i < limit; i++) {
            String allowedCharsAtPos = partitionCharsets[i];
            if (allowedCharsAtPos.indexOf(query.charAt(i)) < 0) {
                return false;
            }
        }

        return true;
    }

    private record PrecomputeSummary(int lmax, int partitionSize, List<String[]> partitions) {
    }
}
