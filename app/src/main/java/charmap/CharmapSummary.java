package charmap;

final class CharmapSummary implements Summary {
    final long[] charSet;   // size = LMAX

    CharmapSummary(int lmax) {
        this.charSet = new long[lmax];
    }

    public boolean mayContain(String query) {
        for (int i = 0; i < Math.min(query.length(), charSet.length); i++) {
            int idx = CharMapper.map(query.charAt(i));

            // 1L << idx creates a bitmask with only the bit at position idx set to 1.
            if ((charSet[i] & (1L << idx)) == 0)
                return false;  // definite absence
        }
        return true;
    }

    public void update(String value) {
        int m = Math.min(value.length(), charSet.length);
        for (int i = 0; i < m; i++) {
            int idx = CharMapper.map(value.charAt(i));
            charSet[i] |= (1L << idx);
        }
    }
}
