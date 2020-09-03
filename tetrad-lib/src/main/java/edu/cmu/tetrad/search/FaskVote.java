package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;
import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Math.signum;
import static java.lang.Math.sqrt;

/**
 * @author Joseph Ramsey
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
    private double delta = -0.1;

    private final List<DataSet> dataSets;

    public FaskVote(List<DataSet> dataSets) {
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search(Parameters parameters) {
        List<DataModel> _dataSets = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            _dataSets.add(DataUtils.standardizeData(dataSet));
        }

        ImagesSemBic imagesSemBic = new ImagesSemBic();
        Graph GPrime = imagesSemBic.search(_dataSets, parameters);

        List<Node> nodes = dataSets.get(0).getVariables();
        Graph G = new EdgeListGraph(nodes);

        double[][][] D = new double[dataSets.size()][][];

        for (int k = 0; k < _dataSets.size(); k++) {
            D[k] = ((DataSet) _dataSets.get(k)).getDoubleData().transpose().toArray();
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                double sum = 0;
                int count = 0;

                Node X = nodes.get(i);
                Node Y = nodes.get(j);

                for (int k = 0; k < dataSets.size(); k++) {
                    if (!GPrime.isAdjacentTo(X, Y)) continue;

                    double[] x = D[k][i];
                    double[] y = D[k][j];

                    double lr = faskLeftRightV2(x, y, parameters.getBoolean(FASK_NONEMPIRICAL));

                    if (lr < 0) {
                        sum += 1;
                    }

                    count++;
                }

                double mean = sum / count;

                System.out.println(X + " " + Y + " " + mean);

                if (mean == 0.5) {
                    G.addUndirectedEdge(Y, X);
                } else if (mean > 0.5) {
                    G.addDirectedEdge(Y, X);
                }
            }
        }

        return G;
    }

    private double faskLeftRightV2(double[] x, double[] y, boolean empirical) {
        double sx = skewness(x);
        double sy = skewness(y);
        double r = correlation(x, y);
        double lr = correxp(x, y, x) - correxp(x, y, y);

        if (empirical) {
            lr *= signum(sx) * signum(sy);
        }

        if (r < delta) {
            lr *= -1;
        }

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
