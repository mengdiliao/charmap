package charmap;

import java.io.*;
import java.nio.file.*;

final class PartitionFilter {

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
            // Create parent directories if they don't exist
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            return Files.newBufferedWriter(output);
        } else {
            return new BufferedWriter(new OutputStreamWriter(System.out));
        }
    }
}
