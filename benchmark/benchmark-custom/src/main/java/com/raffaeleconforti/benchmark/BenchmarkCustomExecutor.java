package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.context.FakePluginContext;
import com.raffaeleconforti.log.util.LogCloner;
import com.raffaeleconforti.measurements.Measure;
import com.raffaeleconforti.measurements.MeasurementAlgorithm;
import com.raffaeleconforti.measurements.impl.*;
import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.PetrinetWithMarking;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


import static com.raffaeleconforti.benchmark.Common.*;

/**
 * Alternative implementation of the benchmark-commandline application. Don't use unless you have a good reason
 */
public class BenchmarkCustomExecutor {
    private final long minerTimeout;
    private final long metricTimeout;
    private final Map<String, Object> logs;

    private final ResultsMap results;
    private final ExecutorService executor;
    private final LogCloner logCloner;

    private static final XEventClassifier xEventClassifier = new XEventNameClassifier();

    public BenchmarkCustomExecutor(String logFolder, long minerTimeout, long metricTimeout) {

        this.minerTimeout = minerTimeout;
        this.metricTimeout = metricTimeout;

        this.results = new ResultsMap();

        this.executor = Executors.newSingleThreadExecutor();
        this.logCloner = new LogCloner();

        this.logs = loadLogs(logFolder);
    }

    public void stop() {
        executor.shutdownNow();
    }


    public void performBenchmark(List<MiningAlgorithm> miningAlgorithms, List<MeasurementAlgorithm> measurementAlgorithms) {
        if (measurementAlgorithms.isEmpty()) {
            logMessage("ERROR - metrics is empty, use BenchmarkCustomNative instead");
            return;
        }

        FakePluginContext fakePluginContext = new FakePluginContext();

        logMessage("DEBUG - loaded miners:");
        for (MiningAlgorithm ma : miningAlgorithms) {
            logMessage(String.format("(%s) - %s", ma.getAcronym(), ma.getAlgorithmName()));
        }

        logMessage("DEBUG - loaded measures:");
        for (MeasurementAlgorithm ma : measurementAlgorithms) {
            logMessage(String.format("(%s) - %s", ma.getAcronym(), ma.getMeasurementName()));
        }

        File resultDir = new File("./results");
        if (!resultDir.exists() && !resultDir.mkdir()) {
            logMessage("ERROR - could not create results directory, stopping");
            System.exit(-1);
        }

        for (MiningAlgorithm miner : miningAlgorithms) {
            String minerName = miner.getAcronym();
            logMessage(String.format("DEBUG - Start evaluating miner: %s (%s)", miner.getAlgorithmName(), minerName));

            setupResultsDir(miner.getAcronym());


            for (String logName : logs.keySet()) {
                XLog log = loadLog(logs.get(logName));
                if (log == null) {
                    logMessage("ERROR - could not load log, moving on");
                    continue;
                }
                logMessage("DEBUG - evaluating log: " + logName);
                DatasetName dataset = new DatasetName(logName);

                // Mine net
                PetrinetWithMarking minerResult = mineLog(fakePluginContext, miner, log, dataset);
                if (minerResult == null) {
                    continue;
                }


//                int knownSound = 0; //0: unknown, 1: known unsound, 2: known sound
                SoundnessStatus soundness = SoundnessStatus.UNKNOWN;
                for (MeasurementAlgorithm measureAlgorithm : measurementAlgorithms) {
                    String measureName = measureAlgorithm.getAcronym();
                    logMessage(String.format("DEBUG - starting measure %s", measureName));

                    try {
                        if (measureAlgorithm instanceof BPMNComplexity) {
                            logMessage("DEBUG - Mining BPMN Diagram, skipping");
                            continue;
                        }


                        computeMeasure(fakePluginContext, measureAlgorithm, minerResult, miner, log, dataset, soundness);
                        if (measureAlgorithm instanceof Soundness) {
                            if (results.getResult(minerName, dataset, "soundness").equals("sound")) {
                                soundness = SoundnessStatus.SOUND;
                            } else {
                                soundness = SoundnessStatus.UNSOUND;
                            }
                        }

                    } catch (Error e) {
                        e.printStackTrace();
                        results.addResult(minerName, dataset, measureName, "-ERROR");
                        logMessage(String.format("ERROR - computing measure %s on log %s (Error)", measureName, logName));
                    } catch (RuntimeException e) {
                        logMessage("ERROR - runtime exception");
                    } catch (Exception e) {
                        e.printStackTrace();
                        results.addResult(minerName, dataset, measureName, "-ERROR");
                        logMessage(String.format("ERROR - computing measure %s on log %s", measureName, logName));
                    }


                }
                String pathnameJSON = String.format("./results/%s/%s_%s.json", minerName, currentTime(), logName);
                results.writeJSON(pathnameJSON);
            }
        }

        results.writeJSON(String.format("./results/benchmark_%s.json", currentTime()));
    }

    private PetrinetWithMarking mineLog(UIPluginContext context, MiningAlgorithm miner, XLog log, DatasetName dataset) throws RuntimeException {
        XLog miningLog = logCloner.cloneLog(log);

        long startTime = System.currentTimeMillis();
        Future<PetrinetWithMarking> minerFuture = executor.submit(
                () -> miner.minePetrinet(context, miningLog, false, null, xEventClassifier)
        );
        try {
            PetrinetWithMarking result = minerFuture.get(this.minerTimeout, TimeUnit.MILLISECONDS);

            long miningTime = System.currentTimeMillis() - startTime;
            logMessage(String.format("DEBUG - mining done, took %d", miningTime));
            results.addResult(miner.getAcronym(), dataset, "mining-time", Long.toString(miningTime));

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
        }

        return null;
    }

    private boolean computeMeasure(UIPluginContext context, MeasurementAlgorithm measure,
                                   PetrinetWithMarking net, MiningAlgorithm miner, XLog log, DatasetName dataset,
                                   SoundnessStatus soundness) throws RuntimeException {

        XLog measureLog = logCloner.cloneLog(log);
        long startTime = System.currentTimeMillis();
        String minerName = miner.getAcronym();
        String measureName = measure.getAcronym();

        Future<Measure> measureFuture;

        switch (soundness) {
            case SOUND:
                measureFuture = executor.submit(
                        () -> measure.computeSoundMeasurement(context, xEventClassifier, net, miner, measureLog)
                );
                break;
            case UNSOUND:
                if (measure.requiresSound()) {
                    results.addResult(minerName, dataset, measureName, "-ERR-UNSOUND");
                    logMessage("DEBUG - " + measureName + " requires soundness, but model is not sound");
                    return true;
                }
            default:
                measureFuture = executor.submit(
                        () -> measure.computeMeasurement(context, xEventClassifier, net, miner, measureLog)
                );
        }

        try {
            Measure result = measureFuture.get(this.metricTimeout, TimeUnit.MILLISECONDS);
            long measureTime = System.currentTimeMillis() - startTime;
            if (measure.isMultimetrics()) {
                for (String subMetric : result.getMetrics()) {
                    results.addResult(minerName, dataset, subMetric, result.getMetricValue(subMetric));
                    logMessage("DEBUG - " + subMetric + " : " + result.getMetricValue(subMetric));
                }
            } else {
                results.addResult(minerName, dataset, measureName, String.format("%.5f", result.getValue()));
                logMessage("DEBUG - " + measureName + " : " + result.getValue());
            }

            results.addResult(minerName, dataset, measureName + "-time", Long.toString(measureTime));
            logMessage(String.format("DEBUG - %s-time : %d ms", measureName, measureTime));
            return true;
        } catch (
                InterruptedException e) {
            logMessage("ERROR - measure was interrupted");
            throw new RuntimeException("break execution");
        } catch (
                TimeoutException e) {
            logMessage(String.format("DEBUG - timeout for measure %s", measureName));
            results.addResult(miner.getAcronym(), dataset, measure.getAcronym(), String.format("-TIMEOUT: %dms", metricTimeout));
            return false;
        } catch (
                ExecutionException e) {
            e.printStackTrace();
            results.addResult(miner.getAcronym(), dataset, measure.getAcronym(), "-ERROR");
            logMessage("ERROR - measure encountered an implementation error");
            return false;
        } finally {
            measureFuture.cancel(true); // may or may not desire this
        }

    }


    enum SoundnessStatus {
        UNKNOWN,
        UNSOUND,
        SOUND;
    }
}
