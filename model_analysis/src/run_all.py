import os
from typing import List

import numpy as np

from src.main import load_model
from src.replay_state_based import replay_traces, StateCache
from src.traces import read_traces, get_unique_traces


def replay_all():
    combinations = [
        ("data/traces/cptc_17_chronological.txt", [
            "inductive_rerun/models/IM_cptc_17_chronological.bpmn",
            "inductive_rerun/models/IMf-05_cptc_17_chronological.bpmn",
            "inductive_rerun/models/IMf-10_cptc_17_chronological.bpmn",
            "inductive_rerun/models/IMf-15_cptc_17_chronological.bpmn",
            "inductive_rerun/models/IMf-20_cptc_17_chronological.bpmn",
        ]),
        ("data/traces/cptc_17_reversed.txt", [
            "inductive_rerun/models/IM_cptc_17_reversed.bpmn",
            "inductive_rerun/models/IMf-05_cptc_17_reversed.bpmn",
            "inductive_rerun/models/IMf-10_cptc_17_reversed.bpmn",
            "inductive_rerun/models/IMf-15_cptc_17_reversed.bpmn",
            "inductive_rerun/models/IMf-20_cptc_17_reversed.bpmn",
        ]),
        ("data/traces/cptc_18_chronological.txt", [
            "inductive_rerun/models/IM_cptc_18_chronological.bpmn",
            "inductive_rerun/models/IMf-05_cptc_18_chronological.bpmn",
            "inductive_rerun/models/IMf-10_cptc_18_chronological.bpmn",
            "inductive_rerun/models/IMf-15_cptc_18_chronological.bpmn",
            "inductive_rerun/models/IMf-20_cptc_18_chronological.bpmn",
        ]),
        ("data/traces/cptc_18_reversed.txt", [
            "inductive_rerun/models/IM_cptc_18_reversed.bpmn",
            "inductive_rerun/models/IMf-05_cptc_18_reversed.bpmn",
            "inductive_rerun/models/IMf-10_cptc_18_reversed.bpmn",
            "inductive_rerun/models/IMf-15_cptc_18_reversed.bpmn",
            "inductive_rerun/models/IMf-20_cptc_18_reversed.bpmn",
        ]),
    ]

    for trace_file, model_files in combinations:
        for model_file in model_files:
            print(f"{trace_file}: {model_file}")
            traces = read_traces(trace_file)
            unique_traces = get_unique_traces(traces)

            model = load_model(model_file)
            model.flatten()

            replay_traces(model, unique_traces, cache=StateCache())
            model_name = model_file.split("/")[-1][:-5]
            model.save(model_name, directory="replayed_models", format="svg", view=False,
                       lighten_threshold=-1)


def replay_eval_traces():
    combinations = [
        ("data/traces/eval_traces/18_rev_insert_duplicate.txt",
         "models_bpmn/duplicated/IM_18_rev_insert_duplicate.bpmn"),
        ("data/traces/eval_traces/18_rev_insert_extend.txt",
         "models_bpmn/duplicated/IM_18_rev_insert_extend.bpmn"),
    ]

    for trace_file, model_file in combinations:
        print(f"{trace_file}: {model_file}")
        traces = read_traces(trace_file)
        unique_traces = get_unique_traces(traces)

        model = load_model(model_file)
        model.flatten()

        replay_traces(model, unique_traces, cache=StateCache())
        model_name = model_file.split("/")[-1][:-5]
        model.save(model_name, directory="model_inserted_replay", format="svg", view=False,
                   lighten_threshold=-1)


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
        new_parts = [v.ljust(int(l)) for (v, l) in zip(part, lengths)]
        print(" & ".join(new_parts), end=" \\\\\n")


def model_complexity():
    # models = [
    #     "models_bpmn/base/IM_cptc_17_chronological.bpmn",
    #     "models_bpmn/base/IMf_cptc_17_chronological.bpmn",
    #     # "models_bpmn/base/SM_cptc_17_chronological.bpmn",
    #     "models_bpmn/base/IM_cptc_17_reversed.bpmn",
    #     "models_bpmn/base/IMf_cptc_17_reversed.bpmn",
    #     "models_bpmn/base/SM_cptc_17_reversed.bpmn",
    #     "models_bpmn/base/IM_cptc_18_chronological.bpmn",
    #     "models_bpmn/base/IMf_cptc_18_chronological.bpmn",
    #     "models_bpmn/base/SM_cptc_18_chronological.bpmn",
    #     "models_bpmn/base/IM_cptc_18_reversed.bpmn",
    #     "models_bpmn/base/IMf_cptc_18_reversed.bpmn",
    #     "models_bpmn/base/SM_cptc_18_reversed.bpmn",
    # ]
    models = []
    base_dir = "inductive_rerun/models"
    for filename in os.listdir(base_dir):
        models.append(f"{base_dir}/{filename}")

    res = []

    for model_file in models:
        print(model_file)
        model = load_model(model_file)
        model.flatten()
        model.validate_degree()

        n_nodes = len(model.nodes)
        n_edges = len(model.edges)

        n_tasks = len([n for n in model.nodes if n.startswith("task")])
        n_xor_splits = len([n for n in model.nodes if n.startswith("xor_split")])
        n_xor_joins = len([n for n in model.nodes if n.startswith("xor_join")])
        n_and_splits = len([n for n in model.nodes if n.startswith("and_split")])
        n_and_joins = len([n for n in model.nodes if n.startswith("and_join")])

        cnc = n_edges / n_nodes

        xor_splits = [n for n in model.nodes if n.startswith("xor_split")]
        and_splits = [n for n in model.nodes if n.startswith("and_split")]
        cfc = len(and_splits)
        for s in xor_splits:
            cfc += len(model.graph[s])

        res.append(
            f"{model_file.split('/')[-1]} {n_nodes} {n_edges} {n_tasks} {n_xor_splits} {n_xor_joins} {n_and_splits} {n_and_joins} {cfc} {cnc:.2f}")

    align_results(res)
    #
    # for r in res:
    #     print(r)


if __name__ == '__main__':
    # replay_all()
    # replay_eval_traces()
    model_complexity()
