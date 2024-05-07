package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for M-separation test. Requires a true DAG as input.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "M-Separation Test",
        command = "m-sep-test",
        dataType = DataType.Graph
)
public class MSeparationTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The true graph.
     */
    private Graph graph;

    /**
     * Use this empty constructor to satisfy the java reflection
     */
    public MSeparationTest() {

    }

    /**
     * <p>Constructor for MSeparationTest.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public MSeparationTest(Graph graph) {
        this.graph = graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        if (dataSet == null) {
            return new MsepTest(this.graph);
        } else {
            throw new IllegalArgumentException("Expecting no data for a m-separation test.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "M-Separation Test";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }

    /**
     * <p>Setter for the field <code>graph</code>.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setGraph(Graph graph) {
        this.graph = graph;
    }

}
