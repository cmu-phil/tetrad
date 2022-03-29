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

    public Lofs(Graph CPDAG, List<DataSet> dataSets)
            throws IllegalArgumentException {

        if (CPDAG == null) {
            throw new IllegalArgumentException("CPDAG must be specified.");
        }

        if (dataSets == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        this.CPDAG = CPDAG;
        this.dataSets = dataSets;

        regressions = new ArrayList<>();
        variables = dataSets.get(0).getVariables();

        for (DataSet dataSet : dataSets) {
            regressions.add(new RegressionDataset(dataSet));
        }
    }

    public Graph orient() {
        Graph skeleton = GraphUtils.undirectedGraph(this.getCPDAG());
        Graph graph = new EdgeListGraph(skeleton.getNodes());

        List<Node> nodes = skeleton.getNodes();
//        Collections.shuffle(nodes);

        if (this.isR1Done()) {
            this.ruleR1(skeleton, graph, nodes);
        }

        for (Edge edge : skeleton.getEdges()) {
            if (!graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        if (this.isR2Done()) {
            this.ruleR2(skeleton, graph);
        }

        if (this.isMeekDone()) {
            new MeekRules().orientImplied(graph);
        }

        return graph;
    }

    private void ruleR1(Graph skeleton, Graph graph, List<Node> nodes) {
        for (Node node : nodes) {
            SortedMap<Double, String> scoreReports = new TreeMap<>();

            List<Node> adj = skeleton.getAdjacentNodes(node);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;
            double maxScore = Double.NEGATIVE_INFINITY;
            List<Node> parents = null;

            while ((choice = gen.next()) != null) {
                List<Node> _parents = GraphUtils.asList(choice, adj);

                double score = this.score(node, _parents);
                scoreReports.put(-score, _parents.toString());

                if (score > maxScore) {
                    maxScore = score;
                    parents = _parents;
                }
            }

            for (double score : scoreReports.keySet()) {
                TetradLogger.getInstance().log("score", "For " + node + " parents = " + scoreReports.get(score) + " score = " + -score);
            }

            TetradLogger.getInstance().log("score", "");

            if (parents == null) {
                continue;
            }

            if (this.normal(node, parents)) continue;

            for (Node _node : adj) {
                if (parents.contains(_node)) {
                    Edge parentEdge = Edges.directedEdge(_node, node);

                    if (!graph.containsEdge(parentEdge)) {
                        graph.addEdge(parentEdge);
                    }
                }
            }
        }
    }

    private void ruleR2(Graph skeleton, Graph graph) {
        Set<Edge> edgeList1 = skeleton.getEdges();
//        Collections.shuffle(edgeList1);

        for (Edge adj : edgeList1) {
            Node x = adj.getNode1();
            Node y = adj.getNode2();

            if (!this.isR2Orient2Cycles() && this.isTwoCycle(graph, x, y)) {
                continue;
            }

            if (!this.isTwoCycle(graph, x, y) && !this.isUndirected(graph, x, y)) {
                continue;
            }

            this.resolveOneEdgeMax(graph, x, y, this.isStrongR2(), new EdgeListGraph(graph));
        }
    }

    private boolean isTwoCycle(Graph graph, Node x, Node y) {
        List<Edge> edges = graph.getEdges(x, y);
        return edges.size() == 2;
    }

    private boolean isUndirected(Graph graph, Node x, Node y) {
        List<Edge> edges = graph.getEdges(x, y);
        if (edges.size() == 1) {
            Edge edge = graph.getEdge(x, y);
            return Edges.isUndirectedEdge(edge);
        }

        return false;
    }

    private boolean normal(Node node, List<Node> parents) {
        if (this.getAlpha() > .999) {
            return false;
        }

        return this.pValue(node, parents) > this.getAlpha();
    }

    private void resolveOneEdgeMax(Graph graph, Node x, Node y, boolean strong, Graph oldGraph) {
        if (RandomUtil.getInstance().nextDouble() > 0.5) {
            Node temp = x;
            x = y;
            y = temp;
        }

        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        SortedMap<Double, String> scoreReports = new TreeMap<>();

        List<Node> neighborsx = graph.getAdjacentNodes(x);
        neighborsx.remove(y);

        double max = Double.NEGATIVE_INFINITY;
        boolean left = false;
        boolean right = false;

        DepthChoiceGenerator genx = new DepthChoiceGenerator(neighborsx.size(), neighborsx.size());
        int[] choicex;

        while ((choicex = genx.next()) != null) {
            List<Node> condxMinus = GraphUtils.asList(choicex, neighborsx);

            List<Node> condxPlus = new ArrayList<>(condxMinus);
            condxPlus.add(y);

            double xPlus = this.score(x, condxPlus);
            double xMinus = this.score(x, condxMinus);

            List<Node> neighborsy = graph.getAdjacentNodes(y);
            neighborsy.remove(x);

            DepthChoiceGenerator geny = new DepthChoiceGenerator(neighborsy.size(), neighborsy.size());
            int[] choicey;

            while ((choicey = geny.next()) != null) {
                List<Node> condyMinus = GraphUtils.asList(choicey, neighborsy);

//                List<Node> parentsY = oldGraph.getParents(y);
//                parentsY.remove(x);
//                if (!condyMinus.containsAll(parentsY)) {
//                    continue;
//                }

                List<Node> condyPlus = new ArrayList<>(condyMinus);
                condyPlus.add(x);

                double yPlus = this.score(y, condyPlus);
                double yMinus = this.score(y, condyMinus);

                // Checking them all at once is expensive but avoids lexical ordering problems in the algorithm.
                if (this.normal(y, condyPlus) || this.normal(x, condxMinus) || this.normal(x, condxPlus) || this.normal(y, condyMinus)) {
                    continue;
                }

                final double delta = 0.0;

                if (strong) {
                    if (yPlus <= xPlus + delta && xMinus <= yMinus + delta) {
                        double score = this.combinedScore(xPlus, yMinus);

                        if (yPlus <= yMinus + delta && xMinus <= xPlus + delta) {
                            StringBuilder builder = new StringBuilder();

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
                            StringBuilder builder = new StringBuilder();

                            builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                            builder.append("\n   Parents(" + x + ") = " + condxMinus);
                            builder.append("\n   Parents(" + y + ") = " + condyMinus);

                            scoreReports.put(-score, builder.toString());
                        }
                    } else if (xPlus <= yPlus + delta && yMinus <= xMinus + delta) {
                        double score = this.combinedScore(yPlus, xMinus);

                        if (yMinus <= yPlus + delta && xPlus <= xMinus + delta) {
                            StringBuilder builder = new StringBuilder();

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
                            StringBuilder builder = new StringBuilder();

                            builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                            builder.append("\n   Parents(" + x + ") = " + condxMinus);
                            builder.append("\n   Parents(" + y + ") = " + condyMinus);

                            scoreReports.put(-score, builder.toString());
                        }
                    } else if (yPlus <= xPlus + delta && yMinus <= xMinus + delta) {
                        double score = this.combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        double score = this.combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());
                    }
                } else {
                    if (yPlus <= xPlus + delta && xMinus <= yMinus + delta) {
                        double score = this.combinedScore(xPlus, yMinus);

                        StringBuilder builder = new StringBuilder();

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
                        double score = this.combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

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
                        double score = this.combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        double score = this.combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge " + x + "--" + y + " " + score);
                        builder.append("\n   Parents(" + x + ") = " + condxMinus);
                        builder.append("\n   Parents(" + y + ") = " + condyMinus);

                        scoreReports.put(-score, builder.toString());
                    }
                }
            }
        }

        for (double score : scoreReports.keySet()) {
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

    private double combinedScore(double score1, double score2) {
        return score1 + score2;
    }

    private double score(Node y, List<Node> parents) {
        if (score == Score.andersonDarling) {
            return this.andersonDarlingPASquareStar(y, parents);
        } else if (score == Score.kurtosis) {
            return Math.abs(StatUtils.kurtosis(this.residual(y, parents)));
        } else if (score == Score.skew) {
            return Math.abs(StatUtils.skewness(this.residual(y, parents)));
        } else if (score == Score.fifthMoment) {
            return Math.abs(StatUtils.standardizedFifthMoment(this.residual(y, parents)));
        } else if (score == Score.absoluteValue) {
            return this.localScoreA(y, parents);
        }

        throw new IllegalStateException();
    }

    //=============================PRIVATE METHODS=========================//

    private double localScoreA(Node node, List<Node> parents) {
        double score = 0.0;

        List<Double> _residuals = new ArrayList<>();

        Node _target = node;
        List<Node> _regressors = parents;
        Node target = this.getVariable(variables, _target.getName());
        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : _regressors) {
            Node variable = this.getVariable(variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < dataSets.size(); m++) {
            RegressionResult result = regressions.get(m).regress(target, regressors);
            Vector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

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
        }

        double[] _f = new double[_residuals.size()];


        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        DoubleArrayList f = new DoubleArrayList(_f);

        for (int k = 0; k < _residuals.size(); k++) {
            f.set(k, Math.abs(f.get(k)));
        }

        double _mean = Descriptive.mean(f);
        double diff = _mean - Math.sqrt(2.0 / Math.PI);
        score += diff * diff;

        return score;
    }

    private double localScoreB(Node node, List<Node> parents) {

        double score = 0.0;
        double maxScore = Double.NEGATIVE_INFINITY;

        Node _target = node;
        List<Node> _regressors = parents;
        Node target = this.getVariable(variables, _target.getName());
        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : _regressors) {
            Node variable = this.getVariable(variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < dataSets.size(); m++) {
            RegressionResult result = regressions.get(m).regress(target, regressors);
            Vector residualsSingleDataset = result.getResiduals();
            DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            double mean = Descriptive.mean(_residualsSingleDataset);
            double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                    Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

            for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
            }

            double[] _f = new double[_residualsSingleDataset.size()];

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _f[k] = _residualsSingleDataset.get(k);
            }

            DoubleArrayList f = new DoubleArrayList(_f);

            for (int k = 0; k < f.size(); k++) {
                f.set(k, Math.abs(f.get(k)));
            }

            double _mean = Descriptive.mean(f);
            double diff = _mean - Math.sqrt(2.0 / Math.PI);
            score += diff * diff;

            if (score > maxScore) {
                maxScore = score;
            }
        }


        double avg = score / dataSets.size();

        return avg;
    }

    private double andersonDarlingPASquareStar(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<>();

        Node _target = node;
        List<Node> _regressors = parents;
        Node target = this.getVariable(variables, _target.getName());
        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : _regressors) {
            Node variable = this.getVariable(variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < dataSets.size(); m++) {
            RegressionResult result = regressions.get(m).regress(target, regressors);
            Vector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

            double mean = Descriptive.mean(_residualsSingleDataset);
            double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                    Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

            // By centering the individual residual columns, all moments of the mixture become weighted averages of the moments
            // of the individual columns. http://en.wikipedia.org/wiki/Mixture_distribution#Finite_and_countable_mixtures
            for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2)) / std);
                if (this.isMeanCenterResiduals()) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean));
                }
            }

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _residuals.add(_residualsSingleDataset.get(k));
            }
        }

        double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        return new AndersonDarlingTest(_f).getASquaredStar();
    }

    private double andersonDarlingPASquareStarB(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<>();

        Node _target = node;
        List<Node> _regressors = parents;
        Node target = this.getVariable(variables, _target.getName());
        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : _regressors) {
            Node variable = this.getVariable(variables, _regressor.getName());
            regressors.add(variable);
        }

        double sum = 0.0;

        DATASET:
        for (int m = 0; m < dataSets.size(); m++) {
            RegressionResult result = regressions.get(m).regress(target, regressors);
            Vector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

            double mean = Descriptive.mean(_residualsSingleDataset);
            double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                    Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

            // By centering the individual residual columns, all moments of the mixture become weighted averages of the moments
            // of the individual columns. http://en.wikipedia.org/wiki/Mixture_distribution#Finite_and_countable_mixtures
            for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2)) / std);
                if (this.isMeanCenterResiduals()) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean));
                }
            }

            double[] _f = new double[_residuals.size()];

            for (int k = 0; k < _residuals.size(); k++) {
                _f[k] = _residuals.get(k);
            }

            sum += new AndersonDarlingTest(_f).getASquaredStar();
        }

        return sum / dataSets.size();
    }

    private double pValue(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<>();

        Node _target = node;
        List<Node> _regressors = parents;
        Node target = this.getVariable(variables, _target.getName());
        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : _regressors) {
            Node variable = this.getVariable(variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < dataSets.size(); m++) {
            RegressionResult result = regressions.get(m).regress(target, regressors);
            Vector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

            double mean = Descriptive.mean(_residualsSingleDataset);
            double std = Descriptive.standardDeviation(Descriptive.variance(_residualsSingleDataset.size(),
                    Descriptive.sum(_residualsSingleDataset), Descriptive.sumOfSquares(_residualsSingleDataset)));

            for (int i2 = 0; i2 < _residualsSingleDataset.size(); i2++) {
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean) / std);
                if (this.isMeanCenterResiduals()) {
                    _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2) - mean));
                }
//                _residualsSingleDataset.set(i2, (_residualsSingleDataset.get(i2)));
            }

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _residuals.add(_residualsSingleDataset.get(k));
            }
        }

        double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        return new AndersonDarlingTest(_f).getP();
    }

    private double[] residual(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<>();

        Node _target = node;
        List<Node> _regressors = parents;
        Node target = this.getVariable(variables, _target.getName());
        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : _regressors) {
            Node variable = this.getVariable(variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < dataSets.size(); m++) {
            RegressionResult result = regressions.get(m).regress(target, regressors);
            Vector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            DoubleArrayList _residualsSingleDataset = new DoubleArrayList(residualsSingleDataset.toArray());

            double mean = Descriptive.mean(_residualsSingleDataset);
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

        double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        return _f;
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

    private Graph getCPDAG() {
        return CPDAG;
    }

    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    public boolean isR1Done() {
        return r1Done;
    }

    public void setR1Done(boolean r1Done) {
        this.r1Done = r1Done;
    }

    public boolean isR2Done() {
        return r2Done;
    }

    public void setR2Done(boolean r2Done) {
        this.r2Done = r2Done;
    }

    public boolean isMeekDone() {
        return meekDone;
    }

    public void setMeekDone(boolean meekDone) {
        this.meekDone = meekDone;
    }

    public boolean isStrongR2() {
        return strongR2;
    }

    public void setStrongR2(boolean strongR2) {
        this.strongR2 = strongR2;
    }

    public void setR2Orient2Cycles(boolean r2Orient2Cycles) {
        this.r2Orient2Cycles = r2Orient2Cycles;
    }

    public boolean isR2Orient2Cycles() {
        return r2Orient2Cycles;
    }

    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
    }

    public boolean isMeanCenterResiduals() {
        return meanCenterResiduals;
    }

    public void setMeanCenterResiduals(boolean meanCenterResiduals) {
        this.meanCenterResiduals = meanCenterResiduals;
    }

}


