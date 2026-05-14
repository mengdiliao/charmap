package charmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Correctness tests for CharmapIndex and MinMaxIndex.
 *
 * Uses a 20-line animal dataset with P=4 (5 partitions):
 *   Partition 0: bear, bird, cat, chicken
 *   Partition 1: cow, deer, dog, dolphin
 *   Partition 2: duck, eagle, elephant, fish
 *   Partition 3: fox, frog, goat, horse
 *   Partition 4: lion, monkey, mouse, owl
 *
 * Test queries:
 *   "dog"   — exists in P1, both strategies keep P1
 *   "horse" — exists in P3, both strategies keep P3
 *   "crab"  — does NOT exist; charmap prunes all (pos1 'r' not in P0{e,i,a,h} or P1{o,e}),
 *             minmax keeps P1 (cow <= crab <= dolphin)
 *   "hawk"  — does NOT exist; charmap prunes all (pos1 'a' not in P3{o,r}),
 *             minmax keeps P3 (fox <= hawk <= horse)
 */
class IndexCorrectnessTest {

    private static final int P = 4;          // partition size
    private static final int LMAX = 3;       // charmap max tracked length

    private static final Path INPUT = Paths.get("src/test/resources/data/test_animals.txt");

    // ─── Charmap tests ───────────────────────────────────────────────

    @Test
    void charmap_dog_existingQuery(@TempDir Path tmpDir) throws IOException {
        Path actual = runCharmap("dog", tmpDir);
        Path expected = Paths.get("src/test/resources/data/charmap_expected/dog_output.txt");
        assertFilesEqual(expected, actual);
    }

    @Test
    void charmap_horse_existingQuery(@TempDir Path tmpDir) throws IOException {
        Path actual = runCharmap("horse", tmpDir);
        Path expected = Paths.get("src/test/resources/data/charmap_expected/horse_output.txt");
        assertFilesEqual(expected, actual);
    }

    @Test
    void charmap_crab_nonExistingQuery(@TempDir Path tmpDir) throws IOException {
        Path actual = runCharmap("crab", tmpDir);
        Path expected = Paths.get("src/test/resources/data/charmap_expected/crab_output.txt");
        assertFilesEqual(expected, actual);
    }

    @Test
    void charmap_hawk_nonExistingQuery(@TempDir Path tmpDir) throws IOException {
        Path actual = runCharmap("hawk", tmpDir);
        Path expected = Paths.get("src/test/resources/data/charmap_expected/hawk_output.txt");
        assertFilesEqual(expected, actual);
    }

    // ─── MinMax tests ────────────────────────────────────────────────

    @Test
    void minmax_dog_existingQuery(@TempDir Path tmpDir) throws IOException {
        Path actual = runMinMax("dog", tmpDir);
        Path expected = Paths.get("src/test/resources/data/minmax_expected/dog_output.txt");
        assertFilesEqual(expected, actual);
    }

    @Test
    void minmax_horse_existingQuery(@TempDir Path tmpDir) throws IOException {
        Path actual = runMinMax("horse", tmpDir);
        Path expected = Paths.get("src/test/resources/data/minmax_expected/horse_output.txt");
        assertFilesEqual(expected, actual);
    }

    @Test
    void minmax_crab_nonExistingQuery(@TempDir Path tmpDir) throws IOException {
        Path actual = runMinMax("crab", tmpDir);
        Path expected = Paths.get("src/test/resources/data/minmax_expected/crab_output.txt");
        assertFilesEqual(expected, actual);
    }

    @Test
    void minmax_hawk_nonExistingQuery(@TempDir Path tmpDir) throws IOException {
        Path actual = runMinMax("hawk", tmpDir);
        Path expected = Paths.get("src/test/resources/data/minmax_expected/hawk_output.txt");
        assertFilesEqual(expected, actual);
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private Path runCharmap(String query, Path tmpDir) throws IOException {
        CharmapIndex index = new CharmapIndex(P, LMAX);
        index.build(INPUT);

        List<CharmapSummary> summaries = index.getSummaries();
        boolean[] keep = new boolean[summaries.size()];
        for (int i = 0; i < summaries.size(); i++) {
            keep[i] = summaries.get(i).mayContain(query);
        }

        Path output = tmpDir.resolve("charmap_" + query + "_output.txt");
        PartitionFilter.filterAndWrite(INPUT, output, keep, P);
        return output;
    }

    private Path runMinMax(String query, Path tmpDir) throws IOException {
        MinMaxIndex index = new MinMaxIndex(P);
        index.build(INPUT);

        List<MinMaxSummary> summaries = index.getSummaries();
        boolean[] keep = new boolean[summaries.size()];
        for (int i = 0; i < summaries.size(); i++) {
            keep[i] = summaries.get(i).mayContain(query);
        }

        Path output = tmpDir.resolve("minmax_" + query + "_output.txt");
        PartitionFilter.filterAndWrite(INPUT, output, keep, P);
        return output;
    }

    private void assertFilesEqual(Path expected, Path actual) throws IOException {
        List<String> expectedLines = Files.readAllLines(expected).stream()
                .filter(line -> !line.isEmpty())
                .toList();
        List<String> actualLines = Files.readAllLines(actual).stream()
                .filter(line -> !line.isEmpty())
                .toList();
        assertEquals(expectedLines, actualLines);
    }
}
