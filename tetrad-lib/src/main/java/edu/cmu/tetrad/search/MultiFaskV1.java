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
    private Graph externalGraph;

    // Elapsed time of the search, in milliseconds.


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

    // Threshold for reversing casual judgments for negative coefficients.
    private double delta = -0.2;

    private List<DataSet> dataSets;

    private final double[][][] data;

    public MultiFaskV1(List<DataSet> dataSets, SemBicScoreMultiFas score) {

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
        ArrayList<DataSet> standardSets = new ArrayList<>();

        for (DataSet set : this.dataSets) {
            dataSet = DataUtils.standardizeData(set);
            standardSets.add(dataSet);
        }

        this.dataSets = standardSets;
        List<Node> variables = this.dataSets.get(0).getVariables();
        Graph G0;

        IndependenceTest test = new IndTestScore(this.score);
        System.out.println("FAS");

        Fas fas = new Fas(test);
        fas.setStable(true);
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        fas.setKnowledge(this.knowledge);
        G0 = fas.search();

        SearchGraphUtils.pcOrientbk(this.knowledge, G0, G0.getNodes());
        G0 = GraphUtils.replaceNodes(G0, this.dataSets.get(0).getVariables());

        System.out.println("Orientation");

        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {

                Node X = variables.get(i);
                Node Y = variables.get(j);

                double[] x;
                double[] y;

                double[][] _x = new double[this.dataSets.size()][];
                double[][] _y = new double[this.dataSets.size()][];

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

                // A threshold for including extra adjacencies due to skewness.
                if ((G0.isAdjacentTo(X, Y)) || ((abs(c1 - c2) / this.dataSets.size()) > 0.3)) {
                    if (knowledgeOrients(X, Y)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (knowledgeOrients(Y, X)) {
                        graph.addDirectedEdge(Y, X);
                    } else if (bidirected(_x, _y, G0, X, Y)) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);
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
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * @param alpha Alpha for orienting 2-cycles. Needs to be on the low side usually. Default 1e-6.
     */
    public void setAlpha(double alpha) {
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
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public int getDepth() {
        return this.depth;
    }

    //======================================== PRIVATE METHODS ===================================//

    private boolean bidirected(double[][] x, double[][] y, Graph G0, Node X, Node Y) {
        Set<Node> adjSet = new HashSet<>(G0.getAdjacentNodes(X));
        adjSet.addAll(G0.getAdjacentNodes(Y));
        List<Node> adj = new ArrayList<>(adjSet);
        adj.remove(X);
        adj.remove(Y);

        int trueCounter = 0;
        int falseCounter = 0;

        for (int i = 0; i < this.dataSets.size(); i++) {

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), this.depth);
            int[] choice;

            boolean possibleTwoCycle = false;

            while ((choice = gen.next()) != null) {

                List<Node> _adj = GraphUtils.asList(choice, adj);
                double[][][] _Z = new double[this.dataSets.size()][_adj.size()][];

                if (_adj.size() > 0) {
                    for (int f = 0; f < _adj.size(); f++) {

                        Node _z = _adj.get(f);

                        for (int g = 0; g < this.dataSets.size(); g++) {

                            int column = this.dataSets.get(0).getColumn(_z);
                            _Z[g][f] = this.data[g][column];
                        }
                    }


                    double pc = partialCorrelation(x[i], y[i], _Z[i], x[i], Double.NEGATIVE_INFINITY);
                    double pc1 = partialCorrelation(x[i], y[i], _Z[i], x[i], 0);
                    double pc2 = partialCorrelation(x[i], y[i], _Z[i], y[i], 0);

                    int nc = StatUtils.getRows(x[i], Double.NEGATIVE_INFINITY, +1).size();
                    int nc1 = StatUtils.getRows(x[i], 0, +1).size();
                    int nc2 = StatUtils.getRows(y[i], 0, +1).size();

                    double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
                    double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
                    double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

                    double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
                    double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

                    boolean rejected1 = abs(zv1) > this.cutoff;
                    boolean rejected2 = abs(zv2) > this.cutoff;

                    if (zv1 < 0 && zv2 > 0 && rejected1) {
                        possibleTwoCycle = true;
                    } else if (zv1 > 0 && zv2 < 0 && rejected2) {
                        possibleTwoCycle = true;
                    } else if (rejected1 && rejected2) {
                        possibleTwoCycle = true;
                    }

                } else {
                    double[][] _emptyZ = new double[0][0];
                    double pc = partialCorrelation(x[i], y[i], _emptyZ, x[i], Double.NEGATIVE_INFINITY);
                    double pc1 = partialCorrelation(x[i], y[i], _emptyZ, x[i], 0);
                    double pc2 = partialCorrelation(x[i], y[i], _emptyZ, y[i], 0);

                    int nc = StatUtils.getRows(x[i], Double.NEGATIVE_INFINITY, +1).size();
                    int nc1 = StatUtils.getRows(x[i], 0, +1).size();
                    int nc2 = StatUtils.getRows(y[i], 0, +1).size();

                    double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
                    double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
                    double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

                    double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
                    double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

                    boolean rejected1 = abs(zv1) > this.cutoff;
                    boolean rejected2 = abs(zv2) > this.cutoff;

                    if (zv1 < 0 && zv2 > 0 && rejected1) {
                        possibleTwoCycle = true;
                    } else if (zv1 > 0 && zv2 < 0 && rejected2) {
                        possibleTwoCycle = true;
                    } else if (rejected1 && rejected2) {
                        possibleTwoCycle = true;
                    }

                }
                if (!possibleTwoCycle) {
                    break;
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

    private boolean leftright(double[][] x, double[][] y) {

        double lrSum = 0;

        for (int i = 0; i < this.dataSets.size(); i++) {
            double left = MultiFaskV1.cu(x[i], y[i], x[i]) / (sqrt(MultiFaskV1.cu(x[i], x[i], x[i]) * MultiFaskV1.cu(y[i], y[i], x[i])));
            double right = MultiFaskV1.cu(x[i], y[i], y[i]) / (sqrt(MultiFaskV1.cu(x[i], x[i], y[i]) * MultiFaskV1.cu(y[i], y[i], y[i])));
            double lr = left - right;

            double r = StatUtils.correlation(x[i], y[i]);
            double sx = StatUtils.skewness(x[i]);
            double sy = StatUtils.skewness(y[i]);

            r *= signum(sx) * signum(sy);
            lr *= signum(r);
            if (r < getDelta()) lr *= -1;

            lrSum += lr;
        }

        return lrSum > 0;
    }

    private static double cu(double[] x, double[] y, double[] condition) {
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

    private double partialCorrelation(double[] x, double[] y, double[][] z, double[] condition, double threshold) throws SingularMatrixException {
        double[][] cv = StatUtils.covMatrix(x, y, z, condition, threshold, 1);
        Matrix m = new Matrix(cv).transpose();
        return StatUtils.partialCorrelation(m);
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    private void setCutoff(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range: " + alpha);
        }

        this.cutoff = StatUtils.getZForAlpha(alpha);
    }

    private boolean knowledgeOrients(Node left, Node right) {
        return this.knowledge.isForbidden(right.getName(), left.getName()) || this.knowledge.isRequired(left.getName(), right.getName());
    }

    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    public double getDelta() {
        return this.delta;
    }

    public void setDelta(double delta) {
        this.delta = delta;
    }
}
