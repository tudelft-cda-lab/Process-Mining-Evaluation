import json

import graphviz

stage_color_mapping = {
    # Low severity
    "tarID": "white",
    "surf": "white",
    "hostD": "white",
    "serD": "white",
    "vulnD": "white",
    "infoD": "white",

    # Medium severity
    "uPrivEsc": "dodgerblue",
    "rPrivEsc": "dodgerblue",
    "netSniff": "dodgerblue",
    "bfCred": "dodgerblue",
    "acctManip": "dodgerblue",
    "TOexp": "dodgerblue",
    "PAexp": "dodgerblue",
    "remoteexp": "dodgerblue",
    "sPhish": "dodgerblue",
    "servS": "dodgerblue",
    "evasion": "dodgerblue",
    "CnC": "dodgerblue",
    "lateral": "dodgerblue",
    "ACE": "dodgerblue",
    "privEsc": "dodgerblue",

    # High severity
    "endDOS": "orangered",
    "netDOS": "orangered",
    "serStop": "orangered",
    "resHJ": "orangered",
    "dDestruct": "orangered",
    "cWipe": "orangered",
    "dEncrypt": "orangered",
    "deface": "orangered",
    "dManip": "orangered",
    "exfil": "orangered",
    "delivery": "orangered",
    "phish": "orangered",

    # Unknown
    "benign": "pink",
}


def label_color(label: str):
    parts = label.split("|")
    if len(parts) != 2:
        return "white"
    return stage_color_mapping.get(parts[0], "white")


def edge_color(label: str):
    parts = label.split("|")
    if len(parts) != 2:
        return "black"
    res = stage_color_mapping.get(parts[0], "white")
    if res == "white":
        return "black"
    return res


def show_json(filename: str, out_file):
    with open(filename, "r") as f:
        machine = json.load(f)

    node_incoming_labels = {
        0: "unknown_label_so_white"
    }
    sinks = set()
    for node in machine["nodes"]:
        if node["issink"] == 1:
            sinks.add(node["id"])

    for edge in machine["edges"]:
        dst = int(edge["target"])

        label = edge["name"]
        if dst in sinks:
            node_incoming_labels[dst] = "sink_unknown_so_white"
            continue
        if dst not in node_incoming_labels:
            node_incoming_labels[dst] = label
        elif node_incoming_labels[dst] != label:
            print(f"Unequal label, expected {node_incoming_labels[dst]}, got {label}")

    res = "digraph {\n"
    res += "\trank=TB;\n"

    nodes = set()

    for node in machine["nodes"]:
        if node["size"] < 5 or node["id"] in sinks:
            continue
        nodes.add(node["id"])
        if node["id"] in sinks:
            res += f'\t{node["id"]} [label="{node["id"]}\n{node["label"]}",style=filled,fillcolor=yellow];\n'
        else:
            res += f'\t{node["id"]} [label="{node["id"]}\n{node["label"]}",style=filled,fillcolor={label_color(node_incoming_labels[node["id"]])}];\n'

    rendered_edges = set()

    for edge in machine["edges"]:
        source = int(edge["source"])
        target = int(edge["target"])
        if source not in nodes or target not in nodes:
            continue

        if int(edge["appearances"]) < 5:
            continue

        if source == target and source in sinks and (source, target) in rendered_edges:
            continue
        rendered_edges.add((source, target))

        res += f'\t{edge["source"]} -> {edge["target"]} [label="{edge["name"]}\n{edge["appearances"]}",color={edge_color(edge["name"])},fontcolor={edge_color(edge["name"])}];\n'
    res += "}"

    # print(res)

    s = graphviz.Source(res)
    s.render(view=True, filename=out_file, directory="renders", format="svg")
    s.render(view=False, filename=out_file, directory="renders", format="eps")


if __name__ == '__main__':
    machines = [
        # (
        # "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/convert_sink/cptc_18_reversed.txt.ff.final.dot.json",
        # "convert_sink"),
        # (
        # "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/no_sink_merge/cptc_18_reversed.txt.ff.final.dot.json",
        # "no_sink_merge"),
        (
        "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/original/cptc_18_reversed.txt.ff.final.dot.json",
        "original_filtered"),
        # (
        # "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/search/cptc_18_reversed.txt.ff.final.dot.json",
        # "search"),
        # (
        # "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/markov/cptc_18_reversed.txt.ff.final.dot.json",
        # "markov"),
        # (
        # "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/bigram/cptc_18_reversed.txt.ff.final.dot.json",
        # "bigram"),
    ]
    for machine_name, out_file in machines:
        print(machine_name)
        show_json(machine_name, out_file)
