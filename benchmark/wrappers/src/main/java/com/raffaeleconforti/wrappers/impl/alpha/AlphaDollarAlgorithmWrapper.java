package com.raffaeleconforti.wrappers.impl.alpha;

import com.raffaeleconforti.alphadollar.AlphaMixMiner;
import com.raffaeleconforti.conversion.petrinet.PetriNetToBPMNConverter;
import com.raffaeleconforti.log.util.LogImporter;
import com.raffaeleconforti.log.util.LogReaderClassic;
import com.raffaeleconforti.marking.MarkingDiscoverer;
import com.raffaeleconforti.wrappers.LogPreprocessing;
import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.PetrinetWithMarking;
import com.raffaeleconforti.wrappers.settings.MiningSettings;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.log.LogFile;
import org.processmining.framework.models.petrinet.PetriNet;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetImpl;
import org.processmining.processtree.ProcessTree;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by conforti on 20/02/15.
 */
@Plugin(name = "Alpha Dollar Algorithm Wrapper", parameterLabels = {"Log"},
        returnLabels = {"PetrinetWithMarking"},
        returnTypes = {PetrinetWithMarking.class})
public class AlphaDollarAlgorithmWrapper implements MiningAlgorithm {

    @UITopiaVariant(affiliation = UITopiaVariant.EHV,
            author = "Raffaele Conforti",
            email = "raffaele.conforti@unimelb.edu.au",
            pack = "Noise Filtering")
    @PluginVariant(variantLabel = "Alpha Dollar Algorithm Wrapper", requiredParameterLabels = {0})
    public PetrinetWithMarking minePetrinet(UIPluginContext context, XLog log) {
        return minePetrinet(context, log, false, null, new XEventNameClassifier());
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
        LogPreprocessing logPreprocessing = new LogPreprocessing();
        log = logPreprocessing.preprocessLog(context, log);

        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
            }
        }));

        String logName = "tmpLog_ADollar_" +
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) +
                ".mxml.gz";

        try {
            LogImporter.exportToFile("", logName, log);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Petrinet petrinet;
        try {
            LogFile lf = LogFile.getInstance(logName);
            PetriNet result = new AlphaMixMiner().mine((LogReaderClassic) LogReaderClassic.createInstance(null, lf));
            petrinet = getPetrinet(result);
            logPreprocessing.removedAddedElements(petrinet);
        } finally {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
            File lf = new File(logName);
            if (lf.exists() && !lf.delete()) {
                System.out.println("Could not remove A$ log");
            } else {
                System.out.println("Removed A$ log " + logName);
            }
        }

        return new PetrinetWithMarking(petrinet, MarkingDiscoverer.constructInitialMarking(context, petrinet), MarkingDiscoverer.constructFinalMarking(context, petrinet));
    }

    @Override
    public BPMNDiagram mineBPMNDiagram(UIPluginContext context, XLog log, boolean structure, MiningSettings params, XEventClassifier xEventClassifier) {
        PetrinetWithMarking petrinetWithMarking = minePetrinet(context, log, structure, params, xEventClassifier);
        return PetriNetToBPMNConverter.convert(petrinetWithMarking.getPetrinet(), petrinetWithMarking.getInitialMarking(), petrinetWithMarking.getFinalMarking(), true);
    }

    @Override
    public String getAlgorithmName() {
        return "Alpha Dollar";
    }

    private Petrinet getPetrinet(PetriNet result) {
        Petrinet petrinet = new PetrinetImpl("Alpha Dollar");
        Map<org.processmining.framework.models.petrinet.Transition, Transition> transitionUnifiedMap = new HashMap<>();
        Map<org.processmining.framework.models.petrinet.Place, Place> placeUnifiedMap = new HashMap<>();

        for (org.processmining.framework.models.petrinet.Transition t : result.getTransitions()) {
            Transition transition = petrinet.addTransition(t.getLogEvent().getModelElementName());
            transition.setInvisible(t.isInvisibleTask());
            transitionUnifiedMap.put(t, transition);
        }

        for (org.processmining.framework.models.petrinet.Place p : result.getPlaces()) {
            Place place = petrinet.addPlace(p.getName());
            placeUnifiedMap.put(p, place);
        }

        for (org.processmining.framework.models.petrinet.Transition t : result.getTransitions()) {
            for (org.processmining.framework.models.petrinet.Place p : result.getPlaces()) {
                Transition transition = transitionUnifiedMap.get(t);
                Place place = placeUnifiedMap.get(p);
                if (result.findEdge(t, p) != null) {
                    petrinet.addArc(transition, place);
                }
                if (result.findEdge(p, t) != null) {
                    petrinet.addArc(place, transition);
                }
            }
        }

        return petrinet;
    }

    @Override
    public String getAcronym() {
        return "A$";
    }
}
