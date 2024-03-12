///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;
import edu.cmu.tetrad.util.StatUtils;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements the Direct-LiNGAM algorithm. The reference is here:
 * <p>
 * S. Shimizu, T. Inazumi, Y. Sogawa, A. Hyvärinen, Y. Kawahara, T. Washio, P. O. Hoyer and K. Bollen. DirectLiNGAM: A
 * direct method for learning a linear non-Gaussian structural equation model. Journal of Machine Learning Research,
 * 12(Apr): 1225–1248, 2011.
 * <p>
 * A. Hyvärinen and S. M. Smith. Pairwise likelihood ratios for estimation of non-Gaussian structural evaluation models.
 * Journal of Machine Learning Research 14:111-152, 2013.
 *
 * @author bryanandrews
 * @version $Id: $Id
 */
public class DirectLingam {
    /**
     * the data set
     */
    private final DataSet dataset;
    /**
     * the variables
     */
    private final List<Node> variables;
    /**
     * the grow-shrink trees
     */
    private final Map<Node, GrowShrinkTree> gsts;

    /**
     * Constructor.
     *
     * @param dataset the data set
     * @param score   the score
     */
    public DirectLingam(DataSet dataset, Score score) {
        this.dataset = dataset;
        this.variables = dataset.getVariables();
        this.gsts = new HashMap<>();

        int i = 0;
        Map<Node, Integer> index = new HashMap<>();
        for (Node node : this.variables) {
            index.put(node, i++);
            this.gsts.put(node, new GrowShrinkTree(score, index, node));
        }
    }

    /**
     * Calculates the maximum entropy approximation for the given array of values.
     *
     * @param x the array of values
     * @return the maximum entropy approximation
     */
    public static double maxEntApprox(double[] x) {
        x = StatUtils.standardizeData(x);

        final double k1 = 79.047;
        double k2 = 36 / (8 * sqrt(3) - 9);
        final double gamma = 0.37457;
        double gaussianEntropy = (log(2.0 * PI) / 2.0) + 1.0 / 2.0;

        // This is negentropy
        double b1 = 0.0;

        for (double aX1 : x) {

            // First term for the Taylor expansion of logcosh.
            b1 += aX1 * aX1 / 2.0;
        }

        b1 /= x.length;

        double b2 = 0.0;

        for (double aX : x) {
            b2 += aX * exp(-(aX * aX) / 2);
        }

        b2 /= x.length;

        double d = b1 - gamma;
        double negentropy = k1 * (d * d) + k2 * (b2 * b2);

        return gaussianEntropy - negentropy;
    }

    /**
     * Performs the search. Returns a graph.
     *
     * @return a graph
     */
    public Graph search() {
        List<Node> U = new ArrayList<>(this.variables);
        Map<Node, double[]> R = new HashMap<>();

        double[][] X = this.dataset.getDoubleData().transpose().toArray();
        for (int i = 0; i < X.length; i++) {
            standardize(X[i]);
            R.put(this.variables.get(i), X[i]);
        }

        Set<Node> K = new HashSet<>();
        Graph g = new EdgeListGraph(this.variables);

        while (!U.isEmpty()) {
            Node m = getNext(U, R);
            U.remove(m);
            for (Node x : U) {
                R.put(x, residuals(R.get(x), R.get(m)));
            }

            K.add(m);
            Set<Node> parents = new HashSet<>();
            this.gsts.get(m).trace(K, K, parents);
            for (Node x : parents) {
                g.addDirectedEdge(x, m);
            }
        }

        return g;
    }

    /**
     * Returns the next node in the list U that minimizes the objective function.
     *
     * @param U the list of nodes
     * @param R a map of nodes to residuals
     * @return the next node in the list U that minimizes the objective function
     */
    private Node getNext(List<Node> U, Map<Node, double[]> R) {
        Node m = U.get(0);
        double best = Double.POSITIVE_INFINITY;

        for (Node x : U) {
            double curr = 0;
            double entx = maxEntApprox(R.get(x));
            for (Node y : U) {
                if (x == y) continue;

                double[] rxy = residuals(R.get(x), R.get(y));
                double[] ryx = residuals(R.get(y), R.get(x));

                double lr = maxEntApprox(R.get(y)) - entx;
                lr += maxEntApprox(rxy) - maxEntApprox(ryx);
                double min = min(0, lr);
                curr += min * min;
            }

            if (curr < best) {
                best = curr;
                m = x;
            }
        }

        return m;
    }

    /**
     * Standardizes an array of doubles.
     *
     * @param x the array of doubles to be standardized
     */
    private void standardize(double[] x) {
        int n = x.length;
        double mu = 0;
        double std = 0;

        for (double v : x) {
            mu += v;
            std += v * v;
        }

        mu /= n;
        std = sqrt(std / n - mu * mu);

        for (int i = 0; i < n; i++) {
            x[i] = (x[i] - mu) / std;
        }
    }

    /**
     * Calculates the residuals between two arrays.
     *
     * @param x the first array
     * @param y the second array
     * @return an array of residuals
     */
    private double[] residuals(double[] x, double[] y) {
        int n = x.length;
        double cov = 0;
        double var = 0;

        for (int i = 0; i < n; i++) {
            cov += x[i] * y[i];
            var += y[i] * y[i];
        }
        double b = cov / var;

        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            r[i] = x[i] - b * y[i];
        }

        return r;
    }
}
