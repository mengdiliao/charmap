package charmap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Charmap-only precompute runner for IP inputs.
 *
 * Processing per line:
 * - Ignore CIDR suffix (anything after '/')
 * - Convert x.x.x.x to a fixed-width 12-digit key (each octet zero-padded to 3 digits)
 *
 * Uses partition sizes P in {5, 10, 50, 100, 200, 500, 1000, 2000}.
 *
 * Computes Charmap summaries for a single input file and a single partition size P,
 * then writes a single output file.
 *
 * Run with:
 * ./gradlew :app:run -PmainClass=charmap.IPCharmapPrecomputeRunner --args="--input <inputPath> --output <outputPath> --lmax <lmax> --p <partitionSize>"
 */
public class IPCharmapPrecomputeRunner {

    private static final int[] PARTITION_SIZES = {5, 10, 50, 100, 200, 500, 1000, 2000};

    public static void main(String[] args) throws Exception {
        CliConfig config = parseArgs(args);

        System.out.println("Reading input: " + config.inputPath);
        List<String> normalizedIps = readNormalizeTo12Digit(config.inputPath);
        System.out.println("Loaded " + normalizedIps.size() + " normalized addresses.");

        Path resolvedOutputPath = withParamSuffix(config.outputPath, config.partitionSize, config.lmax);
        writeCharmapPrecompute(normalizedIps, resolvedOutputPath, config.partitionSize, config.lmax);
        System.out.println("Wrote precompute file: " + resolvedOutputPath);

        System.out.println("Charmap precompute complete.");
    }

    private static CliConfig parseArgs(String[] args) {
        String input = null;
        String output = null;
        Integer partitionSize = null;
        Integer lmax = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--input".equals(arg)) {
                input = requireNext(args, ++i, "--input");
            } else if ("--output".equals(arg)) {
                output = requireNext(args, ++i, "--output");
            } else if ("--p".equals(arg)) {
                partitionSize = parseAllowedPartitionSize(requireNext(args, ++i, "--p"));
            } else if ("--lmax".equals(arg)) {
                lmax = parsePositiveInt(requireNext(args, ++i, "--lmax"), "--lmax");
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsageAndExit(0);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg + "\n" + usageText());
            }
        }

        if (input == null || output == null || partitionSize == null || lmax == null) {
            throw new IllegalArgumentException("Missing required arguments.\n" + usageText());
        }

        Path inputPath = Paths.get(input);
        Path outputPath = Paths.get(output);

        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input file does not exist: " + inputPath);
        }

        Path outputParent = outputPath.getParent();
        if (outputParent != null) {
            try {
                Files.createDirectories(outputParent);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create output directory: " + outputParent, e);
            }
        }

        return new CliConfig(inputPath, outputPath, partitionSize, lmax);
    }

    private static String requireNext(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag + "\n" + usageText());
        }
        return args[index];
    }

    private static int parsePositiveInt(String value, String flag) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(flag + " must be > 0, got: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " must be an integer, got: " + value);
        }
    }

    private static int parseAllowedPartitionSize(String value) {
        int parsed = parsePositiveInt(value, "--p");
        for (int allowed : PARTITION_SIZES) {
            if (allowed == parsed) {
                return parsed;
            }
        }
        throw new IllegalArgumentException("--p must be one of " + partitionSizesText() + ", got: " + value);
    }

    private static String partitionSizesText() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < PARTITION_SIZES.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(PARTITION_SIZES[i]);
        }
        sb.append('}');
        return sb.toString();
    }

    private static String usageText() {
        return "Usage: --input <inputPath> --output <outputPath> --lmax <lmax> --p <partitionSize>"
                + "\nAllowed partition sizes: " + partitionSizesText();
    }

    private static Path withParamSuffix(Path outputPath, int p, int lmax) {
        String fileName = outputPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String stem = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String ext = dotIndex > 0 ? fileName.substring(dotIndex) : ".txt";

        // Avoid duplicate suffixes when caller already includes them.
        stem = stem.replaceAll("_P\\d+(_LMAX\\d+)?$", "");

        String newFileName = stem + "_P" + p + "_LMAX" + lmax + ext;
        Path parent = outputPath.getParent();
        return parent == null ? Path.of(newFileName) : parent.resolve(newFileName);
    }

    private static void printUsageAndExit(int code) {
        System.out.println(usageText());
        System.exit(code);
    }

    static List<String> readNormalizeTo12Digit(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                String normalized = normalizeIpLine(line);
                if (normalized != null) {
                    lines.add(normalized);
                }
            }
        }
        return lines;
    }

    static String normalizeIpLine(String line) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        int slashIndex = trimmed.indexOf('/');
        String ipOnly = slashIndex >= 0 ? trimmed.substring(0, slashIndex) : trimmed;

        String[] octets = ipOnly.split("\\.");
        if (octets.length != 4) {
            return null;
        }

        StringBuilder sb = new StringBuilder(12);
        for (String octetText : octets) {
            int octet;
            try {
                octet = Integer.parseInt(octetText);
            } catch (NumberFormatException e) {
                return null;
            }

            if (octet < 0 || octet > 255) {
                return null;
            }

            if (octet < 10) {
                sb.append('0').append('0').append(octet);
            } else if (octet < 100) {
                sb.append('0').append(octet);
            } else {
                sb.append(octet);
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
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < summary.charSet.length; i++) {
                    if (i > 0) {
                        sb.append(' ');
                    }
                    sb.append(IpPrecomputeRunner.bitmaskToCharset(summary.charSet[i]));
                }
                bw.write(sb.toString());
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

    private record CliConfig(Path inputPath, Path outputPath, int partitionSize, int lmax) {
    }
}
