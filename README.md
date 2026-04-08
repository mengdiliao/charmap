# charmap

## 4.9 Meeting Accomplishments
### Scope
Implemented and validated a unified query workflow for both strategies (Charmap and MinMax)
across both datasets (IP and name).

Completed meeting TODO items:
- Ran experiments varying partition size for MinMax and Charmap.
- Extended partition-size sweeps beyond 500 to include 1000 and 2000.
- Ran fixed-partition experiments with P=200 while varying LMAX in {3, 5, 8, 12}.
- Also ran the fixed-partition LMAX experiments for P=100 and P=500.

**Graphs:**

### Plots

#### Partition-size sweep
![Partition-size sweep](app/src/main/resources/data/results/query/pruning_summary_bar_chart.png)

#### Fixed-P LMAX sweep (P=100)
![Fixed-P LMAX sweep P100](app/src/main/resources/data/results/fixed_p_lmax/p100/plots/pruning_vs_lmax_P100.png)

#### Fixed-P LMAX sweep (P=200)
![Fixed-P LMAX sweep P200](app/src/main/resources/data/results/fixed_p_lmax/p200/plots/pruning_vs_lmax_P200.png)

#### Fixed-P LMAX sweep (P=500)
![Fixed-P LMAX sweep P500](app/src/main/resources/data/results/fixed_p_lmax/p500/plots/pruning_vs_lmax_P500.png)

### Commands
```bash

# 1) Single run

# IP Charmap precompute 
./gradlew :app:run -PmainClass=charmap.IPCharmapPrecomputeRunner --args="--input <input> --output <output_prefix> --p <P> --lmax 12"

# Name Charmap precompute 
./gradlew :app:run -PmainClass=charmap.NameCharmapPrecomputeRunner --args="--input <input> --output <output_prefix> --lmax 12 --p <P>"

# IP MinMax precompute 
./gradlew :app:run -PmainClass=charmap.IPMinmaxPrecomputeRunner --args="--input <input> --output <output_prefix> --p <P> --lmax 12"

# Name MinMax precompute 
./gradlew :app:run -PmainClass=charmap.NameMinmaxPrecomputeRunner --args="--input <input> --output <output_prefix> --p <P> --lmax 12"

# 2) Run multiple in one command

# Name Charmap precompute (all partition sizes for one dataset)
for P in 5 10 50 100 200 500 1000 2000; do
  ./gradlew :app:run -PmainClass=charmap.NameCharmapPrecomputeRunner --args="--input <input> --output <output_prefix> --lmax 12 --p ${P}"
done

# Fixed-P experiment helper (run precompute + query for one P group, varying LMAX)
# Example shown for P=200. Repeat for P=100 or P=500 by changing P value and folder path.
for LMAX in 3 5 8 12; do
  ./gradlew :app:run -PmainClass=charmap.IPCharmapPrecomputeRunner --args="--input src/main/resources/data/ip_experiment/1m_ip_addresses.txt --output src/main/resources/data/results/fixed_p_lmax/p200/precompute/ip_charmap/ip_charmap --lmax ${LMAX} --p 200"
  ./gradlew :app:run -PmainClass=charmap.NameCharmapPrecomputeRunner --args="--input src/main/resources/data/name_experiment/1m_names.txt --output src/main/resources/data/results/fixed_p_lmax/p200/precompute/name_charmap/name_charmap --lmax ${LMAX} --p 200"
  ./gradlew :app:run -PmainClass=charmap.IPMinmaxPrecomputeRunner --args="--input src/main/resources/data/ip_experiment/1m_ip_addresses.txt --output src/main/resources/data/results/fixed_p_lmax/p200/precompute/ip_minmax/ip_minmax --lmax ${LMAX} --p 200"
  ./gradlew :app:run -PmainClass=charmap.NameMinmaxPrecomputeRunner --args="--input src/main/resources/data/name_experiment/1m_names.txt --output src/main/resources/data/results/fixed_p_lmax/p200/precompute/name_minmax/name_minmax --lmax ${LMAX} --p 200"

  ./gradlew :app:run -PmainClass=charmap.IPQueryRunner --args="--strategy charmap --query src/main/resources/data/ip_experiment/1000_query_ips.txt --precomputeDir src/main/resources/data/results/fixed_p_lmax/p200/precompute/ip_charmap --outputDir src/main/resources/data/results/fixed_p_lmax/p200/query/ip_charmap"
  ./gradlew :app:run -PmainClass=charmap.IPQueryRunner --args="--strategy minmax --query src/main/resources/data/ip_experiment/1000_query_ips.txt --precomputeDir src/main/resources/data/results/fixed_p_lmax/p200/precompute/ip_minmax --outputDir src/main/resources/data/results/fixed_p_lmax/p200/query/ip_minmax"
  ./gradlew :app:run -PmainClass=charmap.NameQueryRunner --args="--strategy charmap --query src/main/resources/data/name_experiment/1000_query_names.txt --precomputeDir src/main/resources/data/results/fixed_p_lmax/p200/precompute/name_charmap --outputDir src/main/resources/data/results/fixed_p_lmax/p200/query/name_charmap"
  ./gradlew :app:run -PmainClass=charmap.NameQueryRunner --args="--strategy minmax --query src/main/resources/data/name_experiment/1000_query_names.txt --precomputeDir src/main/resources/data/results/fixed_p_lmax/p200/precompute/name_minmax --outputDir src/main/resources/data/results/fixed_p_lmax/p200/query/name_minmax"
done

# Run all 4 precompute methods for partition sizes {5, 10, 50, 100, 200, 500, 1000, 2000}
for P in 5 10 50 100 200 500 1000 2000; do
  ./gradlew :app:run -PmainClass=charmap.IPCharmapPrecomputeRunner --args="--input <ip_input> --output <ip_charmap_output_prefix> --lmax 12 --p ${P}"
  ./gradlew :app:run -PmainClass=charmap.NameCharmapPrecomputeRunner --args="--input <name_input> --output <name_charmap_output_prefix> --lmax 12 --p ${P}"
  ./gradlew :app:run -PmainClass=charmap.IPMinmaxPrecomputeRunner --args="--input <ip_input> --output <ip_minmax_output_prefix> --lmax 12 --p ${P}"
  ./gradlew :app:run -PmainClass=charmap.NameMinmaxPrecomputeRunner --args="--input <name_input> --output <name_minmax_output_prefix> --lmax 12 --p ${P}"
done

# Fix partition size P=200, vary LMAX in {3, 5, 8, 12}, run Name Charmap precompute
for LMAX in 3 5 8 12; do
  ./gradlew :app:run -PmainClass=charmap.NameCharmapPrecomputeRunner --args="--input <name_input> --output <name_charmap_output_prefix> --lmax ${LMAX} --p 200"
done

# Fix partition size P=200, vary LMAX in {3, 5, 8, 12}, run all 4 precompute methods
for LMAX in 3 5 8 12; do
  ./gradlew :app:run -PmainClass=charmap.IPCharmapPrecomputeRunner --args="--input <ip_input> --output <ip_charmap_output_prefix> --lmax ${LMAX} --p 200"
  ./gradlew :app:run -PmainClass=charmap.NameCharmapPrecomputeRunner --args="--input <name_input> --output <name_charmap_output_prefix> --lmax ${LMAX} --p 200"
  ./gradlew :app:run -PmainClass=charmap.IPMinmaxPrecomputeRunner --args="--input <ip_input> --output <ip_minmax_output_prefix> --lmax ${LMAX} --p 200"
  ./gradlew :app:run -PmainClass=charmap.NameMinmaxPrecomputeRunner --args="--input <name_input> --output <name_minmax_output_prefix> --lmax ${LMAX} --p 200"
done

# 3) Query

# IP query (strategy: charmap)
./gradlew :app:run -PmainClass=charmap.IPQueryRunner --args="--strategy charmap --query <ip_query_file> --precomputeDir <ip_charmap_precompute_dir> --outputDir <ip_charmap_query_output_dir>"

# IP query (strategy: minmax)
./gradlew :app:run -PmainClass=charmap.IPQueryRunner --args="--strategy minmax --query <ip_query_file> --precomputeDir <ip_minmax_precompute_dir> --outputDir <ip_minmax_query_output_dir>"

# Name query (strategy: charmap)
./gradlew :app:run -PmainClass=charmap.NameQueryRunner --args="--strategy charmap --query <name_query_file> --precomputeDir <name_charmap_precompute_dir> --outputDir <name_charmap_query_output_dir>"

# Name query (strategy: minmax)
./gradlew :app:run -PmainClass=charmap.NameQueryRunner --args="--strategy minmax --query <name_query_file> --precomputeDir <name_minmax_precompute_dir> --outputDir <name_minmax_query_output_dir>"

# Plot pruning comparison
python3 tools/plot_pruning_barchart.py

# Plot fixed-P, varying-LMAX comparison (example for P=200)
python3 tools/plot_pruning_fixed_p_lmax.py --query-dir app/src/main/resources/data/results/fixed_p_lmax/p200/query --p 200 --lmax-values 3,5,8,12 --output app/src/main/resources/data/results/fixed_p_lmax/p200/plots/pruning_vs_lmax_P200.png
```
