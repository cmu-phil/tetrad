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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.PermutationMatrixPair;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.abs;

/**
 * Implements the ICA-LiNGAM algorithm. The reference is here:
 * <p>
 * Shimizu, S., Hoyer, P. O., Hyvärinen, A., Kerminen, A., &amp; Jordan, M. (2006). A linear non-Gaussian acyclic model
 * for causal discovery. Journal of Machine Learning Research, 7(10).
 * <p>
 * ICA-LiNGAM is a method for estimating a causal graph from a dataset. It is based on the assumption that the data are
 * generated by a linear model with non-Gaussian noise. The method is based on the following assumptions: (1) The data
 * are generated by a linear model with non-Gaussian noise. (2) The noise is independent across variables. (3) The
 * noises for all but possibly one variable are non-Gaussian. (4) There is no unobserved confounding.
 * <p>
 * Under these assumptions, the method estimates a matrix W such that WX = e, where X is the data matrix, e is a matrix
 * of noise, and W is a matrix of coefficients. The matrix W is then used to estimate a matrix B Hat, where B Hat is the
 * matrix of coefficients in the linear model that generated the data. The graph is then estimated by finding edges in B
 * Hat.
 * <p>
 * We guarantee acyclicity of the output using one of the algorithms in the paper--i.e. we set small coefficients to
 * zero until an acyclic model is achieved, setting any coefficients below threshold to zero as well.
 * <p>
 * We are using the Hungarian Algorithm to solve the linear assignment problem for finding the best diagonal for W.
 * <p>
 * This class is not configured to respect knowledge of forbidden and required edges.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see IcaLingD
 * @see edu.cmu.tetrad.search.utils.HungarianAlgorithm
 */
public class IcaLingam {

    /**
     * Represents a dummy graph.
     */
    private static Graph dummyGraph;
    /**
     * Represents a list of Node objects for the dummy graph.
     */
    private static ArrayList<Node> dummyVars;
    /**
     * Represents a set of Node objects for the dummy graph that are cyclic.
     */
    private Set<Node> dummyCyclicNodes;
    /**
     * The threshold to use for set small elements to zero in the B Hat matrices.
     */
    private double bThreshold = 0.1;
    /**
     * A boolean indicating whether to print verbose output.
     */
    private boolean verbose = false;

    /**
     * Constructor.
     */
    public IcaLingam() {
    }

    /**
     * Fits an ICA-LiNGAM model to the given dataset using a default method for estimating W.
     *
     * @param D A continuous dataset.
     * @return The BHat matrix, where B[i][j] gives the coefficient of j->i if nonzero.
     */
    public Matrix fit(DataSet D) {
        Matrix W = IcaLingD.estimateW(D, 5000, 1e-6, 1.2, true);
        return getAcyclicTrimmedBHat(W);
    }

    /**
     * Calculates and returns the trimmed BHat matrix in an acyclic form using the given matrix W.
     *
     * @param W The input matrix. The BHat matrix is derived from this matrix.
     * @return The trimmed BHat matrix in an acyclic form.
     */
    public Matrix getAcyclicTrimmedBHat(Matrix W) {
        W = new Matrix(W);
        PermutationMatrixPair bestPair = IcaLingD.maximizeDiagonal(W);
        Matrix scaledBHat = IcaLingD.getScaledBHat(bestPair);

        if (isAcyclic(scaledBHat)) {
            return scaledBHat;
        }

        class Record {
            double coef;
            int i;
            int j;
        }

        LinkedList<Record> coefs = new LinkedList<>();

        for (int i = 0; i < scaledBHat.getNumRows(); i++) {
            for (int j = 0; j < scaledBHat.getNumColumns(); j++) {
                if (i != j && scaledBHat.get(i, j) != 0) {
                    Record record = new Record();
                    record.coef = scaledBHat.get(i, j);
                    record.i = i;
                    record.j = j;
                    coefs.add(record);
                }
            }
        }

        coefs.sort(Comparator.comparingDouble(o -> abs(o.coef)));

        Matrix trimmed = null;

        while (!coefs.isEmpty()) {
            Record coef = coefs.removeFirst();

            if (coef.coef < bThreshold) {
                scaledBHat.set(coef.i, coef.j, 0.0);
                Edge edge = dummyGraph.getDirectedEdge(dummyVars.get(coef.j), dummyVars.get(coef.i));
                dummyGraph.removeEdge(edge);
                continue;
            } else if (!existsDirectedCycle()) {

                if (verbose) {
                    TetradLogger.getInstance().log("Effective threshold = " + coef.coef);
                }

                trimmed = scaledBHat;
                break;
            }

            scaledBHat.set(coef.i, coef.j, 0.0);
            Edge edge = dummyGraph.getDirectedEdge(dummyVars.get(coef.j), dummyVars.get(coef.i));
            dummyGraph.removeEdge(edge);
        }

        return trimmed;
    }

    /**
     * Determines whether a BHat matrix parses to an acyclic graph.
     *
     * @param scaledBHat The BHat matrix.
     * @return a boolean
     */
    public boolean isAcyclic(Matrix scaledBHat) {
        dummyVars = new ArrayList<>();

        for (int i = 0; i < scaledBHat.getNumRows(); i++) {
            dummyVars.add(new GraphNode("" + i));
        }

        dummyCyclicNodes = new HashSet<>(dummyVars);

        dummyGraph = IcaLingD.makeGraph(scaledBHat, dummyVars);
        return !dummyGraph.paths().existsDirectedCycle();
    }

    private boolean existsDirectedCycle() {
        for (Node node : new HashSet<>(dummyCyclicNodes)) {
            if (dummyGraph.paths().existsDirectedPath(node, node)) {
                return true;
            } else {
                dummyCyclicNodes.remove(node);
            }
        }

        return false;
    }

    /**
     * The threshold to use for set small elements to zero in the B Hat matrices.
     *
     * @param bThreshold Some value >= 0.
     */
    public void setBThreshold(double bThreshold) {
        if (bThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + bThreshold);
        this.bThreshold = bThreshold;
    }

    /**
     * A boolean indicating whether to print verbose output.
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}

