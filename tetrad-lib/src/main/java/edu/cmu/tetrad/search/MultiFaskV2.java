package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.util.Params.*;
import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Math.*;
import static java.lang.Math.abs;

/**
 * @author Madelyn Glymour
 * @author Joseph Ramsey 9/5/2020
 */
public class MultiFaskV2 {

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

    // Alpha for checking 2-cycles.
    private double twoCycleScreeningThreshold = 0.01;

    // Alpha for checking 2-cycles.
    private double twoCycleTestingAlpha = 0.01;

    // Depth for combinatorial steps.
    private int depth = -1;

    public MultiFaskV2(List<DataSet> dataSets) {
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search(Parameters parameters) {
        List<DataModel> _dataSets = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            _dataSets.add(DataUtils.standardizeData(dataSet));
        }

        ImagesSemBic imagesSemBic = new ImagesSemBic();
        Graph G0 = imagesSemBic.search(_dataSets, parameters);

        List<Node> V = dataSets.get(0).getVariables();
        Graph G = new EdgeListGraph(V);

        double[][][] D = new double[dataSets.size()][][];

        for (int k = 0; k < _dataSets.size(); k++) {
            D[k] = ((DataSet) _dataSets.get(k)).getDoubleData().transpose().toArray();
        }

        this.delta = parameters.getDouble(FASK_DELTA);

        boolean twoCycles = twoCycleScreeningThreshold > 0 || twoCycleTestingAlpha > 0;

        for (Edge edge : G0.getEdges()) {
            double sum1 = 0;
            double sum2 = 0;

            Node X = edge.getNode1();
            Node Y = edge.getNode2();

            int i = V.indexOf(X);
            int j = V.indexOf(Y);

            for (int k = 0; k < dataSets.size(); k++) {
                double[] x = D[k][i];
                double[] y = D[k][j];

                double cutoff = StatUtils.getZForAlpha(twoCycleTestingAlpha);
                double lr = faskLeftRightV2(x, y, !parameters.getBoolean(FASK_NONEMPIRICAL));

                if (twoCycles
                        && ((twoCycleScreeningThreshold == 0 || abs(lr) < twoCycleScreeningThreshold)
                        && (twoCycleTestingAlpha == 0 || twoCycleTest(i, j, D[k], G0, V, cutoff, depth == -1 ? 1000 : depth)))) {
                    sum1++;
                    sum2++;
                } else {
                    if (lr > 0) {
                        sum1++;
                    } else if (lr < 0) {
                        sum2++;
                    }
                }
            }

            double mean1 = sum1 / dataSets.size();
            double mean2 = sum2 / dataSets.size()       ;

            System.out.println(X + " " + Y + " " + mean1 + " " + mean2);

            if (mean1 == 0.5 && mean2 == 0.5) {
                G.addUndirectedEdge(X, Y);
            } else {
                if (mean1 > 0.5) {
                    G.addDirectedEdge(X, Y);
                }

                if (mean2 > 0.5) {
                    G.addDirectedEdge(Y, X);
                }
            }
        }

        return G;
    }

    private boolean twoCycleTest(int i, int j, double[][] D, Graph G0, List<Node> V,
                                 double twoCycleTestingCutoff, int depth) {
        Node X = V.get(i);
        Node Y = V.get(j);

        double[] x = D[i];
        double[] y = D[j];

        Set<Node> adjSet = new HashSet<>(G0.getAdjacentNodes(X));
        adjSet.addAll(G0.getAdjacentNodes(Y));
        List<Node> adj = new ArrayList<>(adjSet);
        adj.remove(X);
        adj.remove(Y);

        DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), Math.min(depth, adj.size()));
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> _adj = GraphUtils.asList(choice, adj);
            double[][] _Z = new double[_adj.size()][];

            for (int f = 0; f < _adj.size(); f++) {
                Node _z = _adj.get(f);
                int column = V.indexOf(_z);
                _Z[f] = D[column];
            }

            double pc, pc1, pc2;

            try {
                pc = partialCorrelation(x, y, _Z, x, Double.NEGATIVE_INFINITY);
                pc1 = partialCorrelation(x, y, _Z, x, 0);
                pc2 = partialCorrelation(x, y, _Z, y, 0);
            } catch (SingularMatrixException e) {
                System.out.println("Singularity X = " + X + " Y = " + Y + " adj = " + adj);
                TetradLogger.getInstance().log("info", "Singularity X = " + X + " Y = " + Y + " adj = " + adj);
                continue;
            }

            int nc = StatUtils.getRows(x, x, 0, Double.NEGATIVE_INFINITY).size();
            int nc1 = StatUtils.getRows(x, x, 0, +1).size();
            int nc2 = StatUtils.getRows(y, y, 0, +1).size();

            double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
            double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
            double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

            double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
            double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

            boolean rejected1 = abs(zv1) > twoCycleTestingCutoff;
            boolean rejected2 = abs(zv2) > twoCycleTestingCutoff;

            boolean possibleTwoCycle = false;

            if (zv1 < 0 && zv2 > 0 && rejected1) {
                possibleTwoCycle = true;
            } else if (zv1 > 0 && zv2 < 0 && rejected2) {
                possibleTwoCycle = true;
            } else if (rejected1 && rejected2) {
                possibleTwoCycle = true;
            }

            if (!possibleTwoCycle) {
                return false;
            }
        }

        return true;
    }

    private double partialCorrelation(double[] x, double[] y, double[][] z, double[] condition, double threshold) throws SingularMatrixException {
        double[][] cv = StatUtils.covMatrix(x, y, z, condition, threshold, 1);
        TetradMatrix m = new TetradMatrix(cv).transpose();
        return StatUtils.partialCorrelation(m);
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

    public void setTwoCycleTestingAlpha(double twoCycleTestingAlpha) {
        this.twoCycleTestingAlpha = twoCycleTestingAlpha;
    }

    public double getTwoCycleScreeningThreshold() {
        return twoCycleScreeningThreshold;
    }

    public void setTwoCycleScreeningThreshold(double twoCycleScreeningThreshold) {
        this.twoCycleScreeningThreshold = twoCycleScreeningThreshold;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
