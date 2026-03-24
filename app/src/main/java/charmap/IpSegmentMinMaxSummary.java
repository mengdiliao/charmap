package charmap;

/**
 * Tracks min/max values for IP address segments with saturation detection.
 *
 * Each IP has 4 segments (0-255). This class tracks:
 * - For each segment: the minimum and maximum value seen
 * - When a segment becomes "saturated" (min=0 AND max=255, i.e., full range)
 *
 * A query address is pruned if ANY segment falls outside its partition's min/max range.
 * Saturation indices show when each segment loses discriminative power (covers entire range).
 */
public class IpSegmentMinMaxSummary {

    // Min and max for each of 4 segments
    private int[] minValues = {255, 255, 255, 255};
    private int[] maxValues = {0, 0, 0, 0};
    
    // Saturation tracking: when min=0 AND max=255 for a segment
    private int[] saturationIndex = {-1, -1, -1, -1};  // -1 = not saturated yet

    /**
     * Update with an IP address split into 4 segments.
     * @param segmentValues int[4] containing the 4 segments (0-255 each)
     * @param addressIndex the 0-based index of this address within the partition
     */
    public void update(int[] segmentValues, int addressIndex) {
        for (int seg = 0; seg < 4; seg++) {
            int value = segmentValues[seg];
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Segment value must be 0-255, got: " + value);
            }
            
            minValues[seg] = Math.min(minValues[seg], value);
            maxValues[seg] = Math.max(maxValues[seg], value);
            
            // Check if this segment just became saturated
            if (saturationIndex[seg] == -1 && minValues[seg] == 0 && maxValues[seg] == 255) {
                saturationIndex[seg] = addressIndex;
            }
        }
    }

    /**
     * Check if a query address may be contained in this partition.
     * Returns false if ANY segment is outside the partition's min/max range (pruned).
     * @param querySegments int[4] containing the query address segments
     * @return true if partition may contain this address, false if pruned
     */
    public boolean mayContain(int[] querySegments) {
        for (int seg = 0; seg < 4; seg++) {
            if (querySegments[seg] < minValues[seg] || querySegments[seg] > maxValues[seg]) {
                return false;  // Pruned: query segment outside range
            }
        }
        return true;  // May contain
    }

    /**
     * Get min/max for all segments as string: "min0 max0 | min1 max1 | min2 max2 | min3 max3"
     */
    public String getMinMaxAsString() {
        StringBuilder sb = new StringBuilder();
        for (int seg = 0; seg < 4; seg++) {
            if (seg > 0) sb.append(" | ");
            sb.append(minValues[seg]).append(" ").append(maxValues[seg]);
        }
        return sb.toString();
    }

    /**
     * Get saturation indices as string: "idx0 idx1 idx2 idx3"
     * where -1 means not saturated
     */
    public String getSaturationIndicesAsString() {
        return saturationIndex[0] + " " + saturationIndex[1] + " " +
               saturationIndex[2] + " " + saturationIndex[3];
    }}