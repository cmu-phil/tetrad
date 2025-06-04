package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * Proportion of semi(X, Y) in estimated graph for which there is no semi(Y, X) in true graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ProportionSemidirectedPathsNotReversedEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public ProportionSemidirectedPathsNotReversedEst() {
    }

    /**
     * Retrieves the abbreviation for the statistic. This abbreviation will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "semi(X,Y,est)==>!semi(Y,X,true)";
    }

    /**
     * Retrieves the description of the statistic: Proportion of semi(X, Y) in estimated graph for which there is no
     * semi(Y, X) in true graph.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Proportion of semi(X, Y) in estimated graph for which there is no semi(Y, X) in true graph";
    }

    /**
     * Calculates the proportion of semi(X, Y) in the estimated graph for which there is no semi(Y, X) in the true
     * graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The proportion of semi(X, Y) in the estimated graph for which there is no semi(Y, X) in the true graph.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        List<Node> nodes = estGraph.getNodes();
        nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        int tp = 0;
        int fp = 0;

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (estGraph.paths().existsSemiDirectedPath(x, y)) {
                    if (!trueGraph.paths().existsSemiDirectedPath(y, x)) {
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
     * Retrieves the normalized value of the given statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
