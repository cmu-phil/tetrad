package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;
import java.util.ArrayList;
import java.util.List;

/**
 * LiNGAM.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "LiNGAM",
        command = "lingam",
        algoType = AlgType.forbid_latent_common_causes
)
public class Lingam implements Algorithm {

    static final long serialVersionUID = 23L;

    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt("bootstrapSampleSize") < 1) {
            edu.cmu.tetrad.search.Lingam lingam = new edu.cmu.tetrad.search.Lingam();
            lingam.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            return lingam.search(DataUtils.getContinuousDataSet(dataSet));
        } else {
            Lingam algorithm = new Lingam();

            DataSet data = (DataSet) dataSet;

            GeneralBootstrapTest search = new GeneralBootstrapTest(data, algorithm, parameters.getInt("bootstrapSampleSize"));

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

    public String getDescription() {
        return "LiNGAM (Linear Non-Gaussian Acyclic Model";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        // Bootstrapping
        parameters.add("penaltyDiscount");
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        parameters.add("verbose");
        return parameters;
    }
}