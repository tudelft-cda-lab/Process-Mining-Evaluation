package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.context.FakePluginContext;
import com.raffaeleconforti.wrappers.PetrinetWithMarking;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;

import java.io.File;
import java.nio.file.Paths;
import java.util.Locale;

import static com.raffaeleconforti.benchmark.BenchmarkCustom.HOUR_MS;
import static com.raffaeleconforti.benchmark.BenchmarkCustom.parseDuration;
import static com.raffaeleconforti.benchmark.Common.*;
import static com.raffaeleconforti.benchmark.SMToPNConverter.*;

public class DFAEvaluator {

    public DFAEvaluator() {
    }

    public void run(String[] args) {
        // Load dfa -> convert
        //   - Merge same incoming transitions? (only if all are the same?)
        //   - Sink is also final state -> tau to end
        // Load dataset
        // Run metrics

        // Cross-val setup:
        // List of machines in the format <machine-name>_1.json

        // Machine structure: name is training dataset name
        // machine_name
        //   name.json
        //   name_0.json
        //   name_1.json
        //   ...

        // Dataset structure:
        // cptc_18_reversed
        //   name.txt
        //   name_0.txt
        //   name_1.txt
        //   ...

        // Timeout -> default 2 hours
        // fitness threads
        // No k -> inferred from the directory
        // Miner name
        // Miner directory
        // Dataset directory

        String machineDir = "";
        String logDir = "";

        long metricTimeout = HOUR_MS;
        int fitnessThreads = 1;


        int argIdx = 0;
        int prevArgIdx = -1;

        while (argIdx != args.length) {
            if (argIdx == prevArgIdx) {
                logMessage("ERR - cannot parse all args, stopping");
                return;
            }
            prevArgIdx = argIdx;

            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-machine-root")) {
                machineDir = args[argIdx + 1];
                argIdx += 2;
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-log-root")) {
                logDir = args[argIdx + 1];
                argIdx += 2;
            }

            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-metric-timeout")) {
                metricTimeout = parseDuration(args[argIdx + 1]);
                argIdx += 2;
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-fitness-threads")) {
                fitnessThreads = Integer.parseInt(args[argIdx + 1]);
                argIdx += 2;
            }
        }

        setupResultsDir("state_machines");

        File machinesRoot = new File(machineDir);
        assert machinesRoot.isDirectory();
        File[] datasetMachines = machinesRoot.listFiles();
        if (datasetMachines == null) {
            logMessage("ERR - cannot list machines root");
            return;
        }

        for (File datasetMachine : datasetMachines) {
            assert datasetMachine.isDirectory();

            logMessage("Eval " + datasetMachine.getName());
            String datasetDirectory = Paths.get(logDir, datasetMachine.getName(), "eval").toString();
            File[] machineList = datasetMachine.listFiles();
            if (machineList == null) {
                logMessage("ERR - cannot list machines root");
                return;
            }

            for (File machine : machineList) {
                String machineName = machine.getName();
                evalMachines(machine, machineName, datasetDirectory, metricTimeout, fitnessThreads);
            }
        }
    }

    private static void evalMachines(File machineDir, String machineName, String logDir, long metricTimeout, int fitnessThreads) {
        String datasetName = new File(logDir).getParentFile().getName();
        DatasetName logName = new DatasetName(datasetName);

//        if (!machineName.equals("markov")) return;
//        if (!datasetName.equals("cptc_18_reversed")) return;

        logMessage(String.format("evaluating %s for logs %s", machineName, logName));


        if (!machineDir.isDirectory()) {
            logMessage("ERR - machineDir is not directory, continue");
            return;
        }
        setupResultsDir("state_machines/" + machineName);

        File[] fileList = machineDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (fileList == null) {
            logMessage("ERR - cannot list machineDir");
            return;
        }
        if (fileList.length == 0) {
            logMessage("WARN - directory empty, skipping");
            return;
        }

        File fullFile = null;
        int k = fileList.length - 1;
        File[] crossValFiles = new File[k];

        for (File f : fileList) {
            assert f.isFile();
            String fileName = f.getName().split("\\.")[0];
            if (Character.isDigit(fileName.charAt(fileName.length() - 1))) {
                String[] nameParts = fileName.split("_");
                crossValFiles[Integer.parseInt(nameParts[nameParts.length - 1])] = f;
            } else {
                assert fullFile == null;
                fullFile = f;
            }
        }
        assert fullFile != null;

        ResultsMap results = new ResultsMap();
        BenchmarkCustomNative b = new BenchmarkCustomNative(-1, metricTimeout, k, fitnessThreads);

        evalMachine(fullFile, machineName, logDir, logName, b, results);

        for (int i = 0; i < crossValFiles.length; i++) {
            evalMachine(crossValFiles[i], String.format("%s-%d", machineName, i), logDir, logName, b, results);
        }

        if (k > 0) {
            b.extractCrossVal(results, logName, machineName);
        }

        String pathnameJSON = String.format("./results/state_machines/%s/%s_results.json", machineName, currentTime());
        results.writeJSON(pathnameJSON);
    }

    private static void evalMachine(File machineFile, String machineName, String logDir, DatasetName logName, BenchmarkCustomNative b, ResultsMap results) {
        assert machineFile.getName().endsWith(".json");
        String fileName = machineFile.getName().split("\\.")[0];

        UIPluginContext ctx = new FakePluginContext();
        DFA machine = DFA.loadJSON(machineFile);
        if (machine == null) {
            logMessage("ERR - dfa is null");
            return;
        }
        PetrinetWithMarking net = dfaToPN(ctx, machine);
        BPMNDiagram bpmn = dfaToBPMNMerged(ctx, machine);

//        String bpmnPath = String.format("./results/models/%s-%s.bpmn", machineName, logName);
//        exportBPMN(bpmn, bpmnPath);

        XLog evalLog = loadLog(Paths.get(logDir, fileName + ".txt").toString());
        b.runPN(ctx, net, bpmn, evalLog, machineName, logName, results);
    }
}