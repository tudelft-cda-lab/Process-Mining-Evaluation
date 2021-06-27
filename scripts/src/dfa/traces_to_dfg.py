import graphviz

from src.filters.read_write import read_traces


def traces_to_dfg(trace_file: str):
    traces = read_traces(trace_file)

    graph = {
        0: set()
    }
    label_mapping = {
        "start": 0,
        "end": 1,
    }

    for trace in traces:
        current_event_id = 0
        for event in trace:
            if event not in label_mapping:
                label_mapping[event] = len(label_mapping)
                graph[label_mapping[event]] = set()
            event_id = label_mapping[event]
            graph[current_event_id].add(event_id)
            current_event_id = event_id
        graph[current_event_id].add(1)

    res = "digraph {\n"
    res += '\trankdir="LR";\n'
    res += '\tnode [ fontsize=20];\n'
    for event, event_id in label_mapping.items():
        if event == "start":
            res += '\t0 [fillcolor=green,style=filled,label="start"];\n'
        elif event == "end":
            res += '\t1 [fillcolor=red,style=filled,label="end"];\n'
        else:
            res += f'\t{event_id} [label="{event}"];\n'

    for event_id, neighbors in graph.items():
        for n in neighbors:
            res += f"\t{event_id} -> {n};\n"
    res += "}"


    # graph = {"start": set()}
    # res = "digraph {"
    # res += '\trank="LR";\n'
    # res += '\tstart [fillcolor=green];\n'
    # res += '\tend [fillcolor=red];\n'
    # for trace in traces:
    #     current_event = "start"
    #     for event in trace:
    #         if event not in graph:
    #             graph[event] = set()
    #             res += f'\t{event} [label="{event}"];\n'
    #         if event not in graph[current_event]:
    #             res += f"\t{current_event} -> {event};\n"
    #         current_event = event
    #     if "end" not in graph[current_event]:
    #         res += f"\t{current_event} -> end;\n"
    # res += "}"
    #
    s = graphviz.Source(res, format="svg")

    print(res)
    s.render(view=True, filename="dfg_example")


if __name__ == '__main__':
    trace_file = "C:/Users/Geert/Desktop/sm_eval/datasets/cptc_18_reversed/train/cptc_18_reversed.txt"
    traces_to_dfg(trace_file)
