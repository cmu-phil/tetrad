package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.StatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetrad.util.Params.*;
import static edu.cmu.tetrad.util.StatUtils.correlation;
import static edu.cmu.tetrad.util.StatUtils.skewness;
import static java.lang.Math.*;
import static java.lang.Math.signum;

/**
 *
 */
public class FaskVote {

    // An initial graph to orient, skipping the adjacency step.
    private Graph initialGraph = null;

    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();

    // A threshold for including extra adjacencies due to skewness.
    private double extraEdgeThreshold = 0.3;

    // True if FAS adjacencies should be included in the output.
    private boolean useFasAdjacencies = true;

    // True if skew adjacencies should be included in the output.
    private boolean useSkewAdjacencies = true;

    // Threshold for reversing casual judgments for negative coefficients.
    private double delta = -0.2;

    private final List<DataSet> dataSets;

    public FaskVote(List<DataSet> dataSets) {
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search(Parameters parameters) {
        List<Graph> graphs = new ArrayList<>();

        ImagesSemBic imagesSemBic = new ImagesSemBic();
        List<DataModel> _dataSets = new ArrayList<>(dataSets);
        Graph external = imagesSemBic.search(_dataSets, parameters);

//        if (true) return external;

        for (DataSet dataSet1 : dataSets) {
            Fask fask;

            if (parameters.getInt(FASK_ADJACENCY_METHOD) != 3) {
                fask = new Fask(dataSet1, new FisherZ().getTest(dataSet1, parameters));
            } else if (parameters.getInt(FASK_ADJACENCY_METHOD) == 3) {
                fask = new Fask(dataSet1, new SemBicTest().getTest(dataSet1, parameters));
            } else {
                throw new IllegalStateException("That adacency method for FASK was not configured: "
                        + parameters.getInt(FASK_ADJACENCY_METHOD));
            }

            Fask.AdjacencyMethod adjacencyMethod = Fask.AdjacencyMethod.FAS_STABLE;

            switch (parameters.getInt(FASK_ADJACENCY_METHOD)) {
                case 1:
                    adjacencyMethod = Fask.AdjacencyMethod.FAS_STABLE;
                    break;
                case 2:
                    adjacencyMethod = Fask.AdjacencyMethod.FAS_STABLE_CONCURRENT;
                    break;
                case 3:
                    adjacencyMethod = Fask.AdjacencyMethod.FGES;
                    break;
                case 4:
                    adjacencyMethod = Fask.AdjacencyMethod.EXTERNAL_GRAPH;
                    break;
            }

            fask.setDepth(parameters.getInt(DEPTH));
            fask.setAdjacencyMethod(adjacencyMethod);
            fask.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            fask.setTwoCycleScreeningThreshold(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
            fask.setTwoCycleTestingAlpha(parameters.getDouble(TWO_CYCLE_TESTING_ALPHA));
            fask.setDelta(parameters.getDouble(FASK_DELTA));
            fask.setEmpirical(!parameters.getBoolean(FASK_NONEMPIRICAL));
            fask.setExternalGraph(new EdgeListGraph(external));
            Graph search = fask.search();
            search = GraphUtils.replaceNodes(search, dataSets.get(0).getVariables());
            graphs.add(search);
        }

        List<Node> nodes = dataSets.get(0).getVariables();
        Graph out = new EdgeListGraph(nodes);

        double[][][] D = new double[dataSets.size()][][];

        for (int k = 0; k < dataSets.size(); k++) {
            D[k] =  DataUtils.standardizeData(dataSets.get(k).getDoubleData()).transpose().toArray();
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                double sum = 0;
                int count = 0;

                Node X = nodes.get(i);
                Node Y = nodes.get(j);

                if (!external.isAdjacentTo(X, Y)) continue;

                for (int k = 0; k < graphs.size(); k++) {
                    double[] x = D[k][i];
                    double[] y = D[k][j];

                    double lr = faskLeftRightV2(x, y, parameters.getBoolean(FASK_NONEMPIRICAL));

                    sum += lr;
                    count++;
                }

                double mean = sum / count;

                System.out.println(X + " " + Y + " " + mean);

                if (mean < 0) {
                    out.addDirectedEdge(Y, X);
                }
            }
        }

        return out;
    }

    private double faskLeftRightV2(double[] x, double[] y, boolean empirical) {
        double sx = skewness(x);
        double sy = skewness(y);
        double r = correlation(x, y);
        double lr = correxp(x, y, x) - correxp(x, y, y);

        if (empirical) {
            lr *= signum(sx) * signum(sy);
        }

        lr *= signum(r);
        return lr;
    }

    private static double correxp(double[] x, double[] y, double[] z) {
        return E(x, y, z) / sqrt(E(x, x, z) * E(y, y, z));
    }

    // Returns E(XY | Z > 0); Z is typically either X or Y.
    private static double E(double[] x, double[] y, double[] z) {
        double exy = 0.0;
        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (z[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        }

        return exy / n;
    }

    /**
     * @return the current knowledge.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    /**
     * @param knowledge Knowledge of forbidden and required edges.
     */
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    //======================================== PRIVATE METHODS ===================================//

    public Graph getInitialGraph() {
        return initialGraph;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public double getExtraEdgeThreshold() {
        return extraEdgeThreshold;
    }

    public void setExtraEdgeThreshold(double extraEdgeThreshold) {
        this.extraEdgeThreshold = extraEdgeThreshold;
    }

    public boolean isUseFasAdjacencies() {
        return useFasAdjacencies;
    }

    public void setUseFasAdjacencies(boolean useFasAdjacencies) {
        this.useFasAdjacencies = useFasAdjacencies;
    }

    public boolean isUseSkewAdjacencies() {
        return useSkewAdjacencies;
    }

    public void setUseSkewAdjacencies(boolean useSkewAdjacencies) {
        this.useSkewAdjacencies = useSkewAdjacencies;
    }

    public double getDelta() {
        return delta;
    }

    public void setDelta(double delta) {
        this.delta = delta;
    }
}
