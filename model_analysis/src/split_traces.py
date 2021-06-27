from src.main import load_model
from src.render_sub_model import reachable_nodes
from src.traces import read_traces, write_traces


def split_traces():
    """
    Split traces based on the partition in the model from the Inductive Miner
    :return:
    """
    trace_file = "data/traces/cptc_18_reversed.txt"
    model_file = "models_bpmn/base/IM_cptc_18_reversed.bpmn"

    model = load_model(model_file)
    upper_nodes = reachable_nodes(model.graph, "node_start",
                                  model.graph[model.label_mapping["remoteexp|broadcast"][0]][0])
    # tasks = [n for n in model.nodes if n.startswith("task")]
    # upper_tasks = {t for t in tasks if t in upper_nodes}

    traces = read_traces(trace_file)
    traces_before = []
    traces_after = []
    for trace in traces:
        split_idx = 0
        for event in trace:
            event_task = model.label_mapping[event][0]
            if event_task not in upper_nodes:
                break
            else:
                split_idx += 1

        trace_before = trace[:split_idx]
        if len(trace_before) > 0:
            traces_before.append(trace_before)
        trace_after = trace[split_idx:]
        if len(trace_after) > 0:
            traces_after.append(trace_after)
    write_traces("cptc_18_before.txt", traces_before)
    write_traces("cptc_18_after.txt", traces_after)


if __name__ == '__main__':
    split_traces()
