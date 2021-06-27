import sys
from xml.dom import minidom

from src.bpmn_model import *


def attr(xml_node, key: str) -> str:
    """
    Helper to retrieve an item from an XML node
    :param xml_node: node to retrieve
    :param key: key of the item
    :return: the value associated to the key
    """
    return xml_node.attributes[key].nodeValue


def load_bpmn(filename: str) -> BPMNModel:
    """
    Loads a .bpmn file
    :param filename: path to the bpmn file
    :return: the internal representation of the BPMN model
    """
    assert filename.endswith(".bpmn")
    xml = minidom.parse(filename)

    start_event = xml.getElementsByTagName("startEvent")[0]
    end_event = xml.getElementsByTagName("endEvent")[0]
    tasks = xml.getElementsByTagName("task")
    exclusive_gateways = xml.getElementsByTagName("exclusiveGateway")
    parallel_gateways = xml.getElementsByTagName("parallelGateway")
    flows = xml.getElementsByTagName("sequenceFlow")

    if len(xml.getElementsByTagName("inclusiveGateway")) > 0:
        print("Model contains OR-gateways which are not supported")
        sys.exit(-1)

    id_mapping = {}
    nodes: Dict[str, Node] = {}
    edges: List[Edge] = []

    def add_node(external_id: str, n: Node):
        if n.node_id in nodes:
            print(f"Duplicate internal id: {n.node_id}")
        if external_id in id_mapping:
            print(f"Duplicate bpmn id: {external_id}")
        id_mapping[external_id] = n.node_id
        nodes[n.node_id] = n

    # Parse start and end node
    add_node(attr(start_event, "id"), Node("start", "node_start", "start"))
    add_node(attr(end_event, "id"), Node("end", "node_end", "end"))

    # Add all tasks and gateways
    for (task_id, task) in enumerate(tasks):
        add_node(attr(task, "id"), Node("task", f"task_{task_id}", attr(task, "name")))
    for (task_id, gateway) in enumerate(exclusive_gateways):
        gateway_type = "xor_split" if attr(gateway, "gatewayDirection") == "Diverging" \
            else "xor_join"
        add_node(attr(gateway, "id"),
                 Node(gateway_type, f"{gateway_type}_{task_id}", attr(gateway, "name")))

    for (task_id, gateway) in enumerate(parallel_gateways):
        gateway_type = "and_split" if attr(gateway, "gatewayDirection") == "Diverging" \
            else "and_join"
        add_node(attr(gateway, "id"),
                 Node(gateway_type, f"{gateway_type}_{task_id}", gateway.attributes["name"]))

    and_count = 0
    xor_count = 0

    for flow in flows:
        src_id = id_mapping[attr(flow, "sourceRef")]
        dest_id = id_mapping[attr(flow, "targetRef")]
        edges.append(Edge(src_id, dest_id))
        if src_id.startswith("and"):
            and_count += 1
        elif src_id.startswith("xor"):
            xor_count += 1
        if dest_id.startswith("and"):
            and_count -= 1
        elif dest_id.startswith("xor"):
            xor_count -= 1

    # Sanity check. Should never occur as the models_bpmn are assumed to be sound
    if and_count != 0:
        print(f"AND degree mismatch: {and_count}")
    if xor_count != 0:
        print(f"XOR degree mismatch: {xor_count}")

    return BPMNModel(nodes, edges)


if __name__ == '__main__':
    model_file = "dfa_bpmn_alt/markov-cptc_18_reversed.bpmn"
    model = load_bpmn(model_file)
    model.validate_degree()
    model.save("markov_18_reversed_dfa", simple=True, lighten_threshold=-1, format="svg", view=True)
    print("Done")
