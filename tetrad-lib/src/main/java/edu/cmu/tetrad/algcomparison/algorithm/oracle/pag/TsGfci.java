package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TsDagToPag;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.List;

/**
 * tsFCI.
 *
 * @author jdramsey
 * @author Daniel Malinsky
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "TsGFCI",
        command = "ts-gfci",
        algoType = AlgType.allow_latent_common_causes
)
public class TsGfci implements Algorithm, TakesInitialGraph, HasKnowledge, TakesIndependenceWrapper, UsesScoreWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = null;

    public TsGfci() {
    }

    public TsGfci(IndependenceWrapper type, ScoreWrapper score) {
        this.test = type;
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
//    	if (!(dataSet instanceof TimeSeriesData)) {
//            throw new IllegalArgumentException("You need a (labeled) time series data set to run TsGFCI.");
//        }
        
        if (parameters.getInt("numberResampling") < 1) {
        	if(knowledge != null) {
        		dataSet.setKnowledge(knowledge);
        	}
            edu.cmu.tetrad.search.TsGFci search = new edu.cmu.tetrad.search.TsGFci(test.getTest(dataSet, parameters),
                    score.getScore(dataSet, parameters));
            IKnowledge _knowledge = dataSet.getKnowledge() != null ? dataSet.getKnowledge() : new Knowledge2();
            search.setKnowledge(dataSet.getKnowledge());
            
            search.setVerbose(parameters.getBoolean("verbose"));
            
            return search.search();
        } else {
            TsGfci algorithm = new TsGfci(test, score);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt("numberResampling"));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble("percentResampleSize"));
            search.setResamplingWithReplacement(parameters.getBoolean("resamplingWithReplacement"));
            
            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("resamplingEnsemble", 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new TsDagToPag(new EdgeListGraph(graph)).convert();
    }

    public String getDescription() {
        return "tsGFCI (Time Series GFCI) using " + test.getDescription() + " and " + score.getDescription()
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
        parameters.addAll(score.getParameters());
        parameters.add("faithfulnessAssumed");
        parameters.add("maxIndegree");
        parameters.add("printStream");
        // Resampling
        parameters.add("numberResampling");
        parameters.add("percentResampleSize");
        parameters.add("resamplingWithReplacement");
        parameters.add("resamplingEnsemble");
        parameters.add("verbose");
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
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

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

}
