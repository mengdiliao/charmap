#!/usr/bin/env python3
"""
Create a grouped bar chart for fixed-P experiments varying LMAX.

X-axis: LMAX
Y-axis: average pruned percentage = (pruned_partitions / total_partitions) * 100
Error bar: standard deviation of per-query pruned percentages

Per LMAX value, the script plots 4 bars in this order:
- ip_minmax
- ip_charmap
- name_minmax
- name_charmap

Expected inputs under --query-dir:
- ip_minmax/ip_minmax_P{P}_LMAX{LMAX}_query.txt
- ip_charmap/ip_charmap_P{P}_LMAX{LMAX}_query.txt
- name_minmax/name_minmax_P{P}_LMAX{LMAX}_query.txt
- name_charmap/name_charmap_P{P}_LMAX{LMAX}_query.txt
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path
from typing import Iterable

import matplotlib.pyplot as plt


SERIES_ORDER = ["ip_minmax", "ip_charmap", "name_minmax", "name_charmap"]

SERIES_STYLE = {
    "ip_minmax": "#4C78A8",
    "ip_charmap": "#F58518",
    "name_minmax": "#54A24B",
    "name_charmap": "#B279A2",
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

        # Metadata lines contain '='. Data rows are plain integers.
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


def collect_series_data(
    query_dir: Path, partition_size: int, lmax_values: list[int]
) -> tuple[dict[str, list[float]], dict[str, list[float]]]:
    means: dict[str, list[float]] = {k: [] for k in SERIES_ORDER}
    errors: dict[str, list[float]] = {k: [] for k in SERIES_ORDER}

    for lmax in lmax_values:
        for series in SERIES_ORDER:
            filename = f"{series}_P{partition_size}_LMAX{lmax}_query.txt"
            file_path = query_dir / series / filename

            partitions, pruned_counts = parse_query_result_file(file_path)
            percentages = [(count / partitions) * 100.0 for count in pruned_counts]

            means[series].append(mean(percentages))
            errors[series].append(stddev(percentages))

    return means, errors


def plot_chart(
    means: dict[str, list[float]],
    errors: dict[str, list[float]],
    lmax_values: list[int],
    output_path: Path,
    title: str,
) -> None:
    plt.style.use("seaborn-v0_8-whitegrid")
    fig, ax = plt.subplots(figsize=(11, 6.5))

    x = list(range(len(lmax_values)))
    labels = [str(v) for v in lmax_values]

    bar_width = 0.2
    offsets = [-1.5 * bar_width, -0.5 * bar_width, 0.5 * bar_width, 1.5 * bar_width]

    for idx, series in enumerate(SERIES_ORDER):
        xpos = [xi + offsets[idx] for xi in x]
        bars = ax.bar(
            xpos,
            means[series],
            yerr=errors[series],
            width=bar_width,
            capsize=4,
            error_kw={"elinewidth": 0.25, "capthick": 0.25},
            color=SERIES_STYLE[series],
            alpha=0.9,
            label=series,
            edgecolor="black",
            linewidth=0.4,
        )
        for bar, value in zip(bars, means[series]):
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
    ax.set_xlabel("LMAX")
    ax.set_ylabel("Pruning Percentage (%)")
    ax.set_title(title)
    ax.legend(title="Series", loc="upper left", bbox_to_anchor=(1.01, 1.0), borderaxespad=0.0)

    fig.tight_layout(rect=(0, 0, 0.82, 1))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=220)
    plt.close(fig)


def parse_lmax_values(raw: str) -> list[int]:
    values = []
    for token in raw.split(","):
        token = token.strip()
        if not token:
            continue
        values.append(int(token))
    if not values:
        raise ValueError("--lmax-values must contain at least one integer")
    return values


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Plot grouped pruning-percentage bars for fixed P while varying LMAX."
    )
    parser.add_argument(
        "--query-dir",
        type=Path,
        required=True,
        help="Directory containing ip_minmax/, ip_charmap/, name_minmax/, name_charmap/ query outputs for one P group",
    )
    parser.add_argument(
        "--p",
        type=int,
        required=True,
        help="Fixed partition size P used in this experiment group",
    )
    parser.add_argument(
        "--lmax-values",
        type=str,
        default="3,5,8,12",
        help="Comma-separated LMAX values (e.g. 3,5,8,12)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Output image path (.png recommended)",
    )
    parser.add_argument(
        "--title",
        type=str,
        default=None,
        help="Chart title (optional)",
    )
    args = parser.parse_args()

    lmax_values = parse_lmax_values(args.lmax_values)
    title = args.title or f"Pruning Percentage by LMAX (P={args.p})"

    means, errors = collect_series_data(args.query_dir, args.p, lmax_values)
    plot_chart(means, errors, lmax_values, args.output, title)

    print(f"Query dir:      {args.query_dir.resolve()}")
    print(f"Fixed P:        {args.p}")
    print(f"LMAX values:    {lmax_values}")
    print(f"Output chart:   {args.output.resolve()}")
    print("Error bars:     sample standard deviation of per-query pruned percentages")


if __name__ == "__main__":
    main()
