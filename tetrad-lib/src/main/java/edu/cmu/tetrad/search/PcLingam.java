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

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;
import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Implements the PC-LiNGAM algorithm which first finds a CPDAG for the variables
 * and then uses a non-Gaussian orientation method to orient the undirected edges. The reference is as follows:
 *
 * <p>>Hoyer et al., "Causal discovery of linear acyclic models with arbitrary
 * distributions," UAI 2008.</p>
 *
 * <p>The test for normality used for residuals is Anderson-Darling, following 'ad.test'
 * in the nortest package of R. The default alpha level is 0.05--that is, p values from AD below 0.05 are taken to
 * indicate nongaussianity.</p>
 *
 * <p>It is assumed that the CPDAG is the result of a CPDAG search such as PC or GES. In any
 * case, it is important that the residuals be independent for ICA to work.</p>
 *
 * <p>This may be replaced by a more general algorithm that allows alternatives for the
 * CPDAG search and for the the non-Gaussian orientation method.</p>
 *
 * <p>This class is not configured to respect knowledge of forbidden and required
 * edges.</p>
 *
 * @author peterspirtes
 * @author patrickhoyer
 * @author josephramsey
 */
public class PcLingam {
    private final Graph cpdag;
    private final DataSet dataSet;
    private double[] pValues;
    private double alpha = 0.05;


    /**
     * Constructor.
     *
     * @param cpdag   The CPDAG whose unoriented edges are to be oriented.
     * @param dataSet Teh dataset to use.
     */
    public PcLingam(Graph cpdag, DataSet dataSet)
            throws IllegalArgumentException {

        if (cpdag == null) {
            throw new IllegalArgumentException("CPDAG must be specified.");
        }

        if (dataSet == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        this.cpdag = cpdag;
        this.dataSet = dataSet;
    }


    /**
     * Runs the search and returns the result graph.
     *
     * @return This graph.
     */
    public Graph search() {

        Graph _cpdag = GraphUtils.bidirectedToUndirected(getCpdag());

        TetradLogger.getInstance().log("info", "Making list of all dags in CPDAG...");

        List<Graph> dags = GraphSearchUtils.getAllGraphsByDirectingUndirectedEdges(_cpdag);

        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Variables\n");
        NumberFormat nf = new DecimalFormat("0.0000");

        if (dags.isEmpty()) {
            return null;
        }

        Matrix data = getDataSet().getDoubleData();
        List<Node> variables = getDataSet().getVariables();

        if (dags.size() == 0) {
            throw new IllegalArgumentException("The data set is empty.");
        }

        // Check that all the daga and the data contain the same variables.

        List<Score> scores = new ArrayList<>();

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
        this.pValues = scores.get(maxj).pvals;

        TetradLogger.getInstance().log("graph", "winning dag = " + dag);

        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Residuals\n");

        for (int j = 0; j < getDataSet().getNumColumns(); j++) {
            TetradLogger.getInstance().log("normalityTests", getDataSet().getVariable(j) + ": " + nf.format(scores.get(maxj).pvals[j]));
        }

        Graph ngDagCPDAG = GraphSearchUtils.cpdagFromDag(dag);

        List<Node> nodes = ngDagCPDAG.getNodes();

        for (Edge edge : ngDagCPDAG.getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            double p1 = getPValues()[nodes.indexOf(node1)];
            double p2 = getPValues()[nodes.indexOf(node2)];

            boolean node1Nongaussian = p1 < this.alpha;
            boolean node2Nongaussian = p2 < this.alpha;

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

        new MeekRules().orientImplied(ngDagCPDAG);

        TetradLogger.getInstance().log("graph", "Returning: " + ngDagCPDAG);
        return ngDagCPDAG;
    }

    /**
     * Returns the p-values of the search.
     *
     * @return This list as a double[] array.
     */
    public double[] getPValues() {
        return this.pValues;
    }

    /**
     * Sets the alpha level for the search.
     *
     * @param alpha This alpha level.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha is in range [0, 1]");
        }

        this.alpha = alpha;
    }


    private Score getScore(Graph dag, Matrix data, List<Node> variables) {
        Regression regression = new RegressionDataset(data, variables);

        List<Node> nodes = dag.getNodes();
        double score = 0.0;
        double[] pValues = new double[nodes.size()];
        Matrix residuals = new Matrix(data.getNumRows(), data.getNumColumns());

        for (int i = 0; i < nodes.size(); i++) {
            Node _target = nodes.get(i);
            List<Node> _regressors = dag.getParents(_target);
            Node target = getVariable(variables, _target.getName());
            List<Node> regressors = new ArrayList<>();

            for (Node _regressor : _regressors) {
                Node variable = getVariable(variables, _regressor.getName());
                regressors.add(variable);
            }

            RegressionResult result = regression.regress(target, regressors);
            Vector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
            DoubleArrayList residualsArray = new DoubleArrayList(residualsColumn.toArray());

            double mean = Descriptive.mean(residualsArray);
            double std = Descriptive.standardDeviation(Descriptive.variance(residualsArray.size(),
                    Descriptive.sum(residualsArray), Descriptive.sumOfSquares(residualsArray)));

            for (int i2 = 0; i2 < residualsArray.size(); i2++) {
                residualsArray.set(i2, (residualsArray.get(i2) - mean) / std);
                residualsArray.set(i2, FastMath.abs(residualsArray.get(i2)));
            }

            double _mean = Descriptive.mean(residualsArray);
            double diff = _mean - FastMath.sqrt(2.0 / FastMath.PI);
            score += diff * diff;
        }

        for (int j = 0; j < residuals.getNumColumns(); j++) {
            double[] x = residuals.getColumn(j).toArray();
            double p = new AndersonDarlingTest(x).getP();
            pValues[j] = p;
        }

        return new Score(score, pValues);
    }

    private Graph getCpdag() {
        return this.cpdag;
    }

    private DataSet getDataSet() {
        return this.dataSet;
    }

    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    private static class Score {
        double score;
        double[] pvals;

        public Score(double score, double[] pvals) {
            this.score = score;
            this.pvals = pvals;
        }
    }
}


