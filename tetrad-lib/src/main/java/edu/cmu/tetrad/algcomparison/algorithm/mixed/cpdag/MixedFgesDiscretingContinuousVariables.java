package edu.cmu.tetrad.algcomparison.algorithm.mixed.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.List;

/**
 * @author jdramsey
 */
@Bootstrapping
public class MixedFgesDiscretingContinuousVariables implements Algorithm {
    static final long serialVersionUID = 23L;
    private final ScoreWrapper score;

    public MixedFgesDiscretingContinuousVariables(final ScoreWrapper score) {
        this.score = score;
    }

    public Graph search(DataModel dataSet, final Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            final Discretizer discretizer = new Discretizer(DataUtils.getContinuousDataSet(dataSet));
            final List<Node> nodes = dataSet.getVariables();

            for (final Node node : nodes) {
                if (node instanceof ContinuousVariable) {
                    discretizer.equalIntervals(node, parameters.getInt(Params.NUM_CATEGORIES));
                }
            }

            dataSet = discretizer.discretize();
            final DataSet _dataSet = DataUtils.getDiscreteDataSet(dataSet);
            final Fges fges = new Fges(this.score.getScore(_dataSet, parameters));
            fges.setVerbose(parameters.getBoolean(Params.VERBOSE));
            final Graph p = fges.search();
            return convertBack(_dataSet, p);
        } else {
            final MixedFgesDiscretingContinuousVariables algorithm = new MixedFgesDiscretingContinuousVariables(this.score);

            final DataSet data = (DataSet) dataSet;
            final GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));

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
    public Graph getComparisonGraph(final Graph graph) {
        return SearchGraphUtils.cpdagForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "FGES after discretizing the continuous variables in the data set using " + this.score.getDescription();
    }

    private Graph convertBack(final DataSet Dk, final Graph p) {
        final Graph p2 = new EdgeListGraph(Dk.getVariables());

        for (int i = 0; i < p.getNodes().size(); i++) {
            for (int j = i + 1; j < p.getNodes().size(); j++) {
                final Node v1 = p.getNodes().get(i);
                final Node v2 = p.getNodes().get(j);

                final Edge e = p.getEdge(v1, v2);

                if (e != null) {
                    final Node w1 = Dk.getVariable(e.getNode1().getName());
                    final Node w2 = Dk.getVariable(e.getNode2().getName());

                    final Edge e2 = new Edge(w1, w2, e.getEndpoint1(), e.getEndpoint2());

                    p2.addEdge(e2);
                }
            }
        }
        return p2;
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = this.score.getParameters();
        parameters.add(Params.NUM_CATEGORIES);
        parameters.add(Params.VERBOSE);
        return parameters;
    }
}
