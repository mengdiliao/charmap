package charmap;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CharmapIndex implements Index<CharmapSummary> {

    private final int P;       // partition size
    private final int LMAX;    // max tracked length
    private final List<CharmapSummary> summaries;

    CharmapIndex(int P, int LMAX) {
        this.P = P;
        this.LMAX = LMAX;
        this.summaries = new ArrayList<>();
    }

    @Override
    public List<CharmapSummary> getSummaries() {
        return summaries;
    }

    @Override
    public void build(Path input) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(input)) {
            CharmapSummary current = null;
            int lineIndex = 0;

            // Read lines and build summaries for each partition.
            for (String line; (line = br.readLine()) != null; ) {

                // Start a new partition summary at the beginning of each partition.
                if (lineIndex % P == 0) {
                    current = new CharmapSummary(LMAX);
                    summaries.add(current);
                }

                current.update(line);
                lineIndex++;
            }
        }
    }
}