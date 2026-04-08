package charmap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Query runner for precomputed summaries over IP data.
 *
 * Supports both strategies:
 * - charmap
 * - minmax
 *
 * Run with:
 * ./gradlew :app:run -PmainClass=charmap.IPQueryRunner --args="--strategy <charmap|minmax> --query <queryPath> --precomputeDir <precomputeDir> --outputDir <outputDir>"
 */
public class IPQueryRunner {

    public static void main(String[] args) throws Exception {
        CliConfig config = parseArgs(args);
        Files.createDirectories(config.outputDir);

        System.out.println("Strategy: " + config.strategy.name().toLowerCase());
        System.out.println("Reading query file: " + config.queryFile);
        List<String> queries = IPCharmapPrecomputeRunner.readNormalizeTo12Digit(config.queryFile);
        System.out.println("Loaded " + queries.size() + " normalized queries.");

        List<Path> precomputeFiles = listPrecomputeFiles(config.precomputeDir);
        System.out.println("Found " + precomputeFiles.size() + " precompute files in: " + config.precomputeDir);

        for (Path precomputeFile : precomputeFiles) {
            processSinglePrecomputeFile(precomputeFile, queries, config.outputDir, config.strategy);
        }

        System.out.println("Query phase complete.");
    }

    private static CliConfig parseArgs(String[] args) {
        String strategy = null;
        String query = null;
        String precomputeDir = null;
        String outputDir = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--strategy".equals(arg)) {
                strategy = requireNext(args, ++i, "--strategy");
            } else if ("--query".equals(arg)) {
                query = requireNext(args, ++i, "--query");
            } else if ("--precomputeDir".equals(arg)) {
                precomputeDir = requireNext(args, ++i, "--precomputeDir");
            } else if ("--outputDir".equals(arg)) {
                outputDir = requireNext(args, ++i, "--outputDir");
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsageAndExit(0);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg + "\n" + usageText());
            }
        }

        if (strategy == null || query == null || precomputeDir == null || outputDir == null) {
            throw new IllegalArgumentException("Missing required arguments.\n" + usageText());
        }

        Strategy parsedStrategy = Strategy.from(strategy);
        Path queryPath = Paths.get(query);
        Path precomputePath = Paths.get(precomputeDir);
        Path outputPath = Paths.get(outputDir);

        if (!Files.exists(queryPath)) {
            throw new IllegalArgumentException("Query file does not exist: " + queryPath);
        }

        if (!Files.exists(precomputePath)) {
            throw new IllegalArgumentException("Precompute directory does not exist: " + precomputePath);
        }

        return new CliConfig(parsedStrategy, queryPath, precomputePath, outputPath);
    }

    private static String requireNext(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag + "\n" + usageText());
        }
        return args[index];
    }

    private static String usageText() {
        return "Usage: --strategy <charmap|minmax> --query <queryPath> --precomputeDir <precomputeDir> --outputDir <outputDir>";
    }

    private static void printUsageAndExit(int code) {
        System.out.println(usageText());
        System.exit(code);
    }

    private static List<Path> listPrecomputeFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(files::add);
        }
        return files;
    }

    private static void processSinglePrecomputeFile(Path precomputeFile,
                                                    List<String> queries,
                                                    Path outputDir,
                                                    Strategy strategy) throws IOException {
        Path outFile = outputDir.resolve(precomputeFile.getFileName().toString().replace(".txt", "_query.txt"));

        if (strategy == Strategy.CHARMAP) {
            CharmapPrecomputeSummary summary = parseCharmapPrecomputeFile(precomputeFile);
            writeCharmapQueryResult(outFile, precomputeFile, queries, summary);
        } else {
            MinmaxPrecomputeSummary summary = parseMinmaxPrecomputeFile(precomputeFile);
            writeMinmaxQueryResult(outFile, precomputeFile, queries, summary);
        }

        System.out.println("  Processed " + precomputeFile.getFileName() + " -> " + outFile.getFileName());
    }

    private static void writeCharmapQueryResult(Path outFile,
                                                Path precomputeFile,
                                                List<String> queries,
                                                CharmapPrecomputeSummary summary) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("SOURCE=" + precomputeFile.getFileName());
            bw.newLine();
            bw.write("STRATEGY=charmap");
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
                    if (!mayContainCharmap(query, partitionCharsets, summary.lmax)) {
                        pruned++;
                    }
                }
                bw.write(String.valueOf(pruned));
                bw.newLine();
            }
        }
    }

    private static void writeMinmaxQueryResult(Path outFile,
                                               Path precomputeFile,
                                               List<String> queries,
                                               MinmaxPrecomputeSummary summary) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("SOURCE=" + precomputeFile.getFileName());
            bw.newLine();
            bw.write("STRATEGY=minmax");
            bw.newLine();
            bw.write("P=" + summary.partitionSize);
            bw.newLine();
            if (summary.lmax != null) {
                bw.write("LMAX=" + summary.lmax);
                bw.newLine();
            }
            bw.write("PARTITIONS=" + summary.partitions.size());
            bw.newLine();
            bw.write("QUERIES=" + queries.size());
            bw.newLine();

            for (String query : queries) {
                int pruned = 0;
                for (String[] minMax : summary.partitions) {
                    if (!mayContainMinmax(query, minMax[0], minMax[1])) {
                        pruned++;
                    }
                }
                bw.write(String.valueOf(pruned));
                bw.newLine();
            }
        }
    }

    private static CharmapPrecomputeSummary parseCharmapPrecomputeFile(Path file) throws IOException {
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
                continue;
            }

            partitions.add(trimmed.split(" "));
        }

        if (lmax == null || p == null) {
            throw new IllegalArgumentException("Invalid charmap precompute file (missing LMAX/P): " + file);
        }

        if (declaredPartitions != null && declaredPartitions != partitions.size()) {
            throw new IllegalArgumentException(
                    "Invalid charmap precompute file (partition count mismatch) in " + file
                            + ": declared=" + declaredPartitions + ", actual=" + partitions.size()
            );
        }

        return new CharmapPrecomputeSummary(lmax, p, partitions);
    }

    private static MinmaxPrecomputeSummary parseMinmaxPrecomputeFile(Path file) throws IOException {
        Integer p = null;
        Integer declaredPartitions = null;
        Integer lmaxFromMeta = null;
        List<String[]> partitions = new ArrayList<>();

        List<String> lines = Files.readAllLines(file);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
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
            if (trimmed.startsWith("LMAX=")) {
                lmaxFromMeta = Integer.parseInt(trimmed.substring("LMAX=".length()));
                continue;
            }
            if (trimmed.contains("=")) {
                continue;
            }

            String[] minMax = trimmed.split(" ");
            if (minMax.length != 2) {
                throw new IllegalArgumentException("Invalid minmax partition row in " + file + ": " + trimmed);
            }
            partitions.add(minMax);
        }

        if (p == null) {
            throw new IllegalArgumentException("Invalid minmax precompute file (missing P): " + file);
        }

        if (declaredPartitions != null && declaredPartitions != partitions.size()) {
            throw new IllegalArgumentException(
                    "Invalid minmax precompute file (partition count mismatch) in " + file
                            + ": declared=" + declaredPartitions + ", actual=" + partitions.size()
            );
        }

        Integer lmax = lmaxFromMeta != null ? lmaxFromMeta : parseLmaxFromFilename(file.getFileName().toString());
        return new MinmaxPrecomputeSummary(lmax, p, partitions);
    }

    private static Integer parseLmaxFromFilename(String filename) {
        int idx = filename.indexOf("_LMAX");
        if (idx < 0) {
            return null;
        }
        int start = idx + "_LMAX".length();
        int end = start;
        while (end < filename.length() && Character.isDigit(filename.charAt(end))) {
            end++;
        }
        if (start == end) {
            return null;
        }
        return Integer.parseInt(filename.substring(start, end));
    }

    private static boolean mayContainCharmap(String query, String[] partitionCharsets, int lmax) {
        int limit = Math.min(Math.min(query.length(), partitionCharsets.length), lmax);

        for (int i = 0; i < limit; i++) {
            String allowedCharsAtPos = partitionCharsets[i];
            if (allowedCharsAtPos.indexOf(query.charAt(i)) < 0) {
                return false;
            }
        }

        return true;
    }

    private static boolean mayContainMinmax(String query, String minValue, String maxValue) {
        return query.compareTo(minValue) >= 0 && query.compareTo(maxValue) <= 0;
    }

    private enum Strategy {
        CHARMAP,
        MINMAX;

        static Strategy from(String value) {
            if ("charmap".equalsIgnoreCase(value)) {
                return CHARMAP;
            }
            if ("minmax".equalsIgnoreCase(value)) {
                return MINMAX;
            }
            throw new IllegalArgumentException("--strategy must be one of {charmap, minmax}, got: " + value);
        }
    }

    private record CliConfig(Strategy strategy, Path queryFile, Path precomputeDir, Path outputDir) {
    }

    private record CharmapPrecomputeSummary(int lmax, int partitionSize, List<String[]> partitions) {
    }

    private record MinmaxPrecomputeSummary(Integer lmax, int partitionSize, List<String[]> partitions) {
    }
}
