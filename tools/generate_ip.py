import random
import os
from pathlib import Path

def generate_ip():
    # 0 = pad with zeros, d = decimal integer
    return "{:03d}.{:03d}.{:03d}.{:03d}".format(
        random.randint(0,255),
        random.randint(0,255),
        random.randint(0,255),
        random.randint(0,255)
    )

# Create output directory
output_dir = Path(__file__).parent.parent / "app" / "src" / "main" / "resources" / "data" / "inputs" / "ip_addresses"
output_dir.mkdir(parents=True, exist_ok=True)

# BUILD PHASE DATA (1,000,000 addresses)
build_file = output_dir / "build_addresses.txt"
build_addresses = []
with open(build_file, "w") as f:
    for _ in range(1_000_000):
        ip = generate_ip()
        build_addresses.append(ip)
        f.write(ip + "\n")
print(f"Generated build dataset: {build_file}")

# QUERY PHASE DATA (1,000 addresses)
# 80% from build addresses, 20% new random addresses
query_file = output_dir / "query_addresses.txt"
num_queries = 1_000
num_from_build = int(num_queries * 0.8)  # 800 from build
num_new = num_queries - num_from_build   # 200 new

with open(query_file, "w") as f:
    # 80% from existing build addresses
    for _ in range(num_from_build):
        f.write(random.choice(build_addresses) + "\n")
    # 20% completely new addresses
    for _ in range(num_new):
        f.write(generate_ip() + "\n")

print(f"Generated query dataset: {query_file}")
print(f"  - {num_from_build} addresses from build ({100*num_from_build/num_queries:.0f}%)")
print(f"  - {num_new} new random addresses ({100*num_new/num_queries:.0f}%)")