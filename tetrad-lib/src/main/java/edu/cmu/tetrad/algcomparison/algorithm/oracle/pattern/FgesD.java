package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScoreDeterministic;
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
public class FgesD implements Algorithm, TakesInitialGraph, HasKnowledge {

	static final long serialVersionUID = 23L;
	private boolean compareToTrue = false;
	private Algorithm algorithm = null;
	private Graph initialGraph = null;
	private IKnowledge knowledge = new Knowledge2();

	public FgesD() {
		this.compareToTrue = false;
	}

	@Override
	public Graph search(DataModel dataSet, Parameters parameters) {
		if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
			if (algorithm != null) {
//				initialGraph = algorithm.search(dataSet, parameters);
			}

			edu.cmu.tetrad.search.FgesD search;

			if (dataSet instanceof ICovarianceMatrix) {
				SemBicScoreDeterministic score = new SemBicScoreDeterministic((ICovarianceMatrix) dataSet);
				score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
				score.setDeterminismThreshold(parameters.getDouble(Params.DETERMINISM_THRESHOLD));
				search = new edu.cmu.tetrad.search.FgesD(score);

			} else if (dataSet instanceof DataSet) {
				SemBicScoreDeterministic score = new SemBicScoreDeterministic(new CovarianceMatrix((DataSet) dataSet));
				score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
				score.setDeterminismThreshold(parameters.getDouble(Params.DETERMINISM_THRESHOLD));
				search = new edu.cmu.tetrad.search.FgesD(score);

			} else {
				throw new IllegalArgumentException("Expecting a dataset or a covariance matrix.");
			}

			search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
			search.setKnowledge(knowledge);
			search.setVerbose(parameters.getBoolean(Params.VERBOSE));
			search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
			// search.setSymmetricFirstStep(parameters.getBoolean("symmetricFirstStep"));

			Object obj = parameters.get("printStedu.cmream");
			if (obj instanceof PrintStream) {
				search.setOut((PrintStream) obj);
			}

			if (initialGraph != null) {
				search.setInitialGraph(initialGraph);
			}

			return search.search();
		} else {
			FgesD algorithm = new FgesD();

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
		if (false) {
			return new EdgeListGraph(graph);
		} else {
			return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
		}
	}

	@Override
	public String getDescription() {
		return "FGESD (Fast Greedy Equivalence Search Deterministic)";
	}

	@Override
	public DataType getDataType() {
		return DataType.Continuous;
	}

	@Override
	public List<String> getParameters() {
		List<String> parameters = new ArrayList<>();
		parameters.add(Params.PENALTY_DISCOUNT);
		parameters.add(Params.SYMMETRIC_FIRST_STEP);
		parameters.add(Params.FAITHFULNESS_ASSUMED);
		parameters.add(Params.MAX_DEGREE);
		parameters.add(Params.DETERMINISM_THRESHOLD);

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
		this.algorithm = algorithm;
	}

}
