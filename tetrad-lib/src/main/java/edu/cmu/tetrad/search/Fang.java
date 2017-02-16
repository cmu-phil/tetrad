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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.TDistribution;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.StatUtils.mean;
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
        for (DataSet dataSet : dataSets) _dataSets.add(DataUtils.center(dataSet));

        DataSet dataSet = DataUtils.concatenate(_dataSets);

        SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
        score.setPenaltyDiscount(penaltyDiscount);
        IndependenceTest test = new IndTestScore(score, dataSet);

        List<Node> variables = dataSet.getVariables();

        double[][] colData = dataSet.getDoubleData().transpose().toArray();

        nonGaussian = new boolean[colData.length];

        for (int i = 0; i < colData.length; i++) {
            final double p = new AndersonDarlingTest(colData[i]).getP();
            nonGaussian[i] = p < alpha;
        }

        final int n = dataSet.getNumRows();
        double T = new TDistribution(n - 1).inverseCumulativeProbability(1.0 - alpha / 2.0);

        FasStableConcurrent fas = new FasStableConcurrent(null, test);
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        Graph graph0 = fas.search();
        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                Node X = variables.get(i);
                Node Y = variables.get(j);

                final double[] xData = colData[i];
                final double[] yData = colData[j];

                double[] p1 = new double[n];
                double[] p2 = new double[n];
                double[] p3 = new double[n];
                double[] p4 = new double[n];
                double[] xy = new double[n];
                double[] r = new double[n];

                for (int k = 0; k < n; k++) {
                    double x = xData[k];
                    double y = yData[k];

                    p1[k] = pos(x) * y;
                    p2[k] = x * pos(y);
                    p3[k] = -pos(-x) * y;
                    p4[k] = x * -pos(-y);

                    xy[k] = x * y;
                    r[k] = g(x) * y - x * g(y);
                }

                double cov = mean(xy, n);
                double cov1 = mean(p1, n);
                double cov2 = mean(p2, n);
                double cov3 = mean(p3, n);
                double cov4 = mean(p4, n);

                double t1 = (mean(p1, n) - 0.0) / (sd(r, n) / sqrt(n));
                double t2 = (mean(p2, n) - 0.0) / (sd(r, n) / sqrt(n));
                double t3 = (mean(p3, n) - 0.0) / (sd(r, n) / sqrt(n));
                double t4 = (mean(p4, n) - 0.0) / (sd(r, n) / sqrt(n));
                double tr = (mean(r, n) - 0.0) / (sd(r, n) / sqrt(n));

                boolean ng = isNonGaussian(i) || isNonGaussian(j);

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

                if (abs(tr) > 10 || graph0.isAdjacentTo(variables.get(i), variables.get(j))) {
                    if (signum(cov1) == -signum(cov)
                            || signum(cov2) == -signum(cov)
                            || signum(cov3) == -signum(cov)
                            || signum(cov4) == -signum(cov)) {
                        graph.addDirectedEdge(X, Y);
                        graph.addDirectedEdge(Y, X);
                    } else if (numZero > 0 && numNonZero > 0) {
                        graph.addDirectedEdge(X, Y);
                        graph.addDirectedEdge(Y, X);
                    } else if (ng && abs(tr) <= T) {
                        graph.addDirectedEdge(X, Y);
                        graph.addDirectedEdge(Y, X);
                    } else if (ng && tr > T) {
                        graph.addDirectedEdge(X, Y);
                    } else if (ng && tr < -T) {
                        graph.addDirectedEdge(Y, X);
                    } else {
                        graph.addUndirectedEdge(X, Y);
                    }
                }
            }
        }

        long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        System.out.println(graph);

        return graph;
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

    //======================================== PRIVATE METHODS ====================================//

    private boolean isNonGaussian(int i) {
        return nonGaussian[i];
    }

    private double g(double x) {
        return log(Math.cosh(Math.max(x, 0)));
    }

    private double pos(double x) {
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
}






