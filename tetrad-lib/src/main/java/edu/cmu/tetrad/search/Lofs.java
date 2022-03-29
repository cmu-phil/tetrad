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
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;

import java.util.*;

/**
 * LOFS = Ling Orientation Fixed Structure.
 *
 * @author Joseph Ramsey
 */
public class Lofs {
    private final Graph CPDAG;
    private final List<DataSet> dataSets;
    private double alpha = 0.05;
    private final ArrayList<Regression> regressions;
    private final List<Node> variables;
    private boolean r1Done = true;
    private boolean r2Done = true;
    private boolean strongR2;
    private boolean meekDone;
    private boolean r2Orient2Cycles = true;
    private boolean meanCenterResiduals;

    public enum Score {
        andersonDarling, skew, kurtosis, fifthMoment, absoluteValue,
        exp, expUnstandardized, expUnstandardizedInverted, other, logcosh, entropy
    }

    private Score score = Score.andersonDarling;

    //===============================CONSTRUCTOR============================//

    public Lofs(final Graph CPDAG, final List<DataSet> dataSets)
            throws IllegalArgumentException {

        if (CPDAG == null) {
            throw new IllegalArgumentException("CPDAG must be specified.");
        }

        if (dataSets == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        this.CPDAG = CPDAG;
        this.dataSets = dataSets;

        this.regressions = new ArrayList<>();
        this.variables = dataSets.get(0).getVariables();

        for (final DataSet dataSet : dataSets) {
            this.regressions.add(new RegressionDataset(dataSet));
        }
    }

    public Graph orient() {
        final Graph skeleton = GraphUtils.undirectedGraph(getCPDAG());
        final Graph graph = new EdgeListGraph(skeleton.getNodes());

        final List<Node> nodes = skeleton.getNodes();
//        Collections.shuffle(nodes);

        if (isR1Done()) {
            ruleR1(skeleton, graph, nodes);
        }

        for (final Edge edge : skeleton.getEdges()) {
            if (!graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        if (isR2Done()) {
            ruleR2(skeleton, graph);
        }

        if (isMeekDone()) {
            new MeekRules().orientImplied(graph);
        }

        return graph;
    }

    private void ruleR1(final Graph skeleton, final Graph graph, final List<Node> nodes) {
        for (final Node node : nodes) {
            final SortedMap<Double, String> scoreReports = new TreeMap<>();

            final List<Node> adj = skeleton.getAdjacentNodes(node);

            final DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;
            double maxScore = Double.NEGATIVE_INFINITY;
            List<Node> parents = null;

            while ((choice = gen.next()) != null) {
                final List<Node> _parents = GraphUtils.asList(choice, adj);

                final double score = score(node, _parents);
                scoreReports.put(-score, _parents.toString());

                if (score > maxScore) {
                    maxScore = score;
                    parents = _parents;
                }
            }

            for (final double score : scoreReports.keySet()) {
                TetradLogger.getInstance().log("score", "For " + node + " parents = " + scoreReports.get(score) + " score = " + -score);
            }

            TetradLogger.getInstance().log("score", "");

            if (parents == null) {
                continue;
            }

            if (normal(node, parents)) continue;

            for (final Node _node : adj) {
                if (parents.contains(_node)) {
                    final Edge parentEdge = Edges.directedEdge(_node, node);

                    if (!graph.containsEdge(parentEdge)) {
                        graph.addEdge(parentEdge);
                    }
                }
            }
        }
    }

    private void ruleR2(final Graph skeleton, final Graph graph) {
        final Set<Edge> edgeList1 = skeleton.getEdges();
//        Collections.shuffle(edgeList1);

        for (final Edge adj : edgeList1) {
            final Node x = adj.getNode1();
            final Node y = adj.getNode2();

            if (!isR2Orient2Cycles() && isTwoCycle(graph, x, y)) {
                continue;
            }

            if (!isTwoCycle(graph, x, y) && !isUndirected(graph, x, y)) {
                continue;
            }

            resolveOneEdgeMax(graph, x, y, isStrongR2(), new EdgeListGraph(graph));
        }
    }

    private boolean isTwoCycle(final Graph graph, final Node x, final Node y) {
        final List<Edge> edges = graph.getEdges(x, y);
        return edges.size() == 2;
    }

    private boolean isUndirected(final Graph graph, final Node x, final Node y) {
        final List<Edge> edges = graph.getEdges(x, y);
        if (edges.size() == 1) {
            final Edge edge = graph.getEdge(x, y);
            return Edges.isUndirectedEdge(edge);
        }

        return false;
    }

    private boolean normal(final Node node, final List<Node> parents) {
        if (getAlpha() > .999) {
            return false;
        }

        return pValue(node, parents) > getAlpha();
    }

    private void resolveOneEdgeMax(final Graph graph, Node x, Node y, final boolean strong, final Graph oldGraph) {
        if (RandomUtil.getInstance().nextDouble() > 0.5) {
            final Node temp = x;
            x = y;
            y = temp;
        }

        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        final SortedMap<Double, String> scoreReports = new TreeMap<>();

        final List<Node> neighborsx = graph.getAdjacentNodes(x);
        neighborsx.remove(y);

        double max = Double.NEGATIVE_INFINITY;
        boolean left = false;
        boolean right = false;

        final DepthChoiceGenerator genx = new DepthChoiceGenerator(neighborsx.size(), neighborsx.size());
        int[] choicex;

        while ((choicex = genx.next()) != null) {
            final List<Node> condxMinus = GraphUtils.asList(choicex, neighborsx);

            final List<Node> condxPlus = new ArrayList<>(condxMinus);
            condxPlus.add(y);

            final double xPlus = score(x, condxPlus);
            final double xMinus = score(x, condxMinus);

            final List<Node> neighborsy = graph.getAdjacentNodes(y);
            neighborsy.remove(x);

            final DepthChoiceGenerator geny = new DepthChoiceGenerator(neighborsy.size(), neighborsy.size());
            int[] choicey;

            while ((choicey = geny.next()) != null) {
                final List<Node> condyMinus = GraphUtils.asList(choicey, neighborsy);

//                List<Node> parentsY = oldGraph.getParents(y);
//                parentsY.remove(x);
//                if (!condyMinus.containsAll(parentsY)) {
//                    continue;
//                }

                final List<Node> condyPlus = new ArrayList<>(condyMinus);
                condyPlus.add(x);

                final double yPlus = score(y, condyPlus);
                final double yMinus = score(y, condyMinus);

                // Checking them all at once is expensive but avoids lexical ordering problems in the algorithm.
                if (normal(y, condyPlus) || normal(x, condxMinus) || normal(x, condxPlus) || normal(y, condyMinus)) {
                    continue;
                }

                final double delta = 0.0;

                if (strong) {
                    if (yPlus <= xPlus + delta && xMinus <= yMinus + delta) {
                        final double score = combinedScore(xPlus, yMinus);

                        if (yPlus <= yMinus + delta && xMinus <= xPlus + delta) {
                            final StringBuilder builder = new StringBuilder();

                            builder.append("\nStrong " + y + "->" + x + " " + score);
                            builder.append("\n   Parents(" + x + ") = " + condxMinus);
                            builder.append("\n   Parents(" + y + ") = " + condyMinus);

                            scoreReports.put(-score, builder.toString());

                            if (score > max) {
                                max = score;
                                left = true;
                                right = false;
                            }
                        } else {
                            final StringBuilder builder = new StringBuilder();

                            builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                            builder.append("\n   Parents(" + x + ") = " + condxMinus);
                            builder.append("\n   Parents(" + y + ") = " + condyMinus);

                            scoreReports.put(-score, builder.toString());
                        }
                    } else if (xPlus <= yPlus + delta && yMinus <= xMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        if (yMinus <= yPlus + delta && xPlus <= xMinus + delta) {
                            final StringBuilder builder = new StringBuilder();

                            builder.append("\nStrong " + x + "->" + y + " " + score);
                            builder.append("\n   Parents(" + x + ") = " + condxMinus);
                            builder.append("\n   Parents(" + y + ") = " + condyMinus);

                            scoreReports.put(-score, builder.toString());

                            if (score > max) {
                                max = score;
                                left = false;
                                right = true;
                            }
                        } else {
                            final StringBuilder builder = new StringBuilder();

                            builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                            builder.append("\n   Parents(" + x + ") = " + condxMinus);
                            builder.append("\n   Parents(" + y + ") = " + condyMinus);

                            scoreReports.put(-score, builder.toString());
                        }
                    } else if (yPlus <= xPlus + delta && yMinus <= xMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());
                    }
                } else {
                    if (yPlus <= xPlus + delta && xMinus <= yMinus + delta) {
                        final double score = combinedScore(xPlus, yMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nWeak " + y + "->" + x + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());

                        if (score > max) {
                            max = score;
                            left = true;
                            right = false;
                        }
                    } else if (xPlus <= yPlus + delta && yMinus <= xMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nWeak " + x + "->" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());

                        if (score > max) {
                            max = score;
                            left = false;
                            right = true;
                        }
                    } else if (yPlus <= xPlus + delta && yMinus <= xMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());
                    }
                }
            }
        }

        for (final double score : scoreReports.keySet()) {
            TetradLogger.getInstance().log("info", scoreReports.get(score));
        }

        graph.removeEdges(x, y);

        if (left) {
            graph.addDirectedEdge(y, x);
        }

        if (right) {
            graph.addDirectedEdge(x, y);
        }

        if (!graph.isAdjacentTo(x, y)) {
            graph.addUndirectedEdge(x, y);
        }
    }

    private double combinedScore(final double score1, final double score2) {
        return score1 + score2;
    }

    private double score(final Node y, final List<Node> parents) {
        if (this.score == Score.andersonDarling) {
            return andersonDarlingPASquareStar(y, parents);
        } else if (this.score == Score.kurtosis) {
            return Math.abs(StatUtils.kurtosis(residual(y, parents)));
        } else if (this.score == Score.skew) {
            return Math.abs(StatUtils.skewness(residual(y, parents)));
        } else if (this.score == Score.fifthMoment) {
            return Math.abs(StatUtils.standardizedFifthMoment(residual(y, parents)));
        } else if (this.score == Score.absoluteValue) {
            return localScoreA(y, parents);
        }

        throw new IllegalStateException();
    }

    //=============================PRIVATE METHODS=========================//

    private double localScoreA(final Node node, final List<Node> parents) {
        double score = 0.0;

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
                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
            }

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _residuals.add(_residualsSingleDataset.get(k));
            }
        }

        final double[] _f = new double[_residuals.size()];


        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        final DoubleArrayList f = new DoubleArrayList(_f);

        for (int k = 0; k < _residuals.size(); k++) {
            f.set(k, Math.abs(f.get(k)));
        }

        final double _mean = Descriptive.mean(f);
        final double diff = _mean - Math.sqrt(2.0 / Math.PI);
        score += diff * diff;

        return score;
    }

    private double localScoreB(final Node node, final List<Node> parents) {

        double score = 0.0;
        double maxScore = Double.NEGATIVE_INFINITY;

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
            final DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            final double mean = Descriptive.mean(_residualsSingleDataset);
            final double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                    Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

            for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
            }

            final double[] _f = new double[_residualsSingleDataset.size()];

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _f[k] = _residualsSingleDataset.get(k);
            }

            final DoubleArrayList f = new DoubleArrayList(_f);

            for (int k = 0; k < f.size(); k++) {
                f.set(k, Math.abs(f.get(k)));
            }

            final double _mean = Descriptive.mean(f);
            final double diff = _mean - Math.sqrt(2.0 / Math.PI);
            score += diff * diff;

            if (score > maxScore) {
                maxScore = score;
            }
        }


        final double avg = score / this.dataSets.size();

        return avg;
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

            // By centering the individual residual columns, all moments of the mixture become weighted averages of the moments
            // of the individual columns. http://en.wikipedia.org/wiki/Mixture_distribution#Finite_and_countable_mixtures
            for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2)) / std);
                if (isMeanCenterResiduals()) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean));
                }
            }

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _residuals.add(_residualsSingleDataset.get(k));
            }
        }

        final double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        return new AndersonDarlingTest(_f).getASquaredStar();
    }

    private double andersonDarlingPASquareStarB(final Node node, final List<Node> parents) {
        final List<Double> _residuals = new ArrayList<>();

        final Node _target = node;
        final List<Node> _regressors = parents;
        final Node target = getVariable(this.variables, _target.getName());
        final List<Node> regressors = new ArrayList<>();

        for (final Node _regressor : _regressors) {
            final Node variable = getVariable(this.variables, _regressor.getName());
            regressors.add(variable);
        }

        double sum = 0.0;

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

            // By centering the individual residual columns, all moments of the mixture become weighted averages of the moments
            // of the individual columns. http://en.wikipedia.org/wiki/Mixture_distribution#Finite_and_countable_mixtures
            for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2)) / std);
                if (isMeanCenterResiduals()) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean));
                }
            }

            final double[] _f = new double[_residuals.size()];

            for (int k = 0; k < _residuals.size(); k++) {
                _f[k] = _residuals.get(k);
            }

            sum += new AndersonDarlingTest(_f).getASquaredStar();
        }

        return sum / this.dataSets.size();
    }

    private double pValue(final Node node, final List<Node> parents) {
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
                if (isMeanCenterResiduals()) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean));
                }
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

        return new AndersonDarlingTest(_f).getP();
    }

    private double[] residual(final Node node, final List<Node> parents) {
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
//            double std = Descriptive.sd(Descriptive.variance(_residualsSingleDataset.size(),
//                    Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

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

        return _f;
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

    private Graph getCPDAG() {
        return this.CPDAG;
    }

    private Node getVariable(final List<Node> variables, final String name) {
        for (final Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    public boolean isR1Done() {
        return this.r1Done;
    }

    public void setR1Done(final boolean r1Done) {
        this.r1Done = r1Done;
    }

    public boolean isR2Done() {
        return this.r2Done;
    }

    public void setR2Done(final boolean r2Done) {
        this.r2Done = r2Done;
    }

    public boolean isMeekDone() {
        return this.meekDone;
    }

    public void setMeekDone(final boolean meekDone) {
        this.meekDone = meekDone;
    }

    public boolean isStrongR2() {
        return this.strongR2;
    }

    public void setStrongR2(final boolean strongR2) {
        this.strongR2 = strongR2;
    }

    public void setR2Orient2Cycles(final boolean r2Orient2Cycles) {
        this.r2Orient2Cycles = r2Orient2Cycles;
    }

    public boolean isR2Orient2Cycles() {
        return this.r2Orient2Cycles;
    }

    public Score getScore() {
        return this.score;
    }

    public void setScore(final Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
    }

    public boolean isMeanCenterResiduals() {
        return this.meanCenterResiduals;
    }

    public void setMeanCenterResiduals(final boolean meanCenterResiduals) {
        this.meanCenterResiduals = meanCenterResiduals;
    }

}


