#!/usr/bin/env python3
"""
Compute Partition Load Factor (PLF) and correlate with pruning frequency.

PLF = ∏(occupancy_i / 256) for segments that didn't saturate
     where occupancy_i = count of seen values in segment i

Output: CSV with one row per partition:
  partition_id, plf, pruning_frequency, sat_indices, occupancy_counts
  
This enables correlation: does high PLF predict higher-than-random pruning?

Run with:
  cd charmap
  python3 tools/compute_plf_correlation.py
"""

import os
import sys
from pathlib import Path
import csv
import numpy as np

def find_base_dir():
    """Locate the base directory for data files."""
    candidates = [
        Path(__file__).parent.parent / "app" / "src" / "main" / "resources" / "data",
        Path.home() / "Desktop" / "Research Project" / "charmap" / "app" / "src" / "main" / "resources" / "data"
    ]
    
    for candidate in candidates:
        if candidate.exists():
            return candidate
    
    raise FileNotFoundError("Could not find data directory.")


def parse_partition_data(charmap_precompute_path):
    """
    Parse charmap precompute file to extract saturation indices and segment occupancy counts.
    
    Returns: List of partition data, where each partition has:
    {
        'partition_id': int,
        'saturation_indices': [sat0, sat1, sat2, sat3],  # -1 if not saturated
        'occupancy_counts': [count0, count1, count2, count3]  # number of unique values seen per segment
    }
    """
    partitions = []
    partition_id = 0
    
    with open(charmap_precompute_path, 'r') as f:
        lines = f.readlines()
    
    # Skip header lines (P=, LMAX=)
    i = 2
    while i < len(lines):
        # Line i: saturation indices
        sat_line = lines[i].strip()
        sat_indices = [int(x) for x in sat_line.split()]
        i += 1
        
        # Line i: charsets (4 segments separated by |)
        charset_line = lines[i].strip()
        charset_parts = charset_line.split('|')
        
        if len(charset_parts) == 4:
            occupancy_counts = []
            for part in charset_parts:
                # Count unique values (by counting elements after split)
                count = len(part.split()) if part.strip() else 0
                occupancy_counts.append(count)
            
            partitions.append({
                'partition_id': partition_id,
                'saturation_indices': sat_indices,
                'occupancy_counts': occupancy_counts
            })
            partition_id += 1
        
        i += 1
    
    return partitions


def compute_plf(occupancy_counts):
    """
    Compute Partition Load Factor.
    PLF = ∏ (count_i / 256) for all 4 segments
    """
    plf = 1.0
    for count in occupancy_counts:
        plf *= (count / 256.0)
    return plf


def read_pruning_matrix(pruning_matrix_path):
    """
    Read pruning matrix CSV and compute pruning frequency per partition.
    
    Returns: Dict mapping partition_id -> pruning_frequency (0.0 to 1.0)
    """
    partition_prune_counts = {}
    query_count = 0
    
    with open(pruning_matrix_path, 'r') as csvfile:
        reader = csv.DictReader(csvfile)
        for row_idx, row in enumerate(reader):
            query_count = row_idx + 1
            
            # Iterate through all partition columns
            for key, value in row.items():
                if key.startswith('p') and key.endswith('_pruned'):
                    partition_id = int(key[1:-7])  # Extract ID from 'pN_pruned'
                    pruned = int(value)
                    
                    if partition_id not in partition_prune_counts:
                        partition_prune_counts[partition_id] = 0
                    partition_prune_counts[partition_id] += pruned
    
    # Convert counts to frequencies
    pruning_frequency = {}
    for partition_id, count in partition_prune_counts.items():
        pruning_frequency[partition_id] = count / query_count
    
    return pruning_frequency, query_count


def main():
    try:
        base_dir = find_base_dir()
        P = 1000
        
        precompute_path = base_dir / "ip_experiment" / "precompute" / f"charmap_P{P}_LMAX12.txt"
        pruning_matrix_path = base_dir / "ip_experiment" / f"charmap_P{P}_partition_pruning_matrix.csv"
        output_dir = base_dir / "ip_experiment"
        output_dir.mkdir(exist_ok=True)
        output_path = output_dir / f"charmap_P{P}_plf_correlation.csv"
        
        print(f"Reading partition data from: {precompute_path}")
        partitions = parse_partition_data(precompute_path)
        print(f"  Loaded {len(partitions)} partitions")
        
        print(f"Reading pruning frequencies from: {pruning_matrix_path}")
        pruning_freq, num_queries = read_pruning_matrix(pruning_matrix_path)
        print(f"  Computed from {num_queries} queries")
        
        print(f"\nComputing PLF and correlation metrics...")
        
        with open(output_path, 'w', newline='') as csvfile:
            fieldnames = [
                'partition_id',
                'plf',
                'pruning_frequency',
                'sat_idx_seg0', 'sat_idx_seg1', 'sat_idx_seg2', 'sat_idx_seg3',
                'occupancy_seg0', 'occupancy_seg1', 'occupancy_seg2', 'occupancy_seg3'
            ]
            writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
            writer.writeheader()
            
            plfs = []
            pruning_freqs = []
            
            for partition in partitions:
                pid = partition['partition_id']
                
                # Compute PLF
                plf = compute_plf(partition['occupancy_counts'])
                
                # Get pruning frequency (default to 0 if not in matrix)
                freq = pruning_freq.get(pid, 0.0)
                
                row = {
                    'partition_id': pid,
                    'plf': f'{plf:.6f}',
                    'pruning_frequency': f'{freq:.6f}',
                    'sat_idx_seg0': partition['saturation_indices'][0],
                    'sat_idx_seg1': partition['saturation_indices'][1],
                    'sat_idx_seg2': partition['saturation_indices'][2],
                    'sat_idx_seg3': partition['saturation_indices'][3],
                    'occupancy_seg0': partition['occupancy_counts'][0],
                    'occupancy_seg1': partition['occupancy_counts'][1],
                    'occupancy_seg2': partition['occupancy_counts'][2],
                    'occupancy_seg3': partition['occupancy_counts'][3],
                }
                
                writer.writerow(row)
                plfs.append(plf)
                pruning_freqs.append(freq)
        
        print(f"✓ Correlation data saved: {output_path}")
        
        # Print summary statistics
        plfs = np.array(plfs)
        pruning_freqs = np.array(pruning_freqs)
        
        print(f"\n--- PLF Statistics ---")
        print(f"  Min: {plfs.min():.6f}, Max: {plfs.max():.6f}")
        print(f"  Mean: {plfs.mean():.6f}, Median: {np.median(plfs):.6f}")
        print(f"  Std: {plfs.std():.6f}")
        
        print(f"\n--- Pruning Frequency Statistics ---")
        print(f"  Min: {pruning_freqs.min():.6f}, Max: {pruning_freqs.max():.6f}")
        print(f"  Mean: {pruning_freqs.mean():.6f}, Median: {np.median(pruning_freqs):.6f}")
        print(f"  Std: {pruning_freqs.std():.6f}")
        
        # Compute correlation
        correlation = np.corrcoef(plfs, pruning_freqs)[0, 1]
        print(f"\n--- Correlation Analysis ---")
        print(f"  Pearson R (PLF vs Pruning Frequency): {correlation:.4f}")
        print(f"  (Positive = higher PLF → higher pruning; Negative = inverse)")
        
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()
