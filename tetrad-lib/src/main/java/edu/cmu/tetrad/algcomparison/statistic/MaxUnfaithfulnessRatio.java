package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Set;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * <p>Maximum unfaithfulness ratio for the extimated graph. The maximum unfaithfulness ratio of a graph is the sum of
 * the sizes of the maximal cliques minus one divided by the number of edges in the graph. This statistic is
 * specifically for perutation algorithms.</p>
 *
 * <p>This statistic calculates the number of edges there would be in the graph for a permutation algorithm if all
 * maximal cliques were subject to maximum unfaithfulness for a linear, Gaussian model and the maximal cliques had no
 * common edges. This is given as a ratio to the number of edges in the estimated graph. The lower thiw number, the more
 * likely the estimated graph is a good fit for the data.</p>
 *
 * @author josephramsey
 */
public class MaxUnfaithfulnessRatio implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "MUR";
    }

    @Override
    public String getDescription() {
        return "Maximum unfaithfulness ratio";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Set<Set<Node>> maxCliques = estGraph.paths().maxCliques();
        double sum = 0.0;

        for (Set<Node> clique : maxCliques) {
            sum += (clique.size() - 1);
        }

        return sum / (double) estGraph.getNumEdges();
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}

