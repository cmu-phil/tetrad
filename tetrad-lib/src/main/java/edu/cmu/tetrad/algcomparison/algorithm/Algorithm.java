package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.List;

/**
 * Interface that algorithm must implement.
 *
 * @author jdramsey
 */
public interface Algorithm extends HasParameters, TetradSerializable {

    /**
     * Runs the search.
     *
     * @param dataSet    The data set to run to the search on.
     * @param parameters The paramters of the search.
     * @return The result graph.
     */
    Graph search(DataModel dataSet, Parameters parameters);

    /**
     * Returns that graph that the result should be compared to.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    Graph getComparisonGraph(Graph graph);

    /**
     * Returns a short, one-line description of this algorithm. This will be
     * printed in the report.
     *
     * @return This description.
     */
    String getDescription();

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return This type.
     */
    DataType getDataType();

    /**
     * Returns the parameters that this search uses.
     *
     * @return A list of String names of parameters.
     */
    List<String> getParameters();
}
