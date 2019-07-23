package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for D-separation test. Requires a true DAG as input.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "D-Separation Test",
        command = "d-sep-test",
        dataType = DataType.Graph
)
public class DSeparationTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;
    private Graph graph;

    /**
     * Use this empty constructor to satisfy the java reflection
     */
    public DSeparationTest() {

    }
    
    public DSeparationTest(Graph graph) {
        this.graph = graph;
    }

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        if (dataSet == null) {
            return new IndTestDSep(graph);
        } else {
            throw new IllegalArgumentException("Expecting no data for a d-separation test.");
        }
    }

    @Override
    public String getDescription() {
        return "D-Separation Test";
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
