package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.wrappers.PetrinetWithMarking;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramFactory;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetImpl;

import java.util.*;

import static com.raffaeleconforti.benchmark.Common.*;


public class SMToPNConverter {
    public static PetrinetWithMarking dfaToPN(final PluginContext context, DFA dfa) {
        // Each state is a place
        // Each edge is a transition with the corresponding arcs
        // Each node with a non-zero final count has a tau to the final place

        Petrinet res = new PetrinetImpl("net_from_tree");

        Map<Integer, Place> stateMapping = new HashMap<>();
        Place start = res.addPlace("start");
        Place end = res.addPlace("end");

        for (DFANode state : dfa.getNodes()) {

            Place p = res.addPlace(String.format("place_%d", stateMapping.size()));
            stateMapping.put(state.getId(), p);
            if (state.isTerminating()) {
                addTau(res, p, end);
            }
        }

        for (DFAEdge edge : dfa.getEdges()) {
            addTransition(res, stateMapping.get(edge.getSrc()), edge.getLabel(), stateMapping.get(edge.getDst()));
        }

        addTau(res, start, stateMapping.get(0));

        return markNet(context, res, start, end);
    }


    private static void addTransition(Petrinet net, Place src, String label, Place dst) {
        Transition t = net.addTransition(label);
        net.addArc(src, t);
        net.addArc(t, dst);
    }

    public static BPMNDiagram dfaToBPMNMerged(PluginContext ctx, DFA dfa) {
        Graph g = new Graph();
        GraphNode startNode = new GraphNode(-1);
        GraphNode finalNode = new GraphNode(999999999);
        g.ensureNode(startNode);
        g.ensureNode(finalNode);

        for (DFANode n : dfa.getNodes()) {
            GraphNode node = new GraphNode(n.getId());
            g.ensureNode(node);
            if (n.isTerminating()) {
                g.addEdge(node, finalNode);
            }
        }
        g.addEdge(startNode, new GraphNode(0));

        for (DFAEdge e : dfa.getEdges()) {
            GraphNode n = new GraphNode(e.getDst(), e.getLabel());
            g.ensureNode(n);
            g.addEdge(new GraphNode(e.getSrc()), n);
            g.addEdge(n, new GraphNode(e.getDst()));
        }

        g.simplifyGraph();

        Map<GraphNode, BPMNNode> inNodes = new HashMap<>();
        Map<GraphNode, BPMNNode> outNodes = new HashMap<>();
        BPMNDiagram res = BPMNDiagramFactory.newBPMNDiagram("bpmn_from_sm");

        Event start = res.addEvent("start", Event.EventType.START, Event.EventTrigger.NONE, Event.EventUse.CATCH, true, null);
        Event end = res.addEvent("end", Event.EventType.END, Event.EventTrigger.NONE, Event.EventUse.CATCH, true, null);

        for (GraphNode n : g.nodes()) {
            int inDegree = g.inEdges.get(n).size();
            int outDegree = g.outEdges.get(n).size();

            if (n.equals(startNode)) {
                if (outDegree == 1) {
                    outNodes.put(n, start);
                } else {
                    Gateway xor = res.addGateway("XOR_SPIT_START", Gateway.GatewayType.DATABASED);
                    res.addFlow(start, xor, "");
                    outNodes.put(n, xor);
                }
            } else if (n.equals(finalNode)) {
                if (inDegree == 1) {
                    inNodes.put(n, end);
                } else {
                    Gateway xor = res.addGateway("XOR_JOIN_END", Gateway.GatewayType.DATABASED);
                    res.addFlow(xor, end, "");
                    inNodes.put(n, xor);
                }
            } else if (n.isState()) {
                assert inDegree >= 1 && outDegree >= 1 && inDegree + outDegree > 2;
                if (inDegree == 1 || outDegree == 1) {
                    Gateway xor = res.addGateway("XOR_state_single", Gateway.GatewayType.DATABASED);
                    inNodes.put(n, xor);
                    outNodes.put(n, xor);
                } else {
                    Gateway xorIn = res.addGateway("XOR_IN_JOIN", Gateway.GatewayType.DATABASED);
                    Gateway xorOut = res.addGateway("XOR_OUT_SPLIT", Gateway.GatewayType.DATABASED);
                    res.addFlow(xorIn, xorOut, "");
                    inNodes.put(n, xorIn);
                    outNodes.put(n, xorOut);
                }
            } else {
                Activity a = res.addActivity(n.getLabel(), false, false, false, false, false);
                if (inDegree > 1) {
                    Gateway xor = res.addGateway(String.format("XOR_in_%s", n.getLabel()), Gateway.GatewayType.DATABASED);
                    res.addFlow(xor, a, "");
                    inNodes.put(n, xor);
                } else {
                    inNodes.put(n, a);
                }

                if (outDegree > 1) {
                    logMessage("ERR - activity cannot have out degree > 1");
                    Gateway xor = res.addGateway(String.format("XOR_out_%s", n.getLabel()), Gateway.GatewayType.DATABASED);
                    res.addFlow(a, xor, "");
                    outNodes.put(n, xor);
                } else {
                    outNodes.put(n, a);
                }
            }
        }

        for (GraphNode from : g.nodes()) {
            for (GraphNode to : g.outEdges.get(from)) {
                res.addFlow(outNodes.get(from), inNodes.get(to), "");
            }
        }

        return res;
    }
}

// Create graph
// - Each node represents a state or (edgeLabel, dst) par
// - Edges are unlabelled

class GraphNode {
    private final int state;
    private final String label;
    public static final String STATE_STR = "_____STATE_____";

    public GraphNode(int state, String label) {
        this.label = label;
        this.state = state;
    }

    public GraphNode(int state) {
        this.label = STATE_STR;
        this.state = state;
    }

    public String getLabel() {
        return label;
    }

    public boolean isState() {
        return this.label.equals(STATE_STR);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphNode graphNode = (GraphNode) o;
        return state == graphNode.state && Objects.equals(label, graphNode.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, label);
    }
}

class Graph {
    Map<GraphNode, Set<GraphNode>> inEdges;
    Map<GraphNode, Set<GraphNode>> outEdges;

    public Graph() {
        this.inEdges = new HashMap<>();
        this.outEdges = new HashMap<>();
    }

    public void ensureNode(GraphNode node) {
        if (inEdges.containsKey(node)) {
            return;
        }
        inEdges.put(node, new HashSet<>());
        outEdges.put(node, new HashSet<>());
    }

    public void addEdge(GraphNode from, GraphNode to) {
        outEdges.get(from).add(to);
        inEdges.get(to).add(from);
    }

    public void simplifyGraph() {
        boolean hasSimplified = true;
        while (hasSimplified) {
            hasSimplified = false;
            Set<GraphNode> nodes = inEdges.keySet();
            Set<GraphNode> simplifiedNodes = new HashSet<>();
            for (GraphNode n : nodes) {
                if (!n.isState()) {
                    continue;
                }
                if (inEdges.get(n).size() == 1 && outEdges.get(n).size() == 1) {
                    hasSimplified = true;
                    GraphNode inNode = inEdges.get(n).iterator().next();
                    GraphNode outNode = outEdges.get(n).iterator().next();

                    outEdges.get(inNode).remove(n);
                    outEdges.get(inNode).add(outNode);
                    inEdges.get(outNode).remove(n);
                    inEdges.get(outNode).add(inNode);

                    simplifiedNodes.add(n);
                }
            }
            for (GraphNode n : simplifiedNodes) {
                inEdges.remove(n);
                outEdges.remove(n);
            }
        }
    }

    public Set<GraphNode> nodes() {
        return this.inEdges.keySet();
    }
}