package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.context.FakePluginContext;
import com.raffaeleconforti.conversion.petrinet.PetriNetToBPMNConverter;
import com.raffaeleconforti.log.util.LogCloner;
import com.raffaeleconforti.measurements.impl.Soundness;
import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.PetrinetWithMarking;
import com.raffaeleconforti.wrappers.impl.inductive.InductiveMinerIMWrapper;
import com.raffaeleconforti.wrappers.settings.MiningSettings;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIContext;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.plugins.bpmn.plugins.BpmnExportPlugin;
import org.processmining.plugins.pnml.exporting.PnmlExportNetToPNML;
import org.processmining.processtree.ProcessTree;
import org.processmining.processtree.ptml.exporting.PtmlExportTree;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.raffaeleconforti.benchmark.Common.*;
import static com.raffaeleconforti.benchmark.SMToPNConverter.dfaToBPMNMerged;
import static com.raffaeleconforti.benchmark.SMToPNConverter.dfaToPN;

public class ModelGenerator {

    private static final XEventClassifier xEventClassifier = new XEventNameClassifier();

    public static void main(String[] args) {
        // Base method: load an entire directory
//        String logFolder = "path\\to\\traces";
//        Map<String, Object> logs = loadLogs(logFolder);

        // Alternative: manually define select logs
        Map<String, Object> logs = new HashMap<>();
        logs.put("test_trace.txt", "..\\..\\data\\base_traces\\cptc_18_reversed.txt");


        LogCloner logCloner = new LogCloner();

        setupResultsDir("models");


        List<MiningAlgorithm> miners = new ArrayList<>();
        miners.add(new InductiveMinerIMWrapper());
//        miners.add(new InductiveMinerIMfWrapper());
//        miners.add(new InductiveMinerIMcWrapper());
//        miners.add(new SplitMinerWrapper());
//        miners.add(new PrefixTreeMiner());
//        miners.add(new FlowerMiner());


        MiningSettings settings = null;
//        MiningSettings settings = new MiningSettings();
//        settings.setParam("noiseThresholdIMf", new Float(0.05));
        UIPluginContext context = new FakePluginContext();

        for (MiningAlgorithm miner : miners) {

            logMessage("Mining with miner: " + miner.getAlgorithmName());
            for (String logName : logs.keySet()) {
                logMessage("Mining log: " + logName);

                XLog miningLog = loadLog(logs.get(logName));


                if (miningLog == null) {
                    logMessage("ERR - log is null: " + logName);
                    continue;
                }


                String fileName = miner.getAcronym() + "_" + logName.split("\\.")[0];

                logMessage("Mining PN");
                PetrinetWithMarking result = miner.minePetrinet(context, logCloner.cloneLog(miningLog), false, settings, xEventClassifier);
                logMessage("Done mining petri net, checking soundness");
                boolean isSound = miner.getAcronym().startsWith("IM") || Soundness.isSound(result);
                if (!isSound) {
                    logMessage("ERR: Petri net is not sound -> skipping export");
                    continue;
                }

                BPMNDiagram convertedPN = PetriNetToBPMNConverter.convert(result.getPetrinet(), result.getInitialMarking(), result.getFinalMarking(), true);
                String bpmnPathPn = "./results/models/" + fileName + "_pn.bpmn";
                try {
                    exportBPMN(convertedPN, bpmnPathPn);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("ERR-continue");
                }

                String pnPath = "./results/models/" + fileName + ".pnml";
                exportPetrinet(result, pnPath);

                logMessage("Mining BPMN");

                BPMNDiagram bpmn = miner.mineBPMNDiagram(context, logCloner.cloneLog(miningLog), false, settings, xEventClassifier);
                if (bpmn == null) {
                    logMessage("Result is null");
                    continue;
                }
                logMessage("Done mining BPMN net, saving");

                String bpmnPath = "./results/models/" + fileName + ".bpmn";
                try {
                    exportBPMN(bpmn, bpmnPath);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("ERR-continue");
                }

                if (miner.canMineProcessTree()) {
                    logMessage("Mining process tree");
                    ProcessTree tree = miner.mineProcessTree(context, logCloner.cloneLog(miningLog), false, settings, xEventClassifier);
                    logMessage("Done mining Process tree, saving");

                    String treePath = "./results/models/" + fileName + ".ptml";
                    try {
                        exportProcessTree(tree, treePath);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("ERR-continue");
                    }


                } else {
                    logMessage("Cannot mine tree, skipping");
                }
            }
        }
    }

    /**
     * Helper to convert a state machine to a BPMN diagram
     */
    private static void exportSMAsBPMN() {
        String smFile = "C:\\Users\\Geert\\Desktop\\sm_eval\\machines\\cptc_18_reversed\\no_sink_merge\\cptc_18_reversed.txt.ff.final.dot.json";

        DFA dfa = DFA.loadJSON(smFile);
        UIPluginContext ctx = new FakePluginContext();
        PetrinetWithMarking net = dfaToPN(ctx, dfa);

        BPMNDiagram converted = PetriNetToBPMNConverter.convert(net.getPetrinet(), net.getInitialMarking(), net.getFinalMarking(), true);
        BPMNDiagram bpmnBase = dfaToBPMNMerged(ctx, dfa);

        exportBPMN(converted, "./results/models/no_sink_merge_pn.bpmn");
        exportBPMN(bpmnBase, "./results/models/no_sink_merge_base.bpmn");
    }


    public static void exportBPMN(BPMNDiagram diagram, String path) {
        BpmnExportPlugin bpmnExportPlugin = new BpmnExportPlugin();
        UIContext context = new UIContext();
        // NOTE: https://www.win.tue.nl/promforum/discussion/1042/using-prom-within-a-docker-container
        UIPluginContext uiPluginContext = context.getMainPluginContext();

        File outFile = new File(path);
        if (!outFile.exists()) {
            logMessage("Outfile does not exist: " + path);
        }
        try {
            bpmnExportPlugin.export(null, diagram, outFile);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR - impossible to export .bpmn");
        }
    }

    protected static void exportPetrinet(PetrinetWithMarking petrinetWithMarking, String path) {
//        ExportAcceptingPetriNetPlugin exportAcceptingPetriNetPlugin = new ExportAcceptingPetriNetPlugin();

        PnmlExportNetToPNML plugin = new PnmlExportNetToPNML();
        UIContext context = new UIContext();
        // NOTE: https://www.win.tue.nl/promforum/discussion/1042/using-prom-within-a-docker-container
        UIPluginContext uiPluginContext = context.getMainPluginContext();
        try {
            plugin.exportPetriNetToPNMLFile(
                    uiPluginContext,
//                    new AcceptingPetriNetImpl(petrinetWithMarking.getPetrinet(), petrinetWithMarking.getInitialMarking(), petrinetWithMarking.getFinalMarking()),
//                    null,
                    petrinetWithMarking.getPetrinet(),
                    new File(path));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR - impossible to export the petrinet to: " + path);
        }
    }

    protected static void exportProcessTree(ProcessTree tree, String path) {
//        ExportAcceptingPetriNetPlugin exportAcceptingPetriNetPlugin = new ExportAcceptingPetriNetPlugin();

        PtmlExportTree plugin = new PtmlExportTree();
        UIContext context = new UIContext();
        // NOTE: https://www.win.tue.nl/promforum/discussion/1042/using-prom-within-a-docker-container
        UIPluginContext uiPluginContext = context.getMainPluginContext();
        try {
            plugin.exportDefault(null, tree, new File(path));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR - impossible to export the process tree to: " + path);
        }
    }
}
