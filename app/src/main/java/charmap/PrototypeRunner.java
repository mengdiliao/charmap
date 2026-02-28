package charmap;
import java.nio.file.*;
import java.util.List;

public class PrototypeRunner {

    // Entry point for the prototype runner with strategy dispatch
    // Usage for charmap:
    //   ./gradlew run --args="PrototypeRunner charmap <inputFile> <outputFile> <queryString> <partitionSize> <maxTrackedLength>"
    // Usage for minmax:
    //   ./gradlew run --args="PrototypeRunner minmax <inputFile> <outputFile> <queryString> <partitionSize>"
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: PrototypeRunner <strategy> [args...]\n" +
                    "Strategies: charmap, minmax");
        }

        String strategy = args[0];

        if ("charmap".equalsIgnoreCase(strategy)) {
            runCharmapStrategy(args);
        } else if ("minmax".equalsIgnoreCase(strategy)) {
            runMinmaxStrategy(args);
        } else {
            throw new IllegalArgumentException("Unknown strategy: " + strategy + ". Use 'charmap' or 'minmax'.");
        }
    }

    private static void runCharmapStrategy(String[] args) throws Exception {
        if (args.length < 6) {
            throw new IllegalArgumentException("Charmap strategy requires: charmap <inputFile> <outputFile> <queryString> <partitionSize> <maxTrackedLength>");
        }

        Path input = Paths.get(args[1]);
        Path output = Paths.get(args[2]);
        String query = args[3];
        int P = Integer.parseInt(args[4]);
        int LMAX = Integer.parseInt(args[5]);

        CharmapIndex index = new CharmapIndex(P, LMAX);
        index.build(input);

        List<CharmapSummary> summaries = index.getSummaries();
        boolean[] keep = new boolean[summaries.size()];

        for (int i = 0; i < summaries.size(); i++) {
            keep[i] = summaries.get(i).mayContain(query);
        }

        PartitionFilter.filterAndWrite(input, output, keep, P);
    }

    private static void runMinmaxStrategy(String[] args) throws Exception {
        if (args.length < 5) {
            throw new IllegalArgumentException("MinMax strategy requires: minmax <inputFile> <outputFile> <queryString> <partitionSize>");
        }

        Path input = Paths.get(args[1]);
        Path output = Paths.get(args[2]);
        String query = args[3];
        int P = Integer.parseInt(args[4]);

        MinMaxIndex index = new MinMaxIndex(P);
        index.build(input);

        List<MinMaxSummary> summaries = index.getSummaries();
        boolean[] keep = new boolean[summaries.size()];

        for (int i = 0; i < summaries.size(); i++) {
            keep[i] = summaries.get(i).mayContain(query);
        }

        PartitionFilter.filterAndWrite(input, output, keep, P);
    }
}