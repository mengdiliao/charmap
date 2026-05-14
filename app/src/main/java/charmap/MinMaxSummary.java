package charmap;

final class MinMaxSummary implements Summary {
    String minValue;
    String maxValue;

    public boolean mayContain(String query) {
        if (minValue == null || maxValue == null)
            return true;  // empty partition, include it

        return query.compareTo(minValue) >= 0 && query.compareTo(maxValue) <= 0;
    }

    public void update(String value) {
        if (minValue == null || value.compareTo(minValue) < 0) {
            minValue = value;
        }

        if (maxValue == null || value.compareTo(maxValue) > 0) {
            maxValue = value;
        }
    }
}