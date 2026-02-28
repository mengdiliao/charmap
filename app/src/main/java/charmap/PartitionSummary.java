package charmap;
final class PartitionSummary {
    final long[] charSet;   // size = LMAX

    PartitionSummary(int lmax) {
        this.charSet = new long[lmax];
    }
}