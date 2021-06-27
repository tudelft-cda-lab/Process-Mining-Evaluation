package com.raffaeleconforti.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static com.raffaeleconforti.benchmark.Common.logMessage;

public class ResultsMap {
    public static final String UNKNOWN_RESULT = "-UNKNOWN";
    private final ObjectMapper SORTED_MAPPER;
    private final Map<String, Map<String, Map<String, Serializable>>> results; // dataset -> miner -> metric -> score

    public ResultsMap() {
        this.results = new HashMap<>();
        this.SORTED_MAPPER = new ObjectMapper();
        SORTED_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public void addResult(String miner, DatasetName dataset, String measure, Serializable result) {
        logMessage(String.format("Adding result: [%s] miner %s, metric %s: %s", dataset, miner, measure, result));
        String logName = dataset.toString();
        if (!results.containsKey(logName)) {
            results.put(logName, new HashMap<>());
        }
        Map<String, Map<String, Serializable>> logMap = results.get(logName);

        if (!logMap.containsKey(miner)) {
            logMap.put(miner, new HashMap<>());
        }
        Map<String, Serializable> minerMap = logMap.get(miner);

        if (minerMap.containsKey(measure)) {
            logMessage(String.format("WARNING - results already contain key for measure %s, adding suffix", measure));
            int i = 1;
            while (true) {
                String newName = String.format("%s_%d", measure, i++);
                if (!minerMap.containsKey(newName)) {
                    minerMap.put(newName, result);
                    break;
                }
            }
        } else {
            minerMap.put(measure, result);
        }
    }

    public void addResult(String miner, DatasetName dataset, String measure, Serializable result, long time) {
        addResult(miner, dataset, measure, result);
        addResult(miner, dataset, measure + "-time", time + " ms");
    }

    public void addIfMissing(String miner, DatasetName dataset, String measure, Serializable result) {
        if (getResult(miner, dataset, measure).equals(UNKNOWN_RESULT)) {
            addResult(miner, dataset, measure, result);
        }
    }

    public String getResult(String miner, DatasetName dataset, String measure) {
        try {
            Serializable res = results.get(dataset.toString()).get(miner).get(measure);
            if (res == null) {
                return UNKNOWN_RESULT;
            }

            return res.toString();
        } catch (Exception e) {
            return UNKNOWN_RESULT;
        }
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(results);
        } catch (JsonProcessingException e) {
            return "ERROR - toString";
        }
    }

    public void writeJSON(String filename, DatasetName datasetName) {
        try {
            Map<String, Map<String, Serializable>> res = results.get(datasetName.toString());
            String json = SORTED_MAPPER.writeValueAsString(res);
            logMessage(json);

            FileOutputStream fileOut = new FileOutputStream(filename);
            SORTED_MAPPER.writerWithDefaultPrettyPrinter().writeValue(fileOut, res);
            fileOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeJSON(String filename) {
        writeJSON(filename, false);
    }

    public void writeJSON(String filename, boolean print) {
        try {
            String json = SORTED_MAPPER.writeValueAsString(results);
            if (print) {
                logMessage(json);
            }

            FileOutputStream fileOut = new FileOutputStream(filename);
            SORTED_MAPPER.writerWithDefaultPrettyPrinter().writeValue(fileOut, results);
            fileOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class DatasetName {
    private final String value;

    public DatasetName(String name) {
        this.value = name;
    }

    public String toString() {
        return this.value;
    }
}

