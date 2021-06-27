package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.benchmark.logic.MeasurementAlgorithmDiscoverer;
import com.raffaeleconforti.benchmark.logic.MiningAlgorithmDiscoverer;
import com.raffaeleconforti.measurements.MeasurementAlgorithm;
import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.PetrinetWithMarking;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.in.XesXmlGZIPParser;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.connections.petrinets.behavioral.FinalMarkingConnection;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.raffaeleconforti.log.util.LogImporter.importFromFile;
import static com.raffaeleconforti.log.util.LogImporter.importFromInputStream;

public class Common {
    public static void logMessage(String message) {
        System.out.printf("%s - %s\n", currentTime(), message);
    }

    public static String currentTime() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        return sdfDate.format(new Date());
    }

    public static Map<String, Object> loadLogs(String extLocation) {
        Map<String, Object> result = new HashMap<>();

        try {
            logMessage("DEBUG - importing external logs.");
            File folder = new File(extLocation);
            File[] listOfFiles = folder.listFiles();
            if (folder.isDirectory() && listOfFiles != null) {
                for (File file : listOfFiles)
                    if (file.isFile()) {
                        String logName = file.getPath();
                        if (logName.endsWith(".json")) {
                            continue;
                        }

                        logMessage("DEBUG - found log: " + logName);
                        result.put(file.getName(), logName);
                    }
            } else {
                logMessage("ERROR - external logs loading failed, input path is not a folder.");
            }

        } catch (Exception e) {
            logMessage("ERROR - something went wrong reading the resource folder: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }


    public static XLog loadLog(Object o) {
        try {
            if (o instanceof String) {
                String logFile = (String) o;
                if (logFile.endsWith(".txt")) {
                    return LogImporterFF.importFromFile(new XFactoryNaiveImpl(), logFile);
                }
                return importFromFile(new XFactoryNaiveImpl(), logFile);
            } else if (o instanceof InputStream) {
                return importFromInputStream((InputStream) o, new XesXmlGZIPParser(new XFactoryNaiveImpl()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<MiningAlgorithm> discoverAlgorithms(List<String> miners) {
        List<MiningAlgorithm> algorithms = MiningAlgorithmDiscoverer.discoverAlgorithms(new HashSet<>());
        algorithms.sort((o1, o2) -> o2.getAlgorithmName().compareTo(o1.getAlgorithmName()));

        List<MiningAlgorithm> res = new ArrayList<>(miners.size());
        for (String v : miners) {
            if (isInt(v)) {
                res.add(algorithms.get(asInt(v)));
            } else {
                v = v.toLowerCase(Locale.ROOT);
                for (MiningAlgorithm ma : algorithms) {
                    if (v.equals(ma.getAcronym().toLowerCase(Locale.ROOT))) {
                        res.add(ma);
                        break;
                    }
                }
            }
        }

        return res;
    }


    public static List<MeasurementAlgorithm> discoverMetrics(List<String> metrics) {
        List<MeasurementAlgorithm> algorithms = MeasurementAlgorithmDiscoverer.discoverAlgorithms(new HashSet<>());
        algorithms.sort((o1, o2) -> o2.getMeasurementName().compareTo(o1.getMeasurementName()));

        List<MeasurementAlgorithm> res = new ArrayList<>(metrics.size());
        for (Integer i : asInts(metrics)) {
            res.add(algorithms.get(i));
        }

        return res;
    }

    private static boolean isInt(String value) {
        return value.matches("-?\\d+");
    }

    private static int asInt(String value) {
        return Integer.parseInt(value);
    }

    public static List<Integer> asInts(List<String> strings) {
        if (strings.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> res = new ArrayList<>(strings.size());
        for (String string : strings) {
            res.add(Integer.parseInt(string, 10));
        }
        return res;
    }


    public static void setupResultsDir(String directoryName) {
        File directory = new File("./results/" + directoryName);
        if (!directory.exists() && !directory.mkdir()) {
            logMessage(String.format("ERROR - could not create results directory '%s' for mining algorithm, stopping", directoryName));
            System.exit(-1);
        }
    }

    public static boolean allowMineBPMN(MiningAlgorithm ma) {
        if (ma == null) {
            return false;
        }
        String minerName = ma.getAcronym();
        if (minerName.startsWith("IM")) {
            return true;
        }

        switch (minerName) {
            case "SM":
            case "PTM":
                return true;
            default:
                return false;
        }
    }


    public static void addTau(Petrinet net, Place from, Place to) {
        Transition t = net.addTransition("tau");
        t.setInvisible(true);
        net.addArc(from, t);
        net.addArc(t, to);
    }

    public static PetrinetWithMarking markNet(PluginContext ctx, Petrinet net, Place start, Place end) {
        Marking initialMarking = new Marking(Collections.singletonList(start));
        Marking finalMarking = new Marking(Collections.singletonList(end));

        // Skipping this breaks precision computing
        ctx.addConnection(new InitialMarkingConnection(net, initialMarking));
        ctx.addConnection(new FinalMarkingConnection(net, finalMarking));

        return new PetrinetWithMarking(net, initialMarking, finalMarking);
    }

    public static List<List<String>> toTraces(XLog log) {
        List<List<String>> traces = new LinkedList<>();
        for (XTrace trace : log) {
            List<String> t = new LinkedList<>();
            for (XEvent event : trace) {
                t.add(event.getAttributes().get("concept:name").toString());
            }
            traces.add(t);
        }
        return traces;
    }

    public static String joinTrace(List<String> trace) {
        StringBuilder sb = new StringBuilder();
        for (String s : trace) {
            sb.append(s);
            sb.append(" ");
        }
        String res = sb.toString();
        return res.substring(0, res.length() - 1);
    }

    public static void mustSleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            System.exit(-1);
        }
    }
}

class TreeNode {
    private final String label;
    public Map<String, TreeNode> children;
    private int countFinal;

    public TreeNode(String label) {
        this.label = label;
        this.countFinal = 0;
        this.children = new HashMap<>();
    }

    public TreeNode getChild(String label) {
        if (this.children.containsKey(label)) {
            return this.children.get(label);
        }
        TreeNode child = new TreeNode(label);
        this.children.put(label, child);
        return child;
    }

    public boolean isFinal() {
        return this.countFinal > 0;
    }

    public Collection<TreeNode> children() {
        return this.children.values();
    }

    public void addFinal() {
        this.countFinal += 1;
    }

    public String getLabel() {
        return this.label;
    }
}

class QueueItem<T> {
    public TreeNode node;
    public T reachedFrom;

    public QueueItem(TreeNode node, T reachedFrom) {
        this.node = node;
        this.reachedFrom = reachedFrom;
    }
}
