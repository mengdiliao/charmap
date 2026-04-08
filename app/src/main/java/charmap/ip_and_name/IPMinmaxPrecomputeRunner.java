package charmap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * MinMax-only precompute runner for IP inputs.
 *
 * Processing per line:
 * - Ignore CIDR suffix (anything after '/')
 * - Convert x.x.x.x to a fixed-width 12-digit key (each octet zero-padded to 3 digits)
 * - LMAX is accepted to keep output naming aligned with other runners
 *
 * Computes MinMax summaries for a single partition size P and a single input file,
 * then writes a single output file.
 *
 * Run with:
 * ./gradlew :app:run -PmainClass=charmap.IPMinmaxPrecomputeRunner --args="--input <inputPath> --output <outputPath> --p <partitionSize> --lmax <lmax>"
 */
public class IPMinmaxPrecomputeRunner {

    public static void main(String[] args) throws Exception {
        CliConfig config = parseArgs(args);

        System.out.println("Reading input: " + config.inputPath);
        List<String> normalizedIps = IPCharmapPrecomputeRunner.readNormalizeTo12Digit(config.inputPath);
        System.out.println("Loaded " + normalizedIps.size() + " normalized addresses.");

        Path resolvedOutputPath = withParamSuffix(config.outputPath, config.partitionSize, config.lmax);
        writeMinmaxPrecompute(normalizedIps, resolvedOutputPath, config.partitionSize);
        System.out.println("Wrote precompute file: " + resolvedOutputPath);
        System.out.println("MinMax precompute complete.");
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
                partitionSize = parsePositiveInt(requireNext(args, ++i, "--p"), "--p");
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

    private static String usageText() {
        return "Usage: --input <inputPath> --output <outputPath> --p <partitionSize> --lmax <lmax>"
                + "\nNote: MinMax ignores LMAX in computation and uses it in output naming.";
    }

    private static Path withParamSuffix(Path outputPath, int p, int lmax) {
        String fileName = outputPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String stem = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String ext = dotIndex > 0 ? fileName.substring(dotIndex) : ".txt";

        stem = stem.replaceAll("_P\\d+(_LMAX\\d+)?$", "");

        String newFileName = stem + "_P" + p + "_LMAX" + lmax + ext;
        Path parent = outputPath.getParent();
        return parent == null ? Path.of(newFileName) : parent.resolve(newFileName);
    }

    private static void printUsageAndExit(int code) {
        System.out.println(usageText());
        System.exit(code);
    }

    private static void writeMinmaxPrecompute(List<String> values, Path outFile, int p) throws IOException {
        List<MinMaxSummary> summaries = buildMinmaxSummaries(values, p);

        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
            bw.write("TOTAL_VALUES=" + values.size());
            bw.newLine();
            bw.write("P=" + p);
            bw.newLine();
            bw.write("PARTITIONS=" + summaries.size());
            bw.newLine();

            for (MinMaxSummary summary : summaries) {
                bw.write(summary.minValue + " " + summary.maxValue);
                bw.newLine();
            }
        }
    }

    private static List<MinMaxSummary> buildMinmaxSummaries(List<String> values, int p) {
        List<MinMaxSummary> summaries = new ArrayList<>();
        MinMaxSummary current = null;

        for (int i = 0; i < values.size(); i++) {
            if (i % p == 0) {
                current = new MinMaxSummary();
                summaries.add(current);
            }
            current.update(values.get(i));
        }

        return summaries;
    }

    private record CliConfig(Path inputPath, Path outputPath, int partitionSize, int lmax) {
    }
}