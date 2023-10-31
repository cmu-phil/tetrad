package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class AncestralPrecision implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AncestralPrecision() {

    }

    /**
     * Returns the name of the statistic.
     * @return the name of the statistic
     */
    @Override
    public String getAbbreviation() {
        return "AncP";
    }

    /**
     * Returns the description of the statistic.
     * @return the description of the statistic
     */
    @Override
    public String getDescription() {
        return "Proportion of X~~>Y for which X~~>Y in true";
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
        int tp = 0, fp = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

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
     * @param value The value of the statistic.
     * @return  the value of the statistic
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
