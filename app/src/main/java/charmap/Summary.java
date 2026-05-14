package charmap;

interface Summary {
    /**
     * Checks if the summary may contain the given query string.
     *
     * @param query the query string to check
     * @return true if the query may be contained in the partition, false if definite absence
     */
    boolean mayContain(String query);

    /**
     * Updates the summary with a new value from the partition.
     *
     * @param value the value to update the summary with
     */
    void update(String value);
}
