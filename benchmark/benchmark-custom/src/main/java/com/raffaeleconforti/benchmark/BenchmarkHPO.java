package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.context.FakePluginContext;
import com.raffaeleconforti.wrappers.MiningAlgorithm;
import com.raffaeleconforti.wrappers.impl.SplitMinerWrapper;
import com.raffaeleconforti.wrappers.settings.MiningSettings;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;

import static com.raffaeleconforti.benchmark.BenchmarkCustomNative.HOUR_MS;
import static com.raffaeleconforti.benchmark.Common.currentTime;
import static com.raffaeleconforti.benchmark.Common.loadLog;

public class BenchmarkHPO {
    public static void main(String[] args) {
        String logFile = "C:\\Users\\Geert\\Desktop\\Thesis\\Datasets\\Final\\FF_traces\\cptc_17_reversed.txt";
        UIPluginContext ctx = new FakePluginContext();
        ResultsMap results = new ResultsMap();
        XLog log = loadLog(logFile);
        DatasetName logName = new DatasetName("cptc_18_reversed");

        for (double eps = 0.0D; eps <= 1.0D; eps += 0.1D) {
            for (double eta = 0.0D; eta <= 1.0D; eta += 0.1D) {
                MiningSettings settings = new MiningSettings();

                settings.setParam("etaSM", eta);
                settings.setParam("epsilonSM", eps);
                MiningAlgorithm miner = new SplitMinerWrapper();

                BenchmarkCustomNative b = new BenchmarkCustomNative(HOUR_MS, HOUR_MS, 5, 6);
                String minerName = String.format("SM_e=%.1f_n=%.1f", eps, eta);
                b.runPerformance(ctx, miner, settings, log, minerName, logName, results);

                b.stop();
            }
        }

        results.writeJSON(String.format("./results/%s/%s_cptc_18_reversed.json", "SM_hpo", currentTime()));
    }
}
