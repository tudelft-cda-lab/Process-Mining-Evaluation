import os
from typing import Set, Optional, List

import numpy as np

from src.bpmn_model import BPMNModel, Graph
from src.main import load_model
from src.replay_state_based import StateCache, replay_trace


def get_submodel(model: BPMNModel, root: str, end: str, exclude: Set[str]):
    """
    Renders a subselection of the given model. The sub-model is defined by all nodes reachable from
    the root without passing the end node (included by default) or de excluded nodes (not included)
    :param model: base model
    :param root: internal ID of the root node
    :param end: internal ID of the end node, not ignored
    :param exclude: internal IDs of nodes to exclude from traversing
    :return: BPMNModel of the sub-model
    """
    subgraph_nodes = reachable_nodes(model.graph, root, end, exclude)

    res_nodes = {n: node for n, node in model.nodes.items() if n in subgraph_nodes}
    res_edges = [e for e in model.edges if e.src in subgraph_nodes and e.dst in subgraph_nodes]

    return BPMNModel(res_nodes, res_edges)


def reachable_nodes(graph: Graph, root: str, end: str, exclude: Set[str]) -> Set[str]:
    res = set()

    queue = [root]
    while len(queue) > 0:
        node = queue.pop(0)
        if node in res:
            continue
        res.add(node)
        if node == end:
            continue

        for child in graph[node]:
            if child not in res and child not in exclude:
                queue.append(child)
    return res


def is_activity_concurrent(model: BPMNModel, node: str) -> bool:
    if is_optional(model, node):
        parent = model.reverse_graph[node][0]
        child = model.graph[node][0]

        if len(model.reverse_graph[parent]) > 1 or len(model.graph[child]) > 1:
            return False
        parent_parent = model.reverse_graph[parent][0]
        child_child = model.graph[child][0]

        if parent_parent.startswith("and_split") and child_child.startswith("and_join"):
            # print(f"ActivityConcurrent: {model.reverse_label_mapping[node]}")
            return True
    elif is_opt_loop(model, node):
        # Child point to parent
        assert len(model.reverse_graph[node]) == 1
        assert len(model.graph[node]) == 1
        parent = model.reverse_graph[node][0]
        child = model.graph[node][0]
        if not parent.startswith("xor_join") or not child.startswith("xor_split"):
            return False

        # Get parent of parent and child of child
        p_parents = model.reverse_graph[parent]
        c_children = model.graph[child]

        if len(p_parents) != 2 or len(c_children) != 2:
            return False

        loop_start = p_parents[0] if p_parents[1] == child else p_parents[1]
        loop_end = c_children[0] if c_children[1] == parent else c_children[1]

        loop_parents = model.reverse_graph[loop_start]
        loop_children = model.graph[loop_end]

        if len(loop_parents) > 1 or len(loop_children) > 1:
            return False

        if loop_parents[0].startswith("and_split") and loop_children[0].startswith("and_join"):
            # print(f"ActivityConcurrent loop: {model.reverse_label_mapping[node]}")
            return True

    return False


def is_optional(model: BPMNModel, node: str) -> bool:
    if not node.startswith("task"):
        return False

    assert len(model.reverse_graph[node]) == 1
    assert len(model.graph[node]) == 1
    parent = model.reverse_graph[node][0]
    child = model.graph[node][0]
    if not parent.startswith("xor_split") or not child.startswith("xor_join"):
        return False

    return len(model.graph[parent]) >= 2 and child in model.graph[parent]


def is_opt_loop(model: BPMNModel, node: str) -> bool:
    if not node.startswith("task"):
        return False

    # Child point to parent
    assert len(model.reverse_graph[node]) == 1
    assert len(model.graph[node]) == 1
    parent = model.reverse_graph[node][0]
    child = model.graph[node][0]
    if not parent.startswith("xor_join") or not child.startswith("xor_split"):
        return False

    # Get parent of parent and child of child
    p_parents = model.reverse_graph[parent]
    c_children = model.graph[child]

    if len(p_parents) != 2 or len(c_children) != 2:
        return False

    loop_start = p_parents[0] if p_parents[1] == child else p_parents[1]
    loop_end = c_children[0] if c_children[1] == parent else c_children[1]

    # Verify the start points to the end
    if not loop_start.startswith("xor_split") or not loop_end.startswith("xor_join"):
        return False

    return loop_end in model.graph[loop_start]


def count_optional_tasks(model: BPMNModel):
    n_optional = 0
    n_opt_loop = 0
    n_concurrent = 0
    n_tasks = 0

    for n in model.nodes:
        if not n.startswith("task"):
            continue
        n_tasks += 1
        if is_optional(model, n):
            n_optional += 1
            # print(f"{n} is optional")
        elif is_opt_loop(model, n):
            n_opt_loop += 1
            # print(f"{n} is opt-loop")

        if is_activity_concurrent(model, n):
            n_concurrent += 1
    print(
        f"{n_tasks} tasks, found {n_optional} optional tasks, {n_opt_loop} loops, {n_concurrent} concurrent")

    get_allowed_stats(model)


def get_allowed_stats(model: BPMNModel):
    print(f"Can replay empty trace: {test_trace(model, [])}")

    # n_single_trace = 0
    # for label in model.label_mapping:
    #     if test_trace(model, [label]):
    #         n_single_trace += 1
    #
    # print(f"Got {n_single_trace} single-event traces")


def test_trace(model: BPMNModel, trace: List[str]) -> Optional[bool]:
    try:
        return replay_trace(model, (trace, 1), cache=StateCache())
    except:
        print("Test trace error")
        return None


def gateway_stats(model: BPMNModel):
    out_degrees = list(
        sorted([len(model.graph[x]) for x in model.nodes if x.startswith("xor_split")]))
    in_degrees = list(
        sorted([len(model.reverse_graph[x]) for x in model.nodes if x.startswith("xor_join")]))

    print(
        f"Out-degree: min {out_degrees[0]}, max: {out_degrees[-1]}, mean: {np.mean(out_degrees)}, mead: {np.median(out_degrees)}")
    print(
        f"In-degree: min {in_degrees[0]}, max: {in_degrees[-1]}, mean: {np.mean(in_degrees)}, mead: {np.median(in_degrees)}")


def dir_stats(directory: str):
    for filename in os.listdir(directory):
        # if filename.startswith("IMf_"):
        #     continue

        # if "IM_hybrid_bigram" in filename:
        #     continue
        model_file = f"{directory}/{filename}"
        model = load_model(model_file)
        model.flatten()
        print(filename)
        count_optional_tasks(model)
        # gateway_stats(model)


if __name__ == '__main__':
    """
    Code to render the sub-part of a model.
    """
    model_file = "models_bpmn/base/SM_cptc_18_reversed.bpmn"

    base_model = load_model(model_file)
    base_model.flatten()

    root = "node_start"
    end = "node_end"

    # exclude = {"xor_split_10", "xor_split_9"}
    exclude = set()
    nodes = reachable_nodes(base_model.graph, root, end, exclude)
    print(nodes)
    directory = "result_directory"
    filename = "result_file"

    new_model = get_submodel(base_model, root, end, exclude)
    new_model.save(filename, directory=directory, format="svg", simple=True, rank="TB")
    new_model.render(simple=True, rank="TB")
