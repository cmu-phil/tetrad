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
public class LingamCPDAG2 {
    private final Graph CPDAG;
    private final List<DataSet> dataSets;
    private IKnowledge knowledge = new Knowledge2();
    private Graph bestDag;
    private Graph ngDagCPDAG;
    private double[] pValues;
    private double alpha = 0.05;
    private long timeLimit = -1;
    private final int numSamples = 200;
    private final List<Regression> regressions;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    private final List<Node> variables;
    private final ArrayList<Matrix> data;

    //===============================CONSTRUCTOR============================//

    public LingamCPDAG2(final Graph CPDAG, final List<DataSet> dataSets)
            throws IllegalArgumentException {

        if (dataSets == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        if (CPDAG == null) {
            throw new IllegalArgumentException("CPDAG must be specified.");
        }

        this.CPDAG = CPDAG;
        this.dataSets = dataSets;

        this.variables = dataSets.get(0).getVariables();

        this.data = new ArrayList<>();

        for (final DataSet dataSet : getDataSets()) {
            final Matrix _data = dataSet.getDoubleData();
            this.data.add(_data);
        }

        this.regressions = new ArrayList<>();

        for (final Matrix _data : this.data) {
            this.regressions.add(new RegressionDataset(_data, this.variables));
        }


    }

    //===============================PUBLIC METHODS========================//

    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Graph search() {
        final long initialTime = System.currentTimeMillis();

        final Graph _CPDAG = GraphUtils.bidirectedToUndirected(getCPDAG());

        TetradLogger.getInstance().log("info", "Making list of all dags in CPDAG...");

//        List<Graph> dags = SearchGraphUtils.getDagsInCPDAGMeek(_CPDAG, getKnowledge());
//        List<Graph> dags = SearchGraphUtils.generateCpdagDags(_CPDAG, false);
//        List<Dag> dags = SearchGraphUtils.getAllDagsByDirectingUndirectedEdges(_CPDAG);
        final List<Graph> dags = SearchGraphUtils.getAllGraphsByDirectingUndirectedEdges(_CPDAG);

        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Variables\n");
        final NumberFormat nf = new DecimalFormat("0.0000");

//        TetradMatrix m = getDataModel().getDoubleData();

//        for (int j = 0; j < getDataModel().getNumColumns(); j++) {
//            double[] x = m.viewColumn(j).toArray();
//            double p = new AndersonDarlingTest(x).getP();
//            System.out.println(getDataModel().getVariable(j) + ": " + nf.format(p));
//        }

//        System.out.println();

        if (dags.isEmpty()) {
//            System.out.println(getCPDAG());
            return null;
        }

        if (dags.size() == 0) {
            throw new IllegalArgumentException("The data set is empty.");
        }

        // Check that all the daga and the data contain the same variables.

        final List<Score> scores = new ArrayList<>();

        for (final Graph dag : dags) {
//            System.out.println(dag);
            scores.add(getScore(dag, this.data, this.variables));
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

//        TetradLogger.getInstance().log("graph", "winning dag = " + dag);
//
//        TetradLogger.getInstance().log("normalityTests", "Anderson Darling P value for Residuals\n");
//
//        for (int j = 0; j < getDataModel().getNumColumns(); j++) {
//            TetradLogger.getInstance().log("normalityTests", getDataModel().getVariable(j) + ": " + nf.format(scores.get(maxj).pvals[j]));
//        }

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

        System.out.println();

//        System.out.println("Applying Meek rules.");
//        System.out.println();

        final MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(true);
        meekRules.orientImplied(ngDagCPDAG);

        this.ngDagCPDAG = ngDagCPDAG;

        TetradLogger.getInstance().log("graph", "Returning: " + ngDagCPDAG);
        return ngDagCPDAG;
    }

    //=============================PRIVATE METHODS=========================//

    private Score getScore1(final Graph dag, final List<Matrix> data, final List<Node> variables) {
//        System.out.println("Scoring DAG: " + dag);

        final List<Regression> regressions = new ArrayList<>();

        for (final Matrix _data : data) {
            regressions.add(new RegressionDataset(_data, variables));
        }

        int totalSampleSize = 0;

        for (final Matrix _data : data) {
            totalSampleSize += _data.rows();
        }

        final int numCols = data.get(0).columns();

        final List<Node> nodes = dag.getNodes();
        double score = 0.0;
        final double[] pValues = new double[nodes.size()];
        final Matrix absoluteStandardizedResiduals = new Matrix(totalSampleSize, numCols);

        for (int i = 0; i < nodes.size(); i++) {
            final List<Double> _absoluteStandardizedResiduals = new ArrayList<>();

            for (int j = 0; j < data.size(); j++) {
                final Node _target = nodes.get(i);
                final List<Node> _regressors = dag.getParents(_target);
                final Node target = getVariable(variables, _target.getName());
                final List<Node> regressors = new ArrayList<>();

                for (final Node _regressor : _regressors) {
                    final Node variable = getVariable(variables, _regressor.getName());
                    regressors.add(variable);
                }

                final RegressionResult result = regressions.get(j).regress(target, regressors);
                final Vector residualsColumn = result.getResiduals();

                final DoubleArrayList _absoluteStandardizedResidualsColumn = new DoubleArrayList(residualsColumn.toArray());

                final double mean = Descriptive.mean(_absoluteStandardizedResidualsColumn);
                final double std = Descriptive.standardDeviation(Descriptive.variance(_absoluteStandardizedResidualsColumn.size(),
                        Descriptive.sum(_absoluteStandardizedResidualsColumn), Descriptive.sumOfSquares(_absoluteStandardizedResidualsColumn)));

                for (int i2 = 0; i2 < _absoluteStandardizedResidualsColumn.size(); i2++) {
                    _absoluteStandardizedResidualsColumn.set(i2, (_absoluteStandardizedResidualsColumn.get(i2) - mean) / std);
                    _absoluteStandardizedResidualsColumn.set(i2, Math.abs(_absoluteStandardizedResidualsColumn.get(i2)));
                }

                for (int k = 0; k < _absoluteStandardizedResidualsColumn.size(); k++) {
                    _absoluteStandardizedResiduals.add(_absoluteStandardizedResidualsColumn.get(k));
                }
            }

            final DoubleArrayList absoluteStandardResidualsList = new DoubleArrayList(absoluteStandardizedResiduals.getColumn(i).toArray());

            for (int k = 0; k < _absoluteStandardizedResiduals.size(); k++) {
                absoluteStandardizedResiduals.set(k, i, _absoluteStandardizedResiduals.get(k));
            }

            final double _mean = Descriptive.mean(absoluteStandardResidualsList);
            final double diff = _mean - Math.sqrt(2.0 / Math.PI);
            score += diff * diff;
        }

        for (int j = 0; j < absoluteStandardizedResiduals.columns(); j++) {
            final double[] x = absoluteStandardizedResiduals.getColumn(j).toArray();
            final double p = new AndersonDarlingTest(x).getP();
            pValues[j] = p;
        }

        return new Score(score, pValues);
    }

    private Score getScore2(final Graph dag, final List<Matrix> data, final List<Node> variables) {
//        System.out.println("Scoring DAG: " + dag);

        final List<Regression> regressions = new ArrayList<>();

        for (final Matrix _data : data) {
            regressions.add(new RegressionDataset(_data, variables));
        }

        int totalSampleSize = 0;

        for (final Matrix _data : data) {
            totalSampleSize += _data.rows();
        }

        final int numCols = data.get(0).columns();

        final List<Node> nodes = dag.getNodes();
        double score = 0.0;
        final double[] pValues = new double[nodes.size()];
        final Matrix residuals = new Matrix(totalSampleSize, numCols);

        for (int j = 0; j < nodes.size(); j++) {
            final List<Double> _residuals = new ArrayList<>();

            final Node _target = nodes.get(j);
            final List<Node> _regressors = dag.getParents(_target);
            final Node target = getVariable(variables, _target.getName());
            final List<Node> regressors = new ArrayList<>();

            for (final Node _regressor : _regressors) {
                final Node variable = getVariable(variables, _regressor.getName());
                regressors.add(variable);
            }

            for (int m = 0; m < data.size(); m++) {
                final RegressionResult result = regressions.get(m).regress(target, regressors);
                final Vector residualsSingleDataset = result.getResiduals();

                final DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

                final double mean = Descriptive.mean(_residualsSingleDataset);
                final double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                        Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

                for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
                }

                for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                    _residuals.add(_residualsSingleDataset.get(k));
                }
            }

            for (int k = 0; k < _residuals.size(); k++) {
                residuals.set(k, j, _residuals.get(k));
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            final DoubleArrayList f = new DoubleArrayList(residuals.getColumn(i).toArray());

            for (int j = 0; j < f.size(); j++) {
                f.set(j, Math.abs(f.get(j)));
            }

            final double _mean = Descriptive.mean(f);
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

    // Return the average score.

    private Score getScore(final Graph dag, final List<Matrix> data, final List<Node> variables) {
//        System.out.println("Scoring DAG: " + dag);

        int totalSampleSize = 0;

        for (final Matrix _data : data) {
            totalSampleSize += _data.rows();
        }

        final int numCols = data.get(0).columns();

        final List<Node> nodes = dag.getNodes();
        double score = 0.0;
        final double[] pValues = new double[nodes.size()];
        final Matrix residuals = new Matrix(totalSampleSize, numCols);

        for (int j = 0; j < nodes.size(); j++) {
            final List<Double> _residuals = new ArrayList<>();

            final Node _target = nodes.get(j);
            final List<Node> _regressors = dag.getParents(_target);
            final Node target = getVariable(variables, _target.getName());
            final List<Node> regressors = new ArrayList<>();

            for (final Node _regressor : _regressors) {
                final Node variable = getVariable(variables, _regressor.getName());
                regressors.add(variable);
            }

            for (int m = 0; m < data.size(); m++) {
                final RegressionResult result = this.regressions.get(m).regress(target, regressors);
                final Vector residualsSingleDataset = result.getResiduals();

                final DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

                final double mean = Descriptive.mean(_residualsSingleDataset);
                final double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                        Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

                for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
                }

                for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                    _residuals.add(_residualsSingleDataset.get(k));
                }

                final DoubleArrayList f = new DoubleArrayList(_residualsSingleDataset.elements());

                for (int k = 0; k < f.size(); k++) {
                    f.set(k, Math.abs(f.get(k)));
                }

                final double _mean = Descriptive.mean(f);
                final double diff = _mean - Math.sqrt(2.0 / Math.PI);
                score += diff * diff;

//                score += andersonDarlingPASquareStar(target, dag.getParents(target));
            }

            for (int k = 0; k < _residuals.size(); k++) {
                residuals.set(k, j, _residuals.get(k));
            }
        }

        for (int j = 0; j < residuals.columns(); j++) {
            final double[] x = residuals.getColumn(j).toArray();
            final double p = new AndersonDarlingTest(x).getP();
            pValues[j] = p;
        }

        return new Score(score, pValues);
    }

    private double andersonDarlingPASquareStar(final Node node, final List<Node> parents) {
        final List<Double> _residuals = new ArrayList<>();

        final Node _target = node;
        final List<Node> _regressors = parents;
        final Node target = getVariable(this.variables, _target.getName());
        final List<Node> regressors = new ArrayList<>();

        for (final Node _regressor : _regressors) {
            final Node variable = getVariable(this.variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < this.dataSets.size(); m++) {
            final RegressionResult result = this.regressions.get(m).regress(target, regressors);
            final Vector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            final DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

            final double mean = Descriptive.mean(_residualsSingleDataset);
            final double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                    Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

            for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean));
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2)));
            }

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _residuals.add(_residualsSingleDataset.get(k));
            }
        }

        final double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        final double p = new AndersonDarlingTest(_f).getASquaredStar();

        System.out.println("Anderson Darling p for " + node + " given " + parents + " = " + p);

        return p;
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

    private List<DataSet> getDataSets() {
        return this.dataSets;
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


