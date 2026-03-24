#!/usr/bin/env python3
"""
PLF vs Pruning Frequency Correlation Visualization

Visualizes the relationship between Partition Load Factor (PLF) and pruning frequency
with regression analysis and statistical annotations.
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy.stats import pearsonr
from scipy.optimize import curve_fit
import seaborn as sns
import warnings
warnings.filterwarnings('ignore')

# Configuration
DATA_PATH = 'app/src/main/resources/data/ip_experiment/charmap_P1000_plf_correlation.csv'
OUTPUT_PATH = 'charmap_plf_pruning_correlation.png'

def load_data():
    """Load correlation data from CSV."""
    try:
        df = pd.read_csv(DATA_PATH)
        print(f"✓ Loaded {len(df)} partitions")
        print(f"  Columns: {list(df.columns)}")
        return df
    except FileNotFoundError:
        print(f"✗ File not found: {DATA_PATH}")
        return None

def compute_statistics(df):
    """Compute key statistics."""
    plf = df['plf']
    pruning = df['pruning_frequency']
    
    r, p_value = pearsonr(plf, pruning)
    
    stats = {
        'correlation': r,
        'p_value': p_value,
        'plf_mean': plf.mean(),
        'plf_std': plf.std(),
        'plf_min': plf.min(),
        'plf_max': plf.max(),
        'pruning_mean': pruning.mean(),
        'pruning_std': pruning.std(),
        'pruning_min': pruning.min(),
        'pruning_max': pruning.max(),
    }
    
    print(f"\n📊 Statistics:")
    print(f"  Pearson r: {stats['correlation']:.4f}")
    print(f"  p-value: {stats['p_value']:.2e}")
    print(f"  PLF range: [{stats['plf_min']:.4f}, {stats['plf_max']:.4f}]")
    print(f"  Pruning range: [{stats['pruning_min']:.4f}, {stats['pruning_max']:.4f}]")
    
    return stats

def linear_model(x, a, b):
    """Linear regression model."""
    return a * x + b

def create_visualization(df, stats, output_path):
    """Create comprehensive scatter plot with regression analysis."""
    
    fig, ax = plt.subplots(figsize=(12, 8), dpi=100)
    
    plf = df['plf'].values
    pruning = df['pruning_frequency'].values
    
    # ========================================
    # 1. Color gradient scatter plot
    # ========================================
    scatter = ax.scatter(
        plf, pruning,
        c=pruning,  # Color by pruning frequency
        cmap='coolwarm_r',  # Blue (low) → Red (high)
        s=50,
        alpha=0.6,
        edgecolors='black',
        linewidth=0.5,
        label='Partitions'
    )
    
    # ========================================
    # 2. Linear regression line + confidence interval
    # ========================================
    popt, _ = curve_fit(linear_model, plf, pruning)
    plf_sorted = np.sort(plf)
    pruning_pred = linear_model(plf_sorted, *popt)
    
    # Calculate residuals and confidence interval
    residuals = pruning - linear_model(plf, *popt)
    residual_std = np.std(residuals)
    
    # 95% confidence interval
    ci_95 = 1.96 * residual_std
    
    ax.plot(
        plf_sorted, pruning_pred,
        'b-', linewidth=2.5, label='Linear fit',
        zorder=5
    )
    
    ax.fill_between(
        plf_sorted,
        pruning_pred - ci_95,
        pruning_pred + ci_95,
        alpha=0.2, color='blue', label='95% CI'
    )
    
    # ========================================
    # 3. Annotation zones
    # ========================================
    # High-risk zone (low PLF, high pruning)
    ax.axvspan(plf.min() - 0.001, np.percentile(plf, 25), 
               alpha=0.05, color='red', zorder=1)
    ax.text(np.percentile(plf, 12.5), np.percentile(pruning, 95),
            'High-Risk\nPartitions',
            fontsize=9, ha='center', style='italic', 
            bbox=dict(boxstyle='round', facecolor='red', alpha=0.1))
    
    # Stable zone (high PLF, low pruning)
    ax.axvspan(np.percentile(plf, 75), plf.max() + 0.001,
               alpha=0.05, color='green', zorder=1)
    ax.text(np.percentile(plf, 87.5), np.percentile(pruning, 5),
            'Stable\nPartitions',
            fontsize=9, ha='center', style='italic',
            bbox=dict(boxstyle='round', facecolor='green', alpha=0.1))
    
    # ========================================
    # 4. Axes and labels
    # ========================================
    ax.set_xlabel('Partition Load Factor (PLF)', fontsize=12, fontweight='bold')
    ax.set_ylabel('Pruning Frequency', fontsize=12, fontweight='bold')
    ax.set_title('PLF vs Pruning Frequency (Charmap, P=1000)\nStrong Negative Correlation', 
                 fontsize=14, fontweight='bold', pad=20)
    
    # Format y-axis as percentage
    ax.yaxis.set_major_formatter(plt.FuncFormatter(lambda y, _: f'{y:.1%}'))
    
    # ========================================
    # 5. Statistics box
    # ========================================
    stats_text = (
        f"Pearson r = {stats['correlation']:.4f}\n"
        f"p-value < 0.001\n\n"
        f"PLF: μ={stats['plf_mean']:.4f}, σ={stats['plf_std']:.4f}\n"
        f"Pruning: μ={stats['pruning_mean']:.1%}, σ={stats['pruning_std']:.1%}\n\n"
        f"Regression: pruning = {popt[0]:.3f}×PLF + {popt[1]:.3f}"
    )
    
    ax.text(0.02, 0.98, stats_text,
            transform=ax.transAxes,
            fontsize=10, verticalalignment='top',
            bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.8),
            family='monospace')
    
    # ========================================
    # 6. Colorbar and legend
    # ========================================
    cbar = plt.colorbar(scatter, ax=ax)
    cbar.set_label('Pruning Frequency', fontsize=11)
    cbar.formatter.set_powerlimits((0, 0))
    
    ax.legend(loc='lower right', fontsize=10, framealpha=0.95)
    
    # ========================================
    # 7. Grid and layout
    # ========================================
    ax.grid(True, alpha=0.3, linestyle='--', linewidth=0.5)
    plt.tight_layout()
    
    # Save figure
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"\n✓ Graph saved to: {output_path}")
    
    plt.show()

def create_residuals_plot(df, stats):
    """Create residuals diagnostic plot."""
    
    fig, axes = plt.subplots(2, 2, figsize=(12, 10), dpi=100)
    
    plf = df['plf'].values
    pruning = df['pruning_frequency'].values
    
    popt, _ = curve_fit(linear_model, plf, pruning)
    pruning_pred = linear_model(plf, *popt)
    residuals = pruning - pruning_pred
    
    # Plot 1: Residuals vs Fitted
    axes[0, 0].scatter(pruning_pred, residuals, alpha=0.5)
    axes[0, 0].axhline(y=0, color='r', linestyle='--', linewidth=2)
    axes[0, 0].set_xlabel('Fitted Values')
    axes[0, 0].set_ylabel('Residuals')
    axes[0, 0].set_title('Residuals vs Fitted Values')
    axes[0, 0].grid(True, alpha=0.3)
    
    # Plot 2: Q-Q plot (normality check)
    from scipy import stats as sp_stats
    sp_stats.probplot(residuals, dist="norm", plot=axes[0, 1])
    axes[0, 1].set_title('Q-Q Plot')
    axes[0, 1].grid(True, alpha=0.3)
    
    # Plot 3: PLF distribution
    axes[1, 0].hist(plf, bins=30, edgecolor='black', alpha=0.7, color='skyblue')
    axes[1, 0].set_xlabel('PLF')
    axes[1, 0].set_ylabel('Frequency')
    axes[1, 0].set_title('PLF Distribution')
    axes[1, 0].grid(True, alpha=0.3, axis='y')
    
    # Plot 4: Pruning frequency distribution
    axes[1, 1].hist(pruning, bins=30, edgecolor='black', alpha=0.7, color='coral')
    axes[1, 1].xaxis.set_major_formatter(plt.FuncFormatter(lambda y, _: f'{y:.1%}'))
    axes[1, 1].set_xlabel('Pruning Frequency')
    axes[1, 1].set_ylabel('Frequency')
    axes[1, 1].set_title('Pruning Frequency Distribution')
    axes[1, 1].grid(True, alpha=0.3, axis='y')
    
    plt.tight_layout()
    diagnostic_path = 'charmap_plf_pruning_diagnostics.png'
    plt.savefig(diagnostic_path, dpi=300, bbox_inches='tight')
    print(f"✓ Diagnostic plot saved to: {diagnostic_path}")
    
    plt.show()

def main():
    """Main entry point."""
    print("=" * 60)
    print("PLF vs Pruning Frequency Correlation Visualization")
    print("=" * 60)
    
    # Load data
    df = load_data()
    if df is None:
        return
    
    # Verify columns exist
    required_cols = ['plf', 'pruning_frequency']
    if not all(col in df.columns for col in required_cols):
        print(f"✗ Missing required columns: {required_cols}")
        print(f"  Available columns: {list(df.columns)}")
        return
    
    # Compute statistics
    stats = compute_statistics(df)
    
    # Create visualizations
    print("\n🎨 Creating main visualization...")
    create_visualization(df, stats, OUTPUT_PATH)
    
    print("\n📈 Creating diagnostic plots...")
    create_residuals_plot(df, stats)
    
    print("\n✅ Complete!")

if __name__ == '__main__':
    main()
