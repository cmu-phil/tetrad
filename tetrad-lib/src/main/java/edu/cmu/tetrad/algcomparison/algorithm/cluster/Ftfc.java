package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.FindTwoFactorClusters;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.util.ArrayList;
import java.util.List;

/**
 * FTFC.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FTFC",
        command = "ftfc",
        algoType = AlgType.search_for_structure_over_latents,
        dataType = DataType.Continuous
)
@Bootstrapping
public class Ftfc implements Algorithm, HasKnowledge, ClusterAlgorithm {

    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public Ftfc() {
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            ICovarianceMatrix cov = null;

            if (dataSet instanceof DataSet) {
                cov = DataUtils.getCovMatrix(dataSet);
            } else if (dataSet instanceof ICovarianceMatrix) {
                cov = (ICovarianceMatrix) dataSet;
            } else {
                throw new IllegalArgumentException("Expected a dataset or a covariance matrix.");
            }

            double alpha = parameters.getDouble(Params.ALPHA);

            boolean gap = parameters.getBoolean(Params.USE_GAP, true);
            FindTwoFactorClusters.Algorithm algorithm;

            if (gap) {
                algorithm = FindTwoFactorClusters.Algorithm.GAP;
            } else {
                algorithm = FindTwoFactorClusters.Algorithm.SAG;
            }

            FindTwoFactorClusters search
                    = new FindTwoFactorClusters(cov, algorithm, alpha);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            return search.search();
        } else {
            Ftfc algorithm = new Ftfc();

            //algorithm.setKnowledge(knowledge);
//          if (initialGraph != null) {
//      		algorithm.setInitialGraph(initialGraph);
//  		}
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
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "FTFC (Find Two Factor Clusters)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.USE_WISHART);
        parameters.add(Params.USE_GAP);
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

}
