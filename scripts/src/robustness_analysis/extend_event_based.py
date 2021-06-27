from itertools import permutations
from typing import Set

from src.standalone.read_write import read_traces, write_traces, Traces


def extract_traces(traces: Traces, events: Set[str]):
    res = []

    for t in traces:
        count = 0
        for e in events:
            if e in t:
                count += 1
        if count > 0:
            res.append((count, t))
            # print(f"{count} -> {' '.join(t)}")

    res.sort(key=lambda x: x[0])
    for r in res:
        print(f'{r[0]}: {" ".join(r[1])}')

    counted = {}

    for r in res:
        t_update = [e for e in r[1] if e in events]
        print(f"{r[0]} -> {' '.join(t_update)}")

        t_str = " ".join(t_update)
        counted[t_str] = counted.get(t_str, 0) + 1

    print(counted)


def insert_sequential(traces: Traces, events: Set[str], out_file: str, n=1):
    """
    Creates a new dataset with introduced sequential traces for the given set of events.
    For each event in events, all traces in which the event occur is changed by repeating the event:
    'a b c d' becomes 'a b b_2 ... b_n c d' if b is in the set of events
    :param traces: list of base traces
    :param events: set of all events to extend
    :param out_file: file to write the results to
    :param n: number of repetitions
    :return:
    """
    updated_traces = []
    for t in traces:
        new_trace = []
        for e in t:
            new_trace.append(e)
            if e in events:
                for i in range(n):
                    new_trace.append(f"{e}_{i}")
        updated_traces.append(new_trace)
    write_traces(out_file, updated_traces)


def insert_exclusive(traces: Traces, events: Set[str], out_file: str, n=1):
    """
    Creates a new dataset with introduced exclusive choice constructs for the given events.
    For each trace in which any of the events occurs, new traces are added where the new events are
    replaced. Given trace 'a b c d', a new trace is added: 'a b_1 c_1 d', assuming b and c are in

    :param traces: list of base traces
    :param events: set of all events to extend
    :param out_file: file to write the results to
    :param n: number of repetitions
    """
    updated_traces = []
    for t in traces:
        updated_traces.append(t)
        has_event = False
        for e in t:
            if e in events:
                has_event = True

        if not has_event:
            continue

        for i in range(n):
            new_trace = []
            for e in t:
                if e in events:
                    new_trace.append(f"{e}_{i}")
                else:
                    new_trace.append(e)
            updated_traces.append(new_trace)

    write_traces(out_file, updated_traces)


def insert_parallel(traces: Traces, events: Set[str], out_file: str, n=1):
    """
    Creates a new dataset with introduced parallel constructs for the given events.
    For each trace in which any of the events occurs, new traces are added where the new events are
    replaced. A trace 'a b c' is replaced by two new traced 'a b_1 ... b_n c' for each permutation
    of 1...n

    :param traces: list of base traces
    :param events: set of all events to extend
    :param out_file: file to write the results to
    :param n: number of repetitions
    """
    updated_traces = []
    for t in traces:
        has_event = False
        for e in t:
            if e in events:
                has_event = True
        if not has_event:
            updated_traces.append(t)
            continue

        for order in permutations(range(n + 1)):
            new_trace = []
            for e in t:
                if e in events:
                    for i in order:
                        new_trace.append(f"{e}_{i}")
                else:
                    new_trace.append(e)
            updated_traces.append(new_trace)

    write_traces(out_file, updated_traces)


def insert_loop(traces: Traces, events: Set[str], out_file: str, n=1):
    """
    Creates a new dataset with introduced loop constructs for the given events.
    For each trace in which any of the events occurs, new traces are added where the new events are
    replaced. Given trace 'a b c', a new trace is added: 'a b b c', assuming b is in the set
    of events.

    :param traces: list of base traces
    :param events: set of all events to extend
    :param out_file: file to write the results to
    :param n: number of repetitions
    """
    updated_traces = []
    for t in traces:
        updated_traces.append(t)
        has_event = False
        for e in t:
            if e in events:
                has_event = True
        if not has_event:
            continue
        # add new trace of 1, 2, ..., n repetitions
        for i in range(1, n + 1):
            new_trace = []
            for e in t:
                new_trace.append(e)
                if e in events:
                    for j in range(i):
                        new_trace.append(e)
            updated_traces.append(new_trace)

    write_traces(out_file, updated_traces)


if __name__ == '__main__':
    # trace_file = "C:/Users/Geert/Desktop/Thesis/Datasets/Final/FF_traces/cptc_18_reversed.txt"
    trace_file = "C:/Users/Geert/Desktop/Thesis/Scripts/src/robustness_analysis/traces_small/small/traces_small.txt"
    base_traces = read_traces(trace_file)
    # out_file = "changed_traces/18_rev_insert_duplicate.txt"

    filter_events = {
        "resHJ|wireless",
        "remoteexp|hostingServer",
        "acctManip|wireless",
        "ACE|http(s)",
        "ACE|wireless",
        "dManip|http(s)",
        # "delivery|hostingServer",
        # "delivery|wireless",
    }
    filter_events_2 = set(
        "exfil|wireless dManip|wireless resHJ|wireless ACE|wireless remoteexp|wireless acctManip|wireless rPrivEsc|wireless".split(
            " "))

    filter_event = {
        # "resHJ|wireless"
        # "remoteexp|hostingServer",
        # "acctManip|hostingServer",
        # "dManip|http(s)",
        # "delivery|http(s)"

        "dManip|http(s)",
        # "resHJ|http(s)",
        "ACE|http(s)",
    }

    insert_exclusive(base_traces, {"ACE|wireless", }, "traces_small/small/insert_ex_10.txt", n=10)
    # insert_parallel(base_traces, {"ACE|wireless", }, "./traces_small/small/insert_par_3.txt", n=3)

    # insert_extend(base_traces, filter_events, "changed_traces/18_rev_insert_extend.txt")
    # insert_duplicate(base_traces, filter_events, "changed_traces/18_rev_insert_duplicate.txt")
    # extract_traces(base_traces, filter_events)

    # extract_traces(base_traces, filter_event)

    # for i in range(1, 6):
    #     insert_sequential(base_traces, filter_events, f"changed_traces/18_rev_all_seq_n={i}.txt",
    #                       n=i)
    #     insert_parallel(base_traces, filter_events, f"changed_traces/18_rev_all_par_n={i}.txt", n=i)
    #     insert_exclusive(base_traces, filter_events, f"changed_traces/18_rev_all_ex_n={i}.txt", n=i)
    #     insert_loop(base_traces, filter_events, f"changed_traces/18_rev_all_loop_n={i}.txt", n=i)

    event_labels = [
        ("resHJ|wireless", "resHJ_wireless"),
        # ("remoteexp|hostingServer", "remoteexp_hostingServer"),
        ("acctManip|wireless", "acctManip_wireless"),
        # ("ACE|http(s)", "ACE_https"),
        ("ACE|wireless", "ACE_wireless"),
        ("exfil|wireless", "exfil_wireless"),
        ("dManip|wireless", "dManip_wireless"),
        ("remoteexp|wireless", "remoteexp_wireless"),
        ("rPrivEsc|wireless", "rPrivEsc_wireless"),
        ("resHJ|unassigned", "resHJ_unassigned"),
        # ("dManip|http(s)", "dManip_https"),
    ]

    # for event, label in event_labels:
    #     for i in range(1, 6):
    #         insert_sequential(base_traces, {event}, f"changed_traces_prom/18_rev_{label}_seq_n={i}.txt",
    #                           n=i)
    #         insert_parallel(base_traces, {event}, f"changed_traces/18_rev_{label}_par_n={i}.txt",
    #                         n=i)
    #         insert_exclusive(base_traces, {event}, f"changed_traces/18_rev_{label}_ex_n={i}.txt",
    #                          n=i)
    #         insert_loop(base_traces, {event}, f"changed_traces/18_rev_{label}_loop_n={i}.txt", n=i)

# {'dManip|http(s)': 5,
#  'ACE|http(s)': 20, 'remoteexp|hostingServer': 3,
#  'dManip|http(s) ACE|http(s)': 43, 'dManip|http(s) ACE|http(s) remoteexp|hostingServer': 1,
#  'resHJ|wireless ACE|wireless acctManip|wireless': 2}
