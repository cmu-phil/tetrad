package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TsDagToPag;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * tsFCI.
 *
 * @author jdramsey
 * @author dmalinsky
 */
public class TsGfci implements Algorithm, TakesInitialGraph, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = null;

    public TsGfci(IndependenceWrapper type) {
        this.test = type;
    }

//    public TsGfci(IndependenceWrapper type, Algorithm initialGraph) {
//        this.test = type;
//        this.initialGraph = initialGraph;
//    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
//        Graph initial = null;
//
//        if (initialGraph != null) {
//            initial = initialGraph.search(dataSet, parameters);
//        }

        edu.cmu.tetrad.search.TsGFci search = new edu.cmu.tetrad.search.TsGFci(test.getTest(dataSet, parameters));

//        if (initial != null) {
//            search.setInitialGraph(initial);
//        }

        search.setKnowledge(knowledge);

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) { return new TsDagToPag(graph).convert(); }

    public String getDescription() {
        return "tsIMaGES (Time Series IMaGES) using " + test.getDescription() +
                (initialGraph != null ? " with initial graph from " +
                        initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        return test.getParameters();
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
