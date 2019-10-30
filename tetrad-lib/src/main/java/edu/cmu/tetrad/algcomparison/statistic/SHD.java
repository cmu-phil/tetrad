package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates the structural Hamming distance (SHD) between the estimated graph and
 * the true graph.
 *
 * @author jdramsey
 */
public class SHD implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "SHD";
    }

    @Override
    public String getDescription() {
        return "Structural Hamming Distance";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return SearchGraphUtils.structuralHammingDistance(trueGraph, estGraph);
    }

    @Override
    /**
     * This will be given the index of the SHD stat.
     */
    public double getNormValue(double value) {
        return 1.0 - Math.tanh(0.001 * value);
    }
}
