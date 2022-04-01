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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;

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
public class LingamPattern2 {
    private final Graph cpdag;
    private final List<DataSet> dataSets;
    private double[] pValues;
    private double alpha = 0.05;
    private final List<Regression> regressions;

    private final List<Node> variables;
    private final ArrayList<Matrix> data;

    //===============================CONSTRUCTOR============================//

    public LingamPattern2(Graph cpdag, List<DataSet> dataSets)
            throws IllegalArgumentException {

        if (dataSets == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        if (cpdag == null) {
            throw new IllegalArgumentException("CPDAG must be specified.");
        }

        this.cpdag = cpdag;
        this.dataSets = dataSets;

        this.variables = dataSets.get(0).getVariables();

        this.data = new ArrayList<>();

        for (DataSet dataSet : getDataSets()) {
            Matrix _data = dataSet.getDoubleData();
            this.data.add(_data);
        }

        this.regressions = new ArrayList<>();

        for (Matrix _data : this.data) {
            this.regressions.add(new RegressionDataset(_data, this.variables));
        }
    }

    //===============================PUBLIC METHODS========================//

    public Graph search() {
        Graph _cpdag = GraphUtils.bidirectedToUndirected(getCpdag());

        TetradLogger.getInstance().log("info", "Making list of all dags in CPDAG...");

        List<Graph> dags = SearchGraphUtils.getAllGraphsByDirectingUndirectedEdges(_cpdag);

        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Variables\n");

        if (dags.isEmpty()) {
            return null;
        }

        List<Score> scores = new ArrayList<>();

        for (Graph dag : dags) {
            scores.add(getScore(dag, this.data, this.variables));
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

        Graph ngDagCPDAG = SearchGraphUtils.cpdagFromDag(dag);

        List<Node> nodes = ngDagCPDAG.getNodes();

        for (Edge edge : ngDagCPDAG.getEdges()) {
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

        System.out.println();

        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(true);
        meekRules.orientImplied(ngDagCPDAG);

        TetradLogger.getInstance().log("graph", "Returning: " + ngDagCPDAG);
        return ngDagCPDAG;
    }

    //=============================PRIVATE METHODS=========================//

    // Return the average score.

    private Score getScore(Graph dag, List<Matrix> data, List<Node> variables) {
        int totalSampleSize = 0;

        for (Matrix _data : data) {
            totalSampleSize += _data.rows();
        }

        int numCols = data.get(0).columns();

        List<Node> nodes = dag.getNodes();
        double score = 0.0;
        double[] pValues = new double[nodes.size()];
        Matrix residuals = new Matrix(totalSampleSize, numCols);

        for (int j = 0; j < nodes.size(); j++) {
            List<Double> _residuals = new ArrayList<>();

            Node _target = nodes.get(j);
            List<Node> _regressors = dag.getParents(_target);
            Node target = getVariable(variables, _target.getName());
            List<Node> regressors = new ArrayList<>();

            for (Node _regressor : _regressors) {
                Node variable = getVariable(variables, _regressor.getName());
                regressors.add(variable);
            }

            for (int m = 0; m < data.size(); m++) {
                RegressionResult result = this.regressions.get(m).regress(target, regressors);
                Vector residualsSingleDataset = result.getResiduals();

                DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

                double mean = Descriptive.mean(_residualsSingleDataset);
                double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                        Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

                for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
                }

                for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                    _residuals.add(_residualsSingleDataset.get(k));
                }

                DoubleArrayList f = new DoubleArrayList(_residualsSingleDataset.elements());

                for (int k = 0; k < f.size(); k++) {
                    f.set(k, Math.abs(f.get(k)));
                }

                double _mean = Descriptive.mean(f);
                double diff = _mean - Math.sqrt(2.0 / Math.PI);
                score += diff * diff;
            }

            for (int k = 0; k < _residuals.size(); k++) {
                residuals.set(k, j, _residuals.get(k));
            }
        }

        for (int j = 0; j < residuals.columns(); j++) {
            double[] x = residuals.getColumn(j).toArray();
            double p = new AndersonDarlingTest(x).getP();
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

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha is in range [0, 1]");
        }

        this.alpha = alpha;
    }

    private Graph getCpdag() {
        return this.cpdag;
    }

    private List<DataSet> getDataSets() {
        return this.dataSets;
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


