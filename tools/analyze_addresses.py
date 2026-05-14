from pathlib import Path
from collections import Counter

# Paths to the data files
data_dir = Path(__file__).parent.parent / "app" / "src" / "main" / "resources" / "data" / "inputs" / "ip_addresses"
build_file = data_dir / "build_addresses.txt"
query_file = data_dir / "query_addresses.txt"

# Read build addresses
print("Reading build_addresses.txt...")
with open(build_file, "r") as f:
    build_addresses = [line.strip() for line in f]

# Count occurrences of each address in build
build_counter = Counter(build_addresses)

# 1. Count addresses that occur more than once
duplicates = {addr: count for addr, count in build_counter.items() if count > 1}
num_duplicates = len(duplicates)
total_duplicate_occurrences = sum(duplicates.values())

print(f"\n1. DUPLICATE ADDRESSES IN BUILD:")
print(f"   - Number of unique addresses that appear more than once: {num_duplicates}")
print(f"   - Total occurrences of duplicated addresses: {total_duplicate_occurrences}")
if num_duplicates > 0 and num_duplicates <= 10:
    print(f"   - Examples: {list(duplicates.items())[:10]}")

# Read query addresses
print("\nReading query_addresses.txt...")
with open(query_file, "r") as f:
    query_addresses = [line.strip() for line in f]

# 2. Count how many query addresses are found in build
build_set = set(build_addresses)
found_in_build = sum(1 for addr in query_addresses if addr in build_set)
not_found_in_build = len(query_addresses) - found_in_build

print(f"\n2. QUERY ADDRESSES FOUND IN BUILD:")
print(f"   - Total query addresses: {len(query_addresses)}")
print(f"   - Found in build_addresses: {found_in_build} ({100*found_in_build/len(query_addresses):.2f}%)")
print(f"   - NOT found in build_addresses: {not_found_in_build} ({100*not_found_in_build/len(query_addresses):.2f}%)")
