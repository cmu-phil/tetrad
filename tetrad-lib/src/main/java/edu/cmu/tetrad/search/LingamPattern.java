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
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the Lingam Pattern algorithm as specified in Hoyer et al., "Causal discovery of linear acyclic models with
 * arbitrary distributions," UAI 2008. The test for normality used for residuals is Anderson-Darling, following ad.test
 * in the nortest package of R. The default alpha level is 0.05--that is, p values from AD below 0.05 are taken to
 * indicate nongaussianity.
 * <p>
 * It is assumed that the pattern is the result of a pattern search such as PC or GES. In any case, it is important that
 * the residuals be independent for ICA to work.
 *
 * @author Joseph Ramsey
 */
public class LingamPattern {
    private Graph pattern;
    private DataSet dataSet;
    private IKnowledge knowledge = new Knowledge2();
    private Graph bestDag;
    private Graph ngDagPattern;
    private double[] pValues;
    private double alpha = 0.05;
    private long timeLimit = -1;
    private int numSamples = 200;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    //===============================CONSTRUCTOR============================//

    public LingamPattern(Graph pattern, DataSet dataSet)
            throws IllegalArgumentException {

        if (pattern == null) {
            throw new IllegalArgumentException("Pattern must be specified.");
        }

        if (dataSet == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        this.pattern = pattern;
        this.dataSet = dataSet;
    }

    //===============================PUBLIC METHODS========================//

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Graph search() {
        long initialTime = System.currentTimeMillis();

        Graph _pattern = GraphUtils.bidirectedToUndirected(getPattern());

        TetradLogger.getInstance().log("info", "Making list of all dags in pattern...");

        List<Graph> dags = SearchGraphUtils.getAllGraphsByDirectingUndirectedEdges(_pattern);

        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Variables\n");
        NumberFormat nf = new DecimalFormat("0.0000");

        if (dags.isEmpty()) {
            return null;
        }

        TetradMatrix data = getDataSet().getDoubleData();
        List<Node> variables = getDataSet().getVariables();

        if (dags.size() == 0) {
            throw new IllegalArgumentException("The data set is empty.");
        }

        // Check that all the daga and the data contain the same variables.

        List<Score> scores = new ArrayList<Score>();

        for (Graph dag : dags) {
            scores.add(getScore(dag, data, variables));
        }

        double maxScore = 0.0;
        int maxj = -1;

        for (int j = 0; j < dags.size(); j++) {
            double _score = scores.get(j).score;

            if (_score > maxScore) {
                maxScore = _score;
                maxj = j;
            }
        }

        Graph dag = dags.get(maxj);
        this.bestDag = new EdgeListGraph(dags.get(maxj));
        this.pValues = scores.get(maxj).pvals;

        TetradLogger.getInstance().log("graph", "winning dag = " + dag);

        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Residuals\n");

        for (int j = 0; j < getDataSet().getNumColumns(); j++) {
            TetradLogger.getInstance().log("normalityTests", getDataSet().getVariable(j) + ": " + nf.format(scores.get(maxj).pvals[j]));
        }

//        System.out.println();

        Graph ngDagPattern = SearchGraphUtils.patternFromDag(dag);

        List<Node> nodes = ngDagPattern.getNodes();

        for (Edge edge : ngDagPattern.getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            double p1 = getPValues()[nodes.indexOf(node1)];
            double p2 = getPValues()[nodes.indexOf(node2)];

            boolean node1Nongaussian = p1 < getAlpha();
            boolean node2Nongaussian = p2 < getAlpha();

            if (node1Nongaussian || node2Nongaussian) {
                if (!Edges.isUndirectedEdge(edge)) {
                    continue;
                }

                ngDagPattern.removeEdge(edge);
                ngDagPattern.addEdge(dag.getEdge(node1, node2));

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

        new MeekRules().orientImplied(ngDagPattern);

        this.ngDagPattern = ngDagPattern;

        TetradLogger.getInstance().log("graph", "Returning: " + ngDagPattern);
        return ngDagPattern;
    }

    //=============================PRIVATE METHODS=========================//

    private Score getScore(Graph dag, TetradMatrix data, List<Node> variables) {
//        System.out.println("Scoring DAG: " + dag);

        Regression regression = new RegressionDataset(data, variables);

        List<Node> nodes = dag.getNodes();
        double score = 0.0;
        double[] pValues = new double[nodes.size()];
        TetradMatrix residuals = new TetradMatrix(data.rows(), data.columns());

        for (int i = 0; i < nodes.size(); i++) {
            Node _target = nodes.get(i);
            List<Node> _regressors = dag.getParents(_target);
            Node target = getVariable(variables, _target.getName());
            List<Node> regressors = new ArrayList<Node>();

            for (Node _regressor : _regressors) {
                Node variable = getVariable(variables, _regressor.getName());
                regressors.add(variable);
            }

            RegressionResult result = regression.regress(target, regressors);
            TetradVector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
            DoubleArrayList residualsArray = new DoubleArrayList(residualsColumn.toArray());

            double mean = Descriptive.mean(residualsArray);
            double std = Descriptive.standardDeviation(Descriptive.variance(residualsArray.size(),
                    Descriptive.sum(residualsArray), Descriptive.sumOfSquares(residualsArray)));

            for (int i2 = 0; i2 < residualsArray.size(); i2++) {
                residualsArray.set(i2, (residualsArray.get(i2) - mean) / std);
                residualsArray.set(i2, Math.abs(residualsArray.get(i2)));
            }

            double _mean = Descriptive.mean(residualsArray);
            double diff = _mean - Math.sqrt(2.0 / Math.PI);
            score += diff * diff;
        }

        for (int j = 0; j < residuals.columns(); j++) {
            double[] x = residuals.getColumn(j).toArray();
            double p = new AndersonDarlingTest(x).getP();
            pValues[j] = p;
        }

        return new Score(score, pValues);
    }

    public double[] getPValues() {
        return pValues;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha is in range [0, 1]");
        }

        this.alpha = alpha;
    }

    public Graph getNgDagPattern() {
        return ngDagPattern;
    }

    public Graph getBestDag() {
        return bestDag;
    }

    public void setTimeLimit(long timeLimit) {
        this.timeLimit = timeLimit;
    }

    private Graph getPattern() {
        return pattern;
    }

    private DataSet getDataSet() {
        return dataSet;
    }

    private IKnowledge getKnowledge() {
        return knowledge;
    }

    private long getTimeLimit() {
        return timeLimit;
    }

    private int getNumSamples() {
        return numSamples;
    }

    private static class Score {
        public Score(double score, double[] pvals) {
            this.score = score;
            this.pvals = pvals;
        }

        double score;
        double[] pvals;
    }

    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }
}


