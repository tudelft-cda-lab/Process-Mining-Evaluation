import numpy as np

from src.standalone.read_write import read_traces


def trace_stats(filename: str):
    traces = read_traces(filename)

    unique_traces = set()
    unique_events = set()
    event_occurrences = 0

    lengths = []

    for trace in traces:
        unique_traces.add(" ".join(trace))
        event_occurrences += len(trace)
        lengths.append(len(trace))
        for event in trace:
            unique_events.add(event)

    print(
        f"{len(traces)} traces, {len(unique_traces)} unique, {len(unique_events)} unique events, {event_occurrences} total events")
    print(
        f"Length: {np.min(lengths)}, {np.average(lengths)}, {np.median(lengths)} {np.max(lengths)}")


if __name__ == '__main__':
    datasets = [
        "C:/Users/Geert/Desktop/Thesis/Datasets/Final/FF_traces/cptc_17_reversed.txt",
        "C:/Users/Geert/Desktop/Thesis/Datasets/Final/FF_traces/cptc_18_reversed.txt"
    ]

    for d in datasets:
        trace_stats(d)
