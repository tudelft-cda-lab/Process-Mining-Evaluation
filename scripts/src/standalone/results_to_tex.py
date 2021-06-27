import json
import os
from typing import List
import re

import numpy as np


def get_results_dir(directory: str, metrics: [str]) -> List[str]:
    """
    Helper to extract results from the JSON files produce by the benchmark tool
    :param directory: Directory containing all JSON files. This directory is traversed recursively
    :param metrics: Fields to extract from the JSON files
    :return: List of all results
    """
    res = []
    for filename in os.listdir(directory):
        full_name = f"{directory}/{filename}"
        if os.path.isdir(full_name):
            res.extend(get_results_dir(full_name, metrics))
        elif filename.endswith("json"):
            res.extend(get_results(full_name, metrics))
        else:
            print(f"Unknown file type: {full_name}, skipping")
    return res


def get_results(filename: str, fields: List[str]) -> List[str]:
    with open(filename, "r") as file:
        data = json.load(file)
    res = []
    for (dataset_name, dataset_results) in data.items():
        # print(dataset_name)
        dataset_str = dataset_name.split(".")[0]
        for (miner, miner_results) in dataset_results.items():
            if re.search(r'-\d$', miner):
                continue
            # print(miner)

            field_values = map(lambda x: str(miner_results.get(x, "MISSING")), fields)
            field_values = map(lambda x: x.replace(",", "."), field_values)
            field_values = map(lambda x: f"{float(x):.2f}" if re.match(r'^\d\.\d+$', x) else x,
                               field_values)
            field_values = [dataset_str, miner] + list(field_values)
            print(" ".join(field_values))
            res.append(" ".join(field_values))
    # print(data)
    return res


def cross_val_valid(results_file: str):
    with open(results_file, "r") as file:
        data = json.load(file)
    res = []
    fields = ["debug-log-size", "debug-replay-size", "debug-replay-correct"]
    for (dataset_name, dataset_results) in data.items():
        # print(dataset_name)
        dataset_str = dataset_name.split(".")[0]
        for (miner, miner_results) in dataset_results.items():
            # print(miner)

            field_values = map(lambda x: str(miner_results.get(x, "MISSING")), fields)
            field_values = map(lambda x: x.replace(",", "."), field_values)
            field_values = map(lambda x: f"{float(x):.2f}" if re.match(r'^\d\.\d+$', x) else x,
                               field_values)
            field_values = [dataset_str, miner] + list(field_values)
            print(" ".join(field_values))
            res.append(" ".join(field_values))
    # print(data)
    return res


def cross_val_valid_dir(directory: str) -> List[str]:
    res = []
    for filename in os.listdir(directory):
        full_name = f"{directory}/{filename}"
        if os.path.isdir(full_name):
            res.extend(cross_val_valid_dir(full_name))
        elif filename.endswith("json"):
            res.extend(cross_val_valid(full_name))
        else:
            print(f"Unknown file type: {full_name}, skipping")
    return res


def align_results(values: List[str]):
    n_parts = len(values[0].split(" "))
    lengths = np.zeros(n_parts)

    values = sorted(values)

    parts = []
    for v in values:
        p = v.split(" ")
        parts.append(p)
        for i in range(n_parts):
            lengths[i] = max(lengths[i], len(p[i]))

    for part in parts:
        # map(lambda v, l: print(v, l), zip(parts, lengths))
        # new_parts = map(lambda v: v[0].ljust(v[1]), zip(parts, lengths))
        new_parts = [v.ljust(int(l)) if re.search(r"[a-z]", v) else v.rjust(int(l)) for (v, l) in
                     zip(part, lengths)]
        print(" & ".join(new_parts), end=" \\\\\n")
        # print(" "*(l - len(v)))


if __name__ == '__main__':
    results_base_dir = "path/to/results/json"

    metrics = [
        "base-soundness",
        "performance-fitness",
        "performance-total-conformance-frac",
        "performance-precision",
        "performance-f-score",
        "average 5-fold soundness",
        "average 5-fold fitness",
        "average 5-fold conformance",
        "average 5-fold precision",
        "average 5-fold f-score",
    ]

    # metrics = [
    #     "size-cfc",
    #     "size-cnc",
    #     "structuredness"
    # ]

    res = get_results_dir(results_base_dir, metrics)
    align_results(res)
