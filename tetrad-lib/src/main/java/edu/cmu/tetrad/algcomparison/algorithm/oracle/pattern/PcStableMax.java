package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * PC-Max
 *
 * @author jdramsey
 */
public class PcStableMax implements Algorithm, TakesInitialGraph, HasKnowledge {
    static final long serialVersionUID = 23L;
    private boolean compareToTrue = false;
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public PcStableMax(IndependenceWrapper test, boolean compareToTrue) {
        this.test = test;
        this.compareToTrue = compareToTrue;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.PcStableMax search = new edu.cmu.tetrad.search.PcStableMax(
                test.getTest(dataSet, parameters));
        search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
        search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
        search.setDepth(parameters.getInt("depth"));
        search.setKnowledge(knowledge);
        search.setVerbose(parameters.getBoolean("verbose"));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        if (compareToTrue) {
            return new EdgeListGraph(graph);
        } else {
            return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
        }
    }

    @Override
    public String getDescription() {
        return "PC-Max (\"Peter and Clark\") using " + test.getDescription()
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
        parameters.add("useMaxPOrientationHeuristic");
        parameters.add("maxPOrientationMaxPathLength");
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

    public boolean isCompareToTrue() {
        return compareToTrue;
    }
}
