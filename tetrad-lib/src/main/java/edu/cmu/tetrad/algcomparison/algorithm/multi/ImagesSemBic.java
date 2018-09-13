package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SemBicScoreImages;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.subsampling.GeneralSubSamplingTest;
import edu.pitt.dbmi.algo.subsampling.SubSamplingEdgeEnsemble;

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
@edu.cmu.tetrad.annotation.Algorithm(
        name = "IMaGES Continuous",
        command = "imgs_cont",
        algoType = AlgType.forbid_latent_common_causes
)
public class ImagesSemBic implements MultiDataSetAlgorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public ImagesSemBic() {
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
    	if (parameters.getInt("numberSubSampling") < 1) {
            final SemBicScoreImages score = new SemBicScoreImages(dataSets);
            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score);
            search.setKnowledge(knowledge);
            return search.search();
        } else {
            ImagesSemBic imagesSemBic = new ImagesSemBic();
            //imagesSemBic.setKnowledge(knowledge);

            List<DataSet> datasets = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                datasets.add((DataSet) dataModel);
            }
            GeneralSubSamplingTest search = new GeneralSubSamplingTest(datasets, imagesSemBic, parameters.getInt("numberSubSampling"));
            search.setKnowledge(knowledge);

            search.setSubSampleSize(parameters.getInt("subSampleSize"));
            search.setSubSamplingWithReplacement(parameters.getBoolean("subSamplingWithReplacement"));
            
            SubSamplingEdgeEnsemble edgeEnsemble = SubSamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("subSamplingEnsemble", 1)) {
                case 0:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt("numberSubSampling") < 1) {
            return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
        } else {
            ImagesSemBic imagesSemBic = new ImagesSemBic();
            imagesSemBic.setKnowledge(knowledge);

            List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
            GeneralSubSamplingTest search = new GeneralSubSamplingTest(dataSets, imagesSemBic, parameters.getInt("numberSubSampling"));

            search.setSubSampleSize(parameters.getInt("subSampleSize"));
            search.setSubSamplingWithReplacement(parameters.getBoolean("subSamplingWithReplacement"));
            
            SubSamplingEdgeEnsemble edgeEnsemble = SubSamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("subSamplingEnsemble", 1)) {
                case 0:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Majority;
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
//        return SearchGraphUtils.patternForDag(graph);
//        return new TsDagToPag(new EdgeListGraph(graph)).convert();
    }

    @Override
    public String getDescription() {
        return "IMaGES for continuous variables (using the SEM BIC score)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new Fges(new SemBicScore(), false).getParameters();
        parameters.add("numRuns");
        parameters.add("randomSelectionSize");
        // Subsampling
        parameters.add("numberSubSampling");
        parameters.add("subSampleSize");
        parameters.add("subSamplingWithReplacement");
        parameters.add("subSamplingEnsemble");
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
