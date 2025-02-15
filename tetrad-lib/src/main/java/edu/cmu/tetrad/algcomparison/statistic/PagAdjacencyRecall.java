package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * A class that implements the PagAdjacencyRecall statistic.
 * <p>
 * This statistic calculates the adjacency recall compared to the true PAG (Partial Ancestral Graph).
 */
public class PagAdjacencyRecall implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public PagAdjacencyRecall() {

    }

    /**
     * Retrieves the abbreviation for the given statistic.
     *
     * @return The abbreviation as a string.
     */
    @Override
    public String getAbbreviation() {
        return "PAR";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Adjacency Recall compared to true PAG";
    }

    /**
     * Calculates the adjacency recall compared to the true PAG (Partial Ancestral Graph).
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG of the true DAG).
     * @param estGraph   The estimated graph (same type as trueGraph).
     * @param dataModel  The data model.
     * @param parameters
     * @return The adjacency recall value as a double.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);

        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(pag, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFn = adjConfusion.getFn();
        return adjTp / (double) (adjTp + adjFn);
    }

    /**
     * Retrieves the normalized value of this statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
