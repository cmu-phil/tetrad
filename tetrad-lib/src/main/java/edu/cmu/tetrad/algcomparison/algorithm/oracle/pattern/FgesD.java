package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;

import edu.cmu.tetrad.search.SemBicScoreDeterministic;
import edu.cmu.tetrad.util.Parameters;
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
		if (parameters.getInt("numberResampling") < 1) {
			if (algorithm != null) {
//				initialGraph = algorithm.search(dataSet, parameters);
			}

			edu.cmu.tetrad.search.FgesD search;

			if (dataSet instanceof ICovarianceMatrix) {
				SemBicScoreDeterministic score = new SemBicScoreDeterministic((ICovarianceMatrix) dataSet);
				score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
				score.setDeterminismThreshold(parameters.getDouble("determinismThreshold"));
				search = new edu.cmu.tetrad.search.FgesD(score);

			} else if (dataSet instanceof DataSet) {
				SemBicScoreDeterministic score = new SemBicScoreDeterministic(new CovarianceMatrix((DataSet) dataSet));
				score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
				score.setDeterminismThreshold(parameters.getDouble("determinismThreshold"));
				search = new edu.cmu.tetrad.search.FgesD(score);

			} else {
				throw new IllegalArgumentException("Expecting a dataset or a covariance matrix.");
			}

			search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
			search.setKnowledge(knowledge);
			search.setVerbose(parameters.getBoolean("verbose"));
			search.setMaxDegree(parameters.getInt("maxDegree"));
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
		parameters.add("penaltyDiscount");
		parameters.add("symmetricFirstStep");
		parameters.add("faithfulnessAssumed");
		parameters.add("maxDegree");
		parameters.add("determinismThreshold");
		parameters.add("verbose");
		// Resampling
        parameters.add("numberResampling");
        parameters.add("percentResampleSize");
        parameters.add("resamplingWithReplacement");
        parameters.add("resamplingEnsemble");
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
