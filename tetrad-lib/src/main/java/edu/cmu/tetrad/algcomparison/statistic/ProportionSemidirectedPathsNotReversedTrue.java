package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * Proportion of semi(X, Y) in true graph for which there is no semi(Y, Z) in estimated graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ProportionSemidirectedPathsNotReversedTrue implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public ProportionSemidirectedPathsNotReversedTrue() {
    }

    /**
     * Returns the abbreviation for the statistic. The abbreviation is a short string that represents the statistic and
     * will be printed at the top of each column in the report.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "semi(X,Y,true)==>!semi(Y,X,est)";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Proportion of semi(X, Y) in true graph for which there is no semi(Y, Z) in estimated graph";
    }

    /**
     * Calculates the proportion of semi(X, Y) paths in the true graph for which there is no semi(Y, Z) path in the
     * estimated graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The proportion of semi(X, Y) paths that do not have a semi(Y, Z) path in the estimated graph.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        List<Node> nodes = estGraph.getNodes();
        nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        int tp = 0;
        int fn = 0;

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.paths().existsSemiDirectedPath(x, y)) {
                    if (!estGraph.paths().existsSemiDirectedPath(y, x)) {
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
     * Calculates the normalized value of a given statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
