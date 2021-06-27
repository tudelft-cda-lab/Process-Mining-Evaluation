package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.impl.SplitMinerWrapper;
import com.raffaeleconforti.wrappers.impl.inductive.InductiveMinerIMWrapper;
import com.raffaeleconforti.wrappers.impl.inductive.InductiveMinerIMfWrapper;
import com.raffaeleconforti.wrappers.impl.inductive.InductiveMinerIMfaWrapper;
import com.raffaeleconforti.wrappers.settings.MiningSettings;
import org.deckfour.xes.model.XLog;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.raffaeleconforti.benchmark.BenchmarkCustom.HOUR_MS;
import static com.raffaeleconforti.benchmark.BenchmarkCustom.parseDuration;
import static com.raffaeleconforti.benchmark.Common.*;

public class BenchmarkCustomData {
    public static void main(String[] args) {
        new BenchmarkCustomData().run(args);
    }

    public void run(String[] args) {
        String dataRoot = "";
        String miningAlgorithm = "";
        long metricTimeout = HOUR_MS;
        long minerTimeout = HOUR_MS;
        int fitnessThreads = 1;
        Set<String> includeDatasets = new HashSet<>();
        Set<String> includeMachines = new HashSet<>();

        String setting = "";
        float value = 0;


        int argIdx = 0;
        int prevArgIdx = -1;

        while (argIdx != args.length) {
            if (argIdx == prevArgIdx) {
                logMessage("ERR - cannot parse all args, stopping");
                return;
            }
            prevArgIdx = argIdx;

            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-data-root")) {
                dataRoot = args[argIdx + 1];
                argIdx += 2;
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-miner")) {
                miningAlgorithm = args[argIdx + 1];
                argIdx += 2;
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-metric-timeout")) {
                metricTimeout = parseDuration(args[argIdx + 1]);
                argIdx += 2;
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-miner-timeout")) {
                minerTimeout = parseDuration(args[argIdx + 1]);
                argIdx += 2;
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-fitness-threads")) {
                fitnessThreads = Integer.parseInt(args[argIdx + 1]);
                argIdx += 2;
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-include-datasets")) {
                argIdx += 1;
                while (argIdx < args.length && !args[argIdx].startsWith("-")) {
                    includeDatasets.add(args[argIdx]);
                    argIdx += 1;
                }
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-include-machines")) {
                argIdx += 1;
                while (argIdx < args.length && !args[argIdx].startsWith("-")) {
                    includeMachines.add(args[argIdx]);
                    argIdx += 1;
                }
            }
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-param")) {
                setting = args[argIdx + 1];
                value = Float.parseFloat(args[argIdx + 2]);
                argIdx += 3;
            }
        }

        if (!includeDatasets.isEmpty()) {
            logMessage("including datasets:");
            for (String d : includeDatasets) {
                logMessage("\t" + d);
            }
        }
        if (!includeMachines.isEmpty()) {
            logMessage("including machines:");
            for (String d : includeMachines) {
                logMessage("\t" + d);
            }
        }


        List<MiningAlgorithm> miners = discoverAlgorithms(Collections.singletonList(miningAlgorithm));
        assert miners.size() == 1;

        File root = new File(dataRoot);
        assert root.isDirectory();


        setupResultsDir("hybrid");

        MiningAlgorithm miner = miners.get(0);

        String minerName = miner.getAcronym();
        MiningSettings settings = new MiningSettings();
        if (!setting.equals("")) {
            logMessage(String.format("setting '%s' set t %f", setting, value));
            settings.setParam(setting, value);
            minerName = String.format("%s_%d", minerName, (int) (value * 100));
        }

        setupResultsDir(String.format("hybrid/%s", minerName));
        File[] datasets = root.listFiles();
        if (datasets == null) {
            logMessage("ERR - cannot iterate datasets");
            return;
        }

        for (File dataset : datasets) {
            logMessage(dataset.getName());
            if (!includeDatasets.isEmpty() && !includeDatasets.contains(dataset.getName())) {
                logMessage("Dataset is not included, skipping");
                continue;
            }

            DatasetName logName = new DatasetName(dataset.getName());

            File[] machineFiles = dataset.listFiles();
            if (machineFiles == null) {
                logMessage("ERR - cannot iterate machine files");
                return;
            }

            for (File machineDir : machineFiles) {
                String machineName = machineDir.getName();
                logMessage(machineName);
                if (!includeMachines.isEmpty() && !includeMachines.contains(machineName)) {
                    logMessage("Machine is not included, skipping");
                    continue;
                }
                evalMachine(machineDir, miner, logName, minerName, settings, minerTimeout, metricTimeout, fitnessThreads);
            }
        }
        System.out.println("Done");
        System.exit(0);
    }

    private static void evalMachine(File machineRoot, MiningAlgorithm miner, DatasetName logName, String minerName, MiningSettings settings, long minerTimeout, long metricTimeout, int fitnessThreads) {
        logMessage(String.format("eval machine %s on data %s", machineRoot.getName(), logName));

        String hybridName = String.format("%s-%s", minerName, machineRoot.getName());
        setupResultsDir(String.format("hybrid/%s/%s", minerName, hybridName));

        File[] files = Paths.get(machineRoot.getAbsolutePath(), "eval").toFile().listFiles();
        if (files == null) {
            logMessage("ERR - cannot list machineDir");
            return;
        }
        if (files.length == 0) {
            logMessage("WARN - directory empty, skipping");
            return;
        }
        File fullFile = null;
        int k = files.length - 1;
        File[] crossValFiles = new File[k];

        for (File f : files) {
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
        BenchmarkCustomNative b = new BenchmarkCustomNative(minerTimeout, metricTimeout, k, fitnessThreads);


        evalLog(machineRoot, fullFile.getName(), miner, hybridName, settings, logName, b, results);
        for (int i = 0; i < crossValFiles.length; i++) {
            evalLog(machineRoot, crossValFiles[i].getName(), miner, String.format("%s-%d", hybridName, i), settings, logName, b, results);
        }

        if (k > 0) {
            b.extractCrossVal(results, logName, hybridName);
        }

        b.stop();

        // results/hybrid/sm/sm-bigram/cptc_18_reversed.json
        String pathnameJSON = String.format("./results/hybrid/%s/%s/%s_%s_results.json",minerName, hybridName, currentTime(), logName);
        results.writeJSON(pathnameJSON);
    }

    private static void evalLog(File machineRoot, String datasetFileName, MiningAlgorithm miner, String minerName, MiningSettings settings, DatasetName logName, BenchmarkCustomNative b, ResultsMap results) {
        String trainFile = Paths.get(machineRoot.getAbsolutePath(), "train", datasetFileName).toString();
        String evalFile = Paths.get(machineRoot.getAbsolutePath(), "eval", datasetFileName).toString();

        XLog trainLog = loadLog(trainFile);
        XLog evalLog = loadLog(evalFile);

        try {
            b.evalLog(minerName, trainLog, evalLog, miner, settings, logName, results, true);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
    }
}
