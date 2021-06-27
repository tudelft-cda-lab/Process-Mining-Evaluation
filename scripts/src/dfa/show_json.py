import json

import graphviz

from src.dfa.complexity import dfa_complexity


def show_json(filename: str, out_file: str):
    with open(filename, "r") as f:
        machine = json.load(f)

    res = "digraph {\n"
    res += "\trank=TB;\n"

    for node in machine["nodes"]:
        res += f'\t{node["id"]} [label="{node["id"]}\n{node["label"]}"];\n'

    for edge in machine["edges"]:
        res += f'\t{edge["source"]} -> {edge["target"]} [label="{edge["name"]}\n{edge["appearances"]}"];\n'
    res += "}"

    # print(res)

    s = graphviz.Source(res)
    # s.render(view=False, filename=out_file, directory="renders", format="svg")
    s.render(view=False, filename=out_file, directory="renders", format="eps")


if __name__ == '__main__':
    machines = [
        (
        "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/bigram/cptc_18_reversed.txt.ff.final.dot.json",
        "bigram_uncolored"),
        (
        "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/convert_sink/cptc_18_reversed.txt.ff.final.dot.json",
        "convert_sink_uncolored"),
        (
        "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/markov/cptc_18_reversed.txt.ff.final.dot.json",
        "markov_uncolored"),
        (
        "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/no_sink_merge/cptc_18_reversed.txt.ff.final.dot.json",
        "no_sink_merge_uncolored"),
        (
        "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/original/cptc_18_reversed.txt.ff.final.dot.json",
        "original_uncolored"),
        (
        "C:/Users/Geert/Desktop/sm_eval/machines/cptc_18_reversed/search/cptc_18_reversed.txt.ff.final.dot.json",
        "search_uncolored"),
    ]
    for machine_name, out_file in machines:
        print(machine_name)
        show_json(machine_name, out_file)
