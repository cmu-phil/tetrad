package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for M-separation test. Requires a true DAG as input.
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "M-Separation Test",
        command = "m-sep-test",
        dataType = DataType.Graph
)
public class MSeparationTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;
    private Graph graph;

    /**
     * Use this empty constructor to satisfy the java reflection
     */
    public MSeparationTest() {

    }

    public MSeparationTest(Graph graph) {
        this.graph = graph;
    }

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        if (dataSet == null) {
            return new MsepTest(this.graph);
        } else {
            throw new IllegalArgumentException("Expecting no data for a m-separation test.");
        }
    }

    @Override
    public String getDescription() {
        return "M-Separation Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Graph;
    }

    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

}
