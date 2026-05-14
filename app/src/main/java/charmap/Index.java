package charmap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

interface Index<T extends Summary> {

    /**
     * Returns the list of summaries for each partition in the index.
     *
     * @return list of summaries, one per partition
     */
    List<T> getSummaries();

    /**
     * Builds the index by reading the input file and creating partition summaries.
     *
     * @param input the path to the input file to index
     * @throws IOException if an I/O error occurs while reading the file
     */
    void build(Path input) throws IOException;
}