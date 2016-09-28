package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.List;

/**
 * An interface to represent a random graph of some sort.
 *
 * @author jdramsey
 */
public interface RandomGraph extends TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * @param parameters Whatever parameters are need for the given graph. See
     *                   getParameters().
     * @return  Returns a random graph using the given parameters.
     */
    Graph createGraph(Parameters parameters);

    /**
     * Returns a short, one-line description of this graph type. This will be
     * printed in the report.
     *
     * @return This description.
     */
    String getDescription();

    /**
     * Returns the parameters that this graph uses.
     *
     * @return A list of String names of parameters.
     */
    List<String> getParameters();
}
