import argparse as ap
import matplotlib.pyplot as plt
import numpy as np
import os
import re  # regular expressions
from typing import List, Tuple, Optional


LINE_PREAMBLE = "after cycle="
LINE_UTILITY_STR = "avg(utility)="
LINE_WINS_STR = "avg(num_wins)="

def load(path: str) -> np.ndarray:
    data: List[Tuple[float, float, float]] = list()

    try:
        with open(path, "r") as f:
            for line in f:
                if LINE_PREAMBLE in line.strip().rstrip() and LINE_UTILITY_STR in line.strip().rstrip()\
                   and LINE_WINS_STR in line.strip().rstrip():
                    values_str = line.strip().rstrip().replace(LINE_PREAMBLE, "").replace(LINE_UTILITY_STR, "")\
                        .replace(LINE_WINS_STR, "")
                    phase_idx, avg_utility, avg_wins = re.sub(r'\s+', ' ', values_str.strip().rstrip()).strip()\
                        .rstrip().split(" ")
                    data.append([float(phase_idx), float(avg_utility), float(avg_wins)])
    except:
        pass

    return np.array(data)


def main() -> None:
    parser = ap.ArgumentParser()
    # Default to training.log so you can run without args
    parser.add_argument("logfile", nargs="?", default="training.log", type=str,
                        help="path to logfile containing eval outputs (default: training.log)")
    parser.add_argument("--eval-games", "-e", type=int, default=None,
                        help="number of evaluation games per cycle (if provided, plots win rate = avg(num_wins)/eval_games).")
    args = parser.parse_args()

    if not os.path.exists(args.logfile):
        raise Exception("ERROR: logfile [%s] does not exist!" % args.logfile)

    data: np.ndarray = load(args.logfile)
    if data.size == 0:
        raise Exception("ERROR: no parsable lines found in logfile. Ensure lines contain 'after cycle=', 'avg(utility)=', and 'avg(num_wins)='.")

    cycles = data[:, 0]
    avg_utility = data[:, 1]
    avg_wins = data[:, 2]

    # Compute win rate if eval-games provided; otherwise plot raw avg wins
    win_series: np.ndarray
    win_label: str
    if isinstance(args.eval_games, int) and args.eval_games and args.eval_games > 0:
        win_series = avg_wins / float(args.eval_games)
        win_label = "avg win rate"
    else:
        win_series = avg_wins
        win_label = "avg wins"

    fig, ax1 = plt.subplots(figsize=(10, 5))
    color1 = "tab:blue"
    ax1.set_xlabel("cycle")
    ax1.set_ylabel("avg utility", color=color1)
    ax1.plot(cycles, avg_utility, color=color1, label="avg utility")
    ax1.tick_params(axis='y', labelcolor=color1)

    ax2 = ax1.twinx()
    # color2 = "tab:invisible"
    color2 = "none"
    ax2.set_ylabel(win_label, color=color2)
    ax2.plot(cycles, win_series, color=color2, label=win_label)
    ax2.tick_params(axis='y', labelcolor=color2)

    # Compose a single legend
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc="best")
    ax1.grid(True, linestyle='--', alpha=0.4)
    plt.title("Learning Curve: Utility and Wins")
    fig.tight_layout()
    plt.show()


if __name__ == "__main__":
    main()

