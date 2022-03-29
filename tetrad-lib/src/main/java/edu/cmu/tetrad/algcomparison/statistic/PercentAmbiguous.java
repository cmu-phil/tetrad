package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.List;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class PercentAmbiguous implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "%AMB";
    }


    @Override
    public String getDescription() {
        return "Percent Ambiguous Triples";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        int numAmbiguous = 0;
        int numTriples = 0;

        final List<Node> nodes = estGraph.getNodes();

        for (final Node b : nodes) {
            final List<Node> adjb = estGraph.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            final ChoiceGenerator gen = new ChoiceGenerator(adjb.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                final List<Node> _adj = GraphUtils.asList(choice, adjb);
                final Node a = _adj.get(0);
                final Node c = _adj.get(1);

                if (estGraph.isAmbiguousTriple(a, b, c)) {
                    numAmbiguous++;
                }

                numTriples++;
            }
        }

        return numAmbiguous / (double) numTriples;
    }

    @Override
    public double getNormValue(final double value) {
        return 1.0 - value;
    }
}
