package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.context.FakePluginContext;
import com.raffaeleconforti.conversion.petrinet.PetriNetToBPMNConverter;
import com.raffaeleconforti.log.util.LogCloner;
import com.raffaeleconforti.measurements.impl.AlignmentBasedFitness;
import com.raffaeleconforti.measurements.impl.XFoldAlignmentBasedFMeasure;
import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.PetrinetWithMarking;
import com.raffaeleconforti.wrappers.settings.MiningSettings;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.plugins.bpmnminer.types.MinerSettings;
import org.processmining.plugins.multietc.plugins.MultiETCPlugin;
import org.processmining.plugins.multietc.res.MultiETCResult;
import org.processmining.plugins.multietc.sett.MultiETCSettings;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.raffaeleconforti.benchmark.Common.*;
import static com.raffaeleconforti.measurements.impl.Soundness.isSound;

public class BenchmarkCustomNative {
    public final static long HOUR_MS = 60 * 60 * 1000;
    public static final String SOUNDNESS_KEY = "base-soundness";
    public static final String FITNESS_KEY = "performance-fitness";
    public static final String PRECISION_KEY = "performance-precision";
    public static final String F_SCORE_KEY = "performance-f-score";

    public static final String CONFORMANCE_UNIQUE_KEY = "performance-unique-conformance";
    public static final String CONFORMANCE_UNIQUE_FRAC_KEY = "performance-unique-conformance-frac";
    public static final String CONFORMANCE_TOTAL_KEY = "performance-total-conformance";
    public static final String CONFORMANCE_TOTAL_FRAC_KEY = "performance-total-conformance-frac";
    public static final String PERFORMANCE_UNIQUE_SIZE = "performance-metric-unique-traces";
    public static final String PERFORMANCE_TOTAL_SIZE = "performance-metric-total-traces";


    public static final String SIZE_NODE_KEY = "size-node";
    public static final String SIZE_ARC_KEY = "size-arc";
    public static final String SIZE_CONNECTORS_KEY = "size-connectors";
    public static final String SIZE_DENSITY_MIN_KEY = "size-density-min";
    public static final String SIZE_DENSITY_MAX_KEY = "size-density-max";
    public static final String SIZE_DENSITY_MEAN_KEY = "size-density-mean";
    public static final String SIZE_DENSITY_MEDIAN_KEY = "size-density-median";
    public static final String SIZE_CNC_KEY = "size-cnc";
    public static final String SIZE_CFC_KEY = "size-cfc";
    public static final String SIZE_STRUCT_KEY = "structuredness";

    public static final String VALUE_TIMEOUT = "TIMEOUT";
    public static final String VALUE_ERROR = "ERROR";

    private final int K;
    private final int fitnessThreads;

    private final long minerTimeout;
    private final long metricTimeout;

    private final Map<String, Object> logs;

    private final ExecutorService executor;
    private final LogCloner logCloner;

    private static final XEventClassifier xEventClassifier = new XEventNameClassifier();


    BenchmarkCustomNative(String logFolder, long minerTimeout, long metricTimeout, int k, int fitnessThreads) {
        this.minerTimeout = minerTimeout;
        this.metricTimeout = metricTimeout;
        this.K = k;
        this.fitnessThreads = fitnessThreads;

        this.logs = loadLogs(logFolder);

        this.executor = Executors.newSingleThreadExecutor();
        this.logCloner = new LogCloner();
    }

    public BenchmarkCustomNative(long minerTimeout, long metricTimeout, int k, int fitnessThreads) {
        this.minerTimeout = minerTimeout;
        this.metricTimeout = metricTimeout;
        this.K = k;
        this.fitnessThreads = fitnessThreads;

        this.logs = new HashMap<>();

        this.executor = Executors.newSingleThreadExecutor();
        this.logCloner = new LogCloner();
    }

    public void runBenchmark(List<MiningAlgorithm> miningAlgorithms) {

        logMessage("loaded miners:");
        for (MiningAlgorithm ma : miningAlgorithms) {
            logMessage(String.format("(%s) - %s", ma.getAcronym(), ma.getAlgorithmName()));
        }

        File resultDir = new File("./results");
        if (!resultDir.exists() && !resultDir.mkdir()) {
            logMessage("ERROR - could not create results directory, stopping");
            System.exit(-1);
        }

        for (MiningAlgorithm miner : miningAlgorithms) {
            String minerName = miner.getAcronym();
            logMessage(String.format("Evaluating miner %s (%s)", miner.getAlgorithmName(), miner.getAcronym()));
            setupResultsDir(minerName);

            for (String logName : logs.keySet()) {
                ResultsMap results = new ResultsMap();
                XLog log = loadLog(logs.get(logName));
                if (log == null) {
                    logMessage("ERROR - could not load log, moving on");
                    continue;
                }
                logMessage("evaluating log: " + logName);

                try {
                    runBenchmark(miner, minerName, null, log, new DatasetName(logName), results);
                } finally {
                    logMessage(String.format("Finishing mining log %s with miner %s", logName, minerName));
                    String pathnameJSON = String.format("./results/%s/%s_%s.json", minerName, currentTime(), logName);
                    results.writeJSON(pathnameJSON);

                    logMessage(String.format("finished mining log %s with miner %s", logName, minerName));
                }
            }
        }
    }

    public void runBenchmark(List<MiningAlgorithm> miningAlgorithms, ParameterRange range) {
        logMessage("loaded miners:");
        for (MiningAlgorithm ma : miningAlgorithms) {
            logMessage(String.format("(%s) - %s", ma.getAcronym(), ma.getAlgorithmName()));
        }

        File resultDir = new File("./results");
        if (!resultDir.exists() && !resultDir.mkdir()) {
            logMessage("ERROR - could not create results directory, stopping");
            System.exit(-1);
        }


        for (float value = range.getStart(); value <= range.getStop(); value += range.getStep()) {
            for (MiningAlgorithm miner : miningAlgorithms) {
                String minerName = String.format("%s_%d", miner.getAcronym(), (int) (value * 100));
                logMessage(String.format("Evaluating miner %s (%s)", miner.getAlgorithmName(), miner.getAcronym()));
                setupResultsDir(minerName);

                for (String logName : logs.keySet()) {
                    ResultsMap results = new ResultsMap();
                    XLog log = loadLog(logs.get(logName));
                    if (log == null) {
                        logMessage("ERROR - could not load log, moving on");
                        continue;
                    }
                    logMessage("evaluating log: " + logName);

                    MiningSettings settings = new MiningSettings();
                    settings.setParam(range.getName(), value);

                    try {
                        runBenchmark(miner, minerName, settings, log, new DatasetName(logName), results);
                    } finally {
                        logMessage(String.format("Finishing mining log %s with miner %s", logName, minerName));
                        String pathnameJSON = String.format("./results/%s/%s_%s.json", minerName, currentTime(), logName);
                        results.writeJSON(pathnameJSON);

                        logMessage(String.format("finished mining log %s with miner %s", logName, minerName));
                    }
                }
            }
        }


    }

    public void stop() {
        this.executor.shutdownNow();
    }

    private void runBenchmark(MiningAlgorithm miner, String minerName, MiningSettings settings, XLog log, DatasetName logName, ResultsMap results) {
        try {
            UIPluginContext ctx = new FakePluginContext();
            PetrinetWithMarking minerResult = mineLog(ctx, miner, settings, log, minerName, logName, results);
            if (minerResult == null) {
                logMessage("WARNING - miner result is null, skipping all metrics, forwarding to k-fold performance");
                this.kFoldPerformance(log, miner, settings, minerName, logName, results);
                return;
            }

            logMessage("computing soundness");
            boolean isSound = getSoundness(minerResult, minerName, logName, results);
            computeComplexity(ctx, minerResult, miner, log, minerName, logName, results, true);

            if (isSound) {
                logMessage("log is sound, computing performance on full data");
                this.computePerformance(minerResult, log, minerName, logName, results);
            }
            this.kFoldPerformance(log, miner, settings, minerName, logName, results);
        } catch (InterruptedException e) {
            logMessage("ERROR - metrics were interrupted, stopping execution");
        }
    }

    public void runPN(UIPluginContext ctx, PetrinetWithMarking net, BPMNDiagram bpmn, XLog log, String minerName, DatasetName logName, ResultsMap results) {
        try {
            logMessage("computing soundness");
            boolean isSound = getSoundness(net, minerName, logName, results);

            if (isSound) {
                logMessage("log is sound, computing performance on full data");
                if (bpmn == null) {
                    computeComplexity(ctx, net, null, log, minerName, logName, results, true);
                } else {
                    computeComplexity(bpmn, minerName, logName, true, results);
                }
                this.computePerformance(net, log, minerName, logName, results);
            }
        } catch (InterruptedException e) {
            logMessage("ERROR - metrics were interrupted, stopping execution");
        }
    }

    public void runPerformance(UIPluginContext ctx, MiningAlgorithm miner, MiningSettings settings, XLog log, String minerName, DatasetName logName, ResultsMap results) {
        try {
            PetrinetWithMarking minerResult = mineLog(ctx, miner, settings, log, minerName, logName, results);
            if (minerResult == null) {
                logMessage("WARNING - miner result is null, skipping all metrics");
                return;
            }

            logMessage("computing soundness");
            boolean isSound = getSoundness(minerResult, minerName, logName, results);
            if (isSound) {
                logMessage("log is sound, computing performance on full data");
                this.computePerformance(minerResult, log, minerName, logName, results);
            }
        } catch (InterruptedException e) {
            logMessage("ERROR - metrics were interrupted, stopping execution");
        }
    }

    private PetrinetWithMarking mineLog(UIPluginContext context, MiningAlgorithm miner, MiningSettings settings, XLog log, String
            minerName, DatasetName logName, ResultsMap results) throws RuntimeException {
        XLog miningLog = logCloner.cloneLog(log);

        long startTime = System.currentTimeMillis();
        Future<PetrinetWithMarking> minerFuture = executor.submit(
                () -> miner.minePetrinet(context, miningLog, false, settings, xEventClassifier)
        );
        try {
            PetrinetWithMarking result = minerFuture.get(minerTimeout, TimeUnit.MILLISECONDS);

            long miningTime = System.currentTimeMillis() - startTime;
            logMessage(String.format("mining done, took %d", miningTime));
            results.addResult(minerName, logName, "mining-time", Long.toString(miningTime));

            return result;
        } catch (TimeoutException ex) {
            logMessage("ERROR - miner timed out");
        } catch (InterruptedException e) {
            logMessage("ERROR - miner was interrupted");
            throw new RuntimeException("break execution");
        } catch (ExecutionException e) {
            e.printStackTrace();
            logMessage("ERROR - miner encountered an implementation error");
        } finally {
            minerFuture.cancel(true); // may or may not desire this
            results.addIfMissing(minerName, logName, "mining-time", VALUE_TIMEOUT);
        }

        return null;
    }

    private BPMNDiagram mineBPMN(UIPluginContext context, MiningAlgorithm miner, XLog log, String
            minerName, DatasetName logName, ResultsMap results) throws RuntimeException {
        XLog miningLog = logCloner.cloneLog(log);

        long startTime = System.currentTimeMillis();
        Future<BPMNDiagram> minerFuture = executor.submit(
                () -> miner.mineBPMNDiagram(context, miningLog, false, null, xEventClassifier)
        );
        try {
            BPMNDiagram result = minerFuture.get(minerTimeout, TimeUnit.MILLISECONDS);

            long miningTime = System.currentTimeMillis() - startTime;
            logMessage(String.format("mining done, took %d", miningTime));
            results.addResult(minerName, logName, "mining-time-bpmn", Long.toString(miningTime));

            return result;
        } catch (TimeoutException ex) {
            logMessage("ERROR - miner timed out");
        } catch (InterruptedException e) {
            logMessage("ERROR - miner was interrupted");
            throw new RuntimeException("break execution");
        } catch (ExecutionException e) {
            e.printStackTrace();
            logMessage("ERROR - miner encountered an implementation error");
        } finally {
            minerFuture.cancel(true); // may or may not desire this
            results.addIfMissing(minerName, logName, "mining-time", VALUE_TIMEOUT);
        }

        return null;
    }

    private boolean getSoundness(PetrinetWithMarking net, String miner, DatasetName logName, ResultsMap results) throws
            InterruptedException {
        if (miner.startsWith("IM")) {
            logMessage("inductive miner -> sound by construction");
            results.addResult(miner, logName, SOUNDNESS_KEY, "sound");
            return true;
        }

        long runtime;
        try {
            logMessage("starting soundness");
            runtime = System.currentTimeMillis();
            Future<Boolean> soundnessFuture = this.executor.submit(() -> isSound(net));

            Boolean result = soundnessFuture.get(metricTimeout, TimeUnit.MILLISECONDS);
            runtime = System.currentTimeMillis() - runtime;
            String resultStr = result ? "sound" : "unsound";
            results.addResult(miner, logName, SOUNDNESS_KEY, resultStr, runtime);
            return result;
        } catch (TimeoutException e) {
            String resultStr = "unsound-TIMEOUT";
            results.addResult(miner, logName, SOUNDNESS_KEY, resultStr, metricTimeout);
            return false;
        } catch (ExecutionException e) {
            e.printStackTrace();
            String resultStr = "unsound-ERROR";
            results.addResult(miner, logName, SOUNDNESS_KEY, resultStr);
            return false;
        }
    }


    private void computeComplexity(UIPluginContext context, PetrinetWithMarking net, MiningAlgorithm miner, XLog log, String
            minerName, DatasetName logName, ResultsMap results, boolean includeStruct) throws InterruptedException {
        BPMNDiagram bpmn = null;
        if (allowMineBPMN(miner)) {
            bpmn = mineBPMN(context, miner, log, minerName, logName, results);
        }
        if (bpmn == null) {
            logMessage("WARN - could not mine for BPMN, converting PN");
            long startTime = System.currentTimeMillis();
            bpmn = PetriNetToBPMNConverter.convert(net.getPetrinet(), net.getInitialMarking(), net.getFinalMarking(), false);
            logMessage(String.format("Conversion took %d ms", (System.currentTimeMillis() - startTime)));
        }
        computeComplexity(bpmn, minerName, logName, includeStruct, results);
    }


    private void computeComplexity(BPMNDiagram bpmn, String miner, DatasetName logName,
                                   boolean includeStruct, ResultsMap results) throws InterruptedException {
        ComplexityCalculatorCustom cc = new ComplexityCalculatorCustom(bpmn);
        logMessage("starting complexity");

        computeMetric(cc::computeSize, miner, logName, SIZE_NODE_KEY, metricTimeout, results);
        computeMetric(cc::getFlowSize, miner, logName, SIZE_ARC_KEY, metricTimeout, results);
        computeMetric(cc::getConnectorSize, miner, logName, SIZE_CONNECTORS_KEY, metricTimeout, results);
        computeMetric(cc::computeMinConnectorDensity, miner, logName, SIZE_DENSITY_MIN_KEY, metricTimeout, results);
        computeMetric(cc::computeMaxConnectorDensity, miner, logName, SIZE_DENSITY_MAX_KEY, metricTimeout, results);
        computeMetric(cc::computeMeanConnectorDensity, miner, logName, SIZE_DENSITY_MEAN_KEY, metricTimeout, results);
        computeMetric(cc::computeMedianConnectorDensity, miner, logName, SIZE_DENSITY_MEDIAN_KEY, metricTimeout, results);
//        computeMetric(cc::getConnectorDensities, miner, logName, "densities", metricTimeout, results);
        computeMetric(cc::computeCNC, miner, logName, SIZE_CNC_KEY, metricTimeout, results);
        computeMetric(cc::computeCFC, miner, logName, SIZE_CFC_KEY, metricTimeout, results);

        if (includeStruct) {
            if (miner.startsWith("IM") || miner.equals("PTM")) {
                logMessage("Inductive miner -> shortcut struct.");
                results.addResult(miner, logName, "struct.", 1.000, 0);
            } else {
                computeMetric(cc::computeStructuredness, miner, logName, SIZE_STRUCT_KEY, metricTimeout, results);
            }
        }
        logMessage("done complexity");
    }

    private void computeMetric(Callable<Serializable> fn, String minerName, DatasetName logName, String
            measureName, long timeoutMs, ResultsMap results) throws InterruptedException {
        logMessage("computing " + measureName);
        long startTime = System.currentTimeMillis();
        Serializable result;
        try {
            Future<Serializable> resFuture = this.executor.submit(fn);
            result = resFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            e.printStackTrace();
            result = VALUE_ERROR;
        } catch (TimeoutException e) {
//            e.printStackTrace();
            result = String.format("TIMEOUT: %d ms", timeoutMs);
        }
        startTime = System.currentTimeMillis() - startTime;

        results.addResult(minerName, logName, measureName, result, startTime);
    }

    private void computePerformance(PetrinetWithMarking net, XLog log, String minerName, DatasetName
            logName, ResultsMap results) throws InterruptedException {
        if (!results.getResult(minerName, logName, SOUNDNESS_KEY).equals("sound")) {
            logMessage("ERROR - result is not sound, skipping performance");
            results.addIfMissing(minerName, logName, FITNESS_KEY, "ERROR-NOT-SOUND");
            results.addIfMissing(minerName, logName, PRECISION_KEY, "ERROR-NOT-SOUND");
            return;
        }

        results.addResult(minerName, logName, "_performanceType", "alignment-based");
        MultiETCPlugin multiETCPlugin = new MultiETCPlugin();
        UIPluginContext pluginContext = new FakePluginContext();

        MultiETCSettings settings = new MultiETCSettings();
        settings.put(MultiETCSettings.ALGORITHM, MultiETCSettings.Algorithm.ALIGN_1);
        settings.put(MultiETCSettings.REPRESENTATION, MultiETCSettings.Representation.ORDERED);

        PNRepResult pnRepResult;

        Future<PNRepResult> repResultFuture = null;
        Future<Object[]> etcResultFuture = null;

        long latestEnd = System.currentTimeMillis() + 2 * metricTimeout;

        // Compute pnRepResult for fitness
        try {
            logMessage("computing fitness");
            AlignmentBasedFitness alignmentBasedFitness = new AlignmentBasedFitness();

            long startTime = System.currentTimeMillis();
            repResultFuture = this.executor.submit(() -> alignmentBasedFitness.computeAlignment(pluginContext, xEventClassifier, net, log, fitnessThreads));

            logMessage("Metric timeout: " + metricTimeout);
            pnRepResult = repResultFuture.get(2 * metricTimeout, TimeUnit.MILLISECONDS);
            startTime = System.currentTimeMillis() - startTime;

            int replaySize = getFitness(minerName, logName, results, pnRepResult, startTime);
            if (pnRepResult == null) {
                return;
            }

            results.addResult(minerName, logName, "debug-log-size", log.size());
            results.addResult(minerName, logName, "debug-replay-size", replaySize);
            if (replaySize != log.size()) {
                results.addResult(minerName, logName, "debug-replay-correct", "ERR-NOT_EQUAL");
            } else {
                results.addResult(minerName, logName, "debug-replay-correct", "OK");
            }

            if (replaySize < 0) {
                results.addIfMissing(minerName, logName, PRECISION_KEY, "ERROR-UNRELIABLE");
                return;
            }

            // Compute precision
            logMessage("computing precision");
            startTime = System.currentTimeMillis();
            etcResultFuture = this.executor.submit(() -> multiETCPlugin.checkMultiETCAlign1(pluginContext, log, net.getPetrinet(), settings, pnRepResult));
            Object[] res = etcResultFuture.get(latestEnd - System.currentTimeMillis(), TimeUnit.MILLISECONDS);

            startTime = System.currentTimeMillis() - startTime;
            MultiETCResult multiETCResult = (MultiETCResult) res[0];
            double precision = (Double) multiETCResult.getAttribute(MultiETCResult.PRECISION);

            results.addResult(minerName, logName, PRECISION_KEY, precision, startTime);
        } catch (TimeoutException ex) {
            logMessage("ERROR - fitness timeout, skipping others");
            pluginContext.getProgress().cancel();

            results.addIfMissing(minerName, logName, FITNESS_KEY, VALUE_TIMEOUT);
            results.addIfMissing(minerName, logName, PRECISION_KEY, VALUE_TIMEOUT);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            results.addIfMissing(minerName, logName, FITNESS_KEY, VALUE_ERROR);
            results.addIfMissing(minerName, logName, PRECISION_KEY, VALUE_ERROR);
            logMessage("ERROR - exception in performance, returning");
            return;
        } finally {
            if (repResultFuture != null) {
                repResultFuture.cancel(true);
            }
            if (etcResultFuture != null) {
                etcResultFuture.cancel(true);
            }

            mustSleep(5000);
        }

        double fitness = Double.parseDouble(results.getResult(minerName, logName, FITNESS_KEY));
        double precision = Double.parseDouble(results.getResult(minerName, logName, PRECISION_KEY));
        if (fitness < 0.001 && precision < 0.001) {
            results.addResult(minerName, logName, F_SCORE_KEY, "ERROR - VALUE");
        } else {
            results.addResult(minerName, logName, F_SCORE_KEY, (2 * (fitness * precision) / (fitness + precision)));
        }
    }

    private int getFitness(String minerName, DatasetName logName, ResultsMap results, PNRepResult pnRepResult, long fitnessTime) {
        if (pnRepResult == null) {
            results.addResult(minerName, logName, FITNESS_KEY, "ERROR-NULL");
            results.addResult(minerName, logName, PRECISION_KEY, "ERROR-NULL");
            results.addResult(minerName, logName, CONFORMANCE_UNIQUE_KEY, "ERROR-NULL");
            results.addResult(minerName, logName, CONFORMANCE_TOTAL_KEY, "ERROR-NULL");
            results.addResult(minerName, logName, CONFORMANCE_UNIQUE_FRAC_KEY, "ERROR-NULL");
            results.addResult(minerName, logName, CONFORMANCE_TOTAL_FRAC_KEY, "ERROR-NULL");
            return -1;
        }

        int uniqueSamplesPerfect = 0;
        int totalSamplesPerfect = 0;

        int uniqueSamples = 0;
        int totalSamples = 0;

        int totalCount = 0;

        int unreliable = 0;

        for (SyncReplayResult srp : pnRepResult) {
            totalCount += srp.getTraceIndex().size();
            if (!srp.isReliable()) {
                unreliable += srp.getTraceIndex().size();
            } else {
                uniqueSamples += 1;
                totalSamples += srp.getTraceIndex().size();

                if (srp.getInfo().get(PNRepResult.TRACEFITNESS) >= 0.999) {
                    uniqueSamplesPerfect += 1;
                    totalSamplesPerfect += srp.getTraceIndex().size();
                }
            }
        }
        logMessage("Number unreliable: " + unreliable);
        if (unreliable > pnRepResult.size() / 2) {
            results.addIfMissing(minerName, logName, CONFORMANCE_UNIQUE_KEY, "ERROR-UNRELIABLE");
            results.addIfMissing(minerName, logName, CONFORMANCE_TOTAL_KEY, "ERROR-UNRELIABLE");
            results.addIfMissing(minerName, logName, CONFORMANCE_UNIQUE_FRAC_KEY, "ERROR-UNRELIABLE");
            results.addIfMissing(minerName, logName, CONFORMANCE_TOTAL_FRAC_KEY, "ERROR-UNRELIABLE");
            return -1;
        } else {
            results.addResult(minerName, logName, FITNESS_KEY,
                    (Double) pnRepResult.getInfo().get(PNRepResult.TRACEFITNESS), fitnessTime);

            results.addResult(minerName, logName, PERFORMANCE_UNIQUE_SIZE, pnRepResult.size());
            results.addResult(minerName, logName, PERFORMANCE_TOTAL_SIZE, totalCount);

            results.addResult(minerName, logName, CONFORMANCE_UNIQUE_KEY, uniqueSamplesPerfect);
            results.addResult(minerName, logName, CONFORMANCE_TOTAL_KEY, totalSamplesPerfect);
            results.addResult(minerName, logName, CONFORMANCE_UNIQUE_FRAC_KEY,
                    (double) uniqueSamplesPerfect / (double) uniqueSamples);
            results.addResult(minerName, logName, CONFORMANCE_TOTAL_FRAC_KEY,
                    (double) totalSamplesPerfect / (double) totalSamples);

            return totalSamples;
        }
    }


    private void kFoldPerformance(XLog log, MiningAlgorithm miner, MiningSettings settings, String minerName, DatasetName
            logName, ResultsMap results) {
        Map<XLog, XLog> crossValidationLogs = XFoldAlignmentBasedFMeasure.getCrossValidationLogs(log, K);
        int i = -1;
        for (XLog miningLog : crossValidationLogs.keySet()) {
            i++;
            XLog evalLog = crossValidationLogs.get(miningLog);
            logMessage(String.format("Start mining fold %d", i));

            String foldMinerName = foldMinerName(minerName, i);
            try {
                evalLog(foldMinerName, miningLog, evalLog, miner, settings, logName, results, false);
            } catch (InterruptedException e) {
                logMessage("ERROR - got InterruptedException, stopping execution");
                return;
            }
        }


        logMessage(String.format("Finished mining folds for miner %s on log %s, computing average results",
                minerName, logName));

        extractCrossVal(results, logName, minerName);
    }

    public void evalLog(String foldMinerName, XLog miningLog, XLog evalLog, MiningAlgorithm miner, MiningSettings settings, DatasetName
            logName, ResultsMap results, boolean includeStruct) throws InterruptedException {
        try {
            UIPluginContext ctx = new FakePluginContext();

            BenchmarkCustomNative wrapper = new BenchmarkCustomNative(minerTimeout, metricTimeout, K, fitnessThreads);
            PetrinetWithMarking minerResult = wrapper.mineLog(ctx, miner, settings, miningLog, foldMinerName, logName, results);
            if (minerResult == null) {
                logMessage("WARNING - miner result is null, skipping this fold");
                return;
            }

            wrapper.computeComplexity(ctx, minerResult, miner, miningLog, foldMinerName, logName, results, includeStruct);

            if (!wrapper.getSoundness(minerResult, foldMinerName, logName, results)) {
                logMessage("fold %d is not sound, skipping");
                return;
            } else {
                logMessage("fold is sound");
            }

            wrapper.computePerformance(minerResult, evalLog, foldMinerName, logName, results);
            logMessage("Finished fold %d");
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("ERR - got other exception, skipping fold");
        }
    }

    public void extractCrossVal(ResultsMap results, DatasetName logName, String minerName) {
        double[] fitness = new double[K];
        double[] precision = new double[K];
        double[] fScore = new double[K];
        double[] conformance = new double[K];
        double[] miningTime = new double[K];
        double[] soundness = new double[K];
        double[] sizes = new double[K];
        boolean[] performanceOk = new boolean[K];


        for (int i = 0; i < K; i++) {
            String foldMinerName = foldMinerName(minerName, i);

            performanceOk[i] = true;

            try {
                miningTime[i] = Double.parseDouble(results.getResult(foldMinerName, logName, "mining-time"));
            } catch (NumberFormatException e) {
                logMessage(String.format("mining time not defined for fold %d", i));
            }

            try {
                sizes[i] = Double.parseDouble(results.getResult(foldMinerName, logName, SIZE_NODE_KEY));
            } catch (NumberFormatException e) {
                logMessage(String.format("size not defined for fold %d", i));
            }

            try {
                String soundnessRes = results.getResult(foldMinerName, logName, SOUNDNESS_KEY);
                soundness[i] = soundnessRes.equals("sound") ? 1 : 0;
            } catch (NumberFormatException e) {
                logMessage(String.format("soundness not defined for fold %d", i));
            }

            try {
                fitness[i] = Double.parseDouble(results.getResult(foldMinerName, logName, FITNESS_KEY));
            } catch (NumberFormatException e) {
                logMessage(String.format("fitness not defined for fold %d", i));
                performanceOk[i] = false;
            }

            try {
                precision[i] = Double.parseDouble(results.getResult(foldMinerName, logName, PRECISION_KEY));
            } catch (NumberFormatException e) {
                logMessage(String.format("precision not defined for fold %d", i));
                performanceOk[i] = false;
            }


            try {
                fScore[i] = Double.parseDouble(results.getResult(foldMinerName, logName, F_SCORE_KEY));
            } catch (NumberFormatException e) {
                logMessage(String.format("f-score not defined for fold %d", i));
                performanceOk[i] = false;
            }

            try {
                conformance[i] = Double.parseDouble(results.getResult(foldMinerName, logName, CONFORMANCE_TOTAL_FRAC_KEY));
            } catch (NumberFormatException e) {
                logMessage(String.format("conformance frac not defined for fold %d", i));
                performanceOk[i] = false;
            }


            logMessage(String.format("Got results for fold %d", i));
        }
        results.addResult(minerName, logName, String.format("average %d-fold fitness", K), average(fitness, performanceOk));
        results.addResult(minerName, logName, String.format("average %d-fold conformance", K), average(conformance, performanceOk));
        results.addResult(minerName, logName, String.format("average %d-fold precision", K), average(precision, performanceOk));
        results.addResult(minerName, logName, String.format("average %d-fold f-score", K), average(fScore, performanceOk));
        results.addResult(minerName, logName, String.format("average %d-fold mining time", K), average(miningTime));
        results.addResult(minerName, logName, String.format("average %d-fold soundness", K), total(soundness));
        results.addResult(minerName, logName, String.format("average %d-fold size", K), average(sizes));
        results.addResult(minerName, logName, String.format("scores %d-fold fitness", K), fitness);
        results.addResult(minerName, logName, String.format("scores %d-fold precision", K), precision);
        results.addResult(minerName, logName, String.format("scores %d-fold conformance", K), conformance);
        results.addResult(minerName, logName, String.format("scores %d-fold f-score", K), fScore);
        results.addResult(minerName, logName, String.format("scores %d-fold mining time", K), miningTime);
        results.addResult(minerName, logName, String.format("scores %d-fold soundness", K), soundness);
        results.addResult(minerName, logName, String.format("scores %d-fold size", K), sizes);

        logMessage("Computed average results");
    }

    public String foldMinerName(String minerName, int i) {
        return String.format("%s-%d", minerName, i);
    }


    public static double average(double[] values, boolean[] ok) {
        double sum = 0;
        double n = 0;
        for (int i = 0; i < values.length; i++)
            if (ok[i]) {
                sum += values[i];
                n += 1;
            }
        return sum / n;
    }

    public static double average(double[] values) {
        double sum = 0;
        for (double d : values) {
            sum += d;
        }
        return sum / values.length;
    }

    public static double total(double[] values) {
        double sum = 0;
        for (double i : values) {
            sum += i;
        }
        return sum;
    }
}
