package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Set;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * <p>Maximum unfaithfulness index for the extimated graph. The maximum unfaithfulness index of a graph is the sum of
 * the sizes of the maximal cliques minus one divided by the number of edges in the graph. This statistic is
 * specifically for permutation algorithms.</p>
 *
 * <p>This statistic calculates the number of edges there would be in the graph for a permutation algorithm if all
 * maximal cliques were subject to maximum unfaithfulness for a linear, Gaussian model and the maximal cliques had no
 * common edges. This is given as a ratio to the number of edges in the estimated graph. Empirically, the closer this
 * number is to 1, the more likely the estimated graph to be a good fit for the data. This is not a substitute for BIC
 * but may help determine whether a particular single empirical graph may have high precisions.</p>
 *
 * @author josephramsey
 */
public class PercentMaxCliqueEdgesNonoverlapping implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "POE";
    }

    @Override
    public String getDescription() {
        return "Percent Overlapping Edges in Maximal Cliques";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Set<Set<Node>> maxCliques = estGraph.paths().maxCliques();
        int edgesIfMaxCliquesNonoverlapping = 0;

        for (Set<Node> clique : maxCliques) {
            edgesIfMaxCliquesNonoverlapping += (clique.size() * (clique.size() - 1) / 2);
        }

        int overlappingEges = edgesIfMaxCliquesNonoverlapping;
        return overlappingEges / (double) (estGraph.getNumEdges());
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}

