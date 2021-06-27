package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.conversion.petrinet.PetriNetToBPMNConverter;
import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.PetrinetWithMarking;
import com.raffaeleconforti.wrappers.settings.MiningSettings;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
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
import org.processmining.processtree.ProcessTree;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static com.raffaeleconforti.benchmark.Common.*;

@Plugin(name = "Prefix Tree Miner", parameterLabels = {"Log"},
        returnLabels = {"PetrinetWithMarking"},
        returnTypes = {PetrinetWithMarking.class})
public class PrefixTreeMiner implements MiningAlgorithm {

    public PrefixTreeMiner() {
    }

    private TreeNode toTree(List<List<String>> traces) {
        TreeNode root = new TreeNode("root");

        for (List<String> trace : traces) {
            TreeNode currentNode = root;
            for (String event : trace) {
                currentNode = currentNode.getChild(event);
            }
            currentNode.addFinal();
        }
        return root;
    }

    private PetrinetWithMarking treeToPN(final PluginContext context, List<List<String>> traces) {
        TreeNode root = toTree(traces);

        // Each node is a place, each edge is a transition
        Petrinet res = new PetrinetImpl("net_from_tree");
        Queue<QueueItem<Transition>> nodeQueue = new LinkedList<>();

        Place start = res.addPlace("start");
        Place end = res.addPlace("end");

        for (TreeNode child : root.children()) {
            Transition t = res.addTransition(child.getLabel());
            res.addArc(start, t);
            nodeQueue.add(new QueueItem<>(child, t));
        }

        while (!nodeQueue.isEmpty()) {
            QueueItem<Transition> entry = nodeQueue.poll();
            Place p = res.addPlace(String.format("place_%d", res.getPlaces().size()));
            res.addArc(entry.reachedFrom, p);
            if (entry.node.isFinal()) {
                addTau(res, p, end);
            }

            for (TreeNode child : entry.node.children()) {
                Transition t = res.addTransition(child.getLabel());
                res.addArc(p, t);
                nodeQueue.add(new QueueItem<>(child, t));
            }
        }

        return markNet(context, res, start, end);
    }

    final BPMNDiagram treeToBPMN(PluginContext context, List<List<String>> traces) {
        TreeNode root = toTree(traces);

        BPMNDiagram res = BPMNDiagramFactory.newBPMNDiagram("bpmn_from_tree");
        Event start = res.addEvent("start", Event.EventType.START, Event.EventTrigger.NONE, Event.EventUse.CATCH, true, null);
        Event end = res.addEvent("end", Event.EventType.END, Event.EventTrigger.NONE, Event.EventUse.CATCH, true, null);

        // if n_children == 1 AND not final: direct edge to activity
        // If n_children > 1 AND not final: edge to XOR split, edge from XOR split to each child
        // if n_children == 0 AND final: edge to XOR join before end
        // if n_children > 0 AND final: edge to XOR split, one edge from split to final join, edge from split to each child
        Queue<QueueItem<BPMNNode>> queue = new LinkedList<>();

        Gateway rootXOR = res.addGateway("XOR_SPLIT_ROOT", Gateway.GatewayType.DATABASED);
        res.addFlow(start, rootXOR, "");

        Gateway endXOR = res.addGateway("XOR_JOIN_FINAL", Gateway.GatewayType.DATABASED);
        res.addFlow(endXOR, end, "");

        for (TreeNode child : root.children()) {
            queue.add(new QueueItem<>(child, rootXOR));
        }

        while (!queue.isEmpty()) {
            QueueItem<BPMNNode> entry = queue.poll();
            if (entry == null) {
                break;
            }
            TreeNode node = entry.node;

            Activity a = res.addActivity(node.getLabel(), false, false, false, false, false);
            res.addFlow(entry.reachedFrom, a, "");
            if (node.children.isEmpty()) {
                res.addFlow(a, endXOR, "");
                continue;
            }
            if (node.isFinal()) {
                Gateway xor = res.addGateway("XOR_split", Gateway.GatewayType.DATABASED);
                res.addFlow(a, xor, "");
                res.addFlow(xor, endXOR, "");
                for (TreeNode child : node.children()) {
                    queue.add(new QueueItem<>(child, xor));
                }
            } else if (node.children.size() == 1) {
                queue.add(new QueueItem<>(node.children().iterator().next(), a));
            } else {
                Gateway xor = res.addGateway("XOR_split", Gateway.GatewayType.DATABASED);
                res.addFlow(a, xor, "");
                for (TreeNode child : node.children()) {
                    queue.add(new QueueItem<>(child, xor));
                }
            }
        }


        return res;
    }

    @Override
    public boolean canMineProcessTree() {
        return false;
    }

    @Override
    public ProcessTree mineProcessTree(UIPluginContext context, XLog log, boolean structure, MiningSettings params, XEventClassifier xEventClassifier) {
        return null;
    }

    // Just copy-pasted to conform to satisfy the interfaces
    @UITopiaVariant(affiliation = UITopiaVariant.EHV,
            author = "Raffaele Conforti",
            email = "raffaele.conforti@unimelb.edu.au",
            pack = "Noise Filtering")
    @PluginVariant(variantLabel = "Prefix Tree Miner", requiredParameterLabels = {0})
    public PetrinetWithMarking minePetrinet(UIPluginContext context, XLog log) {
        return minePetrinet(context, log, false, null, new XEventNameClassifier());
    }


    public PetrinetWithMarking minePetrinet(UIPluginContext context, XLog log, boolean structure, MiningSettings params, XEventClassifier xEventClassifier) {
        return treeToPN(context, toTraces(log));
    }

    @Override
    public BPMNDiagram mineBPMNDiagram(UIPluginContext context, XLog log, boolean structure, MiningSettings params, XEventClassifier xEventClassifier) {
        return treeToBPMN(context, toTraces(log));
    }

    @Override
    public String getAlgorithmName() {
        return "PrefixTreeMiner";
    }

    @Override
    public String getAcronym() {
        return "PTM";
    }
}

