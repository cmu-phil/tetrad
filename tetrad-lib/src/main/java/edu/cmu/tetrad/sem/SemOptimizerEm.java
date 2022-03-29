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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;

import java.util.List;

/**
 * Optimizes a DAG SEM with hidden variables using expectation-maximization.
 * IT SHOULD NOT BE USED WITH SEMs THAT ARE NOT DAGS. For DAGs without hidden
 * variables, SemOptimizerRegression should be more efficient.
 *
 * @author Ricardo Silva
 * @author Joseph Ramsey Cleanup, modernization.
 */
public class SemOptimizerEm implements SemOptimizer {
    static final long serialVersionUID = 23L;

    private static final double FUNC_TOLERANCE = 1.0e-6;

    private SemIm semIm;
    private SemGraph graph;

    private Matrix yCov;   // Sample cov.
    private Matrix yCovModel, yzCovModel, zCovModel; // Partitions of the modeled cov.
    private Matrix expectedCov;

    private int numObserved, numLatent;
    private int[] idxLatent, idxObserved;

    private int[][] parents;
    private Node[] errorParent;
    private double[][] nodeParentsCov;
    private double[][][] parentsCov;
    private int numRestarts = 1;

    public SemOptimizerEm() {
    }

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
            TetradLogger.getInstance().log("details", "Trial " + (count + 1));
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
            TetradLogger.getInstance().log("details", "chisq = " + chisq);

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

    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    @Override
    public int getNumRestarts() {
        return this.numRestarts;
    }

    public Matrix getExpectedCovarianceMatrix() {
        return new Matrix(this.expectedCov);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemOptimizerEm serializableInstance() {
        return new SemOptimizerEm();
    }

    //==============================PRIVATE METHODS========================//

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
            } else if (node.getNodeType() == NodeType.MEASURED) {
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
            } else if (node.getNodeType() == NodeType.MEASURED) {
                this.idxObserved[countObserved++] = i;
            }
        }

        this.expectedCov = new Matrix(this.numObserved + this.numLatent, this.numObserved + this.numLatent);

        for (int i = 0; i < this.numObserved; i++) {
            for (int j = i; j < this.numObserved; j++) {
                this.expectedCov.set(this.idxObserved[i], this.idxObserved[j], this.yCov.get(i, j));
                this.expectedCov.set(this.idxObserved[j], this.idxObserved[i], this.yCov.get(i, j));
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
            List<Node> _parents = this.graph.getParents(node);
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

    public String toString() {
        return "Sem Optimizer EM";
    }

    private void expectation() {
        Matrix bYZModel = this.yCovModel.inverse().times(this.yzCovModel);
        Matrix yzCovPred = this.yCov.times(bYZModel);
        Matrix zCovModel = this.yzCovModel.transpose().times(bYZModel);
        Matrix zCovDiff = this.zCovModel.minus(zCovModel);
        Matrix CzPred = yzCovPred.transpose().times(bYZModel);
        Matrix newCz = CzPred.plus(zCovDiff);

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
        List<Node> nodes = this.graph.getNodes();

        for (Node node : this.graph.getNodes()) {
            if (node.getNodeType() == NodeType.ERROR) {
                continue;
            }

            int idx = nodes.indexOf(node);
            double variance = this.expectedCov.get(idx, idx);

            if (this.parents[idx] != null) {
                for (int i = 0; i < this.parents[idx].length; i++) {
                    int idx2 = this.parents[idx][i];
                    this.nodeParentsCov[idx][i] = this.expectedCov.get(idx, idx2);
                    for (int j = i; j < this.parents[idx].length; j++) {
                        int idx3 = this.parents[idx][j];
                        this.parentsCov[idx][i][j] = this.expectedCov.get(idx2, idx3);
                        this.parentsCov[idx][j][i] = this.expectedCov.get(idx3, idx2);
                    }
                }

                Vector coefs = new Matrix(this.parentsCov[idx]).inverse().times(new Vector(this.nodeParentsCov[idx]));

                for (int i = 0; i < coefs.size(); i++) {

                    if (this.semIm.getSemPm().getParameter(nodes.get(this.parents[idx][i]), node) != null && !this.semIm.getSemPm().getParameter(nodes.get(this.parents[idx][i]), node).isFixed()) {
                        this.semIm.setEdgeCoef(nodes.get(this.parents[idx][i]), node, coefs.get(i));
                    }
                }

                variance -= new Vector(this.nodeParentsCov[idx]).dotProduct(coefs);
            }

            if (!this.semIm.getSemPm().getParameter(this.errorParent[idx], this.errorParent[idx]).isFixed()) {
                this.semIm.setErrCovar(this.errorParent[idx], variance);
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



