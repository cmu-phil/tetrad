package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
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
        command = "d-sep",
        dataType = DataType.Graph
)
public class DSeparationTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;

    public DSeparationTest(RandomGraph randomGraph) {
        this.randomGraph = randomGraph;
    }

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        if (dataSet == null) {
            return new IndTestDSep(randomGraph.createGraph(parameters));
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
}
