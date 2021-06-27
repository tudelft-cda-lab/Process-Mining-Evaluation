from collections import defaultdict
from typing import List, Tuple

Traces = List[List[str]]
CountedTrace = Tuple[List[str], int]
CountedTraces = List[CountedTrace]


def read_traces(filename: str) -> Traces:
    with open(filename, "r") as f:
        data = f.readlines()[1:]

    return [trace.strip().split(" ")[2:] for trace in data]


def write_traces(filename: str, traces: Traces):
    n_traces = len(traces)
    lines = []
    events = set()

    for trace in traces:
        trace_str = " ".join(trace)
        lines.append(f"1 {len(trace)} {trace_str}\n")
        for event in trace:
            events.add(event)

    with open(filename, "w+") as f:
        f.write(f"{n_traces} {len(events)}\n")
        f.writelines(lines)


def get_unique_traces(traces: Traces) -> CountedTraces:
    """
    Groups the given traces and counts duplicates. Useful during replay
    :param traces: input list of traces
    :return: List of (trace, count) for each unique input trace
    """
    counts = defaultdict(int)
    for trace in traces:
        counts[" ".join(trace)] += 1

    res = []
    for trace_str, frequency in counts.items():
        res.append((trace_str.split(" "), frequency))
    return res
