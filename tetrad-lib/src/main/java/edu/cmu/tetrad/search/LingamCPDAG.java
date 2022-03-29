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

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;
import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the Lingam CPDAG algorithm as specified in Hoyer et al., "Causal discovery of linear acyclic models with
 * arbitrary distributions," UAI 2008. The test for normality used for residuals is Anderson-Darling, following ad.test
 * in the nortest package of R. The default alpha level is 0.05--that is, p values from AD below 0.05 are taken to
 * indicate nongaussianity.
 * <p>
 * It is assumed that the CPDAG is the result of a CPDAG search such as PC or GES. In any case, it is important that
 * the residuals be independent for ICA to work.
 *
 * @author Joseph Ramsey
 */
public class LingamCPDAG {
    private final Graph CPDAG;
    private final DataSet dataSet;
    private IKnowledge knowledge = new Knowledge2();
    private Graph bestDag;
    private Graph ngDagCPDAG;
    private double[] pValues;
    private double alpha = 0.05;
    private long timeLimit = -1;
    private final int numSamples = 200;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    //===============================CONSTRUCTOR============================//

    public LingamCPDAG(final Graph CPDAG, final DataSet dataSet)
            throws IllegalArgumentException {

        if (CPDAG == null) {
            throw new IllegalArgumentException("CPDAG must be specified.");
        }

        if (dataSet == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        this.CPDAG = CPDAG;
        this.dataSet = dataSet;
    }

    //===============================PUBLIC METHODS========================//

    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Graph search() {
        final long initialTime = System.currentTimeMillis();

        final Graph _CPDAG = GraphUtils.bidirectedToUndirected(getCPDAG());

        TetradLogger.getInstance().log("info", "Making list of all dags in CPDAG...");

        final List<Graph> dags = SearchGraphUtils.getAllGraphsByDirectingUndirectedEdges(_CPDAG);

        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Variables\n");
        final NumberFormat nf = new DecimalFormat("0.0000");

        if (dags.isEmpty()) {
            return null;
        }

        final Matrix data = getDataSet().getDoubleData();
        final List<Node> variables = getDataSet().getVariables();

        if (dags.size() == 0) {
            throw new IllegalArgumentException("The data set is empty.");
        }

        // Check that all the daga and the data contain the same variables.

        final List<Score> scores = new ArrayList<>();

        for (final Graph dag : dags) {
            scores.add(getScore(dag, data, variables));
        }

        double maxScore = 0.0;
        int maxj = -1;

        for (int j = 0; j < dags.size(); j++) {
            final double _score = scores.get(j).score;

            if (_score > maxScore) {
                maxScore = _score;
                maxj = j;
            }
        }

        final Graph dag = dags.get(maxj);
        this.bestDag = new EdgeListGraph(dags.get(maxj));
        this.pValues = scores.get(maxj).pvals;

        TetradLogger.getInstance().log("graph", "winning dag = " + dag);

        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Residuals\n");

        for (int j = 0; j < getDataSet().getNumColumns(); j++) {
            TetradLogger.getInstance().log("normalityTests", getDataSet().getVariable(j) + ": " + nf.format(scores.get(maxj).pvals[j]));
        }

//        System.out.println();

        final Graph ngDagCPDAG = SearchGraphUtils.cpdagFromDag(dag);

        final List<Node> nodes = ngDagCPDAG.getNodes();

        for (final Edge edge : ngDagCPDAG.getEdges()) {
            final Node node1 = edge.getNode1();
            final Node node2 = edge.getNode2();

            final double p1 = getPValues()[nodes.indexOf(node1)];
            final double p2 = getPValues()[nodes.indexOf(node2)];

            final boolean node1Nongaussian = p1 < getAlpha();
            final boolean node2Nongaussian = p2 < getAlpha();

            if (node1Nongaussian || node2Nongaussian) {
                if (!Edges.isUndirectedEdge(edge)) {
                    continue;
                }

                ngDagCPDAG.removeEdge(edge);
                ngDagCPDAG.addEdge(dag.getEdge(node1, node2));

                if (node1Nongaussian) {
                    TetradLogger.getInstance().log("edgeOrientations", node1 + " nongaussian ");
                }

                if (node2Nongaussian) {
                    TetradLogger.getInstance().log("edgeOrientations", node2 + " nongaussian ");
                }

                TetradLogger.getInstance().log("nongaussianOrientations", "Nongaussian orientation: " + dag.getEdge(node1, node2));
            }
        }

//        System.out.println();
//
//        System.out.println("Applying Meek rules.");
//        System.out.println();

        new MeekRules().orientImplied(ngDagCPDAG);

        this.ngDagCPDAG = ngDagCPDAG;

        TetradLogger.getInstance().log("graph", "Returning: " + ngDagCPDAG);
        return ngDagCPDAG;
    }

    //=============================PRIVATE METHODS=========================//

    private Score getScore(final Graph dag, final Matrix data, final List<Node> variables) {
//        System.out.println("Scoring DAG: " + dag);

        final Regression regression = new RegressionDataset(data, variables);

        final List<Node> nodes = dag.getNodes();
        double score = 0.0;
        final double[] pValues = new double[nodes.size()];
        final Matrix residuals = new Matrix(data.rows(), data.columns());

        for (int i = 0; i < nodes.size(); i++) {
            final Node _target = nodes.get(i);
            final List<Node> _regressors = dag.getParents(_target);
            final Node target = getVariable(variables, _target.getName());
            final List<Node> regressors = new ArrayList<>();

            for (final Node _regressor : _regressors) {
                final Node variable = getVariable(variables, _regressor.getName());
                regressors.add(variable);
            }

            final RegressionResult result = regression.regress(target, regressors);
            final Vector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
            final DoubleArrayList residualsArray = new DoubleArrayList(residualsColumn.toArray());

            final double mean = Descriptive.mean(residualsArray);
            final double std = Descriptive.standardDeviation(Descriptive.variance(residualsArray.size(),
                    Descriptive.sum(residualsArray), Descriptive.sumOfSquares(residualsArray)));

            for (int i2 = 0; i2 < residualsArray.size(); i2++) {
                residualsArray.set(i2, (residualsArray.get(i2) - mean) / std);
                residualsArray.set(i2, Math.abs(residualsArray.get(i2)));
            }

            final double _mean = Descriptive.mean(residualsArray);
            final double diff = _mean - Math.sqrt(2.0 / Math.PI);
            score += diff * diff;
        }

        for (int j = 0; j < residuals.columns(); j++) {
            final double[] x = residuals.getColumn(j).toArray();
            final double p = new AndersonDarlingTest(x).getP();
            pValues[j] = p;
        }

        return new Score(score, pValues);
    }

    public double[] getPValues() {
        return this.pValues;
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(final double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha is in range [0, 1]");
        }

        this.alpha = alpha;
    }

    public Graph getNgDagCPDAG() {
        return this.ngDagCPDAG;
    }

    public Graph getBestDag() {
        return this.bestDag;
    }

    public void setTimeLimit(final long timeLimit) {
        this.timeLimit = timeLimit;
    }

    private Graph getCPDAG() {
        return this.CPDAG;
    }

    private DataSet getDataSet() {
        return this.dataSet;
    }

    private IKnowledge getKnowledge() {
        return this.knowledge;
    }

    private long getTimeLimit() {
        return this.timeLimit;
    }

    private int getNumSamples() {
        return this.numSamples;
    }

    private static class Score {
        public Score(final double score, final double[] pvals) {
            this.score = score;
            this.pvals = pvals;
        }

        double score;
        double[] pvals;
    }

    private Node getVariable(final List<Node> variables, final String name) {
        for (final Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }
}


