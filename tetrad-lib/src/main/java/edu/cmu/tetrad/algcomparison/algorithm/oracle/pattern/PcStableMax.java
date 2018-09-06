package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.PcAll;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.util.List;

/**
 * PC-Max
 *
 * @author jdramsey
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "PcStableMax",
//        command = "pc-stable-max",
//        algoType = AlgType.forbid_latent_common_causes
//)
public class PcStableMax implements Algorithm, TakesInitialGraph, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private boolean compareToTrue = false;
    private IndependenceWrapper test;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public PcStableMax() {
    }

    public PcStableMax(IndependenceWrapper test, boolean compareToTrue) {
        this.test = test;
        this.compareToTrue = compareToTrue;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt("bootstrapSampleSize") < 1) {

            if (algorithm != null) {
//                initialGraph = algorithm.search(dataSet, parameters);
            }

            edu.cmu.tetrad.search.PcAll search = new edu.cmu.tetrad.search.PcAll(test.getTest(dataSet, parameters), initialGraph);
            search.setDepth(parameters.getInt("depth"));
            search.setKnowledge(knowledge);
            search.setFasType(edu.cmu.tetrad.search.PcAll.FasType.STABLE);
            search.setConcurrent(edu.cmu.tetrad.search.PcAll.Concurrent.NO);
            search.setColliderDiscovery(PcAll.ColliderDiscovery.MAX_P);
            search.setConflictRule(edu.cmu.tetrad.search.PcAll.ConflictRule.PRIORITY);
            search.setVerbose(parameters.getBoolean("verbose"));
            search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
            search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
            return search.search();
        } else {
            PcStableMax pcStableMax = new PcStableMax(test, compareToTrue);

            //pcStableMax.setKnowledge(knowledge);
            if (initialGraph != null) {
                pcStableMax.setInitialGraph(initialGraph);
            }
            DataSet data = (DataSet) dataSet;
            GeneralBootstrapTest search = new GeneralBootstrapTest(data, pcStableMax,
                    parameters.getInt("bootstrapSampleSize"));
            search.setKnowledge(knowledge);

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
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
        return "PC-Stable-Max (\"Peter and Clark\"), Priority Rule, using " + test.getDescription()
                + (algorithm != null ? " with initial graph from "
                + algorithm.getDescription() : "");
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
        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
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

    @Override
    public Graph getInitialGraph() {
        return initialGraph;
    }

    @Override
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

}