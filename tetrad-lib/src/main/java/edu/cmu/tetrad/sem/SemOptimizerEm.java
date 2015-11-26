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
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

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

    private TetradMatrix yCov;   // Sample cov.
    private TetradMatrix yCovModel, yzCovModel, zCovModel; // Partitions of the modeled cov.
    private TetradMatrix expectedCov;

    private int numObserved, numLatent;
    private int idxLatent[], idxObserved[];

    private int[][] parents;
    private Node[] errorParent;
    private double[][] nodeParentsCov;
    private double[][][] parentsCov;
    private int numRestarts = 1;

    public SemOptimizerEm() {}

    public void optimize(SemIm semIm) {
        if (numRestarts < 1) numRestarts = 1;

//        if (numRestarts != 1) {
//            throw new IllegalArgumentException("Number of restarts must be 1 for this method.");
//        }

        if (DataUtils.containsMissingValue(semIm.getSampleCovar())) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        if (numRestarts < 1) numRestarts = 1;


//        new SemOptimizerEm().optimize(semIm);

        // Optimize the semIm. Note that the the covariance matrix of the
        // sample data is made available to the following CoefFittingFunction.
        double min = semIm.getChiSquare();
        SemIm _sem = semIm;

        for (int count = 0; count < numRestarts; count++) {
            TetradLogger.getInstance().log("details", "Trial " + (count + 1));
//            System.out.println("Trial " + (count + 1));
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
//            System.out.println("chisq = " + chisq);

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
        } while (newScore > score + FUNC_TOLERANCE);

        semIm.getSemPm().getGraph().setShowErrorTerms(showErrors);
    }

    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    @Override
    public int getNumRestarts() {
        return numRestarts;
    }

    public TetradMatrix getExpectedCovarianceMatrix() {
        return new TetradMatrix(expectedCov);
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
        graph = semIm.getSemPm().getGraph();
        yCov = semIm.getSampleCovar();
        numObserved = 0;
        numLatent = 0;
        List<Node> nodes = graph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.getNodeType() == NodeType.LATENT) {
                numLatent++;
            } else if (node.getNodeType() == NodeType.MEASURED) {
                numObserved++;
            }
        }

        if (numLatent == 0) {
            throw new IllegalArgumentException("Need at least one latent for the EM estimator.");
        }

        idxLatent = new int[numLatent];
        idxObserved = new int[numObserved];
        int countLatent = 0;
        int countObserved = 0;

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.getNodeType() == NodeType.LATENT) {
                idxLatent[countLatent++] = i;
            } else if (node.getNodeType() == NodeType.MEASURED) {
                idxObserved[countObserved++] = i;
            }
        }

        expectedCov = new TetradMatrix(numObserved + numLatent, numObserved + numLatent);

        for (int i = 0; i < numObserved; i++) {
            for (int j = i; j < numObserved; j++) {
                expectedCov.set(idxObserved[i], idxObserved[j], yCov.get(i, j));
                expectedCov.set(idxObserved[j], idxObserved[i], yCov.get(i, j));
            }
        }

        yCovModel = new TetradMatrix(numObserved, numObserved);
        yzCovModel = new TetradMatrix(numObserved, numLatent);
        zCovModel = new TetradMatrix(numLatent, numLatent);

        parents = new int[numLatent + numObserved][];
        errorParent = new Node[numLatent + numObserved];
        nodeParentsCov = new double[numLatent + numObserved][];
        parentsCov = new double[numLatent + numObserved][][];
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.ERROR) {
                continue;
            }
            int idx = nodes.indexOf(node);
            List<Node> _parents = graph.getParents(node);
            for (int i = 0; i < _parents.size(); i++) {
                Node nextParent = _parents.get(i);
                if (nextParent.getNodeType() == NodeType.ERROR) {
                    errorParent[idx] = nextParent;
                    _parents.remove(nextParent);
                    break;
                }
            }
            if (_parents.size() > 0) {
                parents[idx] = new int[_parents.size()];
                nodeParentsCov[idx] = new double[_parents.size()];
                parentsCov[idx] = new double[_parents.size()][_parents.size()];
                for (int i = 0; i < _parents.size(); i++) {
                    parents[idx][i] = nodes.indexOf(_parents.get(i));
                }
            } else {
                parents[idx] = null;
            }
        }
    }

    public String toString() {
        return "Sem Optimizer EM";
    }

    private void expectation() {
        TetradMatrix bYZModel = yCovModel.inverse().times(yzCovModel);
        TetradMatrix yzCovPred = yCov.times(bYZModel);
        TetradMatrix zCovModel = yzCovModel.transpose().times(bYZModel);
        TetradMatrix zCovDiff = this.zCovModel.minus(zCovModel);
        TetradMatrix CzPred = yzCovPred.transpose().times(bYZModel);
        TetradMatrix newCz = CzPred.plus(zCovDiff);

        for (int i = 0; i < numLatent; i++) {
            for (int j = i; j < numLatent; j++) {
                expectedCov.set(idxLatent[i], idxLatent[j], newCz.get(i, j));
                expectedCov.set(idxLatent[j], idxLatent[i], newCz.get(j, i));
            }
        }

        for (int i = 0; i < numLatent; i++) {
            for (int j = 0; j < numObserved; j++) {
                double v = yzCovPred.get(j, i);
                expectedCov.set(idxLatent[i], idxObserved[j], v);
                expectedCov.set(idxObserved[j], idxLatent[i], v);
            }
        }
    }

    private void maximization() {
        List<Node> nodes = graph.getNodes();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.ERROR) {
                continue;
            }

            int idx = nodes.indexOf(node);
            double variance = expectedCov.get(idx, idx);

            if (parents[idx] != null) {
                for (int i = 0; i < parents[idx].length; i++) {
                    int idx2 = parents[idx][i];
                    nodeParentsCov[idx][i] = expectedCov.get(idx, idx2);
                    for (int j = i; j < parents[idx].length; j++) {
                        int idx3 = parents[idx][j];
                        parentsCov[idx][i][j] = expectedCov.get(idx2, idx3);
                        parentsCov[idx][j][i] = expectedCov.get(idx3, idx2);
                    }
                }

                TetradVector coefs = new TetradMatrix(parentsCov[idx]).inverse().times(new TetradVector(nodeParentsCov[idx]));

                for (int i = 0; i < coefs.size(); i++) {

                    if (semIm.getSemPm().getParameter(nodes.get(parents[idx][i]), node) != null && !semIm.getSemPm().getParameter(nodes.get(parents[idx][i]), node).isFixed()) {
                        semIm.setEdgeCoef(nodes.get(parents[idx][i]), node, coefs.get(i));
                    }
                }

                variance -= new TetradVector(nodeParentsCov[idx]).dotProduct(coefs);
            }

            if (!semIm.getSemPm().getParameter(errorParent[idx], errorParent[idx]).isFixed()) {
                semIm.setErrCovar(errorParent[idx], variance);
            }
        }
    }

    private void updateMatrices() {
        TetradMatrix impliedCovar = semIm.getImplCovar(true);
        for (int i = 0; i < numObserved; i++) {
            for (int j = i; j < numObserved; j++) {
                yCovModel.set(i, j, impliedCovar.get(idxObserved[i], idxObserved[j]));
                yCovModel.set(j, i, impliedCovar.get(idxObserved[i], idxObserved[j]));
            }
            for (int j = 0; j < numLatent; j++) {
                yzCovModel.set(i, j, impliedCovar.get(idxObserved[i], idxLatent[j]));
            }
        }
        for (int i = 0; i < numLatent; i++) {
            for (int j = i; j < numLatent; j++) {
                zCovModel.set(i, j, impliedCovar.get(idxLatent[i], idxLatent[j]));
                zCovModel.set(j, i, impliedCovar.get(idxLatent[i], idxLatent[j]));
            }
        }
    }

    private double scoreSemIm() {
        return -semIm.getScore();
    }

}



