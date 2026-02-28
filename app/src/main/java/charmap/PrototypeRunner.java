package charmap;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public class PrototypeRunner {

    // Entry point for the prototype runner
    // Usage: ./gradlew run --args="PrototypeRunner <inputFile> <outputFile> <queryString> <partitionSize> <maxTrackedLength>
    public static void main(String[] args) throws Exception {

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);
        String query = args[2];
        int P = Integer.parseInt(args[3]);
        int LMAX = Integer.parseInt(args[4]);

        CharacterMapIndex index = new CharacterMapIndex(P, LMAX);
        index.build(input);

        List<PartitionSummary> summaries = index.getSummaries();
        boolean[] keep = new boolean[summaries.size()];

        for (int i = 0; i < summaries.size(); i++) {
            keep[i] = mayContain(summaries.get(i), query, LMAX);
        }

        filterAndWrite(input, output, keep, P);
    }
    
    // Check if the partition summary may contain the query string
    static boolean mayContain(PartitionSummary ps, String query, int LMAX) {

        for (int i = 0; i < Math.min(query.length(), LMAX); i++) {
            int idx = AlphabetMapper.map(query.charAt(i));

            //1L << idx creates a bitmask with only the bit at position idx set to 1.
            if ((ps.charSet[i] & (1L << idx)) == 0)
                return false;  // definite absence
        }
        return true;
    }

    // Core filtering and writing logic
    static void filterAndWrite(Path input, Path output, boolean[] keep, int P) throws IOException {

        try (BufferedReader br = getInputReader(input);
             BufferedWriter bw = getOutputWriter(output)) {

            int lineIndex = 0;

            for (String line; (line = br.readLine()) != null; ) {
                int partitionId = lineIndex / P;
                boolean keepPartition = (partitionId < keep.length) ? keep[partitionId] : true;

                if (keepPartition) {
                    bw.write(line);
                    bw.newLine();
                }

                lineIndex++;
            }
        }
    }

    // Read from input source 
    static BufferedReader getInputReader(Path input) throws IOException {
        if (input != null) {
            return Files.newBufferedReader(input);
        } else {
            return new BufferedReader(new InputStreamReader(System.in));
        }
    }

    // Write to output destination 
    static BufferedWriter getOutputWriter(Path output) throws IOException {
        if (output != null) {
            return Files.newBufferedWriter(output);
        } else {
            return new BufferedWriter(new OutputStreamWriter(System.out));
        }
    }
}