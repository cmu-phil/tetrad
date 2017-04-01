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
import org.apache.commons.math3.distribution.TDistribution;

import java.awt.*;
import java.util.List;

import static java.lang.Math.*;

/**
 * Fast adjacency search followed by robust skew orientation. Checks are done for adding
 * two-cycles. The two-cycle checks do not require non-Gaussianity. The robust skew
 * orientation of edges left or right does.
 *
 * @author Joseph Ramsey
 */
public final class Fang2 implements GraphSearch {

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

    /**
     * @param dataSet These datasets must all have the same variables, in the same order.
     */
    public Fang2(DataSet dataSet) {
        this.dataSet = dataSet;
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

        DataSet dataSet = DataUtils.standardizeData(this.dataSet);

        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(penaltyDiscount);
        IndependenceTest test = new IndTestScore(score, dataSet);
        List<Node> variables = dataSet.getVariables();

        double[][] colData = dataSet.getDoubleData().transpose().toArray();

        System.out.println("FAS");

        Fas fas = new Fas(test);
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        fas.setKnowledge(knowledge);
        Graph G0 = fas.search();

        SearchGraphUtils.pcOrientbk(knowledge, G0, G0.getNodes());

        System.out.println("Orientation");

        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                Node X = variables.get(i);
                Node Y = variables.get(j);

                // Standardized.
                final double[] x = colData[i];
                final double[] y = colData[j];

                double[] c1 = cor(x, y, 1, 0);
                double[] c2 = cor(x, y, 0, 1);

                if (G0.isAdjacentTo(X, Y)) {// || abs(c1[0] - c2[0]) > .3) {
                    double c[] = cor(x, y, 0, 0);
                    double G = abs(c[0] - c2[0]) - abs(c[0] - c1[0]);
                    double c3[] = cor(x, y, -1, 0);
                    double c4[] = cor(x, y, 0, -1);

                    double z = getZ(c[0]);
                    double z1 = getZ(c1[0]);
                    double z2 = getZ(c2[0]);

                    double diff1 = z - z1;
                    double diff2 = z - z2;

                    final double t1 = diff1 / (sqrt(1.0 / c[1] + 1.0 / c1[1]));
                    final double t2 = diff2 / (sqrt(1.0 / c[1] + 1.0 / c2[1]));

                    double p1 = 1.0 - new TDistribution(2 * (c[1] + c1[1]) - 2).cumulativeProbability(abs(t1 / 2.0));
                    double p2 = 1.0 - new TDistribution(2 * (c[1] + c2[1]) - 2).cumulativeProbability(abs(t2 / 2.0));

                    if (knowledgeOrients(X, Y)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (knowledgeOrients(Y, X)) {
                        graph.addDirectedEdge(Y, X);
                    } else if (p1 <= alpha && p2 <= alpha) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);

                        edge1.setLineColor(Color.GREEN);
                        edge2.setLineColor(Color.GREEN);

                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    } else if (!((signum(c[0]) == signum(c1[0]) && signum(c[0]) == signum(c3[0]))
                            || (signum(c[0]) == signum(c2[0]) && signum(c[0]) == signum(c4[0])))) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);

                        edge1.setLineColor(Color.RED);
                        edge2.setLineColor(Color.RED);

                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    } else if (abs(diff2) > abs(diff1)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (abs(diff1) > abs(diff2)) {
                        graph.addDirectedEdge(Y, X);
                    } else {
                        graph.addUndirectedEdge(X, Y);
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

    private double[] cor(double[] x, double[] y, int xInc, int yInc) {

        double exy = 0.0;
        double exx = 0.0;
        double eyy = 0.0;

        double ex = 0.0;
        double ey = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (xInc == 0 && yInc == 0) {
                exy += x[k] * y[k];
                exx += x[k] * x[k];
                eyy += y[k] * y[k];
                ex += x[k];
                ey += y[k];
                n++;
            } else if (xInc == 1 && yInc == 0) {
                if (x[k] > 0.0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == 0 && yInc == 1) {
                if (y[k] > 0.0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == -1 && yInc == 0) {
                if (x[k] < 0.0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == 0 && yInc == -1) {
                if (y[k] < 0.0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            }
        }

        exx /= n;
        eyy /= n;
        exy /= n;
        ex /= n;
        ey /= n;

        return new double[]{(exy - ex * ey) / Math.sqrt((exx - ex * ex) * (eyy - ey * ey)), (double) n};
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

    private double getZ(double r) {
        return 0.5 * (log(1.0 + r) - log(1.0 - r));
    }
}






