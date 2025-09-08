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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimizes a DAG SEM with hidden variables using expectation-maximization. IT SHOULD NOT BE USED WITH SEMs THAT ARE
 * NOT DAGS. For DAGs without hidden variables, SemOptimizerRegression should be more efficient.
 *
 * @author Ricardo Silva
 * @author josephramsey Cleanup, modernization.
 * @version $Id: $Id
 */
public class SemOptimizerEm implements SemOptimizer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Tolerance for the function.
     */
    private static final double FUNC_TOLERANCE = 1.0e-6;

    /**
     * The SEM to optimize.
     */
    private SemIm semIm;

    /**
     * The SEM graph.
     */
    private SemGraph graph;

    /**
     * The sample covariance matrix.
     */
    private Matrix yCov;   // Sample cov.

    /**
     * Partitions of the modeled cov.
     */
    private Matrix yCovModel,

    /**
     * Partitions of the modeled cov.
     */
    yzCovModel,

    /**
     * Partitions of the modeled cov.
     */
    zCovModel;

    /**
     * Expected covariance matrix.
     */
    private Matrix expectedCov;

    /**
     * Number of observed and latent variables.
     */
    private int numObserved,

    /**
     * Number of observed and latent variables.
     */
    numLatent;

    /**
     * Indices of the latent variables.
     */
    private int[] idxLatent,

    /**
     * Indices of the observed variables.
     */
    idxObserved;

    /**
     * Indices of the parents of each node.
     */
    private int[][] parents;

    /**
     * Error parent of each node.
     */
    private Node[] errorParent;

    /**
     * Covariance of each node with its parents.
     */
    private double[][] nodeParentsCov;

    /**
     * Parents covariance matrix.
     */
    private double[][][] parentsCov;

    /**
     * Number of restarts.
     */
    private int numRestarts = 1;

    /**
     * <p>Constructor for SemOptimizerEm.</p>
     */
    public SemOptimizerEm() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemOptimizerEm} object
     */
    public static SemOptimizerEm serializableInstance() {
        return new SemOptimizerEm();
    }

    /**
     * Optimizes an unoptimized Sem object by minimizing the chi-square statistic.
     *
     * @param semIm The unoptimized Sem object to be optimized.
     * @throws NullPointerException     If the sample covariance matrix has not been set.
     * @throws IllegalArgumentException If the sample covariance matrix contains missing values.
     * @throws RuntimeException         If an error occurs during optimization.
     */
    public void optimize(SemIm semIm) {
        if (this.numRestarts < 1) this.numRestarts = 1;

        Matrix sampleCovar = semIm.getSampleCovar();

        if (sampleCovar == null) {
            throw new NullPointerException("Sample covar has not been set.");
        }

        if (DataUtils.containsMissingValue(sampleCovar)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        if (this.numRestarts < 1) this.numRestarts = 1;


        // Optimize the semIm. Note that the the covariance matrix of the
        // sample data is made available to the following CoefFittingFunction.
        double min = semIm.getChiSquare();
        SemIm _sem = semIm;

        for (int count = 0; count < this.numRestarts; count++) {
            TetradLogger.getInstance().log("Trial " + (count + 1));
            SemIm _sem2 = new SemIm(semIm);

            List<Parameter> freeParameters = _sem2.getFreeParameters();

            double[] p = new double[freeParameters.size()];

            for (int i = 0; i < freeParameters.size(); i++) {
                if (freeParameters.get(i).getType() == ParamType.VAR) {
                    p[i] = RandomUtil.getInstance().nextUniform(0, 3);
                } else {
                    p[i] = RandomUtil.getInstance().nextUniform(-2, 2);
                }
            }

            _sem2.setFreeParamValues(p);

            optimize2(_sem2);

            double chisq = _sem2.getChiSquare();
            TetradLogger.getInstance().log("chisq = " + chisq);

            if (chisq < min) {
                min = chisq;
                _sem = _sem2;
            }
        }

        for (Parameter param : semIm.getFreeParameters()) {
            try {
                Node nodeA = param.getNodeA();
                Node nodeB = param.getNodeB();

                Node _nodeA = _sem.getVariableNode(nodeA.getName());
                Node _nodeB = _sem.getVariableNode(nodeB.getName());

                double value = _sem.getParamValue(_nodeA, _nodeB);
                semIm.setParamValue(param, value);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private void optimize2(SemIm semIm) {
        boolean showErrors = semIm.getSemPm().getGraph().isShowErrorTerms();
        semIm.getSemPm().getGraph().setShowErrorTerms(true);

        initialize(semIm);
        updateMatrices();
        double score, newScore = scoreSemIm();
        do {
            score = newScore;
            expectation();
            maximization();
            updateMatrices();
            newScore = scoreSemIm();
        } while (newScore > score + SemOptimizerEm.FUNC_TOLERANCE);

        semIm.getSemPm().getGraph().setShowErrorTerms(showErrors);
    }

    /**
     * Returns the number of restarts for the optimization process.
     *
     * @return The number of restarts for the optimization process.
     */
    @Override
    public int getNumRestarts() {
        return this.numRestarts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    private void initialize(SemIm semIm) {
        this.semIm = semIm;
        this.graph = semIm.getSemPm().getGraph();
        this.yCov = semIm.getSampleCovar();

        if (this.yCov == null) {
            throw new NullPointerException("Sample covar has not been set.");
        }

        this.numObserved = 0;
        this.numLatent = 0;
        List<Node> nodes = this.graph.getNodes();

        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.LATENT) {
                this.numLatent++;
            } else if (node.getNodeType() == NodeType.MEASURED || node.getNodeType() == NodeType.SELECTION) {
                this.numObserved++;
            }
        }

        if (this.numLatent == 0) {
            throw new IllegalArgumentException("Need at least one latent for the EM estimator.");
        }

        this.idxLatent = new int[this.numLatent];
        this.idxObserved = new int[this.numObserved];
        int countLatent = 0;
        int countObserved = 0;

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.getNodeType() == NodeType.LATENT) {
                this.idxLatent[countLatent++] = i;
            } else if (node.getNodeType() == NodeType.MEASURED || node.getNodeType() == NodeType.SELECTION) {
                this.idxObserved[countObserved++] = i;
            }
        }

        // In initialize(), replace how you fill idxObserved/idxLatent.
        List<Node> allVars = semIm.getSemPm().getVariableNodes(); // canonical order for SemIm matrices
        List<Node> measured = semIm.getMeasuredNodes();

        this.idxObserved = new int[measured.size()];
        this.idxLatent   = new int[allVars.size() - measured.size()];

        int o = 0, l = 0;
        for (int i = 0; i < allVars.size(); i++) {
            Node node = allVars.get(i);
            if (node.getNodeType() == NodeType.MEASURED || node.getNodeType() == NodeType.SELECTION) {
                this.idxObserved[o++] = i;
            } else if (node.getNodeType() == NodeType.LATENT) {
                this.idxLatent[l++] = i;
            }
        }
        this.numObserved = o;
        this.numLatent   = l;

        // Fill expectedCov’s observed block using yCov in measured order:
        this.expectedCov = new Matrix(this.numObserved + this.numLatent, this.numObserved + this.numLatent);
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = i; j < this.numObserved; j++) {
                double v = this.yCov.get(i, j); // yCov is in 'measured' order
                int ii = this.idxObserved[i], jj = this.idxObserved[j];
                this.expectedCov.set(ii, jj, v);
                this.expectedCov.set(jj, ii, v);
            }
        }

        this.yCovModel = new Matrix(this.numObserved, this.numObserved);
        this.yzCovModel = new Matrix(this.numObserved, this.numLatent);
        this.zCovModel = new Matrix(this.numLatent, this.numLatent);

        this.parents = new int[this.numLatent + this.numObserved][];
        this.errorParent = new Node[this.numLatent + this.numObserved];
        this.nodeParentsCov = new double[this.numLatent + this.numObserved][];
        this.parentsCov = new double[this.numLatent + this.numObserved][][];
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.ERROR) {
                continue;
            }
            int idx = nodes.indexOf(node);
            List<Node> _parents = new ArrayList<>(this.graph.getParents(node));
            for (int i = 0; i < _parents.size(); i++) {
                Node nextParent = _parents.get(i);
                if (nextParent.getNodeType() == NodeType.ERROR) {
                    this.errorParent[idx] = nextParent;
                    _parents.remove(nextParent);
                    break;
                }
            }
            if (_parents.size() > 0) {
                this.parents[idx] = new int[_parents.size()];
                this.nodeParentsCov[idx] = new double[_parents.size()];
                this.parentsCov[idx] = new double[_parents.size()][_parents.size()];
                for (int i = 0; i < _parents.size(); i++) {
                    this.parents[idx][i] = nodes.indexOf(_parents.get(i));
                }
            } else {
                this.parents[idx] = null;
            }
        }
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Sem Optimizer EM";
    }

    private void expectation() {

        // Solve yCovModel * B = yzCovModel  -> B = yCovModel^{-1} * yzCovModel
        Matrix B = yCovModel.solve(yzCovModel); // implement solve() or replace with a small helper

        Matrix yzCovPred = this.yCov.times(B);           // E[Z|Y] covariance
        Matrix zCovModel = this.yzCovModel.transpose().times(B);
        Matrix zCovDiff  = this.zCovModel.minus(zCovModel);
        Matrix CzPred    = yzCovPred.transpose().times(B);
        Matrix newCz     = CzPred.plus(zCovDiff);

        for (int i = 0; i < this.numLatent; i++) {
            for (int j = i; j < this.numLatent; j++) {
                this.expectedCov.set(this.idxLatent[i], this.idxLatent[j], newCz.get(i, j));
                this.expectedCov.set(this.idxLatent[j], this.idxLatent[i], newCz.get(j, i));
            }
        }

        for (int i = 0; i < this.numLatent; i++) {
            for (int j = 0; j < this.numObserved; j++) {
                double v = yzCovPred.get(j, i);
                this.expectedCov.set(this.idxLatent[i], this.idxObserved[j], v);
                this.expectedCov.set(this.idxObserved[j], this.idxLatent[i], v);
            }
        }
    }

    private void maximization() {
        // We work in the graph.getNodes() index space, consistent with how 'parents',
        // 'errorParent', and 'expectedCov' were built in initialize().
        List<Node> nodes = this.graph.getNodes();

        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.ERROR) continue;

            int idx = nodes.indexOf(node);

            // Start from the (expected) variance of this node.
            double variance = this.expectedCov.get(idx, idx);

            // If the node has structural parents, estimate regression coefficients via
            // (regularized) normal equations: coefs = (X'X + λI)^{-1} X'y.
            if (this.parents[idx] != null && this.parents[idx].length > 0) {
                int k = this.parents[idx].length;

                // Build cov(y, parents) and cov(parents, parents) from expectedCov
                for (int i = 0; i < k; i++) {
                    int pi = this.parents[idx][i];
                    this.nodeParentsCov[idx][i] = this.expectedCov.get(idx, pi);
                    for (int j = i; j < k; j++) {
                        int pj = this.parents[idx][j];
                        double v = this.expectedCov.get(pi, pj);
                        this.parentsCov[idx][i][j] = v;
                        this.parentsCov[idx][j][i] = v;
                    }
                }

                // Ridge regularization to stabilize in case of near-singular parent covariance.
                // We keep λ tiny so estimates remain close to OLS when well-conditioned.
                final double ridge = 1e-8;
                Matrix M = new Matrix(this.parentsCov[idx]);         // k x k
                for (int d = 0; d < k; d++) {
                    M.set(d, d, M.get(d, d) + ridge);
                }
                Vector c = new Vector(this.nodeParentsCov[idx]);      // k

                // Solve for coefficients. If a direct solve is unavailable, fall back to inverse().
                Vector coefs;
                try {
                    // Prefer a linear solve if available in your Matrix class:
                    // coefs = M.solve(c);
                    // If not available, use inverse() as a fallback:
                    coefs = M.inverse().times(c);
                } catch (Throwable t) {
                    // Extremely defensive: try a slightly larger ridge and retry.
                    double more = 1e-6;
                    for (int d = 0; d < k; d++) {
                        M.set(d, d, M.get(d, d) + more);
                    }
                    coefs = M.inverse().times(c);
                }

                // Write coefficients back to the SEM (respect fixed parameters).
                for (int i = 0; i < k; i++) {
                    Node parent = nodes.get(this.parents[idx][i]);
                    double beta = coefs.get(i);

                    // Only set if the coefficient parameter exists and is free.
                    Parameter p = this.semIm.getSemPm().getParameter(parent, node);
                    if (p != null && !p.isFixed()) {
                        this.semIm.setEdgeCoef(parent, node, beta);
                    }
                }

                // Residual variance = Var(y) - cov(y,parents)·beta
                double explained = new Vector(this.nodeParentsCov[idx]).dotProduct(coefs);
                variance -= explained;

                // Clamp tiny negative values due to roundoff.
                if (variance < 0.0) variance = 0.0;
            }

            // Update the error variance parameter for this node's error term, if present and free.
            Node err = this.errorParent[idx];
            if (err != null) {
                Parameter varParam = this.semIm.getSemPm().getParameter(err, err);
                if (varParam != null && !varParam.isFixed()) {
                    this.semIm.setErrCovar(err, err, variance);
                }
            }
        }
    }

    private void updateMatrices() {
        Matrix impliedCovar = this.semIm.getImplCovar(true);
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = i; j < this.numObserved; j++) {
                this.yCovModel.set(i, j, impliedCovar.get(this.idxObserved[i], this.idxObserved[j]));
                this.yCovModel.set(j, i, impliedCovar.get(this.idxObserved[i], this.idxObserved[j]));
            }
            for (int j = 0; j < this.numLatent; j++) {
                this.yzCovModel.set(i, j, impliedCovar.get(this.idxObserved[i], this.idxLatent[j]));
            }
        }
        for (int i = 0; i < this.numLatent; i++) {
            for (int j = i; j < this.numLatent; j++) {
                this.zCovModel.set(i, j, impliedCovar.get(this.idxLatent[i], this.idxLatent[j]));
                this.zCovModel.set(j, i, impliedCovar.get(this.idxLatent[i], this.idxLatent[j]));
            }
        }
    }

    private double scoreSemIm() {
        return -this.semIm.getScore();
    }

}



