package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AncestralRecall implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AncestralRecall() {

    }

    /**
     * {@inheritDoc}
     *
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "AncR";
    }

    /**
     * {@inheritDoc}
     *
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Proportion of X~~>Y in true for which X~~>Y in est";
    }

    /**
     * {@inheritDoc}
     *
     * Calculates the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.paths().isAncestorOf(x, y)) {
                    if (estGraph.paths().isAncestorOf(x, y)) {
                        tp++;
                    } else {
                        fn++;
                    }
                }
            }
        }

        return tp / (double) (tp + fn);
    }

    /**
     * {@inheritDoc}
     *
     * Returns the norm value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
