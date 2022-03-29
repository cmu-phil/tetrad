package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
public class SingleGraphAlg implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private final Graph graph;

    public SingleGraphAlg(final Graph graph) {
        this.graph = graph;
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        return this.graph;
    }

    @Override
    public Graph getComparisonGraph(final Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "Given graph";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }

    @Override
    public IKnowledge getKnowledge() {
        return new Knowledge2();
    }

    @Override
    public void setKnowledge(final IKnowledge knowledge) {
    }

}
