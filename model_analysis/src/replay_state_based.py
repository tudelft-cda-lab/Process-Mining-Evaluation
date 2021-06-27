import time
from collections import defaultdict
from copy import deepcopy
from typing import List, Set, Dict, Tuple, Optional

from src.traces import read_traces, get_unique_traces, CountedTrace, CountedTraces
from src.bpmn_model import BPMNModel, Graph
from src.read_bpmn import load_bpmn

PRINT_DEBUG = False


def debug_message(message: str):
    if PRINT_DEBUG:
        print(message)


_path = List[Tuple[str, str]]


class State:
    def __init__(self, nodes: Set[str]):
        # Set of all nodes the execution currently holds
        self.nodes = nodes

        # For each and_split, the dict keeps track of all paths taken. Every time we reach the
        # and_split, each child path can only be taken once. During execution, the entry for a
        # split is removed when all child paths have been taken.
        self.observed_split_paths: Dict[str, Set[str]] = {}

        # For each and_join, the dict keeps track of all paths taken to reach the join. Once the
        # join is reached through a parent node, that node cannot be used to reach the join again
        # until all parents have been used to reach the join. During execution, the entry for a j
        # oin is removed when all parents have been used to reach the join.
        self.observed_join_paths: Dict[str, Set[str]] = {}

        # List of all paths taken during execution
        self.paths: List[_path] = []

    def __str__(self) -> str:
        res = "nodes: "
        res += _as_sorted_str(self.nodes)

        res += ", splits: <"
        splits = sorted(self.observed_split_paths.keys())
        for split in splits:
            res += f"{split}:{_as_sorted_str(self.observed_split_paths[split])},"
        res += ">, joins: <"
        joins = sorted(self.observed_join_paths.keys())
        for join in joins:
            res += f"{join}:{_as_sorted_str(self.observed_join_paths[join])},"
        res += ">"
        return res


def copy_state(s: State) -> State:
    return deepcopy(s)


def _as_sorted_str(s: Set[str]) -> str:
    """
    Sorts the set and converts it to a string. This is used to ensure the representation for
    equal sets is always the same
    :param s: set of strings
    :return: string representation
    """
    items = sorted(list(s))
    return f"{{ {' '.join(items)} }}"


edgeCount = Dict[Tuple[str, str], int]
cacheEntry = Tuple[List[_path], bool]


def __sub_paths(start_paths: List[_path], final_paths: List[_path]) -> List[_path]:
    res = []
    for p in start_paths:
        assert p in final_paths

    for p in final_paths:
        if p not in start_paths:
            res.append(p)
    return res


def _state_diff(current_state: State, final_state: State, result_ok: bool) -> cacheEntry:
    if not result_ok:
        return [], False
    return __sub_paths(current_state.paths, final_state.paths), True


def _to_result_state(base_state: State, diff: cacheEntry) -> Tuple[State, bool]:
    if not diff[1]:
        return base_state, False

    new_state = State({"node_end"})
    new_state.paths += base_state.paths + diff[0]

    return new_state, True


def _cache_key(s: State, t: List[str]) -> str:
    return f"{_as_sorted_str(s.nodes)}-{' '.join(t)}"


class StateCache:
    """
    Used to store results for previously seen state/trace remaining combos.
    For each pair, the counts added are stored in the cache
    """

    def __init__(self):
        self.__state_cache: Dict[str, cacheEntry] = {}
        self.hits = 0

    def put_result(self, s: State, t: List[str], result_state: State, result_ok: bool):
        state_str = _cache_key(s, t)
        if state_str in self.__state_cache:
            if state_str in self.__state_cache:
                raise BaseException(f"Value already in cache: {state_str}")
        else:
            self.__state_cache[state_str] = _state_diff(s, result_state, result_ok)

    def get_result(self, s: State, t: List[str]) -> Optional[Tuple[State, bool]]:
        state_str = _cache_key(s, t)
        if state_str not in self.__state_cache:
            return None
        self.hits += 1
        return _to_result_state(s, self.__state_cache[state_str])
        # return self.__state_cache.get(self.__cache_key(s, t), None)


class FakeCache:
    def __init__(self):
        self.hits = 0

    def put_result(self, *args):
        return

    def get_result(self, *args) -> Optional[Tuple[State, bool]]:
        return None


def replay_traces(model: BPMNModel, traces: CountedTraces, max_width=10, cache=None):
    """
    Replays all traces over the given model. No value is returned, but during replay, the counts in
    the nodes/edges in the model are updated.
    :param model: base BPMN model
    :param traces: List of counted traces
    :param max_width: maximum number of paths to consider at each point in the search tree.
                      Default 10
    :param cache: State cache object to store previously seen state/trace combinations.
                  If None, no caching is used
    """

    def current_milli_time():
        return round(time.time() * 1000)

    start_time = current_milli_time()

    total_traces = sum([t[1] for t in traces])
    # print(f"Replaying {len(traces)} unique traces (total of {total_traces} traces)")

    n_success = 0
    n_fail = 0
    n_unique_success = 0
    n_unique_fail = 0
    for idx, trace in enumerate(traces):
        # print(
        #     f"Replay trace {idx}/{len(traces) - 1} (occurs {trace[1]} times): {' '.join(trace[0])}")
        try:
            trace_ok = replay_trace(model, trace, max_width=max_width, cache=cache)
        except Exception as e:
            print(e)
            trace_ok = False
        if trace_ok:
            n_success += trace[1]
            n_unique_success += 1
        else:
            n_fail += trace[1]
            n_unique_fail += 1

        # print(f"{n_success} success, {n_fail} failed")

    print(f"Done, Total of {n_success + n_fail} traces, {n_success} success, {n_fail} failed")
    print(
        f"Total of {n_unique_success + n_unique_fail} unique traces, {n_unique_success} success, {n_unique_fail} failed")
    print(f"Took {current_milli_time() - start_time} ms")


def replay_trace(model: BPMNModel, trace: CountedTrace, max_width=10, cache=None) -> bool:
    """
    Searches all the possible paths through the model to explain the given trace.

    The pseudocode for the replay is as follows:
    Here, S only contains the set of all nodes the process currently reached
    get_all_paths(S: State, T: node)
    # return all paths starting in any node in S, ending in T
    # sorted by least and-splits, and-joins, shortest path

    execute_trace(S: State, Trace: Trace)
    # T = Trace[0]
    # For each node N in S:
    #   if there is no path N to T
    #       continue
    #   for each path P from N to T:
    #       S', ok = execute_path(S, P)
    #       if ok:
    #           execute_trace(S', T[1:]
    #
    #       if either execute_path or execute_trace fails, try with the next path

    execute_path(S: State, path: Path)
    # S' = copy(S)
    # for each edge E in path:
    #     S' = step_edge(S', E) -> break if step_edge failed
    # return S'

    step_edge(S: State, (src, dst): Edge)
    # assert src in S
    # S = S-src
    # if src is and_split:
    #   add all children of src to S
    # elif dst is and_join:
    #   merge src to dst
    #   n <- in_degree of dst
    #   merge n-1 nodes in S to dst

    If the trace perfectly fits the model, all counts in the model object are updated
    :param model: BPMNModel used to replay
    :param trace: a tuple with a List[str] (the events in the trace) and an int
        (the count of how often it occurs)
    :param max_width: maximum number of paths to consider at each point in the search tree.
                      Default 10
    :param cache: State cache object to store previously seen state/trace combinations.
                  If None, no caching is used
    """

    trace_update = trace[0] + ["end"]

    and_gateways = [n for n in model.graph if n.startswith("and")]
    # if len(and_gateways) == 0:
    #     # This model does not contain any parallel nodes, hence we can use
    #     result_state, ok = execute_trace_simple(model, trace[0])
    # else:
    #     state = State({"node_start"})
    #     result_state, ok = execute_trace(model, state, trace_update, cache, max_width=max_width)
    state = State({"node_start"})
    result_state, ok = execute_trace(model, state, trace_update, cache, max_width=max_width)

    if not ok:
        # print("Replay failed")
        return False
    # print("SUCCESS")

    # Update the counts in the model
    edge_counts = defaultdict(int)
    for path in result_state.paths:
        for edge in path:
            edge_counts[edge] += 1

    join_counts = defaultdict(int)
    frequency = trace[1]
    model.nodes["node_start"].count += frequency

    for edge, count in edge_counts.items():
        model.edge_map[edge].count += count * frequency
        dest = edge[1]
        model.nodes[dest].count += count * frequency

        # Keep track how often we encounter each join
        if dest.startswith("and_join"):
            join_counts[dest] += 1

    # Each time a join is crossed, the count is updated for each incoming edge.
    # See how often the join is crossed, and remove the additional counts
    for join_node, counts in join_counts.items():
        join_degree = len(model.reverse_graph[join_node])

        # If this does not hold, some execution did not merge all incoming paths
        assert counts % join_degree == 0

        n_executions = counts / join_degree
        model.nodes[join_node].count -= int(n_executions * (join_degree - 1) * frequency)

    return True


def execute_trace_simple(model: BPMNModel, trace: List[str]) -> Tuple[State, bool]:
    """
    When the model does not contain any parallel structures, the replay is simply a case of finding
    paths between the task nodes. As a result, there is only one possible start node at any time,
    and all valid paths can be taken to yield the same result.
    :param model: base BPMN model
    :param trace:
    :return:
    """
    and_gateways = [n for n in model.graph if n.startswith("and")]
    assert len(and_gateways) == 0

    trace = trace + ["end"]

    state = State({"node_start"})
    for e in trace:
        assert len(state.nodes) == 1
        if e not in model.label_mapping:
            print(f"Missing label for event '{e}'")
            return state, False
        next_tasks = model.label_mapping[e]
        paths = _get_all_paths(model, state, state.nodes, next_tasks)
        if len(paths) == 0:
            return state, False
        state, ok = execute_path(model, state, paths[0])
        if not ok:
            return state, False
        assert paths[0][-1][1] in state.nodes
    return state, True


def execute_trace(model: BPMNModel, state: State, trace: List[str], cache, max_width=10) -> \
        Tuple[State, bool]:
    debug_message(f"Working on trace '{' '.join(trace)}'")
    if len(trace) == 0:
        if len(state.observed_split_paths) != 0 or len(state.observed_join_paths) != 0 or len(
                state.nodes) != 1 or "node_end" not in state.nodes:
            # The final state is not clean -> something is up
            print("WARN - SOMETHING WRONG")

        return state, True

    stored_result = cache.get_result(state, trace)
    if stored_result is not None:
        debug_message("Cache shortcut")
        return stored_result

    if trace[0] not in model.label_mapping:
        print(f"ERR - Missing label for event '{trace[0]}'")
        return state, False

    next_tasks = model.label_mapping[trace[0]]
    all_paths = _get_all_paths(model, state, state.nodes, next_tasks)[:max_width]
    for path in all_paths:
        new_state, ok = execute_path(model, deepcopy(state), path)
        if not ok:
            debug_message("Path did not work, skipping")
            continue

        result_state, ok = execute_trace(model, new_state, trace[1:], cache, max_width=max_width)
        if ok:
            cache.put_result(state, trace, result_state, True)
            return result_state, True
        else:
            debug_message("Invalid solution, backtracking")
            continue

    cache.put_result(state, trace, state, False)
    return state, False


def execute_path(model: BPMNModel, state: State, path: _path) -> Tuple[State, bool]:
    new_state = copy_state(state)
    new_state.paths.append(path)
    for edge in path:
        new_state, ok = step_edge(model, new_state, edge)
        if not ok:
            return state, False
    return new_state, True


def step_edge(model: BPMNModel, f: State, edge: Tuple[str, str]) -> Tuple[State, bool]:
    src, dst = edge
    if src not in f.nodes:
        return f, False

    new_state = copy_state(f)
    # new_state.edge_counts[edge] += 1

    if src.startswith("and_split"):
        # Add dst to the observed split paths
        if src in new_state.observed_split_paths:
            # Check if this path has already been taken
            if dst in new_state.observed_split_paths[src]:
                return new_state, False
            new_state.observed_split_paths[src].add(dst)
        else:
            # First time encountering this split, so add a new entry
            new_state.observed_split_paths[src] = {dst}

        # Test if all paths have been taken
        if len(new_state.observed_split_paths[src]) == len(model.graph[src]):
            # Remove and_split if all paths have been taken
            del new_state.observed_split_paths[src]
            new_state.nodes.remove(src)
        else:
            assert src in new_state.observed_split_paths
            assert len(new_state.observed_split_paths[src]) != len(model.graph[src])
            assert len(new_state.observed_split_paths[src]) != 0

        new_state.nodes.add(dst)
    elif src.startswith("and_join"):
        n_to_join = len(model.reverse_graph[src]) - len(new_state.observed_join_paths[src])
        if n_to_join == 0:
            # All paths are joined, no issue
            del new_state.observed_join_paths[src]
        else:
            while n_to_join > 0:
                # Note: a search trough all possible paths should not be needed: when the join is
                #  merged, the parent paths should be independent, i.e. if n_to_join joins are left,
                #  only n_to_join nodes in state.nodes have a path to this join node. Besides, these
                #  paths are independent hence order does not matter
                join_paths = _get_all_paths(model, new_state, new_state.nodes, [src])
                if len(join_paths) == 0:
                    return new_state, False
                # assert len(join_paths) == n_to_join
                new_state, ok = execute_path(model, new_state, join_paths[0])
                if not ok:
                    return new_state, False
                n_to_join -= 1
            assert len(new_state.observed_join_paths[src]) == len(model.reverse_graph[src])
            del new_state.observed_join_paths[src]

        new_state.nodes.remove(src)
        new_state.nodes.add(dst)
    else:
        new_state.nodes.remove(src)
        new_state.nodes.add(dst)

    if dst.startswith("and_join"):
        if dst in new_state.observed_join_paths:
            if src in new_state.observed_join_paths[dst]:
                # This path is already joined
                return new_state, False

            new_state.observed_join_paths[dst].add(src)
        else:
            new_state.observed_join_paths[dst] = {src}

    return new_state, True


def _path_is_valid(model: BPMNModel, state: State, path: _path) -> bool:
    """
    Verifies if the found path is invalid. Reasons for a path to be invalid are:
    - The path goes from an and_split to a node already visited -> cannot take the same path twice
    - The path merges to an and_join through a node already used -> cannot join twice

    Further constrains are checked to be sure. These should always hold due to the implementation
    for finding a path.
    - Any node or edge not in the model
    - The path crosses a task which is not the start or endpoint
    - The path has a gap

    :param model: base BPMN model
    :param state: current state
    :param path: List of edge tuples in the path
    :return: True if the path is valid
    """
    if len(path) == 0:
        return False
    for i, (src, dst) in enumerate(path):
        if dst not in model.graph[src]:
            raise BaseException("invalid path -> edge does not exist")

        if src.startswith("task") and i != 0:
            raise BaseException("Path crosses task")
        if dst.startswith("task") and i != len(path) - 1:
            raise BaseException("Path crosses task")

        if i > 0 and src != path[i - 1][1]:
            raise BaseException("Path is not sequential")

        # Check for an invalid path through an and_split or and_join
        if src in state.observed_split_paths:
            if dst in state.observed_split_paths[src]:
                return False
        elif dst in state.observed_join_paths:
            if src in state.observed_join_paths[dst]:
                return False

    return True


def _get_all_paths(model: BPMNModel, state: State, sources: Set[str], targets: List[str]) -> List[
    _path]:
    """
    Uses DFS to find all possible paths from source to target. The path will not contain any tasks,
    other than possibly the source or target
    :param model: base BPMN model
    :param state: current state
    :param sources: Set of all possible start points -> usually state.nodes
    :param targets: list of possible target nodes for the paths, used if duplicate labels exist
    :return:
    """
    # if len(targets) > 1:
    #     print("MULTIPLE TARGETS: ", targets)
    #
    all_paths = []
    for source in sources:
        for target in targets:
            new_paths = []
            _dfs(model.graph, source, target, [source], new_paths)

            # Filter out any path which is already taken
            if source.startswith("and_split"):
                new_paths = list(
                    filter(lambda x: _path_is_valid(model, state, x), new_paths)
                )

            all_paths += new_paths

    counted_paths: List[Tuple[_path, int, int, int]] = []
    for path in all_paths:
        and_splits = 0
        and_joins = 0
        for e in path:
            if e[0].startswith("and_split"):
                and_splits += 1
            elif e[0].startswith("and_split"):
                and_joins += 1

        counted_paths.append((path, and_splits, and_joins, len(path)))

    # Sort all possible paths using a best-first heuristic. This way, the most optimal paths are
    # checked first which in turn should result in a speedup of execution.
    # Heuristics in order of most to least important
    # - crosses less and_splits
    # - crosses less and_joins
    # - shortest length

    # sort by length
    counted_paths.sort(key=lambda x: x[3])
    # sort by number of joins
    counted_paths.sort(key=lambda x: x[2])
    # sort by number of splits
    counted_paths.sort(key=lambda x: x[1])

    return [p[0] for p in counted_paths]


def _dfs(graph: Graph, source: str, target: str, current_path: List[str], results: List[_path]):
    """
    Performs a depth-first search to find all possible paths from source to target
    :param graph: Dict containing all edges in the model
    :param source: start node of this path, should always be current_path[-1]
    :param target: target node
    :param current_path: sequence of nodes visited in the current search
    :param results: list of all possible results -> initialized as an empty list. When the initial
                    call to _dfs returns, this list contains all possible paths
    :return: None, the results are contained in the results list
    """
    # Check if we found the target. The check length check is done when searching for find a loop
    if source == target and len(current_path) > 1:
        res_path: _path = []
        for i in range(1, len(current_path)):
            res_path.append((current_path[i - 1], current_path[i]))
        results.append(res_path)
        return

    for child in graph[source]:
        # Skip all paths going through a task which is not the target
        if child.startswith("task") and child != target:
            continue

        # Check if we visit the next node twice. The first node of current_path is ignored for when
        # we are searching for a loop, and the child can be the start node
        if child not in current_path[1:]:
            new_path = current_path.copy()
            new_path.append(child)
            _dfs(graph, child, target, new_path, results)


if __name__ == '__main__':
    model_file = "custom_miner_test/ptm/PTM_cptc_18_reversed.bpmn"
    loaded_model = load_bpmn(model_file)
    loaded_model.flatten()

    trace_file = "data/traces/cptc_18_reversed.txt"
    base_traces = read_traces(trace_file)
    unique_traces = get_unique_traces(base_traces)
    replay_traces(loaded_model, unique_traces, cache=StateCache())
