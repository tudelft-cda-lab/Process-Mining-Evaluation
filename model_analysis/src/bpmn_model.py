from collections import defaultdict
from time import sleep
from typing import *

import graphviz

Graph = Dict[str, List[str]]


class Node:
    def __init__(self, node_type, node_id, name):
        self.node_type = node_type
        self.node_id = node_id
        self.name = name
        self.count = 0


class Edge:
    def __init__(self, src: str, dst: str, count=0):
        self.src = src
        self.dst = dst
        self.count = count


class BPMNModel:
    def __init__(self, nodes: Dict[str, Node], edges: List[Edge], edge_map=None, graph=None,
                 flatten=False):
        self.nodes = nodes
        self.edges = edges
        self.edge_map = {(e.src, e.dst): e for e in edges} if edge_map is None else edge_map
        self.graph = _construct_graph(edges) if graph is None else graph
        self.reverse_graph = _reverse_graph(self.graph)
        self.label_mapping = _construct_label_mapping(nodes)
        self.reverse_label_mapping = _construct_reverse_label_mapping(nodes)

        if flatten:
            self.flatten()

    def reduce(self) -> bool:
        """
        Removes any redundant values from the model. These include:
        - Edges from and and_split directly to an and_join
        - Any and_split, and_join, xor_split or xor_join with exactly one incoming and
          one outgoing edge
        :return: True iff any element was removed
        """

        def reduce_node(node_id) -> bool:
            if node_id not in self.nodes:
                return False
            if node_id.startswith("task") or node_id.startswith("node"):
                return False
            if len(self.graph[node_id]) != 1:
                return False
            if len(self.reverse_graph[node_id]) != 1:
                return False

            parent_id = self.reverse_graph[node_id][0]
            child_id = self.graph[node_id][0]
            count = self.nodes[node_id].count
            print(f"Removing {node_id}: {parent_id} {child_id}")

            del self.nodes[node_id]
            del self.graph[node_id]
            del self.reverse_graph[node_id]
            del self.edge_map[(parent_id, node_id)]
            del self.edge_map[(node_id, child_id)]

            self.graph[parent_id].remove(node_id)
            if child_id not in self.graph[parent_id]:
                self.graph[parent_id].append(child_id)

            self.reverse_graph[child_id].remove(node_id)
            if parent_id not in self.reverse_graph[child_id]:
                self.reverse_graph[child_id].append(parent_id)

            e = (parent_id, child_id)
            if e not in self.edge_map:
                # self.edge_map[e].count += count
                # else:
                self.edge_map[e] = Edge(parent_id, child_id, count=count)

            return True

        has_removed = True
        reduced_any = False
        while has_removed:
            has_removed = False
            # Remove redundant XOR gateways
            nodes = [n for n in self.nodes if n.startswith("xor")]
            for join in nodes:
                has_removed = reduce_node(join) or has_removed

            # Remove redundant AND gateways
            nodes = [n for n in self.nodes if n.startswith("and")]
            for join in nodes:
                has_removed = reduce_node(join) or has_removed

            reduced_any = reduced_any or has_removed

        self.edges = list(self.edge_map.values())
        return reduced_any

    def remove_and_and_edges(self):
        # Remove and_split->and_join edges
        for src, dst in list(self.edge_map.keys()):
            if src.startswith("and_split") and dst.startswith("and_join"):
                # Don't delete if this is the only edge
                if len(self.graph[src]) == 1:
                    continue
                del self.edge_map[(src, dst)]
                self.graph[src].remove(dst)
                self.reverse_graph[dst].remove(src)

    def flatten(self, verbose=False) -> bool:
        """
        Removes all instances of edges two and_split or two and_join nodes are directly connected.
        :return True iff any gateway could be removed
        """

        def reduce_node_to_parent(node_id) -> bool:
            if node_id not in self.nodes:
                return False
            if not node_id.startswith("and"):
                return False
            if node_id.startswith("and_split"):
                if len(self.reverse_graph[node_id]) != 1:
                    return False
                if not self.reverse_graph[node_id][0].startswith("and_split"):
                    return False
                if verbose:
                    print(f"Flattening {node_id} into {self.reverse_graph[node_id][0]}")
            if node_id.startswith("and_join"):
                if len(self.graph[node_id]) != 1:
                    return False
                if not self.graph[node_id][0].startswith("and_join"):
                    return False
                if verbose:
                    print(f"Flattening {node_id} into {self.graph[node_id][0]}")

            parents = self.reverse_graph[node_id]
            children = self.graph[node_id]
            node_count = self.nodes[node_id].count
            # Remove all edges
            for parent in parents:
                del self.edge_map[(parent, node_id)]
                self.graph[parent].remove(node_id)
            for child in children:
                del self.edge_map[(node_id, child)]
                self.reverse_graph[child].remove(node_id)

            for parent in parents:
                for child in children:
                    if (parent, child) not in self.edge_map:
                        self.edge_map[(parent, child)] = Edge(parent, child, count=node_count)
                        self.graph[parent].append(child)
                        self.reverse_graph[child].append(parent)

            del self.nodes[node_id]
            del self.graph[node_id]
            del self.reverse_graph[node_id]
            return True

        has_removed = True
        removed_any = False
        while has_removed:
            has_removed = False
            gateways = [n for n in self.nodes if n.startswith("and")]
            for gateway in gateways:
                has_removed = reduce_node_to_parent(gateway) or has_removed
            removed_any = removed_any or has_removed

        self.edges = list(self.edge_map.values())
        return removed_any

    def __validate_map(self):
        """
        Helper function for debugging. Validates that self.graph, self.reverse_graph
        and self.edge_map are consistent with each other
        :return:
        """
        for node, children in self.graph.items():
            assert node in self.nodes
            for child in children:
                assert child in self.nodes
                assert (node, child) in self.edge_map
                assert node in self.reverse_graph[child]
        for node, parents in self.reverse_graph.items():
            assert node in self.nodes
            for parent in parents:
                assert parent in self.nodes
                assert (parent, node) in self.edge_map
                assert node in self.graph[parent]
        for src, dst in self.edge_map:
            assert src in self.nodes
            assert dst in self.nodes
            assert dst in self.graph[src]
            assert src in self.reverse_graph[dst]

    def simplified(self):
        """
        Constructs a new model with all nodes/edges with a zero count removed
        :return: Simplified model
        """
        # Remove all items with a zero count
        # TODO: Enhance as multiple incoming edges can cause a count to be non-negative
        nodes = {node_id: val for node_id, val in self.nodes.items() if val.count != 0}
        edges = [e for e in self.edges if e.count != 0 or (e.src in nodes and e.dst in nodes)]

        res = BPMNModel(nodes, edges)
        # Simplify the model until no improvements can be found
        has_simplified = True
        while has_simplified:
            has_simplified = res.reduce()
            has_simplified = res.flatten() or has_simplified
            res.remove_and_and_edges()

        # Sanity check on the results, should never fail
        res.validate_counts()
        return res

    def update_node_count(self, node_id: str, amount: int = 1):
        self.nodes[node_id].count += amount

    def update_edge_count(self, edge: Tuple[str, str], amount: int = 1):
        self.edge_map[edge].count += amount

    def get_start(self) -> Node:
        return self.nodes["node_start"]

    def get_end(self) -> Node:
        return self.nodes["node_end"]

    def validate_counts(self):
        """
        Used for testing/validation. Verifies that for each node the count is consistent with
        the counts for all incoming/outgoing edges.
        """
        is_valid = True
        for node_id in self.nodes:
            node = self.nodes[node_id]
            parents = self.reverse_graph[node_id]
            children = self.graph[node_id]
            if node_id == "node_start":
                # Only check out-edge
                assert len(children) == 1
                if node.count != self.edge_map[(node_id, children[0])].count:
                    print("Bad count node_start")
                    is_valid = False
            elif node_id == "node_end":
                # Only check in-edge
                assert len(parents) == 1
                if node.count != self.edge_map[(parents[0], node_id)].count:
                    print("Bad count node_end")
                    is_valid = False
            elif node_id.startswith("and_split"):
                # In-edge and out-edges all have the same count
                assert len(parents) == 1
                parent_id = self.reverse_graph[node_id][0]

                if self.edge_map[(parent_id, node_id)].count != node.count:
                    print(
                        f"Bad parent count {node_id}: {node.count} -> {self.edge_map[(parent_id, node_id)].count}")
                    is_valid = False

                child_ids = self.graph[node_id]
                for child_id in child_ids:
                    if self.edge_map[(node_id, child_id)].count != node.count:
                        print(
                            f"Bad child count {node_id} {child_id}: {node.count} -> {self.edge_map[(node_id, child_id)].count}")
                        is_valid = False
            elif node_id.startswith("and_join"):
                # In-edges and out-edge all have the same count
                for parent_id in parents:
                    if self.edge_map[(parent_id, node_id)].count != node.count:
                        print(
                            f"Bad parent count: {node_id} {parent_id}: {node.count} -> {self.edge_map[(parent_id, node_id)].count}")
                        is_valid = False

                assert len(children) == 1
                if self.edge_map[(node_id, children[0])].count != node.count:
                    print(
                        f"Bad child count {node_id}: {node.count} -> {self.edge_map[(node_id, children[0])].count}")
                    is_valid = False
            else:
                # Check sum of counts for in-edges and suf of counts for out-edges
                in_edge_counts = 0
                out_edge_counts = 0
                for parent_id in parents:
                    in_edge_counts += self.edge_map[(parent_id, node_id)].count
                for child_id in children:
                    out_edge_counts += self.edge_map[(node_id, child_id)].count

                if in_edge_counts != node.count or out_edge_counts != node.count:
                    print(
                        f"Bad count {node_id}: {node.count} -> {in_edge_counts}, {out_edge_counts}")
                    is_valid = False
        if is_valid:
            print("All counts are valid")
        else:
            print("Counts are not valid")

    def compute_complexity(self):
        and_split_count = 0
        and_join_count = 0
        xor_split_count = 0
        xor_join_count = 0
        task_count = 0
        edge_count = 1  # out from start
        cfc = 0

        for node_id in self.nodes:
            if node_id.startswith("node"):
                continue
            edge_count += len(self.graph[node_id])
            if node_id.startswith("task"):
                task_count += 1
            elif node_id.startswith("and_split"):
                and_split_count += 1
                cfc += 1
            elif node_id.startswith("and_join"):
                and_join_count += 1
            elif node_id.startswith("xor_split"):
                xor_split_count += 1
                cfc += len(self.graph[node_id])
            elif node_id.startswith("xor_join"):
                xor_join_count += 1

        got_nodes = and_split_count + and_join_count + xor_split_count + xor_join_count + task_count + 2

        print(f"Expected nodes: {len(self.nodes)}")
        print(f"total node count: {got_nodes}")
        print(f"Expected edges: {len(self.edges)}")
        print(f"edge_count: {edge_count}")
        print(f"cfc: {cfc}")
        print(f"cnc: {edge_count / got_nodes}")

        print(f"and_split_count: {and_split_count}")
        print(f"and_join_count: {and_join_count}")
        print(f"xor_split_count: {xor_split_count}")
        print(f"xor_join_count: {xor_join_count}")
        print(f"task_count: {task_count}")

    def validate_degree(self):
        gateways = [n for n in self.nodes if ("xor" in n or "and" in n)]
        n_trivial = 0
        for g in gateways:
            if len(self.graph[g]) == 1 and len(self.reverse_graph[g]) == 1:
                print(f"Redundant: {g}")
                n_trivial += 1
        if n_trivial == 0:
            print("Model has no trivial gateways")
        else:
            print(f"Model has {n_trivial} trivial gateways")

    def to_dot_string(self, rank="TB", lighten_threshold=0) -> str:
        """

        :param rank:
        :param lighten_threshold:
        :return:
        """
        result = "digraph {\n"
        result += f'\trankdir="{rank}";\n'

        # Write nodes
        for node_id, node in self.nodes.items():
            if node.node_type == "start" or node.node_type == "end":
                result += f'\t{node_id} [label="{node.name}\n{node.node_id}\n{node.count}",shape=square];\n'
            elif node.node_type == "task":
                result += f'\t{node_id} [label="{node.name}\n{node.node_id}\n{node.count}",{node_style(node, lighten_threshold)}];\n'
            elif "xor" in node.node_type:
                result += f'\t{node_id} [label="{node.node_id}\n{node.count}",shape=diamond,{node_style(node, lighten_threshold)}];\n'
            elif "and" in node.node_type:
                result += f'\t{node_id} [label="{node.node_id}\n{node.count}",shape=diamond,{node_style(node, lighten_threshold)}];\n'
            elif node.node_type == "fake_node":
                result += f'\t{node_id} [label="{node.node_id}\n{node.count}",shape=circle,style=filled,fill_color=black];\n'

        result += "\n"
        # Write edges
        for edge in self.edges:
            if edge.count <= lighten_threshold:
                result += f'\t{edge.src} -> {edge.dst} [label="{edge.count}",color=gray80];\n'
            else:
                result += f'\t{edge.src} -> {edge.dst} [label="{edge.count}"];\n'
        result += "}"
        return result

    def to_simple_dot_string(self, rank="TB") -> str:
        """
        :param rank:
        :return:
        """
        lighten_threshold = -99999
        result = "digraph {\n"
        result += f'\trankdir="{rank}";\n'

        # Write nodes
        for node_id, node in self.nodes.items():
            if node.node_type == "start" or node.node_type == "end":
                result += f'\t{node_id} [label="{node.name}",shape=square];\n'
            elif node.node_type == "task":
                if "|" not in node.name:
                    result += f'\t{node_id} [label="{node.name}"];\n'
                else:
                    name_parts = node.name.split("|")
                    result += f'\t{node_id} [label="{name_parts[0]}\n{name_parts[1]}",{node_style(node, lighten_threshold)}];\n'
            elif "xor" in node.node_type:
                result += f'\t{node_id} [label="×",shape=diamond,{node_style(node, lighten_threshold)},fontsize=18];\n'
            elif "and" in node.node_type:
                result += f'\t{node_id} [label="+",shape=diamond,{node_style(node, lighten_threshold)},fontsize=18];\n'
            elif node.node_type == "fake_node":
                result += f'\t{node_id} [label="FAKE",shape=circle,style=filled,fill_color=black];\n'

        result += "\n"
        # Write edges
        for edge in self.edges:
            if edge.count <= lighten_threshold:
                result += f'\t{edge.src} -> {edge.dst} [color=gray80];\n'
            else:
                result += f'\t{edge.src} -> {edge.dst};\n'
        result += "}"
        return result

    def to_reduced_dot_string(self, rank="TB") -> str:
        """
        :param rank:
        :return:
        """
        lighten_threshold = -99999
        result = "digraph {\n"
        result += f'\trankdir="{rank}";\n'

        # Write nodes
        for node_id, node in self.nodes.items():
            if node.node_type == "start" or node.node_type == "end":
                result += f'\t{node_id} [label="{node.name}",shape=square];\n'
            elif node.node_type == "task":
                name_parts = node.name.split("|")
                result += f'\t{node_id} [label="{name_parts[0]}\n{name_parts[1]}",{node_style(node, lighten_threshold)}];\n'
            elif "xor" in node.node_type:
                result += f'\t{node_id} [label="×",shape=diamond,{node_style(node, lighten_threshold)},fontsize=18];\n'
            elif "and" in node.node_type:
                result += f'\t{node_id} [label="+",shape=diamond,{node_style(node, lighten_threshold)},fontsize=18];\n'
            elif node.node_type == "fake_node":
                result += f'\t{node_id} [label="FAKE",shape=circle,style=filled,fill_color=black];\n'

        result += "\n"
        # Write edges
        for edge in self.edges:
            result += f'\t{edge.src} -> {edge.dst} [label="{edge.count}"];\n'
        result += "}"
        return result

    def render(self, rank="TB", lighten_threshold=-1, simple=False, reduced=False, name="tmp"):
        """
        Visualizes the model in the system default image viewer
        :param rank: Direction of the model, taken from graphviz
        :param lighten_threshold: cutoff to render edges as light.
                                  Set to -1 to render all edges normally
        """
        if simple:
            res = self.to_simple_dot_string(rank=rank)
        elif reduced:
            res = self.to_reduced_dot_string(rank=rank)
        else:
            res = self.to_dot_string(rank=rank, lighten_threshold=lighten_threshold)
        s = graphviz.Source(res, format="svg")
        s.view(filename=name, cleanup=True)

    def save(self, filename: str, directory="out", format: str = "png", rank="TB",
             lighten_threshold=-1, view=False, simple=False, reduced=False):
        """
        Stores the model in the specified format using graphviz
        :param filename: Filename to store the result. Does not contain the path to the directory
        :param directory: Directory to store the result in
        :param format: Extension, taken from graphviz. Default is svg
        :param rank: Direction of the model, taken from graphviz
        :param lighten_threshold: cutoff to render edges as light.
                                  Set to -1 to render all edges normally
        :param view: True to directly show the result, False to only store to a file
        """
        if simple:
            res = self.to_simple_dot_string(rank=rank)
        elif reduced:
            res = self.to_reduced_dot_string(rank=rank)
        else:
            res = self.to_dot_string(rank=rank, lighten_threshold=lighten_threshold)
        s = graphviz.Source(res, format=format, filename=filename, directory=directory)
        s.render(filename, directory, view=view)


def _construct_graph(edges: List[Edge]) -> Graph:
    graph = defaultdict(list)
    for edge in edges:
        graph[edge.src].append(edge.dst)
    return graph


def _reverse_graph(base: Graph) -> Graph:
    reverse = defaultdict(list)
    for node, children in base.items():
        for child in children:
            reverse[child].append(node)

    return reverse


def _construct_label_mapping(nodes: Dict[str, Node]) -> Dict[str, List[str]]:
    label_to_node_mapping = {}
    for node_id, node in nodes.items():
        # Don't keep a mapping for gateways
        if node_id.startswith("xor") or node_id.startswith("and"):
            continue

        if node.name not in label_to_node_mapping:
            label_to_node_mapping[node.name] = [node_id]
        elif node_id.startswith("task"):
            label_to_node_mapping[node.name].append(node_id)
            # print(f"Duplicate label: {node.name}")
            # raise BaseException(f"Duplicate label: {node.name}")
    return label_to_node_mapping


def _construct_reverse_label_mapping(nodes: Dict[str, Node]) -> Dict[str, str]:
    mapping = {}
    for node_id, node in nodes.items():
        if node_id.startswith("xor") or node_id.startswith("and"):
            continue

        if node.name not in mapping:
            mapping[node_id] = node.name
        elif node_id.startswith("task"):
            print("ERR - duplicate in reverse mapping")
    return mapping


def node_style(n: Node, pale_threshold) -> str:
    if "xor" in n.node_type:
        if n.count <= pale_threshold:
            return "fillcolor=lemonchiffon1,style=filled"
        return "fillcolor=yellow,style=filled"
    if "and" in n.node_type:
        if n.count <= pale_threshold:
            return "fillcolor=greenyellow,style=filled"
        return "fillcolor=green4,style=filled,fontcolor=white"

    stage = n.name.split("|")[0]
    if stage in stage_color_mapping:
        color = stage_color_mapping[stage]
        if n.count <= pale_threshold:
            color = light_color_mapping[color]
        return f"fillcolor={color},style=filled"
    return "white"


light_color_mapping = {
    "white": "white",
    "dodgerblue": "lightskyblue1",
    "orangered": "lightpink2",
    "pink": "pink",
}

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
