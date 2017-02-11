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
    private int depth = 5;

    // For the SEM BIC score, for the Fast Adjacency Search.
    private double penaltyDiscount = 6;

    // For the T tests of equality and the Anderson-Darling tests.
    private double alpha = 0.05;

    public Fang(List<DataSet> dataSets) {
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * Runs the search on the concatenated data, returning a graph, possibly cyclic, possibly with
     * two-cycles.
     */
    public Graph search() {
        long start = System.currentTimeMillis();

        List<DataSet> _dataSets = new ArrayList<>();
        for (DataSet dataSet : dataSets) _dataSets.add(DataUtils.center(dataSet));

        DataSet dataSet = DataUtils.concatenate(_dataSets);

        SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
        score.setPenaltyDiscount(penaltyDiscount);
        IndependenceTest test = new IndTestScore(score, dataSet);

        final ICovarianceMatrix cov = new CovarianceMatrix(dataSet);
        List<Node> variables = cov.getVariables();

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

                double[] xg = new double[n];
                double[] yg = new double[n];
                double[] p1 = new double[n];
                double[] p2 = new double[n];
                double[] p3 = new double[n];
                double[] p4 = new double[n];
                double[] xy = new double[n];
                double[] dh = new double[n];
                double[] dg = new double[n];

                int n1 = 0;
                int n2 = 0;
                int n3 = 0;
                int n4 = 0;

                for (int k = 0; k < xg.length; k++) {
                    double x = xData[k];
                    double y = yData[k];

                    xg[k] = g(x) * y;
                    yg[k] = x * g(y);
                    p1[k] = pos(x) * y;
                    p2[k] = x * pos(y);
                    p3[k] = -pos(-x) * y;
                    p4[k] = x * -pos(-y);

                    if (p1[k] != 0) n1++;
                    if (p2[k] != 0) n2++;
                    if (p3[k] != 0) n3++;
                    if (p4[k] != 0) n4++;

                    xy[k] = x * y;
                    dg[k] = xg[k] - yg[k];
                    dh[k] = p1[k] - p2[k];
                }

                double tdh = (mean(dh) - 0.0) / (sd(dh, n) / sqrt(n));
                double tdp1 = (mean(p1, n1) - 0.0) / (sd(p1, n1) / sqrt(n1));
                double tdp2 = (mean(p2, n2) - 0.0) / (sd(p2, n2) / sqrt(n2));
                double tdp3 = (mean(p3, n3) - 0.0) / (sd(p3, n3) / sqrt(n3));
                double tdp4 = (mean(p4, n4) - 0.0) / (sd(p4, n4) / sqrt(n4));
                double tdxy = (mean(xy, n) - 0.0) / (sd(xy, n) / sqrt(n));
                double tdg = (mean(dg, n) - 0.0) / (sd(dg, n) / sqrt(n));

                boolean ng = isNonGaussian(i) || isNonGaussian(j);

                int numZero = 0;

                if (abs(tdp1) < T) numZero++;
                if (abs(tdp2) < T) numZero++;
                if (abs(tdp3) < T) numZero++;
                if (abs(tdp4) < T) numZero++;

                int numNonZero = 0;

                if (abs(tdp1) > T) numNonZero++;
                if (abs(tdp2) > T) numNonZero++;
                if (abs(tdp3) > T) numNonZero++;
                if (abs(tdp4) > T) numNonZero++;

                if (abs(tdh) > 12 || graph0.isAdjacentTo(variables.get(i), variables.get(j))) {
                    if (signum(tdp1) == -signum(tdxy)
                            || signum(tdp1) == -signum(tdxy)
                            || signum(tdp3) == -signum(tdxy)
                            || signum(tdp3) == -signum(tdxy)) {
                        graph.addDirectedEdge(X, Y);
                        graph.addDirectedEdge(Y, X);
                    } else if (abs(tdxy) > T && numZero > 0 && numNonZero > 0) {
//                            (abs(tdp1) < T || abs(tdp2) < T || abs(tdp3) < T || abs(tdp4) < T)) {
                        graph.addDirectedEdge(X, Y);
                        graph.addDirectedEdge(Y, X);
                    } else if (ng && abs(tdg) < T) {
                        graph.addDirectedEdge(X, Y);
                        graph.addDirectedEdge(Y, X);
                    } else if (ng && tdg >= T) {
                        graph.addDirectedEdge(X, Y);
                    } else if (ng && tdg <= T) {
                        graph.addDirectedEdge(Y, X);
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
     * @return The depth of search for the Fast Adjacency Search.
     */

    public int getDepth() {
        return depth;
    }

    /**
     * @param depth The depth of search for the Fast Adjacency Search.
     *              The default is 5. Making this too high may results in statistical errors.
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
     * @return The alpha value used for T tests of equality and non-Gaussianity tests.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * @param alpha The alpha value used for T tests of equality and non-Gaussianity tests.
     *              The default is 0.05.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * @return Returns the penalty discount used for the adjacency search.
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    /**
     * @param penaltyDiscount Sets the penalty discount used for the adjacency search.
     *                        The default is 6, deliberately high to remove weak edges from
     *                        the graph.
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

    public static double sd(double array[], int N) {
        return Math.pow(ssx(array, N) / (N - 1), .5);
    }

    public static double ssx(double array[], int N) {

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






