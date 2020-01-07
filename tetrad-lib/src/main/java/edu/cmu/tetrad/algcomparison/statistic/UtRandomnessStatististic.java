package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.List;
import java.util.Set;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class UtRandomnessStatististic implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "UtRandomness";
    }

    @Override
    public String getDescription() {
        return "0 = completely reversed, 1 = completely correct";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        UnshieldedTripleConfusion confusion = new UnshieldedTripleConfusion(trueGraph, estGraph);

        Set<Edge> edges = confusion.getInvolvedUtFp();

        if (edges.isEmpty()) return 1;

        int correct = 0;
        int count = 0;

        for (Edge edge : edges) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (trueGraph.isDirectedFromTo(a, b) == estGraph.isDirectedFromTo(a, b)) {
                correct++;
            }

            count++;
        }

        return correct / (double) count;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
