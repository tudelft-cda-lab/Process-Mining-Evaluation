import sys
from typing import List, Set, FrozenSet

from src.bpmn_model import BPMNModel
from src.read_bpmn import load_bpmn
from src.read_process_tree import load_process_tree
from src.replay_state_based import replay_traces, StateCache
from src.traces import *


def load_model(filename: str) -> BPMNModel:
    if filename.endswith(".bpmn"):
        return load_bpmn(filename)
    elif filename.endswith(".ptml"):
        return load_process_tree(filename)
    else:
        print(f"ERR - unknown extension for model file: {filename}")
        sys.exit(-1)


def filter_traces_source_ip(base_traces: StatList, ips: List[str], exclude=False) -> StatList:
    """
    Filter list of traces based on source IP
    :param base_traces: Base list of traces to filter
    :param ips: List of reference IPs
    :param exclude: True if ips cannot occur in the result, False if the ips can only occur
    :return: Filtered list
    """
    return [x for x in base_traces if (x.source_ip in ips) != exclude]


def filter_traces_dest_ip(base_traces: StatList, ips: List[str], exclude=False) -> StatList:
    """
    Filter list of traces based on dest IP
    :param base_traces: Base list of traces to filter
    :param ips: List of reference IPs
    :param exclude: True if ips cannot occur in the result, False if the ips can only occur
    :return: Filtered list
    """
    return [x for x in base_traces if (x.dest_ip in ips) != exclude]


def filter_traces_mcat(base_traces: StatList, mcats: List[str]) -> StatList:
    """
    Filter list of traces based on an mcat occurring anywhere in the trace
    :param base_traces: Base list of traces to filter
    :param mcats: List of mcats to keep in the result
    :return: Filtered list
    """
    return [x for x in base_traces if __includes_any(x.mcats, mcats)]


def filter_traces_mserv(base_traces: StatList, mservs: List[str]) -> StatList:
    """
    Filter list of traces based on an mserv occurring anywhere in the trace
    :param base_traces: Base list of traces to filter
    :param mservs: List of mcats to keep in the result
    :return: Filtered list
    """
    return [x for x in base_traces if __includes_any(x.mservs, mservs)]


def filter_traces_event(base_traces: StatList, events: List[str]) -> StatList:
    """
    Filter list of traces based on an event occurring anywhere in the trace
    :param base_traces: Base list of traces to filter
    :param events: List of mcats to keep in the result
    :return: Filtered list
    """
    return [x for x in base_traces if __includes_any(x.events, events)]


def filter_traces_team(base_traces: StatList, teams: List[str], exclude=False) -> StatList:
    """
    Filter out all traces for a given team
    :param base_traces: Base list of traces to filter
    :param teams: List of teams to keep/discard
    :param exclude: True if all values in teams should be removed,
                    False if all values in teams are kept
    :return: Filtered list
    """
    return [x for x in base_traces if (x.team in teams) != exclude]


def __includes_any(full_list: FrozenSet[str], reference_list: List[str]) -> bool:
    """
    Helper function for filtering
    :param full_list: Set of all base items
    :param reference_list: Items to keep with filtering
    :return: True if any item in reference list occurs in full_list
    """
    for value in reference_list:
        if value in full_list:
            return True
    return False


if __name__ == '__main__':
    # Loads the model from the folder, and flattens any and_split/and_join gateways where possible
    model_file = "models_bpmn/IM_cptc_18_reversed.bpmn"
    model = load_model(model_file)
    model.flatten()  # Optional, used to reduce the number of nodes in the model
    model.render()

    # Loads the data via the JSON file. This allows for filtering traces based on meta-data like
    # team, related source/destination IP or times, or on information like certain
    # events/attack stages/attacked services (not) occurring. Filters can be stacked.
    # Custom filters can be written. See the TraceWithStats class for a full overview of all
    # fields available.
    trace_file = "data/traces/cptc_18.json"
    traces = load_json(trace_file)
    # traces = filter_traces_team(traces, ["t5"])  # Filters are optional, but can also be combined
    # traces = filter_traces_event(traces, ["resHJ|http(s)"])
    # traces = filter_traces_dest_ip(traces, ["10.0.1.41"])
    # traces = filter_traces_event(traces, ["netDOS|http(s)"])

    # 10.0.1.5-DATAMANIPULATIONhttp
    # 10.0.1.41-NETWORKDOShttp
    unique_traces = to_counted_traces(traces, use_reversed=True)

    # Alternative: Don't filter anything and use a custom trace file.
    # This file assumes the FlexFringe format
    # trace_file = "data/traces/cptc_18_reversed.txt"
    # traces = read_traces(trace_file, reverse=False)
    # unique_traces = get_unique_traces(traces)

    # Perform the actual replay, and write the result to a svg file.
    replay_traces(model, unique_traces, cache=StateCache())
    # model_name = "replay_IM"
    model.render(lighten_threshold=-1)
    # model.save(model_name, directory="filtered_models", format="svg", view=True)
    #
    # simplified_model = model.simplified()
    # simplified_model.save(model_name + "_simplified", directory="filtered_models", format="svg",
    #                       view=True)

    print("Done")
