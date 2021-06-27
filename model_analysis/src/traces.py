import json
from collections import defaultdict
from dataclasses import dataclass
from typing import List, Tuple, FrozenSet

Traces = List[List[str]]
CountedTrace = Tuple[List[str], int]
CountedTraces = List[CountedTrace]


def read_traces(filename: str, reverse=False) -> Traces:
    """
    Reads a trace file as used for Flexfringe
    :param filename: path to the file
    :param reverse: True if the order of the traces should be reversed,
                    False if the order of the traces should remain unchanged
    :return: List of only the traces
    """
    assert filename.endswith("txt")

    f = open(filename, "r+")
    data = f.readlines()[1:]
    f.close()

    res = []
    for trace in data:
        values = trace.strip().split(" ")[2:]
        if reverse:
            values.reverse()
        res.append(values)
    return res


def write_traces(filename: str, traces: Traces):
    """
    Saves traces in the Flexfringe format
    :param filename: path to the output file
    :param traces: List of traces
    """
    n_traces = len(traces)
    lines = []
    events = set()

    for trace in traces:
        trace_str = " ".join(trace)
        lines.append(f"1 {len(trace)} {trace_str}\n")
        for event in trace:
            events.add(event)

    f = open(filename, "w+")
    f.write(f"{n_traces} {len(events)}\n")
    f.writelines(lines)
    f.close()


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


@dataclass(frozen=True)
class TraceWithStats:
    # List of events in the trace
    trace_chronological: List[str]
    trace_reversed: List[str]

    # Key as produced by `break_into_subbehaviors` from the AD-Attack-Graph code
    trace_key: str
    team: str
    source_ip: str
    dest_ip: str
    key_suffix: str

    # Times related to each event in the trace
    times: List[int]
    times_reversed: List[int]

    # Attack stage/attack service of the last event (highest timestamp) in the trace.
    # This is the most severe event in the trace
    final_mcat: str
    final_mserv: str

    # Collection of all events, attacked services, and attack stages in this trace
    events: FrozenSet[str]
    mservs: FrozenSet[str]
    mcats: FrozenSet[str]


StatList = List[TraceWithStats]


def load_json(filename: str) -> StatList:
    assert filename.endswith("json")
    file = open(filename, "r+")
    data = json.load(file)
    file.close()

    res: List[TraceWithStats] = []

    for trace in data:
        events = frozenset(trace["trace_chronological"])
        mcats = frozenset({e[0] for e in events})
        mservs = frozenset({e[1] for e in events})

        res.append(TraceWithStats(
            trace["trace_chronological"],
            trace["trace_reversed"],
            trace["trace_key"],
            trace["team"],
            trace["source_ip"],
            trace["dest_ip"],
            trace["key_suffix"],
            trace["times"],
            trace["times_reversed"],
            trace["final_mcat"],
            trace["final_mserv"],
            events,
            mservs,
            mcats,
        ))

    return res


def to_counted_traces(base_traces: StatList, use_reversed=True) -> CountedTraces:
    """
    Converts a list of TracesWithStats (used for filtering) to CountedTraces(used for replay).
    :param base_traces: List of filtered traces
    :param use_reversed: True if the reverse traces are used
                         False if the chronological traces are used
    :return: List of traces with their frequency
    """
    if use_reversed:
        traces = [x.trace_reversed for x in base_traces]
    else:
        traces = [x.trace_chronological for x in base_traces]
    return get_unique_traces(traces)
