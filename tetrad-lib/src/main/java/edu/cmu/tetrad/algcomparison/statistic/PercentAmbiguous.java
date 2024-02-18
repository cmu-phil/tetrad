package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PercentAmbiguous implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "%AMB";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Percent Ambiguous Triples";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int numAmbiguous = 0;
        int numTriples = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node b : nodes) {
            List<Node> adjb = new ArrayList<>(estGraph.getAdjacentNodes(b));

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

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - value;
    }
}
