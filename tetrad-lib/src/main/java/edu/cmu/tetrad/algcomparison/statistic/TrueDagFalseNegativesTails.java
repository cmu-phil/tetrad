package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * The class TrueDagFalseNegativesTails implements the Statistic interface to calculate the number of false negatives
 * for tails compared to the true Directed Acyclic Graph (DAG).
 */
public class TrueDagFalseNegativesTails implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public TrueDagFalseNegativesTails() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation as a String.
     */
    @Override
    public String getAbbreviation() {
        return "DFNT";
    }

    /**
     * Retrieves a short one-line description of the statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "False Negatives for Tails compared to true DAG";
    }

    /**
     * Calculates the number of false negatives for tails compared to the true DAG.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters
     * @return The number of false negatives for tails.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
//        int tp = 0;
        int fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.paths().isAncestorOf(x, y)) {
                    Edge e = estGraph.getEdge(x, y);

                    if (e != null && e.getProximalEndpoint(x) != Endpoint.TAIL) {
                        fn++;
                    }
                }
            }
        }

        return fn;
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
