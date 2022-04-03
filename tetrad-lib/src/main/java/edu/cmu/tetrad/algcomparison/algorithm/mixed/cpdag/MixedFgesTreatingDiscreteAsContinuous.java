package edu.cmu.tetrad.algcomparison.algorithm.mixed.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
@Bootstrapping
public class MixedFgesTreatingDiscreteAsContinuous implements Algorithm {
    static final long serialVersionUID = 23L;

    public Graph search(DataModel Dk, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet mixedDataSet = DataUtils.getMixedDataSet(Dk);
            mixedDataSet = DataUtils.convertNumericalDiscreteToContinuous(mixedDataSet);
            SemBicScore score = new SemBicScore(new CovarianceMatrix(mixedDataSet));
            score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            Fges fges = new Fges(score);
            fges.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph p = fges.search();
            return convertBack(mixedDataSet, p);
        } else {
            MixedFgesTreatingDiscreteAsContinuous algorithm = new MixedFgesTreatingDiscreteAsContinuous();

            DataSet data = (DataSet) Dk;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    private Graph convertBack(DataSet Dk, Graph p) {
        Graph p2 = new EdgeListGraph(Dk.getVariables());

        for (int i = 0; i < p.getNodes().size(); i++) {
            for (int j = i + 1; j < p.getNodes().size(); j++) {
                Node v1 = p.getNodes().get(i);
                Node v2 = p.getNodes().get(j);

                Edge e = p.getEdge(v1, v2);

                if (e != null) {
                    Node w1 = Dk.getVariable(e.getNode1().getName());
                    Node w2 = Dk.getVariable(e.getNode2().getName());

                    Edge e2 = new Edge(w1, w2, e.getEndpoint1(), e.getEndpoint2());

                    p2.addEdge(e2);
                }
            }
        }
        return p2;
    }

    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.cpdagForDag(new EdgeListGraph(graph));
    }

    public String getDescription() {
        return "FGES2, using the SEM BIC score, treating all discrete variables as " +
                "continuous";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.VERBOSE);
        return parameters;
    }
}


