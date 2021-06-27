import sys
from xml.dom import minidom

from src.bpmn_model import *
from src.read_bpmn import attr


class _TreeNode:
    def __init__(self, node_type: str, external_id: str, internal_id: str, name: str):
        self.node_type = node_type
        self.external_id = external_id
        self.internal_id = internal_id
        self.name = name

        self.children = []
        self.parent: Optional[_TreeNode] = None


def load_process_tree(filename: str, render_tree=False, flatten=False) -> BPMNModel:
    """
    Reads a .ptml file and converts the contents to a BPMN model
    :param filename: path to the .ptml file
    :param render_tree: if set to True, the read tree is rendered
    :param flatten: if set to True, and_splits and and_joins are flattened in the result
    :return: the BPMN model equivalent to this tree
    """
    assert filename.endswith(".ptml")
    xml = minidom.parse(filename)

    tree = xml.getElementsByTagName("processTree")
    automatic_tasks = xml.getElementsByTagName("automaticTask")
    manual_tasks = xml.getElementsByTagName("manualTask")
    xor_gateways = xml.getElementsByTagName("xor")
    and_gateways = xml.getElementsByTagName("and")
    xor_loops = xml.getElementsByTagName("xorLoop")
    sequences = xml.getElementsByTagName("sequence")
    relations = xml.getElementsByTagName("parentsNode")

    nodes: Dict[str, _TreeNode] = {}

    internal_id = [0]

    def get_internal_id() -> str:
        val = internal_id[0]
        internal_id[0] = val + 1
        return f"node_{val}"

    def add_nodes(node_list, node_type):
        for sequence_id, node in enumerate(node_list):
            external_id = attr(node, "id")
            if external_id in nodes:
                print(f"ERR - Duplicate external id: {external_id}")
                sys.exit(-1)
            nodes[external_id] = _TreeNode(node_type, external_id, get_internal_id(),
                                           attr(node, "name"))

    # Add all other nodes
    add_nodes(automatic_tasks, "task_automatic")
    add_nodes(manual_tasks, "task_manual")
    add_nodes(xor_gateways, "xor")
    add_nodes(and_gateways, "and")
    add_nodes(xor_loops, "loop_xor")
    add_nodes(sequences, "sequence")

    for edge in relations:
        src = attr(edge, "sourceId")
        dst = attr(edge, "targetId")
        assert src in nodes
        assert dst in nodes
        nodes[src].children.append(nodes[dst])
        nodes[dst].parent = nodes[src]

    root_id = attr(tree[0], "root")
    assert root_id in nodes
    assert nodes[root_id].parent is None

    bpmn_nodes: Dict[str, Node] = {}
    bpmn_edges: List[Edge] = []

    counters = {
        "task": 0,
        "and": 0,
        "xor": 0,
        "fake": 0,
    }
    _tau = ("tau", "tau")

    def get_count(node_type: str) -> int:
        res = counters[node_type]
        counters[node_type] += 1
        return res

    def add_node(n: Node):
        bpmn_nodes[n.node_id] = n

    def add_block_edges(start: str, end: str, block: Tuple[str, str]):
        if block == _tau:
            bpmn_edges.append(Edge(start, end))
        else:
            bpmn_edges.append(Edge(start, block[0]))
            bpmn_edges.append(Edge(block[1], end))

    def convert_node(node: _TreeNode) -> Tuple[str, str]:
        if node.node_type == "task_automatic":
            return _tau
        elif node.node_type == "task_manual":
            node_id = f"task_{get_count('task')}"
            add_node(Node("task", node_id, node.name))
            return node_id, node_id
        elif node.node_type == "xor":
            assert len(node.children) >= 2
            xor_split_id = f"xor_split_{get_count('xor')}"
            xor_join_id = f"xor_join_{get_count('xor')}"
            add_node(Node("xor_split", xor_split_id, "XOR split"))
            add_node(Node("xor_join", xor_join_id, "XOR join"))

            for child_entry_exits in [convert_node(child) for child in node.children]:
                add_block_edges(xor_split_id, xor_join_id, child_entry_exits)
            return xor_split_id, xor_join_id
        elif node.node_type == "and":
            assert len(node.children) >= 2
            and_split_id = f"and_split_{get_count('and')}"
            and_join_id = f"and_join_{get_count('and')}"
            add_node(Node("and_split", and_split_id, "AND split"))
            add_node(Node("and_join", and_join_id, "AND join"))

            for child_entry_exits in [convert_node(child) for child in node.children]:
                # Prevent and_split-and_split edges
                if child_entry_exits[0].startswith("and_split"):
                    fake_id = f"fake_{get_count('fake')}"
                    add_node(Node("fake_node", fake_id, "FAKE"))
                    bpmn_edges.append(Edge(and_split_id, fake_id))
                    bpmn_edges.append(Edge(fake_id, child_entry_exits[0]))
                    bpmn_edges.append(Edge(child_entry_exits[1], and_join_id))
                else:
                    add_block_edges(and_split_id, and_join_id, child_entry_exits)
            return and_split_id, and_join_id
        elif node.node_type == "loop_xor":
            assert len(node.children) == 3

            xor_start_id = f"xor_join_{get_count('xor')}"
            xor_end_id = f"xor_split_{get_count('xor')}"

            do_entry_exit = convert_node(node.children[0])
            while_entry_exit = convert_node(node.children[1])
            after_entry_exit = convert_node(node.children[2])
            assert do_entry_exit != _tau

            add_node(Node("xor_split", xor_end_id, "XOR split"))
            add_node(Node("xor_join", xor_start_id, "XOR join"))

            add_block_edges(xor_start_id, xor_end_id, do_entry_exit)
            add_block_edges(xor_end_id, xor_start_id, while_entry_exit)
            if after_entry_exit == _tau:
                return xor_start_id, xor_end_id
            else:
                bpmn_edges.append(Edge(xor_end_id, after_entry_exit[0]))
                return xor_start_id, after_entry_exit[1]
        elif node.node_type == "sequence":
            # assert len(node.children) >= 2
            entry_exit_ids = [convert_node(child) for child in node.children]
            for start, end in entry_exit_ids:
                assert start != "tau"
                assert end != "tau"

            for i in range(1, len(entry_exit_ids)):
                bpmn_edges.append(Edge(entry_exit_ids[i - 1][1], entry_exit_ids[i][0]))

            return entry_exit_ids[0][0], entry_exit_ids[-1][1]
        else:
            print(f"ERR: unknown node type: {node.node_type}")
            sys.exit(-1)

    process_entry_exit = convert_node(nodes[root_id])

    if render_tree:
        _render_process_tree(nodes[root_id])

    add_node(Node("start", "node_start", "start"))
    add_node(Node("end", "node_end", "end"))

    add_block_edges("node_start", "node_end", process_entry_exit)

    return BPMNModel(bpmn_nodes, bpmn_edges, flatten=flatten)


def _render_process_tree(root: _TreeNode):
    """
    Renders the given process tree using graphviz
    :param root: root node of the tree. All child nodes are contained in the parent node,
    hence this root holds the whole tree
    """
    res = "digraph {\n"
    res += f'\trankdir="TB";\n'

    def add_node(n: _TreeNode, r: str):
        if n.name == "tau":
            assert len(n.children) == 0
            r += f'\t{n.internal_id} [shape=circle,style=filled,fill_color=black];\n'
        elif n.node_type == "task_manual":
            assert len(n.children) == 0
            r += f'\t{n.internal_id} [label="{n.name}",shape=square];\n'
        else:
            r += f'\t{n.internal_id} [label="{n.node_type}\n{n.internal_id}",shape=circle];\n'
            for c in n.children:
                r = add_node(c, r)
                r += f'\t{n.internal_id} -> {c.internal_id};\n'
        return r

    res = add_node(root, res)

    res += "}"
    print(res)
    s = graphviz.Source(res, filename="tmp.png", format="png")
    s.view()


if __name__ == '__main__':
    filename = "models_bpmn/base/IM_cptc_18_reversed.ptml"
    graph = load_process_tree(filename)

    graph.validate_counts()
    graph.render(lighten_threshold=-1, simple=True, name="IM_tree")
