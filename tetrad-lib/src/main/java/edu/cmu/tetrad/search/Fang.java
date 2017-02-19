///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.math3.distribution.TDistribution;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Math.*;

/**
 * Fast adjacency search followed by robust skew orientation. Checks are done for adding
 * two-cycles. The two-cycle checks do not require non-Gaussianity. The robust skew
 * orientation of edges left or right does.
 *
 * @author Joseph Ramsey
 */
public final class Fang implements GraphSearch {

    // Elapsed time of the search, in milliseconds.
    private long elapsed = 0;

    // The data sets being analyzed. They must all have the same variables and the same
    // number of records.
    private List<DataSet> dataSets = null;

    // nonGaussian[i] is true iff the i'th variable is judged non-Gaussian by the
    // Anderson-Darling test.
    private boolean[] nonGaussian;

    // For the Fast Adjacency Search.
    private int depth = -1;

    // For the SEM BIC score, for the Fast Adjacency Search.
    private double penaltyDiscount = 1;

    // For the T tests of equality and the Anderson-Darling tests.
    private double alpha = 0.05;

    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();

    // The threshold for adding in extra adjacencies for control two-cycles.
    private double extraAdjacencyThreshold = 10;

    // The alpha for testing non-Gaussianity (Anderson Darling test).
    private double ngAlpha = 0.05;

    /**
     * @param dataSets These datasets must all have the same variables, in the same order.
     */
    public Fang(List<DataSet> dataSets) {
        this.dataSets = dataSets;
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

        List<DataSet> _dataSets = new ArrayList<>();
        for (DataSet dataSet : dataSets) _dataSets.add(DataUtils.standardizeData(dataSet));

        DataSet dataSet = DataUtils.concatenate(_dataSets);

        SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
        score.setPenaltyDiscount(penaltyDiscount);
        IndependenceTest test = new IndTestScore(score, dataSet);

        List<Node> variables = dataSet.getVariables();

        double[][] colData = dataSet.getDoubleData().transpose().toArray();

        nonGaussian = new boolean[colData.length];

        for (int i = 0; i < colData.length; i++) {
            final double p = new AndersonDarlingTest(colData[i]).getP();

            if (Double.isInfinite(p)) {
                nonGaussian[i] = true;
            } else {
                nonGaussian[i] = p < ngAlpha;
            }
        }

        final int n = dataSet.getNumRows();
        double T = new TDistribution(n - 1).inverseCumulativeProbability(1.0 - alpha / 2.0);

        FasStableConcurrent fas = new FasStableConcurrent(null, test);
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        fas.setKnowledge(knowledge);
        Graph graph0 = fas.search();
        SearchGraphUtils.pcOrientbk(knowledge, graph0, graph0.getNodes());

        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                Node X = variables.get(i);
                Node Y = variables.get(j);

//                if (graph0.getEdge(X, Y) != null) {
//                    if (shouldGo(X, Y)) {
//                        graph.addDirectedEdge(Y, X);
//                        continue;
//                    } else if (shouldGo(X, Y)) {
//                        graph.addDirectedEdge(X, Y);
//                        continue;
//                    }
//                }

                final double[] xData = colData[i];
                final double[] yData = colData[j];

//                double startAngle = -90;
//                double width = 90;
//
//                startAngle *= PI / 180.0;
//                width *= PI / 180.0;
//
//                if (startAngle < 0) startAngle += 2 * PI;

                double[] p1 = new double[n];
                double[] p2 = new double[n];
                double[] p3 = new double[n];
                double[] p4 = new double[n];
                double[] xy = new double[n];
                double[] h = new double[n];

                double[] p5 = new double[n];
                double[] p6 = new double[n];
//
                for (int k = 0; k < n; k++) {
                    double x = xData[k];
                    double y = yData[k];

                    p1[k] = h(x) * y;
                    p2[k] = x * h(y);
                    p3[k] = -h(-x) * y;
                    p4[k] = x * -h(-y);

                    xy[k] = x * y;
                    h[k] = h(x) * y - x * h(y);

//                    double a = getAngle(x, y, startAngle);
//
//                    if (a > startAngle && a < width + startAngle) {
//                        p5[k] = x * y;
//                    } else if (a > PI + startAngle && a < PI + width + startAngle) {
//                        p6[k] = x * y;
//                    }

                    if (x > 0 && y < 0) {
                        p5[k] = x * y;
                    } else if (x < 0 && y > 0) {
                        p6[k] = x * y;
                    }
                }

                p1 = nonzero(p1);
                p2 = nonzero(p2);
                p3 = nonzero(p3);
                p4 = nonzero(p4);
                h = nonzero(h);

                p5 = nonzero(p5);
                p6 = nonzero(p6);

                final double signumcovxy = signum(covariance(yData, xData));
                double R = sum(h) * signumcovxy;

                double cov = sum(xy);
                double cov1 = sum(p1);
                double cov2 = sum(p2);
                double cov3 = sum(p3);
                double cov4 = sum(p4);

                double t1 = mean(p1, p1.length) / (sd(p1, p1.length) / sqrt(p1.length));
                double t2 = mean(p2, p2.length) / (sd(p2, p2.length) / sqrt(p2.length));
                double t3 = mean(p3, p3.length) / (sd(p3, p3.length) / sqrt(p3.length));
                double t4 = mean(p4, p4.length) / (sd(p4, p4.length) / sqrt(p4.length));
                double th = mean(h, h.length) / (sd(h, h.length) / sqrt(h.length));
                double tp5 = mean(p5, p5.length) / (sd(p5, p5.length) / sqrt(p5.length));
                double tp6 = mean(p6, p6.length) / (sd(p6, p6.length) / sqrt(p6.length));

                boolean ng = isNonGaussian(i) && isNonGaussian(j);

                int numZero = 0;

                if (abs(t1) < T) numZero++;
                if (abs(t2) < T) numZero++;
                if (abs(t3) < T) numZero++;
                if (abs(t4) < T) numZero++;

                int numNonZero = 0;

                if (abs(t1) > T) numNonZero++;
                if (abs(t2) > T) numNonZero++;
                if (abs(t3) > T) numNonZero++;
                if (abs(t4) > T) numNonZero++;

                if (graph0.isAdjacentTo(X, Y)) {
                    if (shouldGo(X, Y)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (shouldGo(Y, X)) {
                        graph.addDirectedEdge(Y, X);
                    } else if (ng && abs(th) <= T) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);

                        edge1.setLineColor(Color.GREEN);
                        edge2.setLineColor(Color.GREEN);

                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    } else if (ng && R > 0) {
                        graph.addDirectedEdge(X, Y);
                    } else if (ng && R < 0) {
                        graph.addDirectedEdge(Y, X);
                    } else {
                        graph.addUndirectedEdge(X, Y);
                    }
                } else if (abs(th) > extraAdjacencyThreshold) {
                    if (ng && (signum(cov1) == -signum(cov)
                            || signum(cov2) == -signum(cov)
                            || signum(cov3) == -signum(cov)
                            || signum(cov4) == -signum(cov))) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);

                        edge1.setLineColor(Color.RED);
                        edge2.setLineColor(Color.RED);

                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    } else if (ng && numZero > 0 && numNonZero > 0) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);

                        edge1.setLineColor(Color.BLACK);
                        edge2.setLineColor(Color.BLACK);

                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    }
                }
            }
        }

        long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        System.out.println(graph);

        return graph;
    }

//    private double getAngle(double x, double y, double startAngle) {
//        double a = atan(y / x);
//        if (Double.isNaN(a)) {
//            if (y > 0) a = .5 * PI;
//            else a = -.5 * PI;
//        }
//        if (x < 0) a += PI;
//        if (a < startAngle) a += 2 * PI;
//        return a;
//    }

    private double[] nonzero(double[] q3) {
        int N = 0;

        for (double aQ3 : q3) {
            if (aQ3 != 0) N++;
        }

        double[] ret = new double[N];

        int t = 0;

        for (double aQ3 : q3) {
            if (aQ3 != 0) {
                ret[t++] = aQ3;
            }
        }

        return ret;
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
     * @return The alpha value used for T tests of equality and non-Gaussianity tests,
     * by default 0.05.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * @param alpha The alpha value used for T tests of equality and non-Gaussianity tests.
     *              The default is 0.05. The test used for non-Gaussianity is the Anderson-
     *              Darling test.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
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

//    /**
//     * The threshold for adding in extra adjacencies for two-cycles where the coefficients are of opposite sign.
//     *
//     * @return This threshold, default 10.
//     */
//    public double getExtraAdjacencyThreshold() {
//        return extraAdjacencyThreshold;
//    }

    /**
     * The threshold for adding in extra adjacencies for two-cycles where the coefficients are of opposite sign.
     *
     * @param extraAdjacencyThreshold This threshold, default 10.
     */
    public void setExtraAdjacencyThreshold(double extraAdjacencyThreshold) {
        this.extraAdjacencyThreshold = extraAdjacencyThreshold;
    }

//    /**
//     * The alpha for testing non-Gaussianity.
//     *
//     * @return This alpha, default 0.05.
//     */
//    public double getNgAlpha() {
//        return ngAlpha;
//    }

    /**
     * The alpha for testing non-Gaussianity.
     *
     * @param ngAlpha This alpha, default 0.05.
     */
    public void setNgAlpha(double ngAlpha) {
        this.ngAlpha = ngAlpha;
    }

    //======================================== PRIVATE METHODS ====================================//

    private boolean isNonGaussian(int i) {
        return nonGaussian[i];
    }

    private double g(double x) {
        return log(Math.cosh(Math.max(x, 0)));
    }

    private double h(double x) {
        return x < 0 ? 0 : x;
    }

    private static double sd(double array[], int N) {
        return Math.pow(ssx(array, N) / (N - 1), .5);
    }

    private static double ssx(double array[], int N) {
        int i;
        double difference;
        double meanValue = mean(array, N);
        double sum = 0.0;

        for (i = 0; i < N; i++) {
            difference = array[i] - meanValue;
            sum += difference * difference;
        }

        return sum;
    }

    private boolean shouldGo(Node x, Node y) {
        return knowledge.isForbidden(y.getName(), x.getName()) || knowledge.isRequired(x.getName(), y.getName());
    }

    /**
     * @param x Paired differences.
     */
    private double wilcoxonz(double[] x) {
        x = nonzero(x);
        long nr = x.length;
        double[] absx = new double[x.length];
        for (int i = 0; i < x.length; i++) absx[i] = abs(x[i]);
        double[] sortedabsx = Arrays.copyOf(absx, x.length);
        Arrays.sort(sortedabsx);
        double[] ranks = ranks(sortedabsx);

        double W = 0.0;

        for (int i = 0; i < nr; i++) {
            W += signum(x[i]) * ranks[i];
        }

        long i = nr * (nr + 1) * (2 * nr + 1);

        return getZ(W, i);
    }

    private double getZ(double w, long i) {
        return w / sqrt(i / 6.0);
    }

    private static double[] ranks(double[] x) {
        double[] ranks = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            double d = x[i];
            int count = 1;

            for (int k = 0; k < x.length; k++) {
                if (x[k] <= d) {
                    count++;
                }
            }

            ranks[i] = count;
        }

        return ranks;
    }

    private double regressionCoef(double[] xValues, double[] yValues) {
        List<Node> v = new ArrayList<>();
        v.add(new GraphNode("x"));
        v.add(new GraphNode("y"));

        TetradMatrix bothData = new TetradMatrix(xValues.length, 2);

        for (int i = 0; i < xValues.length; i++) {
            bothData.set(i, 0, xValues[i]);
            bothData.set(i, 1, yValues[i]);
        }

        Regression regression2 = new RegressionDataset(bothData, v);

        RegressionResult result;
        try {
            result = regression2.regress(v.get(0), v.get(1));
        } catch (Exception e) {
            return Double.NaN;
        }
        return result.getCoef()[1];
    }
}






