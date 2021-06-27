package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.benchmark.logic.MeasurementAlgorithmDiscoverer;
import com.raffaeleconforti.benchmark.logic.MiningAlgorithmDiscoverer;
import com.raffaeleconforti.measurements.MeasurementAlgorithm;
import com.raffaeleconforti.noisefiltering.event.InfrequentBehaviourFilter;
import com.raffaeleconforti.wrappers.MiningAlgorithm;
import org.apache.commons.io.FileUtils;
import org.apache.poi.util.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.raffaeleconforti.benchmark.Common.*;

public class BenchmarkCustom {
    public final static long HOUR_MS = 60 * 60 * 1000;

    private final static String LPSOLVE55 = "lpsolve55";
    private final static String LPSOLVE55J = "lpsolve55j";


    private final static String LIBLPSOLVE55 = "liblpsolve55";
    private final static String LIBLPSOLVE55J = "liblpsolve55j";

    static {
        try {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            System.out.printf("Resolved OS: '%s'\n", os);

            if (os.contains("win")) {
                System.loadLibrary(LPSOLVE55);
                System.loadLibrary(LPSOLVE55J);
            } else if (os.contains("mac")) {
                System.loadLibrary(LIBLPSOLVE55);
                System.loadLibrary(LIBLPSOLVE55J);
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                System.loadLibrary(LIBLPSOLVE55);
                System.loadLibrary(LIBLPSOLVE55J);
            }
        } catch (UnsatisfiedLinkError e) {
            loadFromJar();
        }
        System.out.println("Loaded library");
    }

    private static void loadFromJar() {
        // we need to put both DLLs to temp dir
        String path = "AC_" + new Date().getTime();
        String os = System.getProperty("os.name");
        System.out.printf("Resolved OS from JAR: '%s'\n", os);
        if (os.contains("win")) {
            loadLib(LPSOLVE55, ".ddl");
            loadLib(LPSOLVE55J, ".ddl");
        } else if (os.contains("mac")) {
            loadLib(LIBLPSOLVE55, ".jnilib");
            loadLib(LIBLPSOLVE55J, ".jnilib");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            loadLib(LIBLPSOLVE55, ".so");
            loadLib(LIBLPSOLVE55J, ".so");
        }

        System.out.println("Loaded library from JAR");
    }

    private static void loadLib(String name, String suffix) {
        name = name + suffix;
        try {
            // have to use a stream
            InputStream in = InfrequentBehaviourFilter.class.getResourceAsStream("/" + name);
            // always write to different location
            File fileOut = new File(name);
            OutputStream out = FileUtils.openOutputStream(fileOut);
            IOUtils.copy(in, out);
            in.close();
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Failed to load required %s", suffix.toUpperCase()), e);
        }

        System.out.println("LoadLib: " + name);
    }

    public static void run(String[] args) {
        List<String> miners = new ArrayList<>();
        List<String> metrics = new ArrayList<>();
        String logFolder = "";

        long minerTimeout = HOUR_MS;
        long metricTimeout = HOUR_MS;
        int k = 5;
        int fitnessThreads = 1;
        ParameterRange range = null;


        if (args.length == 0 || args[0].equalsIgnoreCase("--help")) {
            printHelp();
            return;
        }

        if (args[0].equalsIgnoreCase("-p")) {
            printAlgorithms();
            return;
        }

        int argIdx = 0;
        int prevArgIdx = -1;

        while (argIdx != args.length) {
            if (argIdx == prevArgIdx) {
                logMessage("ERR - cannot parse all args, stopping");
                return;
            }
            prevArgIdx = argIdx;

            if (args[argIdx].equalsIgnoreCase("-ext")) {
                logFolder = args[argIdx + 1];
                argIdx += 2;
                System.out.printf("DEBUG - loading logs from %s\n", logFolder);
            } else {
                System.out.println("DEBUG - using current folder for logs");
            }

            // Parse miners
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-miner")) {
                miners.add(args[argIdx + 1]);
                argIdx += 2;
            }

            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-miners")) {
                if (!miners.isEmpty()) {
                    System.out.println("ERROR - Both -miner and -miners defined, breaking");
                    System.exit(-1);
                }

                argIdx += 1;
                while (argIdx < args.length && !args[argIdx].startsWith("-")) {
                    miners.add(args[argIdx]);
                    argIdx += 1;
                }
            }

            if (miners.isEmpty()) {
                System.out.println("ERROR - no miners given, breaking");
                System.exit(-1);
            }

            // Parse metrics
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-metric")) {
                metrics.add(args[argIdx + 1]);
                argIdx += 2;
            }

            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-metrics")) {
                if (!metrics.isEmpty()) {
                    System.out.println("ERROR - Both -metric and -metrics defined, breaking");
                    System.exit(-1);
                }

                argIdx += 1;
                while (argIdx < args.length && !args[argIdx].startsWith("-")) {
                    metrics.add(args[argIdx]);
                    argIdx += 1;
                }
            }

            if (metrics.isEmpty()) {
                System.out.println("DEBUG - No metrics given, using custom setup");
            }

            // Parse miner timeout (opt)
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-miner-timeout")) {
                minerTimeout = parseDuration(args[argIdx + 1]);
                argIdx += 2;
            }
            // Parse per-metric timeout (opt)
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-metric-timeout")) {
                metricTimeout = parseDuration(args[argIdx + 1]);
                argIdx += 2;
            }
            // Parse per-metric timeout (opt)
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-k")) {
                k = Integer.parseInt(args[argIdx + 1]);
                argIdx += 2;
            }
            // Parse per-metric timeout (opt)
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-fitness-threads")) {
                fitnessThreads = Integer.parseInt(args[argIdx + 1]);
                argIdx += 2;
            }
            // Parse per-metric timeout (opt)
            if (argIdx < args.length && args[argIdx].equalsIgnoreCase("-parameter-range")) {
                String name = args[argIdx + 1];
                float start = Float.parseFloat(args[argIdx + 2]);
                float stop = Float.parseFloat(args[argIdx + 3]);
                float step = Float.parseFloat(args[argIdx + 4]);

                range = new ParameterRange(name, start, stop, step);
                argIdx += 5;
            }
        }

        List<MiningAlgorithm> miningAlgorithms = discoverAlgorithms(miners);
        List<MeasurementAlgorithm> metricAlgorithms = discoverMetrics(metrics);

        ScheduledExecutorService heartbeatTimer = Executors.newSingleThreadScheduledExecutor();
        heartbeatTimer.scheduleAtFixedRate(() -> logMessage("HEARTBEAT-TIMER"), 30, 30, TimeUnit.MINUTES);

        try {
            if (metricAlgorithms.isEmpty()) {
                BenchmarkCustomNative benchmark = new BenchmarkCustomNative(logFolder, minerTimeout, metricTimeout, k, fitnessThreads);

                if (range == null) {
                    benchmark.runBenchmark(miningAlgorithms);
                } else {
                    benchmark.runBenchmark(miningAlgorithms, range);
                }
                benchmark.stop();
            } else {
                BenchmarkCustomExecutor executor = new BenchmarkCustomExecutor(logFolder, minerTimeout, metricTimeout);
                executor.performBenchmark(miningAlgorithms, metricAlgorithms);
                executor.stop();
            }
        } finally {
            heartbeatTimer.shutdownNow();
            logMessage("SHUTDOWN");
        }
        System.exit(-1);
    }

    public static Long parseDuration(String duration) {
        if (duration.endsWith("h")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1), 10) * HOUR_MS;
        } else {
            return Long.parseLong(duration, 10);
        }
    }

    private static void printHelp() {
        System.out.println("Help not implemented");
    }

    private static void printAlgorithms() {
        Set<String> packages = new HashSet<>();
        List<MiningAlgorithm> miningAlgorithms = MiningAlgorithmDiscoverer.discoverAlgorithms(packages);
        List<MeasurementAlgorithm> measurementAlgorithms = MeasurementAlgorithmDiscoverer.discoverAlgorithms(packages);
        int index;

        miningAlgorithms.sort((o1, o2) -> o2.getAlgorithmName().compareTo(o1.getAlgorithmName()));
        measurementAlgorithms.sort((o1, o2) -> o2.getMeasurementName().compareTo(o1.getMeasurementName()));

        index = 0;
        System.out.println("Mining algorithms available: ");
        for (MiningAlgorithm ma : miningAlgorithms)
            System.out.println(index++ + " - " + ma.getAlgorithmName() + " (" + ma.getAcronym() + ")");
        System.out.println();

        index = 0;
        System.out.println("Measurement algorithms available: ");
        for (MeasurementAlgorithm ma : measurementAlgorithms)
            System.out.println(index++ + " - " + ma.getMeasurementName() + " : " + ma.getAcronym());


    }
}

class ParameterRange {
    private String name;
    private float start;
    private float stop;
    private float step;

    public ParameterRange(String name, float start, float stop, float step) {
        this.name = name;
        this.start = start;
        this.stop = stop;
        this.step = step;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getStart() {
        return start;
    }

    public void setStart(float start) {
        this.start = start;
    }

    public float getStop() {
        return stop;
    }

    public void setStop(float stop) {
        this.stop = stop;
    }

    public float getStep() {
        return step;
    }

    public void setStep(float step) {
        this.step = step;
    }
}