/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

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
 * ICA-LiNGAM (Shimizu et al., 2006), with small numerical hardening to match the stabilized ICA-LiNG-D pipeline.
 * <p>
 * Changes vs. the earlier version: - Optionally threshold W before Hungarian assignment (reduces spurious diagonals). -
 * After forming B̂ via IcaLingD.getScaledBHat (guarded diag), hard-threshold B̂ using bThreshold prior to acyclicity
 * trimming. - Always return a (possibly empty) trimmed matrix (never null).
 */
public class IcaLingam {

    /**
     * Dummy graph and helpers for cycle detection during trimming.
     */
    private static Graph dummyGraph;
    private static ArrayList<Node> dummyVars;
    private Set<Node> dummyCyclicNodes;

    /**
     * Threshold for |B_ij| -> 0 before acyclicity trimming.
     */
    private double bThreshold = 0.1;

    /**
     * (Optional) pre-threshold for |W_ij| -> 0 before Hungarian; set to 0 to disable.
     */
    private double wThreshold = 0.0;

    /**
     * Verbose logging.
     */
    private boolean verbose = false;

    /**
     * Constructor for the IcaLingam class. Initializes an instance of the ICA-LiNGAM algorithm for causal discovery.
     * This class provides methods to estimate causal structures from observational data, ensuring acyclic causal graphs
     * and performing weight matrix estimation and processing.
     */
    public IcaLingam() {
    }

    /**
     * Fits a dataset to estimate a weight matrix using the ICA-LiNGAM algorithm. The method utilizes stabilized FastICA
     * for the estimation process and further processes the weights to ensure they conform to an acyclic structure.
     *
     * @param D the dataset, represented as a DataSet object, which includes the observed data necessary for estimating
     *          the weight matrix.
     * @return a matrix representing acyclic trimmed weights derived from the dataset.
     */
    public Matrix fit(DataSet D) {
        // Slightly more conservative defaults play nicely with stabilized FastICA.
        Matrix W = IcaLingD.estimateW(D, 5000, 1e-6, 1.2, true);
        return getAcyclicTrimmedBHat(W);
    }

    /**
     * Processes a given matrix W to generate a scaled and trimmed matrix B̂ that is guaranteed to correspond to an
     * acyclic directed graph (DAG). The method applies optional denoising, diagonal maximization via the Hungarian
     * algorithm, hard-thresholding, and iterative edge trimming to ensure the acyclicity of B̂.
     *
     * @param W the input matrix representing initial weights or coefficients; it will be processed to produce an
     *          acyclic trimmed matrix.
     * @return a matrix B̂ that corresponds to an acyclic directed graph derived from the input matrix W.
     */
    public Matrix getAcyclicTrimmedBHat(Matrix W) {
        W = new Matrix(W);

        // (1) Optional gentle denoising of W
        double wt = Math.max(0.0, this.wThreshold);
        if (wt > 0) {
            for (int i = 0; i < W.getNumRows(); i++) {
                for (int j = 0; j < W.getNumColumns(); j++) {
                    if (abs(W.get(i, j)) < wt) W.set(i, j, 0.0);
                }
            }
        }

        // (2) Best diagonal via Hungarian, then robust scaling to produce B̂
        PermutationMatrixPair bestPair = IcaLingD.maximizeDiagonal(W);
        Matrix scaledBHat = IcaLingD.getScaledBHat(bestPair);

        // (3) Hard-threshold B̂ BEFORE trimming
        double bt = Math.max(0.0, this.bThreshold);
        if (bt > 0) {
            for (int i = 0; i < scaledBHat.getNumRows(); i++) {
                for (int j = 0; j < scaledBHat.getNumColumns(); j++) {
                    if (abs(scaledBHat.get(i, j)) < bt) scaledBHat.set(i, j, 0.0);
                }
            }
        }

        // Quick pass: already acyclic?
        if (isAcyclic(scaledBHat)) {
            return scaledBHat;
        }

        // (4) Trim smallest-magnitude edges until DAG
        class Record {
            double coef;
            int i;
            int j;
        }
        LinkedList<Record> coefs = new LinkedList<>();

        for (int i = 0; i < scaledBHat.getNumRows(); i++) {
            for (int j = 0; j < scaledBHat.getNumColumns(); j++) {
                if (i != j && scaledBHat.get(i, j) != 0.0) {
                    Record r = new Record();
                    r.coef = abs(scaledBHat.get(i, j));
                    r.i = i;
                    r.j = j;
                    coefs.add(r);
                }
            }
        }

        // Smallest first (remove weakest edges before stronger ones)
        coefs.sort(Comparator.comparingDouble(o -> o.coef));

        // Ensure dummyGraph is initialized to the current B̂
        isAcyclic(scaledBHat); // sets dummyGraph & dummyVars

        while (!coefs.isEmpty()) {
            Record r = coefs.removeFirst();

            // Remove that edge from both matrix and graph
            scaledBHat.set(r.i, r.j, 0.0);
            Edge e = dummyGraph.getDirectedEdge(dummyVars.get(r.j), dummyVars.get(r.i));
            if (e != null) dummyGraph.removeEdge(e);

            // Check acyclicity
            if (!existsDirectedCycle()) {
                if (verbose) {
                    TetradLogger.getInstance().log("ICA-LiNGAM: effective trim threshold = " + r.coef);
                }
                return scaledBHat;
            }
        }

        // If we get here, all edges were removed; return empty DAG (still valid & acyclic).
        if (verbose) {
            TetradLogger.getInstance().log("ICA-LiNGAM: all edges removed to achieve acyclicity.");
        }
        return scaledBHat;
    }

    /**
     * Checks whether the graph induced by the scaled coefficient matrix represents an acyclic directed graph (DAG).
     *
     * @param scaledBHat the scaled coefficient matrix used to construct the graph.
     * @return true if the graph is acyclic, otherwise false.
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
     * Threshold for |B_ij| -> 0 before trimming.
     *
     * @param bThreshold the threshold value for B_ij coefficients.
     */
    public void setBThreshold(double bThreshold) {
        if (bThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + bThreshold);
        this.bThreshold = bThreshold;
    }

    /**
     * Optional pre-threshold for |W_ij| -> 0 before Hungarian; set to 0 to disable (default).
     *
     * @param wThreshold the threshold value for W_ij coefficients.
     */
    public void setWThreshold(double wThreshold) {
        if (wThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + wThreshold);
        this.wThreshold = wThreshold;
    }

    /**
     * Verbose logging.
     *
     * @param verbose whether to enable verbose logging.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}