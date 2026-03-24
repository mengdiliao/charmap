#!/usr/bin/env python3
"""
Graph generation script for IP address experiment results.

Reads query output files and generates visualizations showing:
- Graph 1: Pruning percentage vs partition size (grouped bar chart)
- Graph 2: Saturation index distribution by partition size
- Graph 3: Pruning effectiveness vs saturation state
"""

import os
import sys
from pathlib import Path
from collections import defaultdict
import matplotlib.pyplot as plt
import numpy as np

def find_base_dir():
    """Locate the base directory for data files."""
    # Try common paths
    candidates = [
        Path(__file__).parent.parent / "app" / "src" / "main" / "resources" / "data",
        Path.home() / "Desktop" / "Research Project" / "charmap" / "app" / "src" / "main" / "resources" / "data"
    ]
    
    for candidate in candidates:
        if candidate.exists():
            return candidate
    
    raise FileNotFoundError("Could not find data directory. Please specify path explicitly.")

def read_query_results(query_dir):
    """
    Read all query result files and extract statistics.
    
    Returns: {
        'minmax': {P: [pruned_counts]},
        'charmap': {P: [pruned_counts]},
        'or': {P: [pruned_counts]}
    }
    """
    results = {'minmax': {}, 'charmap': {}, 'or': {}}
    
    for filename in sorted(os.listdir(query_dir)):
        filepath = os.path.join(query_dir, filename)
        if not os.path.isfile(filepath):
            continue
        
        # Parse filename to determine algorithm and P value
        if filename.startswith('minmax_P'):
            # minmax_P10_query.txt
            parts = filename.replace('minmax_P', '').replace('_query.txt', '')
            P = int(parts)
            algo = 'minmax'
        elif filename.startswith('charmap_P'):
            # charmap_P10_LMAX12_query.txt
            parts = filename.replace('charmap_P', '').replace('_query.txt', '').split('_LMAX')
            P = int(parts[0])
            algo = 'charmap'
        elif filename.startswith('or_P'):
            # or_P10_query.txt
            parts = filename.replace('or_P', '').replace('_query.txt', '')
            P = int(parts)
            algo = 'or'
        else:
            continue
        
        # Read pruned counts
        pruned_counts = []
        with open(filepath, 'r') as f:
            lines = f.readlines()
            for line in lines[1:]:  # Skip header (P=X or P=X, LMAX=X for charmap)
                if line.strip() and line[0].isdigit():
                    pruned_counts.append(int(line.strip()))
        
        if pruned_counts:
            results[algo][P] = pruned_counts
    
    return results

def read_precompute_minmax(precompute_dir):
    """
    Read MinMax precompute files and extract saturation indices.
    
    Returns: {P: [[sat_idx0, sat_idx1, sat_idx2, sat_idx3], ...]}
    where each partition has 4 saturation indices.
    """
    minmax_data = {}
    
    for filename in sorted(os.listdir(precompute_dir)):
        if not filename.startswith('minmax_P') or not filename.endswith('.txt'):
            continue
        
        P_str = filename.replace('minmax_P', '').replace('.txt', '')
        P = int(P_str)
        
        saturation_indices = []
        with open(os.path.join(precompute_dir, filename), 'r') as f:
            lines = f.readlines()
            # Skip header (line 0: "P=X")
            i = 1
            while i < len(lines):
                # Line i: saturation indices "idx0 idx1 idx2 idx3"
                sat_line = lines[i].strip()
                if sat_line:
                    indices = [int(x) for x in sat_line.split()]
                    saturation_indices.append(indices)
                i += 2  # Skip next line (min/max values)
        
        minmax_data[P] = saturation_indices
    
    return minmax_data

def read_precompute_charmap(precompute_dir):
    """
    Read Charmap precompute files and extract saturation indices.
    
    Returns: {P: [[sat_idx0, sat_idx1, sat_idx2, sat_idx3], ...]}
    where each partition has 4 saturation indices.
    """
    charmap_data = {}
    
    for filename in sorted(os.listdir(precompute_dir)):
        if not filename.startswith('charmap_P') or not filename.endswith('.txt'):
            continue
        
        parts = filename.replace('charmap_P', '').replace('.txt', '').split('_LMAX')
        P = int(parts[0])
        
        saturation_indices = []
        with open(os.path.join(precompute_dir, filename), 'r') as f:
            lines = f.readlines()
            # Skip header (line 0: "P=X", line 1: "LMAX=X")
            i = 2
            while i < len(lines):
                # Line i: saturation indices "idx0 idx1 idx2 idx3"
                sat_line = lines[i].strip()
                if sat_line:
                    indices = [int(x) for x in sat_line.split()]
                    saturation_indices.append(indices)
                i += 2  # Skip next line (charsets)
        
        charmap_data[P] = saturation_indices
    
    return charmap_data

def read_precompute_results(precompute_dir):
    """
    Read precompute files to get total partitions per P value.
    
    Returns: {P: num_partitions}
    """
    partitions = {}
    
    for filename in sorted(os.listdir(precompute_dir)):
        if not filename.startswith('minmax_P'):
            continue
        
        # minmax_P10.txt
        P_str = filename.replace('minmax_P', '').replace('.txt', '')
        P = int(P_str)
        
        # Count lines, accounting for header (first line is "P=X")
        with open(os.path.join(precompute_dir, filename), 'r') as f:
            lines = f.readlines()
            # Each partition has 2 lines: saturation indices + min/max
            num_partitions = (len(lines) - 1) // 2
            partitions[P] = num_partitions
    
    return partitions

def flatten_segment_saturation_indices(saturation_data):
    """
    Flatten saturation indices so each segment is one sample.
    Keeps -1 as unsaturated.

    Input: {P: [[idx0, idx1, idx2, idx3], ...]}
    Returns: {P: [segment_index_0, segment_index_1, ...]}
    """
    flat_indices = {}

    for P, partitions in saturation_data.items():
        segment_values = []
        for indices in partitions:
            segment_values.extend(indices)
        flat_indices[P] = segment_values

    return flat_indices

def get_adaptive_bin_width(P):
    """Choose histogram bin width based on partition size."""
    if P <= 100:
        return 1
    if P <= 1000:
        return 10
    return 50

def calculate_statistics(results, partitions):
    """
    Calculate average pruning percentage and standard deviation for each algorithm and P value.
    
    Returns: {
        'minmax': {P: (mean_percentage, std_percentage)},
        'charmap': {P: (mean_percentage, std_percentage)},
        'or': {P: (mean_percentage, std_percentage)}
    }
    """
    stats = {'minmax': {}, 'charmap': {}, 'or': {}}
    
    for algo in ['minmax', 'charmap', 'or']:
        for P, pruned_counts in results[algo].items():
            total_partitions = partitions.get(P)
            if total_partitions is None:
                print(f"Warning: Could not find total partitions for P={P}")
                continue
            
            # Convert counts to percentages
            percentages = [(count / total_partitions) * 100 for count in pruned_counts]
            avg_percentage = np.mean(percentages)
            std_percentage = np.std(percentages)
            
            stats[algo][P] = (avg_percentage, std_percentage)
    
    return stats

def plot_graph1_pruning_percentage(stats, output_dir):
    """
    Graph 1: Grouped bar chart showing pruning percentage vs partition size.
    
    X-axis: Partition size P
    Y-axis: Pruning percentage (%)
    Bars: MinMax, Charmap, OR grouped by P
    Error bars: Standard deviation shown as error bars
    """
    P_values = sorted(set().union(
        stats['minmax'].keys(),
        stats['charmap'].keys(),
        stats['or'].keys()
    ))
    
    x = np.arange(len(P_values))
    width = 0.25  # Width of each bar
    
    fig, ax = plt.subplots(figsize=(14, 8))
    
    # Get data for each algorithm (mean and std)
    minmax_means = [stats['minmax'].get(P, (0, 0))[0] for P in P_values]
    minmax_stds = [stats['minmax'].get(P, (0, 0))[1] for P in P_values]
    
    charmap_means = [stats['charmap'].get(P, (0, 0))[0] for P in P_values]
    charmap_stds = [stats['charmap'].get(P, (0, 0))[1] for P in P_values]
    
    or_means = [stats['or'].get(P, (0, 0))[0] for P in P_values]
    or_stds = [stats['or'].get(P, (0, 0))[1] for P in P_values]
    
    # Create bars with error bars
    bars1 = ax.bar(x - width, minmax_means, width, yerr=minmax_stds, 
                   label='MinMax', color='#1f77b4', alpha=0.8, capsize=5, error_kw={'elinewidth': 2})
    bars2 = ax.bar(x, charmap_means, width, yerr=charmap_stds,
                   label='Charmap', color='#ff7f0e', alpha=0.8, capsize=5, error_kw={'elinewidth': 2})
    bars3 = ax.bar(x + width, or_means, width, yerr=or_stds,
                   label='OR (MinMax + Charmap)', color='#2ca02c', alpha=0.8, capsize=5, error_kw={'elinewidth': 2})
    
    # Customize plot
    ax.set_xlabel('Partition Size (P)', fontsize=13, fontweight='bold')
    ax.set_ylabel('Pruning Percentage (%)', fontsize=13, fontweight='bold')
    ax.set_title('Pruning Effectiveness vs Partition Size\n(Error bars show ±1 standard deviation)', 
                 fontsize=14, fontweight='bold')
    ax.set_xticks(x)
    ax.set_xticklabels([str(P) for P in P_values], fontsize=11)
    ax.set_ylim(0, 110)
    ax.legend(fontsize=12, loc='upper right')
    ax.grid(axis='y', alpha=0.3, linestyle='--')
    
    # Add value labels on bars (mean only)
    for bars in [bars1, bars2, bars3]:
        for bar in bars:
            height = bar.get_height()
            if height > 0:
                ax.text(bar.get_x() + bar.get_width()/2., height,
                       f'{height:.1f}%',
                       ha='center', va='bottom', fontsize=9)
    
    plt.tight_layout()
    output_path = os.path.join(output_dir, 'graph1_pruning_percentage.png')
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"✓ Graph 1 saved: {output_path}")
    plt.close()

def plot_graph2_saturation_distribution(minmax_flat, charmap_flat, output_dir):
    """
    Graph 2: Two-part saturation view by partition size.

    Top: Unsaturated-rate comparison (-1 indices).
    Bottom: Positive-only saturation index histograms (index > 0),
    with adaptive bin widths by P.
    """
    P_values = sorted(set(minmax_flat.keys()).union(charmap_flat.keys()))
    n_cols = 2
    n_rows = max(1, int(np.ceil(len(P_values) / n_cols)))

    fig = plt.figure(figsize=(15, 4 + 4.2 * n_rows))
    gs = fig.add_gridspec(n_rows + 1, n_cols, height_ratios=[1.3] + [3] * n_rows)
    fig.suptitle('Saturation Distribution by Partition Size\n(Top: Unsaturated rate; Bottom: Positive-only saturation timing)',
                 fontsize=15, fontweight='bold', y=0.995)

    # Top panel: unsaturated rates
    top_ax = fig.add_subplot(gs[0, :])
    x = np.arange(len(P_values))
    width = 0.35
    minmax_unsat = []
    charmap_unsat = []
    for P in P_values:
        mm = minmax_flat.get(P, [])
        cm = charmap_flat.get(P, [])
        mm_unsat_pct = (sum(1 for v in mm if v == -1) / len(mm) * 100) if mm else 0
        cm_unsat_pct = (sum(1 for v in cm if v == -1) / len(cm) * 100) if cm else 0
        minmax_unsat.append(mm_unsat_pct)
        charmap_unsat.append(cm_unsat_pct)

    top_ax.bar(x - width / 2, minmax_unsat, width, label='MinMax', color='#1f77b4', alpha=0.85)
    top_ax.bar(x + width / 2, charmap_unsat, width, label='Charmap', color='#ff7f0e', alpha=0.85)
    top_ax.set_xticks(x)
    top_ax.set_xticklabels([str(P) for P in P_values], fontsize=10)
    top_ax.set_xlabel('Partition Size (P)', fontsize=11)
    top_ax.set_ylabel('Unsaturated Segments (%)', fontsize=11)
    top_ax.set_ylim(0, 105)
    top_ax.grid(axis='y', alpha=0.3, linestyle='--')
    top_ax.legend(fontsize=10, loc='upper right')

    # Bottom panels: positive-only histogram per P
    hist_axes = []
    for idx, P in enumerate(P_values):
        row = 1 + (idx // n_cols)
        col = idx % n_cols
        ax = fig.add_subplot(gs[row, col])
        hist_axes.append(ax)

        minmax_positive = [v for v in minmax_flat.get(P, []) if v > 0]
        charmap_positive = [v for v in charmap_flat.get(P, []) if v > 0]
        bin_width = get_adaptive_bin_width(P)

        max_idx = max(max(minmax_positive + [0]), max(charmap_positive + [0]))
        if max_idx == 0:
            bins = np.array([0, bin_width])
        else:
            upper = ((max_idx // bin_width) + 1) * bin_width
            bins = np.arange(0, upper + bin_width, bin_width)

        ax.hist(minmax_positive, bins=bins, alpha=0.6, label='MinMax', color='#1f77b4', edgecolor='black')
        ax.hist(charmap_positive, bins=bins, alpha=0.6, label='Charmap', color='#ff7f0e', edgecolor='black')

        minmax_mean = np.mean(minmax_positive) if minmax_positive else 0
        charmap_mean = np.mean(charmap_positive) if charmap_positive else 0

        ax.set_title(
            f'P={P} (bin={bin_width}, MinMax n+={len(minmax_positive)}, Charmap n+={len(charmap_positive)})\n'
            f'μ+ MinMax={minmax_mean:.1f}, μ+ Charmap={charmap_mean:.1f}',
            fontsize=11,
            fontweight='bold'
        )
        ax.set_xlabel('Saturation Index (>0 only)', fontsize=10)
        ax.set_ylabel('Number of Segments', fontsize=10)
        ax.legend(fontsize=9)
        ax.grid(alpha=0.3, axis='y')

    for ax in hist_axes[len(P_values):]:
        ax.set_visible(False)

    plt.tight_layout(rect=[0, 0, 1, 0.97])
    output_path = os.path.join(output_dir, 'graph2_saturation_distribution.png')
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"✓ Graph 2 saved: {output_path}")
    plt.close()

def print_saturation_statistics(minmax_flat, charmap_flat):
    """Print saturation statistics to console."""
    print("\n" + "="*100)
    print("SATURATION INDEX STATISTICS (Segment-level; unsaturated kept as -1)")
    print("="*100)

    P_values = sorted(set(minmax_flat.keys()).union(charmap_flat.keys()))

    print(f"\n{'Partition':<12} {'MinMax':<40} {'Charmap':<40}")
    print("-" * 100)

    for P in P_values:
        minmax_indices = minmax_flat.get(P, [])
        charmap_indices = charmap_flat.get(P, [])

        minmax_unsat = sum(1 for x in minmax_indices if x == -1)
        charmap_unsat = sum(1 for x in charmap_indices if x == -1)
        minmax_positive = [x for x in minmax_indices if x > 0]
        charmap_positive = [x for x in charmap_indices if x > 0]

        minmax_unsat_pct = (minmax_unsat / len(minmax_indices) * 100) if minmax_indices else 0
        charmap_unsat_pct = (charmap_unsat / len(charmap_indices) * 100) if charmap_indices else 0

        minmax_pos_mean = np.mean(minmax_positive) if minmax_positive else 0
        charmap_pos_mean = np.mean(charmap_positive) if charmap_positive else 0
        minmax_pos_median = np.median(minmax_positive) if minmax_positive else 0
        charmap_pos_median = np.median(charmap_positive) if charmap_positive else 0

        print(
            f"P={P:<10} unsat={minmax_unsat:>5}/{len(minmax_indices):<5} ({minmax_unsat_pct:>6.2f}%), "
            f"n+={len(minmax_positive):>5}, μ+={minmax_pos_mean:>7.1f}, med+={minmax_pos_median:>7.1f}"
            + "  "
            + f"unsat={charmap_unsat:>5}/{len(charmap_indices):<5} ({charmap_unsat_pct:>6.2f}%), "
            f"n+={len(charmap_positive):>5}, μ+={charmap_pos_mean:>7.1f}, med+={charmap_pos_median:>7.1f}"
        )
    
    print("="*100)

def print_statistics(stats):
    """Print statistics to console."""
    print("\n" + "="*90)
    print("PRUNING EFFECTIVENESS STATISTICS (Mean ± Std Dev)")
    print("="*90)
    
    P_values = sorted(set().union(
        stats['minmax'].keys(),
        stats['charmap'].keys(),
        stats['or'].keys()
    ))
    
    print(f"\n{'Partition':<12} {'MinMax':<25} {'Charmap':<25} {'OR':<25}")
    print("-" * 90)
    
    for P in P_values:
        minmax_mean, minmax_std = stats['minmax'].get(P, (0, 0))
        charmap_mean, charmap_std = stats['charmap'].get(P, (0, 0))
        or_mean, or_std = stats['or'].get(P, (0, 0))
        
        print(f"P={P:<10} {minmax_mean:>6.2f}% ± {minmax_std:>5.2f}%   {charmap_mean:>6.2f}% ± {charmap_std:>5.2f}%   {or_mean:>6.2f}% ± {or_std:>5.2f}%")
    
    print("="*90)

def main():
    try:
        base_dir = find_base_dir()
        query_dir = base_dir / "ip_experiment" / "query"
        precompute_dir = base_dir / "ip_experiment" / "precompute"
        graphs_dir = base_dir / "ip_experiment" / "graphs"
        graphs_dir.mkdir(exist_ok=True)
        output_dir = graphs_dir  # Save graphs in dedicated graphs directory
        
        print(f"Reading data from: {query_dir}")
        
        # Graph 1: Query results
        results = read_query_results(str(query_dir))
        stats = calculate_statistics(results, read_precompute_results(str(precompute_dir)))
        print_statistics(stats)
        plot_graph1_pruning_percentage(stats, str(output_dir))
        
        # Graph 2: Saturation distribution
        print(f"\nReading precompute data from: {precompute_dir}")
        minmax_data = read_precompute_minmax(str(precompute_dir))
        charmap_data = read_precompute_charmap(str(precompute_dir))
        
        minmax_flat = flatten_segment_saturation_indices(minmax_data)
        charmap_flat = flatten_segment_saturation_indices(charmap_data)

        print_saturation_statistics(minmax_flat, charmap_flat)
        plot_graph2_saturation_distribution(minmax_flat, charmap_flat, str(output_dir))
        
        print(f"\nGraphs saved to: {output_dir}")
        
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
