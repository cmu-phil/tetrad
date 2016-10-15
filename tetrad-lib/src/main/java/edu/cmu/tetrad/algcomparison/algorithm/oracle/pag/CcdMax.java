package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * FGS (the heuristic version).
 *
 * @author jdramsey
 */
public class CcdMax implements Algorithm {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public CcdMax(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        DataSet continuousDataSet = DataUtils.getContinuousDataSet(dataSet);
        IndependenceTest test = this.test.getTest(continuousDataSet, parameters);
        edu.cmu.tetrad.search.CcdMax search = new edu.cmu.tetrad.search.CcdMax(test);
        search.setApplyOrientAwayFromCollider(parameters.getBoolean("applyR1"));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return graph;
    }

    @Override
    public String getDescription() {
        return "CCD-Max (Cyclic Discovery Search Max) using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        parameters.add("applyR1");
        return parameters;
    }
}
