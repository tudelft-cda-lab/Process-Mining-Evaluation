import json
import os
from pathlib import Path

from src.filters.read_write import *


def replay_dfa(machine_file: str, trace_file: str) -> Traces:
    with open(machine_file, "r") as f:
        machine = json.load(f)

    node_mapping = {}
    node_incoming_labels = {}
    sinks = set()
    for n in machine["nodes"]:
        node_mapping[n["id"]] = {}
        if n["issink"] == 1:
            sinks.add(n["id"])
            node_incoming_labels[n["id"]] = f"sink"
        else:
            node_incoming_labels[n["id"]] = "unknown"


    for edge in machine["edges"]:
        src = int(edge["source"])
        dst = int(edge["target"])
        node_mapping[src][edge["name"]] = dst
        if dst in node_incoming_labels:
            current_label = node_incoming_labels[dst]
            if current_label != "unknown" and current_label != edge["name"]:
                if dst not in sinks:
                    print(f"ERR: wrong label, expected {current_label}, got {edge['name']}")
            else:
                node_incoming_labels[dst] = edge["name"]

    new_traces = []
    no_fit = 0
    traces = read_traces(trace_file)
    for i, trace in enumerate(traces):
        new_trace = []
        current_state = 0
        fits = True
        for event in trace:
            if event in node_mapping[current_state]:
                new_state = node_mapping[current_state][event]
                new_trace.append(f"{node_incoming_labels[new_state]}_{new_state}")
                current_state = new_state
            else:
                if current_state in sinks:
                    print(f"{i} don't fit, end in sink: {trace}")
                    break
                else:
                    fits = False
                    print(f"{i} don't fit, NO SINK {trace}")
                    new_trace.append("ERR")
        if not fits:
            no_fit += 1
            print(f"{new_trace}")
        new_traces.append(new_trace)
    print(f"{no_fit} out of {len(traces)} traces don't fit")
    return new_traces


def replay_dir(dataset_root: str, machine_root: str, results_root: str):
    for dataset_name in os.listdir(machine_root):
        if dataset_name != "cptc_18_reversed":
            continue
        dataset_machines = f"{machine_root}/{dataset_name}"
        for machine_name in os.listdir(dataset_machines):
            machine_dir = f"{dataset_machines}/{machine_name}"

            train_replay_dir = f"{results_root}/{dataset_name}/{machine_name}/train"
            eval_replay_dir = f"{results_root}/{dataset_name}/{machine_name}/eval"

            Path(train_replay_dir).mkdir(parents=True, exist_ok=True)
            Path(eval_replay_dir).mkdir(parents=True, exist_ok=True)
            for machine in os.listdir(machine_dir):
                dataset_file_name = machine.split(".")[0]
                # print(f"{dataset_name} - {machine_name} - {machine} - {dataset_file_name}")
                machine_file = f"{machine_dir}/{machine}"
                train_file = f"{dataset_root}/{dataset_name}/train/{dataset_file_name}.txt"
                eval_file = f"{dataset_root}/{dataset_name}/eval/{dataset_file_name}.txt"

                train_replay_traces = replay_dfa(machine_file, train_file)
                eval_replay_traces = replay_dfa(machine_file, eval_file)

                write_traces(f"{train_replay_dir}/{dataset_file_name}.txt", train_replay_traces)
                write_traces(f"{eval_replay_dir}/{dataset_file_name}.txt", eval_replay_traces)


if __name__ == '__main__':
    dataset = "C:/Users/Geert/Desktop/sm_eval/datasets"
    machines = "C:/Users/Geert/Desktop/sm_eval/machines"
    dataset_replay = "C:/Users/Geert/Desktop/sm_eval/datasets_replay_named"

    replay_dir(dataset, machines, dataset_replay)
    # model_file = "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/markov/cptc_18_reversed_0.txt.ff.final.dot.json"
    # trace_file = "C:/Users/Geert/Desktop/sm_eval/datasets/cptc_18_reversed/eval/cptc_18_reversed_0.txt"
    #
    # for t in replay_dfa(model_file, trace_file):
    #     print(" ".join(t))
