package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.FasLofs;
import edu.cmu.tetrad.search.Lofs2;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple
 * values.
 *
 * @author jdramsey
 */
public class FasRSkewConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
	static final long serialVersionUID = 23L;
	private boolean empirical = false;
	private IKnowledge knowledge = new Knowledge2();

	public FasRSkewConcatenated() {
		this.empirical = false;
	}

	public FasRSkewConcatenated(boolean empirical) {
		this.empirical = empirical;
	}

	@Override
	public Graph search(List<DataModel> dataModels, Parameters parameters) {
		if (!parameters.getBoolean("bootstrapping")) {
			List<DataSet> dataSets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				dataSets.add((DataSet) dataModel);
			}

			DataSet dataSet = DataUtils.concatenate(dataSets);

			FasLofs search;

			if (empirical) {
				search = new FasLofs(dataSet, Lofs2.Rule.RSkewE);
			} else {
				search = new FasLofs(dataSet, Lofs2.Rule.RSkew);
			}

			search.setDepth(parameters.getInt("depth"));
			search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
			search.setKnowledge(knowledge);
			return getGraph(search);
		}else{
			FasRSkewConcatenated algorithm = new FasRSkewConcatenated(empirical);
			algorithm.setKnowledge(knowledge);

			List<DataSet> datasets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				datasets.add((DataSet) dataModel);
			}
			GeneralBootstrapTest search = new GeneralBootstrapTest(datasets, algorithm,
					parameters.getInt("bootstrapSampleSize"));

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

	private Graph getGraph(FasLofs search) {
		return search.search();
	}

	@Override
	public Graph search(DataModel dataSet, Parameters parameters) {
		if (!parameters.getBoolean("bootstrapping")) {
			return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
		}else{
			FasRSkewConcatenated algorithm = new FasRSkewConcatenated(empirical);
			algorithm.setKnowledge(knowledge);

			List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
			GeneralBootstrapTest search = new GeneralBootstrapTest(dataSets, algorithm,
					parameters.getInt("bootstrapSampleSize"));

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
		return new EdgeListGraph(graph);
	}

	@Override
	public String getDescription() {
		return "FAS followed by RSkew " + (empirical ? "Empirical " : "");
	}

	@Override
	public DataType getDataType() {
		return DataType.Continuous;
	}

	@Override
	public List<String> getParameters() {
		List<String> parameters = new ArrayList<>();
		parameters.add("depth");
		parameters.add("penaltyDiscount");

		parameters.add("numRuns");
		parameters.add("randomSelectionSize");
		// Bootstrapping
		parameters.add("bootstrapping");
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
}
