package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * A class implementing the Semidirected-Rec statistic.
 */
public class SemidirectedRecall implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public SemidirectedRecall() {
    }

    /**
     * Retrieves the abbreviation for the SemidirectedRecall statistic.
     *
     * @return The abbreviation for the SemidirectedRecall statistic.
     */
    @Override
    public String getAbbreviation() {
        return "Semidirected-Rec";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The short description of this statistic.
     */
    @Override
    public String getDescription() {
        return "Proportion of (X, Y) where if semidirected path in true then also in est";
    }

    /**
     * Calculates the Semidirected-Rec statistic, which is the proportion of (X, Y) where if there is a semidirected
     * path in the true graph, then there is also a semidirected path in the estimated graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters
     * @return The Semidirected-Rec statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0, fn = 0;

        List<Node> nodes = estGraph.getNodes();

        nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.paths().existsSemiDirectedPath(x, Collections.singleton(y))) {
                    if (estGraph.paths().existsSemiDirectedPath(x, Collections.singleton(y))) {
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
