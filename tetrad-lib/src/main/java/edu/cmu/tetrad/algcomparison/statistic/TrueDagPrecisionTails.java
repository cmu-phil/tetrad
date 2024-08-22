package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.Serial;
import java.util.List;

/**
 * A class that implements the Statistic interface to calculate the proportion of X-->Y edges in the estimated graph for
 * which there is a path X~~>Y in the true graph.
 */
public class TrueDagPrecisionTails implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * This class represents a statistic that calculates the precision for tails compared to the true DAG.
     */
    public TrueDagPrecisionTails() {
    }

    /**
     * Returns the abbreviation for the statistic.
     *
     * @return The abbreviation string.
     */
    @Override
    public String getAbbreviation() {
        return "-->-Prec";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Proportion of X-->Y in the estimated graph for which there is a path X~~>Y in the true graph";
    }

    /**
     * Calculates the proportion of X-->Y edges in the estimated graph for which there is a path X~~>Y in the true
     * graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The proportion of X-->Y edges in the estimated graph for which there is a path X~~>Y in the true graph.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                Edge edge = estGraph.getEdge(x, y);

                if (edge == null) continue;

                if (Edges.directedEdge(x, y).equals(edge)) {
                    if (trueGraph.paths().isAncestorOf(x, y)) {
                        tp++;
                    } else {
                        fp++;
                    }

                    if (trueGraph.paths().isAncestorOf(y, x)) {
//                        System.out.println("Should be " + y + "~~>" + x + ": " + estGraph.getEdge(x, y));
                    } else {
//                        System.out.println("Should be " + x + "o~~>" + y + ": " + estGraph.getEdge(x, y));
                    }
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    /**
     * Calculates the normalized value of a statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
