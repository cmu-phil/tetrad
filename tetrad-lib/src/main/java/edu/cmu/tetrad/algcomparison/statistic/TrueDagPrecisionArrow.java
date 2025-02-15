package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * The proportion of X*->Y in the estimated graph for which there is no path Y~~>X in the true graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TrueDagPrecisionArrow implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * This class represents a statistic that calculates the precision for arrows compared to the true DAG.
     */
    public TrueDagPrecisionArrow() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "*->-Prec";
    }

    /**
     * Retrieves the description of the statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Proportion of X*->Y in the estimated graph for which there is no path Y~~>X in the true graph";
    }

    /**
     * Calculates the proportion of X*->Y in the estimated graph for which there is no path Y~~>X in the true graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters
     * @return The calculated proportion value.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;
        int fp = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (estGraph.isAdjacentTo(x, y) && estGraph.getEndpoint(x, y) == Endpoint.ARROW) {
                    if (!trueGraph.paths().isAncestorOf(y, x)) {
                        tp++;
                    } else {
//                        System.out.println("Shouldn't be " + y + "~~>" + x + ": " + estGraph.getEdge(x, y));
                        fp++;
                    }
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    /**
     * Retrieves the normalized value of the statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
