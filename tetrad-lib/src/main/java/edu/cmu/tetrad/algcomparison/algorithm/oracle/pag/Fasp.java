package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
public class Fasp implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        DataSet continuousDataSet = DataUtils.getContinuousDataSet(dataSet);
        dataSet = TimeSeriesUtils.createLagData(continuousDataSet, 1);
        IndependenceTest test = this.test.getTest(dataSet, parameters);
        IKnowledge knowledge = dataSet.getKnowledge();
        edu.cmu.tetrad.search.Fasp search = new edu.cmu.tetrad.search.Fasp(test);
        search.setKnowledge(knowledge);
        search.setDepth(parameters.getInt("depth"));
        search.setCollapseTiers(parameters.getBoolean("collapseTiers"));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
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
        parameters.add("collapseTiers");
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
