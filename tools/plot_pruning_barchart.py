#!/usr/bin/env python3
"""
Create a grouped bar chart for pruning performance across partition sizes.

X-axis: partition size P
Y-axis: average pruned percentage = (pruned_partitions / total_partitions) * 100
Error bar: standard deviation of per-query pruned percentages

Per partition size, the script plots 4 bars:
- ip_addresses
- sorted_ip_addresses
- names
- sorted_names

Expected inputs:
- app/src/main/resources/data/results/query/ip_charmap/*_query.txt
- app/src/main/resources/data/results/query/name_charmap/*_query.txt
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path
from typing import Iterable

import matplotlib.pyplot as plt


PARTITION_SIZES = [10, 50, 100, 200, 500]

SERIES_FILES = {
    "ip_addresses": "ip_charmap_P{p}_query.txt",
    "sorted_ip_addresses": "sorted_ip_charmap_P{p}_query.txt",
    "names": "name_charmap_P{p}_query.txt",
    "sorted_names": "sorted_name_charmap_P{p}_query.txt",
}

SERIES_STYLE = {
    "ip_addresses": "#1f77b4",
    "sorted_ip_addresses": "#ff7f0e",
    "names": "#2ca02c",
    "sorted_names": "#d62728",
}


def parse_query_result_file(path: Path) -> tuple[int, list[int]]:
    """Return (partitions, pruned_counts_per_query)."""
    if not path.exists():
        raise FileNotFoundError(f"Missing query result file: {path}")

    partitions = None
    counts: list[int] = []

    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue

        if line.startswith("PARTITIONS="):
            partitions = int(line.split("=", 1)[1])
            continue

        # Metadata lines have '='. Data rows are plain integers.
        if "=" in line:
            continue

        counts.append(int(line))

    if partitions is None:
        raise ValueError(f"PARTITIONS metadata not found in: {path}")
    if not counts:
        raise ValueError(f"No query data rows found in: {path}")

    return partitions, counts


def mean(values: Iterable[float]) -> float:
    seq = list(values)
    return sum(seq) / len(seq)


def stddev(values: Iterable[float]) -> float:
    seq = list(values)
    if len(seq) < 2:
        return 0.0
    mu = mean(seq)
    var = sum((x - mu) ** 2 for x in seq) / (len(seq) - 1)
    return math.sqrt(var)


def collect_series_data(base_query_dir: Path) -> tuple[dict[str, list[float]], dict[str, list[float]]]:
    means: dict[str, list[float]] = {k: [] for k in SERIES_FILES}
    errors: dict[str, list[float]] = {k: [] for k in SERIES_FILES}

    ip_dir = base_query_dir / "ip_charmap"
    name_dir = base_query_dir / "name_charmap"

    for p in PARTITION_SIZES:
        for series, pattern in SERIES_FILES.items():
            filename = pattern.format(p=p)
            if "name" in series:
                file_path = name_dir / filename
            else:
                file_path = ip_dir / filename

            partitions, pruned_counts = parse_query_result_file(file_path)
            percentages = [(count / partitions) * 100.0 for count in pruned_counts]

            means[series].append(mean(percentages))
            errors[series].append(stddev(percentages))

    return means, errors


def plot_chart(
    means: dict[str, list[float]],
    errors: dict[str, list[float]],
    output_path: Path,
    title: str,
) -> None:
    plt.style.use("seaborn-v0_8-whitegrid")
    fig, ax = plt.subplots(figsize=(11, 6.5))

    x = list(range(len(PARTITION_SIZES)))
    labels = [str(p) for p in PARTITION_SIZES]

    series_order = ["ip_addresses", "sorted_ip_addresses", "names", "sorted_names"]
    bar_width = 0.2
    offsets = [-1.5 * bar_width, -0.5 * bar_width, 0.5 * bar_width, 1.5 * bar_width]

    for idx, series in enumerate(series_order):
        xpos = [xi + offsets[idx] for xi in x]
        bars = ax.bar(
            xpos,
            means[series],
            yerr=errors[series],
            width=bar_width,
            capsize=4,
            color=SERIES_STYLE[series],
            alpha=0.9,
            label=series,
            edgecolor="black",
            linewidth=0.4,
        )
        for bar, value in zip(bars, means[series]):
            # Anchor labels to bar tops (mean values), not to error-bar caps.
            ax.text(
                bar.get_x() + bar.get_width() / 2,
                bar.get_height() + 1.0,
                f"{value:.1f}",
                ha="center",
                va="bottom",
                fontsize=8,
            )

    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.tick_params(axis="x", pad=1)
    ax.set_xlabel("Partition Size (P)")
    ax.set_ylabel("Average Pruned Partitions (%)")
    ax.set_title(title)
    ax.legend(title="Dataset", loc="upper left", bbox_to_anchor=(1.01, 1.0), borderaxespad=0.0)

    fig.tight_layout(rect=(0, 0, 0.82, 1))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=220)
    plt.close(fig)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Plot grouped pruning-percentage bars with variability error bars."
    )
    parser.add_argument(
        "--query-dir",
        type=Path,
        default=Path("app/src/main/resources/data/results/query"),
        help="Base directory containing ip_charmap/ and name_charmap/ query files",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/resources/data/results/query/pruning_summary_bar_chart.png"),
        help="Output image path (.png recommended)",
    )
    parser.add_argument(
        "--title",
        type=str,
        default="Charmap Pruning by Partition Size",
        help="Chart title",
    )
    args = parser.parse_args()

    means, errors = collect_series_data(args.query_dir)
    plot_chart(means, errors, args.output, args.title)

    print(f"Query base dir: {args.query_dir.resolve()}")
    print(f"Output chart:   {args.output.resolve()}")
    print("Error bars:     sample standard deviation of per-query pruned percentages")


if __name__ == "__main__":
    main()
