package com.raffaeleconforti.benchmark;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Datastructures and helper methods to load the state machines produced by flexfringe.
 */
class DFA {
    @JsonProperty("nodes")
    private List<DFANode> nodes;
    @JsonProperty("edges")
    private List<DFAEdge> edges;

    public DFA(List<DFANode> nodes, List<DFAEdge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public DFA() {
    }

    public static DFA loadJSON(String filename) {
        File data = new File(filename);
        return loadJSON(data);
    }

    public static DFA loadJSON(File machineFile) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            return mapper.readValue(machineFile, DFA.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public List<DFANode> getNodes() {
        return nodes;
    }

    public void setNodes(List<DFANode> nodes) {
        this.nodes = nodes;
    }

    public List<DFAEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<DFAEdge> edges) {
        this.edges = edges;
    }
}

class DFAEdge {
    @JsonProperty("source")
    private int src;
    @JsonProperty("target")
    private int dst;
    @JsonProperty("name")
    private String label;
    @JsonProperty("appearances")
    private int count;

    public DFAEdge(int src, int dst, String label, int count) {
        this.src = src;
        this.dst = dst;
        this.label = label;
        this.count = count;
    }

    public DFAEdge() {
    }

    public int getSrc() {
        return src;
    }

    public void setSrc(int src) {
        this.src = src;
    }

    public int getDst() {
        return dst;
    }

    public void setDst(int dst) {
        this.dst = dst;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}


class DFANode {
    @JsonProperty("id")
    private int id;
    @JsonProperty("label")
    private String label;
    @JsonProperty("size")
    private int size;
    @JsonProperty("issink")
    private boolean isSink;
    @JsonProperty("data")
    private Map<String, Object> data;


    public DFANode(int id, String label, int size, Map<String, Object> data) {
        this.id = id;
        this.label = label;
        this.size = size;
        this.data = data;
    }

    public DFANode() {
    }

    public boolean isTerminating() {
        return this.isSink || this.getnTerminate() > 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getnTerminate() {
        return (Integer) data.getOrDefault("total_final", 0);
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
