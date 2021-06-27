package com.raffaeleconforti.benchmark;

import au.edu.qut.bpmn.metrics.ComplexityCalculator;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;

import java.util.Arrays;

/**
 * Updated version of the base ComplexityCalculator. Adds new options for other size metrics.
 */
public class ComplexityCalculatorCustom extends ComplexityCalculator {
    private int[] connectorDensities;
    private BPMNDiagram bpmnDiagram;

    public ComplexityCalculatorCustom() {
        super();
    }

    public ComplexityCalculatorCustom(BPMNDiagram diagram) {
        super(diagram);
        this.bpmnDiagram = diagram;
    }


    private void computeDensities() {
        if (connectorDensities != null) {
            return;
        }
        if (this.bpmnDiagram == null) {
            connectorDensities = new int[]{-1};
            return;
        }

        this.connectorDensities = new int[this.bpmnDiagram.getGateways().size()];

        int i = 0;
        for (Gateway g : this.bpmnDiagram.getGateways()) {
            connectorDensities[i] = this.bpmnDiagram.getInEdges(g).size() + this.bpmnDiagram.getOutEdges(g).size();
            i++;
        }

        Arrays.sort(this.connectorDensities);
    }

    public int computeMinConnectorDensity() {
        computeDensities();
        return this.connectorDensities[0];
    }

    public int computeMaxConnectorDensity() {
        computeDensities();
        return this.connectorDensities[this.connectorDensities.length - 1];
    }

    public double computeMeanConnectorDensity() {
        computeDensities();

        int sum = 0;
        for (int i : this.connectorDensities) {
            sum += i;
        }

        return ((double) sum) / ((double) this.connectorDensities.length);
    }

    public double computeMedianConnectorDensity() {
        computeDensities();
        int nNodes = this.connectorDensities.length;
        int idx = (nNodes - 1) / 2;
        if (nNodes % 2 == 0) {
            // nNodes = 10 -> index=4 and 5
            double sum = connectorDensities[idx] + connectorDensities[idx + 1];
            return sum / 2;
        } else {
            // nNodes = 9 -> index=4
            return connectorDensities[idx];
        }
    }

    public int[] getConnectorDensities() {
        computeDensities();
        return this.connectorDensities;
    }

    public int getFlowSize() {
        return this.bpmnDiagram.getFlows().size();
    }

    public int getConnectorSize() {
        return this.bpmnDiagram.getGateways().size();
    }
}
