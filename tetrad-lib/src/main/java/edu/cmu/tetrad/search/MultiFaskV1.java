package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.Math.*;

/**
 * Created by user on 3/29/18.
 */
public class MultiFaskV1 {

    private final SemBicScoreMultiFas score;

    // An initial graph to orient, skipping the adjacency step.
    private Graph externalGraph = null;

    // Elapsed time of the search, in milliseconds.
    private final long elapsed = 0;


    // For the Fast Adjacency Search.
    private final int depth = -1;

    // For the SEM BIC score, for the Fast Adjacency Search.
    private double penaltyDiscount = 1;

    // Alpha for orienting 2-cycles. Usually needs to be low.
    private double alpha = 1e-6;

    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();

    // Cutoff for T tests for 2-cycle tests.
    private double cutoff;

    // A threshold for including extra adjacencies due to skewness.
    private double extraEdgeThreshold = 0.3;

    // True if FAS adjacencies should be included in the output.
    private boolean useFasAdjacencies = true;

    // True if skew adjacencies should be included in the output.
    private boolean useSkewAdjacencies = true;

    // Threshold for reversing casual judgments for negative coefficients.
    private double delta = -0.2;

    private List<DataSet> dataSets = null;

    private final double[][][] data;

    public MultiFaskV1(final List<DataSet> dataSets, final SemBicScoreMultiFas score) {

        this.dataSets = dataSets;
        this.score = score;

        this.data = new double[dataSets.size()][][];

        for (int i = 0; i < dataSets.size(); i++) {
            this.data[i] = dataSets.get(i).getDoubleData().transpose().toArray();
        }

    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search() {

        setCutoff(this.alpha);

        DataSet dataSet;

        // System.out.println(dataSets.size());

        final ArrayList<DataSet> standardSets = new ArrayList<>();

        for (int i = 0; i < this.dataSets.size(); i++) {
            dataSet = DataUtils.standardizeData(this.dataSets.get(i));
            //System.out.println("Standardized " + Integer.toString(i));
            standardSets.add(dataSet);
        }

        this.dataSets = standardSets;

        final List<Node> variables = this.dataSets.get(0).getVariables();

        Graph G0;

        final IndependenceTest test = new IndTestScore(this.score);
        System.out.println("FAS");

        final Fas fas = new Fas(test);
        fas.setStable(true);
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        fas.setKnowledge(this.knowledge);
        G0 = fas.search();

        SearchGraphUtils.pcOrientbk(this.knowledge, G0, G0.getNodes());
        G0 = GraphUtils.replaceNodes(G0, this.dataSets.get(0).getVariables());

        System.out.println("Orientation");

        final Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {

                final Node X = variables.get(i);
                final Node Y = variables.get(j);

                double[] x;
                double[] y;

                final double[][] _x = new double[this.dataSets.size()][];
                final double[][] _y = new double[this.dataSets.size()][];

                double c1 = 0;
                double c2 = 0;

                for (int k = 0; k < this.dataSets.size(); k++) {
                    x = this.data[k][i];
                    y = this.data[k][j];

                    _x[k] = x;
                    _y[k] = y;

                    c1 += StatUtils.cov(x, y, x, 0, +1)[1];
                    c2 += StatUtils.cov(x, y, y, 0, +1)[1];
                }

                if ((isUseFasAdjacencies() && G0.isAdjacentTo(X, Y)) || (isUseSkewAdjacencies() && (Math.abs(c1 - c2) / this.dataSets.size()) > getExtraEdgeThreshold())) {
                    // if ((isUseFasAdjacencies() && G0.isAdjacentTo(X, Y)) || (isUseSkewAdjacencies() && (Math.abs(c1 - c2) > getSkewEdgeAlpha()))) {

                    if (knowledgeOrients(X, Y)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (knowledgeOrients(Y, X)) {
                        graph.addDirectedEdge(Y, X);
                    } else if (bidirected(_x, _y, G0, X, Y)) {
                        final Edge edge1 = Edges.directedEdge(X, Y);
                        final Edge edge2 = Edges.directedEdge(Y, X);
                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    } else {
                        if (leftright(_x, _y)) {
                            graph.addDirectedEdge(X, Y);
                        } else {
                            graph.addDirectedEdge(Y, X);
                        }
                    }
                }
            }
        }

        System.out.println();
        System.out.println("Done");

        // long stop = System.currentTimeMillis();
        // this.elapsed = stop - start;

        return graph;
    }

    /**
     * @return Returns the penalty discount used for the adjacency search. The default is 1,
     * though a higher value is recommended, say, 2, 3, or 4.
     */
    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    /**
     * @param penaltyDiscount Sets the penalty discount used for the adjacency search.
     *                        The default is 1, though a higher value is recommended, say,
     *                        2, 3, or 4.
     */
    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * @param alpha Alpha for orienting 2-cycles. Needs to be on the low side usually. Default 1e-6.
     */
    public void setAlpha(final double alpha) {
        this.alpha = alpha;
    }

    /**
     * @return the current knowledge.
     */
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * @param knowledge Knowledge of forbidden and required edges.
     */
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public int getDepth() {
        return this.depth;
    }

    //======================================== PRIVATE METHODS ===================================//

    private boolean bidirected(final double[][] x, final double[][] y, final Graph G0, final Node X, final Node Y) {
        final Set<Node> adjSet = new HashSet<Node>(G0.getAdjacentNodes(X));
        adjSet.addAll(G0.getAdjacentNodes(Y));
        final List<Node> adj = new ArrayList<>(adjSet);
        // System.out.println(adj.size());
        adj.remove(X);
        adj.remove(Y);

        // System.out.println(adj.size());

        int trueCounter = 0;
        int falseCounter = 0;

        for (int i = 0; i < this.dataSets.size(); i++) {

            final DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), Math.min(this.depth, adj.size()));
            int[] choice;
            DataSet dataSet;

            boolean possibleTwoCycle = false;

            while ((choice = gen.next()) != null) {

                final List<Node> _adj = GraphUtils.asList(choice, adj);
                final double[][][] _Z = new double[this.dataSets.size()][_adj.size()][];


                // System.out.println(_adj.size());

                if (_adj.size() > 0) {


                    for (int f = 0; f < _adj.size(); f++) {

                        final Node _z = _adj.get(f);

                        for (int g = 0; g < this.dataSets.size(); g++) {

                            final int column = this.dataSets.get(0).getColumn(_z);
                            _Z[g][f] = this.data[g][column];
                        }
                    }


                    final double pc = partialCorrelation(x[i], y[i], _Z[i], x[i], Double.NEGATIVE_INFINITY, +1);
                    final double pc1 = partialCorrelation(x[i], y[i], _Z[i], x[i], 0, +1);
                    final double pc2 = partialCorrelation(x[i], y[i], _Z[i], y[i], 0, +1);

                    final int nc = StatUtils.getRows(x[i], Double.NEGATIVE_INFINITY, +1).size();
                    final int nc1 = StatUtils.getRows(x[i], 0, +1).size();
                    final int nc2 = StatUtils.getRows(y[i], 0, +1).size();

                    final double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
                    final double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
                    final double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

                    final double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
                    final double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

                    final boolean rejected1 = abs(zv1) > this.cutoff;
                    final boolean rejected2 = abs(zv2) > this.cutoff;

                    if (zv1 < 0 && zv2 > 0 && rejected1) {
                        possibleTwoCycle = true;
                    } else if (zv1 > 0 && zv2 < 0 && rejected2) {
                        possibleTwoCycle = true;
                    } else if (rejected1 && rejected2) {
                        possibleTwoCycle = true;
                    }

                    if (!possibleTwoCycle) {
                        break;
                    }

                } else {
                    final double[][] _emptyZ = new double[0][0];
                    final double pc = partialCorrelation(x[i], y[i], _emptyZ, x[i], Double.NEGATIVE_INFINITY, +1);
                    final double pc1 = partialCorrelation(x[i], y[i], _emptyZ, x[i], 0, +1);
                    final double pc2 = partialCorrelation(x[i], y[i], _emptyZ, y[i], 0, +1);

                    final int nc = StatUtils.getRows(x[i], Double.NEGATIVE_INFINITY, +1).size();
                    final int nc1 = StatUtils.getRows(x[i], 0, +1).size();
                    final int nc2 = StatUtils.getRows(y[i], 0, +1).size();

                    final double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
                    final double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
                    final double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

                    final double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
                    final double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

                    final boolean rejected1 = abs(zv1) > this.cutoff;
                    final boolean rejected2 = abs(zv2) > this.cutoff;

                    if (zv1 < 0 && zv2 > 0 && rejected1) {
                        possibleTwoCycle = true;
                    } else if (zv1 > 0 && zv2 < 0 && rejected2) {
                        possibleTwoCycle = true;
                    } else if (rejected1 && rejected2) {
                        possibleTwoCycle = true;
                    }

                    if (!possibleTwoCycle) {
                        break;
                    }

                }

            }

            if (possibleTwoCycle) {
                trueCounter++;
            } else {
                falseCounter++;
            }
        }

        return trueCounter > falseCounter;
    }

    private boolean leftright(final double[][] x, final double[][] y) {

        double lrSum = 0;

        for (int i = 0; i < this.dataSets.size(); i++) {
            final double left = cu(x[i], y[i], x[i]) / (sqrt(cu(x[i], x[i], x[i]) * cu(y[i], y[i], x[i])));
            final double right = cu(x[i], y[i], y[i]) / (sqrt(cu(x[i], x[i], y[i]) * cu(y[i], y[i], y[i])));
            double lr = left - right;

            double r = StatUtils.correlation(x[i], y[i]);
            final double sx = StatUtils.skewness(x[i]);
            final double sy = StatUtils.skewness(y[i]);

            r *= signum(sx) * signum(sy);
            lr *= signum(r);
            if (r < getDelta()) lr *= -1;

            lrSum += lr;
        }

        return lrSum > 0;
    }

    private static double cu(final double[] x, final double[] y, final double[] condition) {
        double exy = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        }

        return exy / n;
    }

    private double partialCorrelation(final double[] x, final double[] y, final double[][] z, final double[] condition, final double threshold, final double direction) throws SingularMatrixException {
        final double[][] cv = StatUtils.covMatrix(x, y, z, condition, threshold, direction);
        final Matrix m = new Matrix(cv).transpose();
        return StatUtils.partialCorrelation(m);
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    private void setCutoff(final double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range: " + alpha);
        }

        this.cutoff = StatUtils.getZForAlpha(alpha);
    }

    private boolean knowledgeOrients(final Node left, final Node right) {
        return this.knowledge.isForbidden(right.getName(), left.getName()) || this.knowledge.isRequired(left.getName(), right.getName());
    }

    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    public void setExternalGraph(final Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    public double getExtraEdgeThreshold() {
        return this.extraEdgeThreshold;
    }

    public void setExtraEdgeThreshold(final double extraEdgeThreshold) {
        this.extraEdgeThreshold = extraEdgeThreshold;
    }

    public boolean isUseFasAdjacencies() {
        return this.useFasAdjacencies;
    }

    public void setUseFasAdjacencies(final boolean useFasAdjacencies) {
        this.useFasAdjacencies = useFasAdjacencies;
    }

    public boolean isUseSkewAdjacencies() {
        return this.useSkewAdjacencies;
    }

    public void setUseSkewAdjacencies(final boolean useSkewAdjacencies) {
        this.useSkewAdjacencies = useSkewAdjacencies;
    }

    public double getDelta() {
        return this.delta;
    }

    public void setDelta(final double delta) {
        this.delta = delta;
    }
}
