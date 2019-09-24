package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
@Bootstrapping
public class PcFges implements Algorithm, TakesInitialGraph, HasKnowledge {

    static final long serialVersionUID = 23L;
    private boolean compareToTrue = false;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();
    private Graph initialGraph = null;

    public PcFges(ScoreWrapper score, boolean compareToTrueGraph) {
        this.score = score;
        this.compareToTrue = compareToTrueGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet _dataSet = (DataSet) dataSet;
            ICovarianceMatrix cov = new CovarianceMatrix(_dataSet);

            edu.cmu.tetrad.search.FasStable fas = new edu.cmu.tetrad.search.FasStable(new IndTestFisherZ(cov, 0.001));//parameters.getDouble("alpha")));
            Graph bound = fas.search();

            edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score.getScore(cov, parameters));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
            search.setKnowledge(knowledge);
            search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
            search.setSymmetricFirstStep(parameters.getBoolean(Params.SYMMETRIC_FIRST_STEP));

            System.out.println("Bound graph done");

            Object obj = parameters.get(Params.PRINT_STREAM);
            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            search.setBoundGraph(bound);
            return search.search();
    	}else{
    		PcFges algorithm = new PcFges(score, compareToTrue);
    		
			if (initialGraph != null) {
				algorithm.setInitialGraph(initialGraph);
			}
			DataSet data = (DataSet) dataSet;
			GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));
            
            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
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
			search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
			
			search.setParameters(parameters);
			search.setVerbose(parameters.getBoolean(Params.VERBOSE));
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
        return "PC-FGES (PC followed by FGES) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.SYMMETRIC_FIRST_STEP);
        parameters.add(Params.MAX_DEGREE);

        parameters.add(Params.VERBOSE);

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

    public void setCompareToTrue(boolean compareToTrue) {
        this.compareToTrue = compareToTrue;
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
