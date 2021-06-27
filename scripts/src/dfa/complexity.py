import json
import os
from collections import defaultdict

from src.standalone.results_to_tex import align_results


def dfa_complexity(filename: str):
    with open(filename, "r") as f:
        machine = json.load(f)

    n_nodes = len(machine["nodes"])
    n_edges = len(machine["edges"])
    node_out_degree = defaultdict(int)

    for edge in machine["edges"]:
        src = edge["source"]
        node_out_degree[src] += 1

    cfc = 0
    for out_degree in node_out_degree.values():
        if out_degree >= 2:
            cfc += out_degree

    return f"{n_nodes} {n_edges} {cfc} {n_edges / n_nodes:.2f}"


if __name__ == '__main__':
    machines_root = "C:/Users/Geert/Desktop/sm_eval/machines"
    # machines_root = "C:/Users/Geert/Desktop/sm_eval/machines/"
    results = ["dataset machine nodes edges cfc cnc"]

    for dataset in os.listdir(machines_root):
        for machine_name in os.listdir(f"{machines_root}/{dataset}"):
            file = f"{machines_root}/{dataset}/{machine_name}/{dataset}.txt.ff.final.dot.json"
            # print(f"{dataset} - {machine_name}", end=" ")
            result = dfa_complexity(file)
            results.append(f"{dataset} {machine_name} {result}")

    align_results(results)
    # machine_file = "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/markov/cptc_18_reversed.txt.ff.final.dot.json"
    # dfa_complexity(machine_file)
