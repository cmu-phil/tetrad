///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (c) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.util.StatUtils.skewness;
import static java.lang.Math.*;

/**
 * Fast adjacency search followed by robust skew orientation. Checks are done for adding
 * two-cycles. The two-cycle checks do not require non-Gaussianity. The robust skew
 * orientation of edges left or right does.
 *
 * This one does the FAS search including mixed variable, then removes the mixed variable
 * and continues on with orientation.
 *
 * @author Joseph Ramsey
 */
public final class FaskMixed implements GraphSearch {

    // The score to be used for the FAS adjacency search.
    private final Score score;

    // An initial graph to orient, skipping the adjacency step.
    private Graph initialGraph = null;

    // Elapsed time of the search, in milliseconds.
    private long elapsed = 0;

    // The data sets being analyzed. They must all have the same variables and the same
    // number of records.
    private DataSet dataSet = null;

    // For the Fast Adjacency Search.
    private int depth = -1;

    // For the SEM BIC score, for the Fast Adjacency Search.
    private double penaltyDiscount = 1;

    // Alpha for orienting 2-cycles. Usually needs to be low.
    private double alpha = 1e-6;

    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();

    // Data as a double[][].
    private final double[][] data;

    // Cutoff for T tests for 2-cycle tests.
    private double cutoff;

    private boolean reverseOrientationsBySignOfCorrelation = true;
    private boolean reverseOrientationsBySkewnessOfVariables = true;

    private double extraEdgeThreshold = 0.3;
    private boolean useFasAdjacencies = true;
    private boolean useCorrDiffAdjacencies = true;

    /**
     * @param dataSet These datasets must all have the same variables, in the same order.
     */
    public FaskMixed(DataSet dataSet, Score score) {
        this.dataSet = dataSet;
        this.score = score;

        data = dataSet.getDoubleData().transpose().toArray();
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * Runs the search on the concatenated data, returning a graph, possibly cyclic, possibly with
     * two-cycles. Runs the fast adjacency search (FAS, Spirtes et al., 2000) follows by a modification
     * of the robust skew rule (Pairwise Likelihood Ratios for Estimation of Non-Gaussian Structural
     * Equation Models, Smith and Hyvarinen), together with some heuristics for orienting two-cycles.
     *
     * @return the graph. Some of the edges may be undirected (though it shouldn't be many in most cases)
     * and some of the adjacencies may be two-cycles.
     */
    public Graph search() {
        long start = System.currentTimeMillis();

        setCutoff(alpha);

        DataSet dataSet = DataUtils.standardizeData(this.dataSet);

        double[][] colData = dataSet.getDoubleData().transpose().toArray();
        Graph G0;

        if (getInitialGraph() != null) {
            Graph g1 = new EdgeListGraph(getInitialGraph().getNodes());

            for (Edge edge : getInitialGraph().getEdges()) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (!g1.isAdjacentTo(x, y)) g1.addUndirectedEdge(x, y);
            }

            g1 = GraphUtils.replaceNodes(g1, dataSet.getVariables());

            G0 = g1;
        } else {
//            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet, false));
//            score.setPenaltyDiscount(penaltyDiscount);
            IndependenceTest test = new IndTestScore(score, dataSet);

//            for (int j = 0; j < colData.length; j++) {
//                double[] x = colData[j];
//
//                double s = signum(StatUtils.skewness(x));
//
//                for (int i = 0; i < x.length; i++) {
//                    x[i] = s * x[i];
//                }
//
//                colData[j] = x;
//            }

            System.out.println("FAS");

            FasStable fas = new FasStable(test);
            fas.setDepth(getDepth());
            fas.setVerbose(false);
            fas.setKnowledge(knowledge);
            G0 = fas.search();
        }

        if (G0 != null) {
            DataSet discreteData = DataUtils.copyDiscreteVariables(dataSet);

            for (Node x : discreteData.getVariables()) {
                G0.removeNode(x);
            }
        }

        this.dataSet = DataUtils.copycContinuousVariables(this.dataSet);
        List<Node> variables = this.dataSet.getVariables();

        SearchGraphUtils.pcOrientbk(knowledge, G0, G0.getNodes());

        System.out.println("Orientation");

        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                Node X = variables.get(i);
                Node Y = variables.get(j);

                // Centered
                final double[] x = colData[i];
                final double[] y = colData[j];

                double c1 = StatUtils.cov(x, y, x, 0, +1)[1];
                double c2 = StatUtils.cov(x, y, y, 0, +1)[1];

                if ((isUseFasAdjacencies() && G0.isAdjacentTo(X, Y)) || (isUseCorrDiffAdjacencies() && Math.abs(c1 - c2) > getExtraEdgeThreshold())) {
                    if (knowledgeOrients(X, Y)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (knowledgeOrients(Y, X)) {
                        graph.addDirectedEdge(Y, X);
                    } else if (bidirected(x, y, G0, X, Y)) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);

//                        edge1.setLineColor(Color.GREEN);
//                        edge2.setLineColor(Color.GREEN);

                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    } else if (leftright(x, y)) {
                        graph.addDirectedEdge(X, Y);
                    } else {
                        graph.addDirectedEdge(Y, X);
                    }
                }
            }
        }

        System.out.println();
        System.out.println("Done");

        long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        return graph;
    }

    private boolean bidirected(double[] x, double[] y, Graph G0, Node X, Node Y) {

        Set<Node> adjSet = new HashSet<Node>(G0.getAdjacentNodes(X));
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
                int column = dataSet.getColumn(_z);
                _Z[f] = data[column];
            }

            double pc = partialCorrelation(x, y, _Z, x, Double.NEGATIVE_INFINITY, +1);
            double pc1 = partialCorrelation(x, y, _Z, x, 0, +1);
            double pc2 = partialCorrelation(x, y, _Z, y, 0, +1);

            int nc = StatUtils.getRows(x, x, Double.NEGATIVE_INFINITY, +1).size();
            int nc1 = StatUtils.getRows(x, x, 0, +1).size();
            int nc2 = StatUtils.getRows(y, y, 0, +1).size();

            double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
            double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
            double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

            double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
            double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

//            double p1 = 2 * (1.0 - new NormalDistribution(0, 1).cumulativeProbability(abs(zv1)));
//            double p2 = 2 * (1.0 - new NormalDistribution(0, 1).cumulativeProbability(abs(zv2)));

//            boolean rejected1 = p1 < twoCycleAlpha;
//            boolean rejected2 = p2 < twoCycleAlpha;

            boolean rejected1 = abs(zv1) > cutoff;
            boolean rejected2 = abs(zv2) > cutoff;

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

    private boolean leftright(double[] x, double[] y) {
        double left = cu(x, y, x) / (sqrt(cu(x, x, x) * cu(y, y, x)));
        double right = cu(x, y, y) / (sqrt(cu(x, x, y) * cu(y, y, y)));
        double lr = left - right;

        if (isReverseOrientationsBySignOfCorrelation()) {
            lr *= signum(StatUtils.correlation(x, y));
        }

        System.out.println(skewness(x) + " " + skewness(y));

        if (isReverseOrientationsBySkewnessOfVariables()) {
            double sd = sqrt(6.0 / dataSet.getNumRows());

            double sx = StatUtils.skewness(x);
            double sy = StatUtils.skewness(y);

            if (abs(sx) > 5 * sd && abs(sy) > 5 * sd) {
                lr *= signum(sx) * signum(sy);
            }
        }

        return lr > 0;
    }

    public static double cu(double[] x, double[] y, double[] condition) {
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

    private double partialCorrelation(double[] x, double[] y, double[][] z, double[] condition, double threshold, double direction) throws SingularMatrixException {
        double[][] cv = StatUtils.covMatrix(x, y, z, condition, threshold, direction);
        TetradMatrix m = new TetradMatrix(cv).transpose();
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

    /**
     * @return The depth of search for the Fast Adjacency Search (FAS).
     */

    public int getDepth() {
        return depth;
    }

    /**
     * @param depth The depth of search for the Fast Adjacency Search (S). The default is -1.
     *              unlimited. Making this too high may results in statistical errors.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * @return The elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return elapsed;
    }

    /**
     * @return Returns the penalty discount used for the adjacency search. The default is 1,
     * though a higher value is recommended, say, 2, 3, or 4.
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
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
        return knowledge;
    }

    /**
     * @param knowledge Knowledge of forbidden and required edges.
     */
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    //======================================== PRIVATE METHODS ====================================//

    private boolean knowledgeOrients(Node left, Node right) {
        return knowledge.isForbidden(right.getName(), left.getName()) || knowledge.isRequired(left.getName(), right.getName());
    }

    public Graph getInitialGraph() {
        return initialGraph;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public void setReverseOrientationsBySignOfCorrelation(boolean reverseOrientationsBySignOfCorrelation) {
        this.reverseOrientationsBySignOfCorrelation = reverseOrientationsBySignOfCorrelation;
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

    public boolean isUseCorrDiffAdjacencies() {
        return useCorrDiffAdjacencies;
    }

    public void setUseCorrDiffAdjacencies(boolean useCorrDiffAdjacencies) {
        this.useCorrDiffAdjacencies = useCorrDiffAdjacencies;
    }

    public boolean isReverseOrientationsBySkewnessOfVariables() {
        return reverseOrientationsBySkewnessOfVariables;
    }

    public void setReverseOrientationsBySkewnessOfVariables(boolean reverseOrientationsBySkewnessOfVariables) {
        this.reverseOrientationsBySkewnessOfVariables = reverseOrientationsBySkewnessOfVariables;
    }

    public boolean isReverseOrientationsBySignOfCorrelation() {
        return reverseOrientationsBySignOfCorrelation;
    }
}






