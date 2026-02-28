package charmap;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CharacterMapIndex {

    private final int P;       // partition size
    private final int LMAX;    // max tracked length
    private final List<PartitionSummary> summaries;

    CharacterMapIndex(int P, int LMAX) {
        this.P = P;
        this.LMAX = LMAX;
        this.summaries = new ArrayList<>();
    }

    List<PartitionSummary> getSummaries() {
        return summaries;
    }

    void build(Path input) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(input)) {
            PartitionSummary current = null;
            int lineIndex = 0;

            // Read lines and build summaries for each partition.
            for (String line; (line = br.readLine()) != null; ) {

                // Start a new partition summary at the beginning of each partition.
                if (lineIndex % P == 0) {
                    current = new PartitionSummary(LMAX);
                    summaries.add(current);
                }

                updateSummary(current, line);
                lineIndex++;
            }
        }
    }

    // Update the partition summary with the characters from the given string.
    private void updateSummary(PartitionSummary ps, String s) {
        int m = Math.min(s.length(), LMAX);
        for (int i = 0; i < m; i++) {
            int idx = AlphabetMapper.map(s.charAt(i));
            ps.charSet[i] |= (1L << idx);
        }
    }
}