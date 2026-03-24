package charmap;

/**
 * Tracks saturation state for IP address segments.
 *
 * Each IP has 4 segments (0-255). This class tracks:
 * - For each segment: which values (0-255) have been seen
 * - When each segment becomes "saturated" (all 256 values present)
 * - The address index that causes saturation for each segment
 *
 * Uses a byte array (256 elements per segment) as a set to track seen values.
 */
public class IpSegmentSaturationSummary {

    // Four segments, each tracking 256 possible values
    private byte[][] seen = new byte[4][256];  // seen[segment][value] = 1 if seen, 0 otherwise
    private int[] saturationIndex = {-1, -1, -1, -1};  // -1 = not saturated yet
    private int[] saturationCount = {0, 0, 0, 0};  // how many unique values seen per segment

    /**
     * Update with an IP address split into 4 segments.
     * @param segmentValues int[4] containing the 4 segments (0-255 each)
     * @param addressIndex the index of this address in the build set (used to record saturation index)
     */
    public void update(int[] segmentValues, int addressIndex) {
        for (int seg = 0; seg < 4; seg++) {
            int value = segmentValues[seg];
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Segment value must be 0-255, got: " + value);
            }

            // If not yet saturated and this value is new, record it
            if (saturationIndex[seg] == -1 && seen[seg][value] == 0) {
                seen[seg][value] = 1;
                saturationCount[seg]++;

                // Check if we've reached saturation (all 256 values seen)
                if (saturationCount[seg] == 256) {
                    saturationIndex[seg] = addressIndex;
                }
            }
        }
    }

    /**
     * Check if a specific value was seen in a specific segment.
     * @param segment segment index (0-3)
     * @param value the value to check (0-255)
     * @return true if this value was seen in this segment
     */
    public boolean isValueSeen(int segment, int value) {
        if (segment < 0 || segment >= 4 || value < 0 || value > 255) {
            return false;
        }
        return seen[segment][value] != 0;
    }

    /**
     * Get all saturation indices as a space-separated string.
     */
    public String getSaturationIndicesAsString() {
        return saturationIndex[0] + " " + saturationIndex[1] + " " +
               saturationIndex[2] + " " + saturationIndex[3];
    }

    /**
     * Get the charset (set of seen values) for a specific segment as space-separated numbers.
     */
    public String getSegmentCharsetAsString(int segment) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            if (seen[segment][i] != 0) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(i);
            }
        }
        return sb.toString();
    }

    /**
     * Get charsets for all 4 segments, for output format: seg0charset|seg1charset|seg2charset|seg3charset
     */
    public String getAllCharsetsAsString() {
        StringBuilder sb = new StringBuilder();
        for (int seg = 0; seg < 4; seg++) {
            if (seg > 0) sb.append("|");
            sb.append(getSegmentCharsetAsString(seg));
        }
        return sb.toString();
    }
}
