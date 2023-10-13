package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Ancestor precision.
 *
 * @author josephramsey
 */
public class AncestorPrecision implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AncestorPrecision() {

    }

    /**
     * Returns the name of the statistic.
     * @return the name of the statistic
     */
    @Override
    public String getAbbreviation() {
        return "Anc-Prec";
    }

    /**
     * Returns the description of the statistic.
     * @return the description of the statistic
     */
    @Override
    public String getDescription() {
        return "Proportion of X~~>Y in the estimated graph for which also X~~>Y in true graph";
    }

    /**
     * Calculates the statistic.
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return the statistic
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
//                if (x == y) continue;
                if (estGraph.paths().isAncestorOf(x, y)) {
                    if (trueGraph.paths().isAncestorOf(x, y)) {
                        tp++;
                    } else {
                        fp++;
                    }
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    /**
     * Returns the norm value of the statistic.
     * @param value The norm value of the statistic.
     * @return the norm value of the statistic
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
