package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.util.List;

/**
 * FCI.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FCIMax",
        command = "fci-max",
        algoType = AlgType.allow_latent_common_causes
)
public class FciMax implements Algorithm, TakesInitialGraph, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public FciMax() {
    }

    public FciMax(IndependenceWrapper test) {
        this.test = test;
    }

    public FciMax(IndependenceWrapper test, Algorithm algorithm) {
        this.test = test;
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("bootstrapSampleSize") < 1) {
            if (algorithm != null) {
                initialGraph = algorithm.search(dataSet, parameters);
            }

            edu.cmu.tetrad.search.FciMax search = new edu.cmu.tetrad.search.FciMax(test.getTest(dataSet, parameters));
            search.setDepth(parameters.getInt("depth"));
            search.setKnowledge(knowledge);
            search.setMaxPathLength(parameters.getInt("maxPathLength"));
            search.setCompleteRuleSetUsed(parameters.getBoolean("completeRuleSetUsed"));
            search.setPossibleDsepSearchDone(parameters.getBoolean("possibleDsepDone"));
            search.setPossibleDsepDepth(parameters.getInt("possibleDsepDepth"));
            search.setVerbose(parameters.getBoolean("verbose"));

//            if (initialGraph != null) {
//                search.setInitialGraph(initialGraph);
//            }
            return search.search();
        } else {
            FciMax algorithm = new FciMax(test);
            //algorithm.setKnowledge(knowledge);
//          if (initialGraph != null) {
//      		algorithm.setInitialGraph(initialGraph);
//  		}

            DataSet data = (DataSet) dataSet;

            GeneralBootstrapTest search = new GeneralBootstrapTest(data, algorithm, parameters.getInt("bootstrapSampleSize"));
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
        return new DagToPag(new EdgeListGraph(graph)).convert();
    }

    public String getDescription() {
        return "FCIMax (Fast Causal Inference Max) using " + test.getDescription()
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
        parameters.add("maxPathLength");
        parameters.add("completeRuleSetUsed");
        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
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
