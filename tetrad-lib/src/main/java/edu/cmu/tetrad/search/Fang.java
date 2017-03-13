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
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.util.StatUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

    // For the Fast Adjacency Search.
    private int depth = -1;

    // For the SEM BIC score, for the Fast Adjacency Search.
    private double penaltyDiscount = 1;

    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();

    // The maximum coefficient in absolute value (used for orienting 2-cycles.
    private double maxCoef = 0.6;

    // Alpha level for detecting noncorrelated errors.
    private double corrErrorsAlpha;

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

        RegressionDataset regression = new RegressionDataset(dataSet);

        SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
        score.setPenaltyDiscount(penaltyDiscount);
        IndependenceTest test = new IndTestScore(score, dataSet);
        List<Node> variables = dataSet.getVariables();

        double[][] colData = dataSet.getDoubleData().transpose().toArray();

        System.out.println("FAS");

        FasStableConcurrent fas = new FasStableConcurrent(test);
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

                double c1 = cov(x, y, 1, 0);
                double c2 = cov(x, y, 0, 1);

                if (G0.isAdjacentTo(X, Y) || abs(c1 - c2) > 0.2) {
                    double c = cov(x, y, 0, 0);
                    double c3 = cov(x, y, -1, 0);
                    double c4 = cov(x, y, 0, -1);

                    double R = abs(c - c2) - abs(c - c1);

                    if (knowledgeOrients(X, Y)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (knowledgeOrients(Y, X)) {
                        graph.addDirectedEdge(Y, X);
                    } else if (!((signum(c) == signum(c1) && signum(c) == signum(c3))
                            || (signum(c) == signum(c2) && signum(c) == signum(c4)))) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);

                        edge1.setLineColor(Color.RED);
                        edge2.setLineColor(Color.RED);

                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    } else if ((abs(c) > maxCoef)) {
                        Edge edge1 = Edges.directedEdge(X, Y);
                        Edge edge2 = Edges.directedEdge(Y, X);

                        edge1.setLineColor(Color.GREEN);
                        edge2.setLineColor(Color.GREEN);

                        graph.addEdge(edge1);
                        graph.addEdge(edge2);
                    } else if (R > 0) {
                        graph.addDirectedEdge(X, Y);
                    } else if (R < 0) {
                        graph.addDirectedEdge(Y, X);
                    } else {
                        graph.addUndirectedEdge(X, Y);
                    }
                }
            }
        }

        System.out.println("Remove extraneous edges.");

        double z0 = StatUtils.getZForAlpha(corrErrorsAlpha);

        // Remove extraneous edges.
        boolean changed = true;

        while (changed) {
            changed = false;

            for (int k = 0; k < variables.size(); k++) {
                Node X = variables.get(k);
                List<Node> parX = graph.getParents(X);

                for (Node Y : parX) {
                    double[] rx = regression.regress(X, graph.getParents(X)).getResiduals().toArray();
                    double[] ry = regression.regress(Y, graph.getParents(Y)).getResiduals().toArray();

                    if (isIndependent(rx, ry, z0)) {
                        if (!graph.getParents(X).contains(Y)) continue;

                        List<Node> _parX = graph.getParents(X);
                        _parX.remove(Y);

                        double[] rx2 = regression.regress(X, _parX).getResiduals().toArray();

                        if (!isIndependent(rx2, ry, z0)) {
                            Edge edge = graph.getDirectedEdge(Y, X);
                            graph.removeEdge(edge);
                            System.out.println("Removed " + edge);

                            List<Edge> edges = graph.getEdges(X, Y);

                            if (edges.size() == 1 && edges.get(0).isDirected()) {
                                edges.get(0).setLineColor(Color.BLUE);
                            }

                            changed = true;
                        }
                    }
                }
            }
        }

        System.out.println("Mark correlated errors.");

        double[][] res = new double[variables.size()][];

        for (int i = 0; i < variables.size(); i++) {
            Node v = variables.get(i);
            res[i] = regression.regress(v, graph.getParents(v)).getResiduals().toArray();
            res[i] = DataUtils.standardizeData(res[i]);
        }

        // Mark dependent errors.
        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                Node X = variables.get(i);
                Node Y = variables.get(j);

                if (graph.isAdjacentTo(X, Y)) continue;

                if (!isIndependent(res[i], res[j], z0)) {
                    System.out.println("Depenedent residuals " + X + "---" + Y);

                    if (graph.isAdjacentTo(X, Y)) {
                        List<Edge> edges = graph.getEdges(X, Y);

                        for (Edge edge : edges) {
                            edge.setDashed(true);
                        }
                    } else {
                        Edge edge = Edges.nondirectedEdge(X, Y);
                        edge.setLineColor(Color.GREEN.darker().darker());
                        edge.setDashed(true);
                        graph.addEdge(edge);
                    }
                }
            }
        }

        System.out.println("Done");

        long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        return graph;
    }

    private boolean isIndependent(double[] rx, double[] ry, double z0) {
        List<List<Double>> c = new ArrayList<List<Double>>();

        c.add(cor(rx, ry, 1, 0));
        c.add(cor(rx, ry, 0, 1));
        c.add(cor(rx, ry, -1, 0));
        c.add(cor(rx, ry, 0, -1));

        for (List<Double> c0 : c) {
            double _c = c0.get(0);
            double _n = c0.get(1);
            double z = 0.5 * sqrt(_n) * (log(1 + _c) / (1 - _c));
            if (z > z0) return false;
        }

        return true;
    }

    private double cov(double[] x, double[] y, int xInc, int yInc) {
        double exy = 0.0;

        double ex = 0.0;
        double ey = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (xInc == 0 && yInc == 0) {
                exy += x[k] * y[k];
                ex += x[k];
                ey += y[k];
                n++;
            } else if (xInc == 1 && yInc == 0) {
                if (x[k] > 0) {
                    exy += x[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == 0 && yInc == 1) {
                if (y[k] > 0) {
                    exy += x[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == -1 && yInc == 0) {
                if (x[k] < 0) {
                    exy += x[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == 0 && yInc == -1) {
                if (y[k] < 0) {
                    exy += x[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            }
        }

        exy /= n;
        ex /= n;
        ey /= n;

        return (exy - ex * ey);
    }

    private List<Double> cor(double[] x, double[] y, int xInc, int yInc) {
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
                if (x[k] > 0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == 0 && yInc == 1) {
                if (y[k] > 0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == -1 && yInc == 0) {
                if (x[k] < 0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == 0 && yInc == -1) {
                if (y[k] < 0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            }
        }

        exy /= n;
        ex /= n;
        ey /= n;

        double c = (exy - ex * ey) / sqrt((exx - ex * ex) * (eyy * ey * ey));

        List<Double> ret = new ArrayList<>();
        ret.add(c);
        ret.add((double) n);

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
     * @return The maximum coefficient in absoluate value (used for orienting 2-cycles).
     */
    public double getMaxCoef() {
        return maxCoef;
    }

    /**
     * @param maxCoef The maximum coefficient in absoluate value (used for orienting 2-cycles).f
     */
    public void setMaxCoef(double maxCoef) {
        this.maxCoef = maxCoef;
    }

    /**
     * @return Alpha level for detecting noncorrelated errors. The high this is set, the fewer noncorrelated
     * errors will be round.
     */
    public double getCorrErrorsAlpha() {
        return corrErrorsAlpha;
    }

    /**
     * @param corrErrorsAlpha Alpha level for detecting noncorrelated errors. The high this is set, the fewer noncorrelated
     *                        errors will be round.
     */
    public void setCorrErrorsAlpha(double corrErrorsAlpha) {
        this.corrErrorsAlpha = corrErrorsAlpha;
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
}






