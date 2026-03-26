# charmap

## 3.25 Meeting Accomplishments
### Scope
Implemented and validated a Charmap-focused workflow for both IP and name datasets.
Built a unified Charmap evaluation pipeline for IP and name datasets, then compared pruning performance across partition sizes and data ordering.

**Findings:**
- Sorted datasets maintain near-100% pruning across all tested partition sizes.
- Unsorted datasets are much more sensitive to partition size.
- Smaller partition sizes give much better pruning for unsorted data, and adding `P=5` improves pruning compared with larger `P`.
- Variability (error bars) is low for sorted datasets and higher for unsorted datasets, especially at medium/large partition sizes.

### Commands
```bash

# IP Charmap precompute (single run, parameterized)
./gradlew :app:run -PmainClass=charmap.IPCharmapPrecomputeRunner --args="--input <input> --output <output> --p <P> --lmax 12"

# IP Charmap query over all precomputed files
./gradlew :app:run -PmainClass=charmap.IPCharmapQueryRunner

# Name Charmap precompute (all configured partition sizes)
./gradlew :app:run -PmainClass=charmap.NameCharmapPrecomputeRunner

# Name Charmap query over all precomputed files
./gradlew :app:run -PmainClass=charmap.NameCharmapQueryRunner

# Plot pruning comparison
python3 tools/plot_pruning_barchart.py
```
