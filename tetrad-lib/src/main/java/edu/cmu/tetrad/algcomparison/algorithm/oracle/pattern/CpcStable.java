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
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
public class CpcStable implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public CpcStable(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.PcAll search = new edu.cmu.tetrad.search.PcAll(test.getTest(dataSet, parameters));
        search.setDepth(parameters.getInt("depth"));
        search.setKnowledge(knowledge);
        search.setFasRule(PcAll.FasRule.FAS_STABLE);
        search.setColliderDiscovery(edu.cmu.tetrad.search.PcAll.ColliderDiscovery.CONSERVATIVE);
        search.setConflictRule(edu.cmu.tetrad.search.PcAll.ConflictRule.PRIORITY);
        search.setVerbose(parameters.getBoolean("verbose"));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "CPC-Stable (Conservative \"Peter and Clark\" Stable), Priority Rule, using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
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
