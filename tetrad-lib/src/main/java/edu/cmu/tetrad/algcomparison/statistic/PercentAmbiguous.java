package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.graph.*;
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
    public double getValue(Graph trueGraph, Graph estGraph) {
        int numAmbiguous = 0;
        int numTriples = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node b : nodes) {
            List<Node> adjb = estGraph.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adjb.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> _adj = GraphUtils.asList(choice, adjb);
                Node a = _adj.get(0);
                Node c = _adj.get(1);

                if (estGraph.isAmbiguousTriple(a, b, c)) {
                    numAmbiguous++;
                }

                numTriples++;
            }
        }

        return numAmbiguous / (double) numTriples;
    }

    @Override
    public double getNormValue(double value) {
        return 1.0 - value;
    }
}
