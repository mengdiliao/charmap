#!/usr/bin/env python3
"""
Extract per-partition pruning matrix from Charmap experiment for P=1000.

Output: CSV with rows = queries, columns = partition_id, partition_1_pruned, partition_2_pruned, ...
This allows correlation analysis between partition saturation (PLF) and pruning frequency.

Run with:
  cd charmap
  python3 tools/extract_partition_pruning_matrix.py
"""

import os
import sys
from pathlib import Path
import csv

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


def read_query_addresses(query_file_path):
    """Read IP addresses from file, parse as 4 segments each."""
    addresses = []
    with open(query_file_path, 'r') as f:
        for line in f:
            line = line.strip()
            if line and line[0].isdigit():
                segments = [int(x) for x in line.split('.')]
                if len(segments) == 4:
                    addresses.append(segments)
    return addresses


def parse_partition_charsets(charmap_precompute_path, P):
    """
    Parse charmap precompute file to extract partition charsets.
    
    Returns: List of partitions, where each partition has 4 charsets (sets of seen segment values).
    Format: {
        'partition_id': int,
        'charsets': [set0, set1, set2, set3]  # 4 sets, one per segment
    }
    """
    partitions = []
    partition_id = 0
    
    with open(charmap_precompute_path, 'r') as f:
        lines = f.readlines()
    
    # Skip header lines (P=, LMAX=)
    i = 2
    while i < len(lines):
        # Line i: saturation indices (skip)
        sat_line = lines[i].strip()
        i += 1
        
        # Line i: charsets (4 segments separated by |)
        charset_line = lines[i].strip()
        charset_parts = charset_line.split('|')
        
        if len(charset_parts) == 4:
            charsets = []
            for part in charset_parts:
                # Parse space-separated values into a set
                values = set()
                if part.strip():
                    values = set(int(v) for v in part.split())
                charsets.append(values)
            
            partitions.append({
                'partition_id': partition_id,
                'charsets': charsets
            })
            partition_id += 1
        
        i += 1
    
    return partitions


def is_partition_pruned(partition, query_segments):
    """
    Check if Charmap prunes a partition for a query.
    
    Charmap prunes if ANY segment value is NOT in the partition's charset.
    (i.e., if all 4 query segment values are in the partition's charsets, no pruning)
    """
    for seg in range(4):
        query_val = query_segments[seg]
        if query_val not in partition['charsets'][seg]:
            return True  # Pruned: query value not in this segment's charset
    
    return False  # Not pruned: all query segment values found


def main():
    try:
        base_dir = find_base_dir()
        P = 1000
        
        precompute_path = base_dir / "ip_experiment" / "precompute" / f"charmap_P{P}_LMAX12.txt"
        query_address_path = base_dir / "inputs" / "ip_addresses" / "query_addresses.txt"
        output_dir = base_dir / "ip_experiment"
        output_dir.mkdir(exist_ok=True)
        output_path = output_dir / f"charmap_P{P}_partition_pruning_matrix.csv"
        
        print(f"Reading partitions from: {precompute_path}")
        partitions = parse_partition_charsets(precompute_path, P)
        print(f"  Loaded {len(partitions)} partitions")
        
        print(f"Reading query addresses from: {query_address_path}")
        queries = read_query_addresses(query_address_path)
        print(f"  Loaded {len(queries)} query addresses")
        
        print(f"\nProcessing {len(queries)} queries across {len(partitions)} partitions...")
        
        # Build pruning matrix
        with open(output_path, 'w', newline='') as csvfile:
            # Header: query_id, partition_0_pruned, partition_1_pruned, ...
            fieldnames = ['query_id'] + [f'p{pid}_pruned' for pid in range(len(partitions))]
            writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
            writer.writeheader()
            
            for query_idx, query_segments in enumerate(queries):
                row = {'query_id': f'query_{query_idx + 1:04d}'}
                
                for partition in partitions:
                    pruned = 1 if is_partition_pruned(partition, query_segments) else 0
                    row[f'p{partition["partition_id"]}_pruned'] = pruned
                
                writer.writerow(row)
                
                if (query_idx + 1) % 100 == 0:
                    print(f"  Processed {query_idx + 1}/{len(queries)} queries...")
        
        print(f"\n✓ Matrix saved: {output_path}")
        print(f"  Shape: {len(queries)} queries × {len(partitions)} partitions")
        
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()
