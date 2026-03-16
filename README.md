# charmap

## 3.12 Meeting Accomplishments

### IP Address Experiment
Implemented a complete experimental pipeline for evaluating MinMax and Charmap index performance on IP address datasets:
- **Precomputation phase**: Builds and precomputes indexes for multiple partition sizes (P = {10, 100, 1000, 10000})
- **Query evaluation phase**: Measures how effectively each index method prunes partitions during query processing
- **Data infrastructure**: Added support for IP address datasets with tools for generation and analysis

### Running the Experiments
```bash
# Precompute indexes
./gradlew :app:run -PmainClass=charmap.IpPrecomputeRunner

# Evaluate query performance
./gradlew :app:run -PmainClass=charmap.IpQueryRunner
```

### Experiment Results
**MinMax Index:**
- P=10: ✅ Excellent pruning (~300-400 partitions per query)
- P=100: ⚠️ Significant pruning drop
- P≥1000: ❌ Minimal pruning (near zero)

**Charmap Index:**
- P=10: ✅ Excellent pruning (~97k+ partitions per query) - far superior to MinMax
- P=100: ⚠️ Minimal pruning (~8 partitions)
- P≥1000: ❌ No pruning (zero)

**Key Finding:** Both methods degrade with larger partition sizes. Small partitions (P=10) maintain discriminative summaries, while large partitions (P≥1000) become too loose to prune effectively. Charmap significantly outperforms MinMax at small partition sizes.


## Building & Running

```bash
# Build and run tests
./gradlew build test

# Run example queries
./gradlew run --args="charmap <input> <output> <search_string> <L> <LMAX>"
./gradlew run --args="minmax <input> <output> <search_string> <L>"
```