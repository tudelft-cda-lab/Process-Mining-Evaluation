package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.PetrinetWithMarking;

import com.raffaeleconforti.wrappers.settings.MiningSettings;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramFactory;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetImpl;
import org.processmining.processtree.ProcessTree;

import java.util.*;

import static com.raffaeleconforti.benchmark.Common.*;

@Plugin(name = "Prefix Tree Miner", parameterLabels = {"Log"},
        returnLabels = {"PetrinetWithMarking"},
        returnTypes = {PetrinetWithMarking.class})
public class FlowerMiner implements MiningAlgorithm {

    public FlowerMiner() {
    }

    @Override
    public boolean canMineProcessTree() {
        return false;
    }

    @Override
    public ProcessTree mineProcessTree(UIPluginContext context, XLog log, boolean structure, MiningSettings params, XEventClassifier xEventClassifier) {
        return null;
    }

    @Override
    public PetrinetWithMarking minePetrinet(UIPluginContext context, XLog log, boolean structure, MiningSettings params, XEventClassifier xEventClassifier) {
        Petrinet res = new PetrinetImpl("flower_net");
        Place start = res.addPlace("start");
        Place center = res.addPlace("center");
        Place end = res.addPlace("end");

        // Silent transitions from start -> center and center -> end
        addTau(res, start, center);
        addTau(res, center, end);

        Set<String> seenTransitions = new HashSet<>();
        List<List<String>> traces = toTraces(log);
        for (List<String> trace : traces) {
            for (String event : trace) {
                if (seenTransitions.contains(event)) {
                    continue;
                }
                seenTransitions.add(event);
                Transition t = res.addTransition(event);
                res.addArc(center, t);
                res.addArc(t, center);
            }
        }

        return markNet(context, res, start, end);
    }

    @Override
    public BPMNDiagram mineBPMNDiagram(UIPluginContext context, XLog log, boolean structure, MiningSettings params, XEventClassifier xEventClassifier) {
        BPMNDiagram res = BPMNDiagramFactory.newBPMNDiagram("bpmn_flower");
        Event start = res.addEvent("start", Event.EventType.START, Event.EventTrigger.NONE, Event.EventUse.CATCH, true, null);
        Event end = res.addEvent("end", Event.EventType.END, Event.EventTrigger.NONE, Event.EventUse.CATCH, true, null);

        Gateway split = res.addGateway("XOR_split", Gateway.GatewayType.DATABASED);
        Gateway join = res.addGateway("XOR_join", Gateway.GatewayType.DATABASED);

        res.addFlow(start, join, "");
        res.addFlow(join, split, "");
        res.addFlow(split, end, "");

        Set<String> seenTransitions = new HashSet<>();
        List<List<String>> traces = toTraces(log);
        for (List<String> trace : traces) {
            for (String event : trace) {
                if (seenTransitions.contains(event)) {
                    continue;
                }
                seenTransitions.add(event);
                Activity a = res.addActivity(event, false, false, false, false, false);
                res.addFlow(split, a, "");
                res.addFlow(a, join, "");
            }
        }

        return res;
    }

    @Override
    public String getAlgorithmName() {
        return "FlowerModelMiner";
    }

    @Override
    public String getAcronym() {
        return "FLOWER";
    }
}

