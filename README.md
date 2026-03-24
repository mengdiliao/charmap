# charmap

## 3.19 Meeting Accomplishments

### 1. Segment-level Saturation Instrumentation
Added segment-level summary utilities for the IP experiment to better track how quickly summaries saturate:
- Added `IpSegmentSaturationSummary` to track per-segment saturation timing.
- Added `IpSegmentMinMaxSummary` for comparable MinMax segment statistics.
- Updated experiment runners (`IpPrecomputeRunner`, `IpQueryRunner`) to emit richer precompute and query outputs.

### 2. Expanded Query Evaluation Outputs
Extended experiment outputs beyond individual index methods:
- Generated MinMax and Charmap query results for partition sizes `P = {10, 100, 1000, 10000}`.
- Added combined OR-baseline outputs (`or_P*_query.txt`) for side-by-side pruning comparisons.
- Regenerated precompute artifacts for both methods using the latest summary logic.

### 3. Analysis Tooling Pipeline 
Added a full post-processing and visualization pipeline under `tools/`:
- `generate_graphs.py`: computes pruning stats and saturation-distribution plots from experiment outputs.
- `extract_partition_pruning_matrix.py`: exports query-by-partition pruning matrix for Charmap (`P=1000`).
- `compute_plf_correlation.py`: computes Partition Load Factor (PLF) and pruning-frequency table.
- `plot_plf_pruning_correlation.py`: produces correlation and diagnostic visualizations.

### 4. New Data and Figures Produced
Generated new artifacts for downstream analysis:
- Partition pruning matrix: `charmap_P1000_partition_pruning_matrix.csv`.
- PLF correlation table: `charmap_P1000_plf_correlation.csv`.
- Experiment graph outputs in `app/src/main/resources/data/ip_experiment/graphs/`.
- Correlation figures in repository root:
  - `charmap_plf_pruning_correlation.png`
  - `charmap_plf_pruning_diagnostics.png`

### 5. Key Result from 3.19 Analysis
Pruning effectiveness decreases as partition size grows.
If the partition size is big enough to lead precomputed arrays saturated(cover all possibilities), the saturate index is fixed even the partition size keeps increasing.
## Next Week's TODO

1. Apply different strategies (Charmap and bitsets) to IP address querying and compare their pruning performance.
2. Try letter-based datasets (for example, names) and evaluate Charmap behavior on them.
3. Investigate and reference external datasets so experiments can use real-world sources instead of only self-generated data.

## Reproduce 3.19 Outputs

```bash
# Precompute and query runs
./gradlew :app:run -PmainClass=charmap.IpPrecomputeRunner
./gradlew :app:run -PmainClass=charmap.IpQueryRunner

# Analysis scripts
python3 tools/generate_graphs.py
python3 tools/extract_partition_pruning_matrix.py
python3 tools/compute_plf_correlation.py
python3 tools/plot_plf_pruning_correlation.py
```


## Building & Running

```bash
# Build and run tests
./gradlew build test

# Run example queries
./gradlew run --args="charmap <input> <output> <search_string> <L> <LMAX>"
./gradlew run --args="minmax <input> <output> <search_string> <L>"
```