package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.PcAll;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
public class Pc implements Algorithm, TakesInitialGraph, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public Pc(IndependenceWrapper test) {
        this.test = test;
    }

    public Pc(IndependenceWrapper test, Algorithm initialGraph) {
        this.test = test;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Graph init = null;
        if (initialGraph != null) {
            init = initialGraph.search(dataSet, parameters);
        }
        edu.cmu.tetrad.search.PcAll search = new edu.cmu.tetrad.search.PcAll(test.getTest(dataSet, parameters), init);
        search.setDepth(parameters.getInt("depth"));
        search.setKnowledge(knowledge);
        search.setFasRule(PcAll.FasRule.FAS);
        search.setColliderDiscovery(PcAll.ColliderDiscovery.FAS_SEPSETS);
        search.setConflictRule(PcAll.ConflictRule.PRIORITY);
        search.setVerbose(parameters.getBoolean("verbose"));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "PC (\"Peter and Clark\"), Priority Rule, using " + test.getDescription()
                + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        parameters.add("verbose");
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
